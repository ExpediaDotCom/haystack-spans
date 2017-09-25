/*
 *  Copyright 2017 Expedia, Inc.
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */

package com.expedia.www.haystack.trace.reader.stores

import java.util

import com.expedia.open.tracing.api._
import com.expedia.www.haystack.trace.commons.config.entities.{CassandraConfiguration, WhitelistIndexFieldConfiguration}
import com.expedia.www.haystack.trace.reader.config.entities.ElasticSearchConfiguration
import com.expedia.www.haystack.trace.reader.exceptions.InvalidTraceIdInDocument
import com.expedia.www.haystack.trace.reader.metrics.MetricsSupport
import com.expedia.www.haystack.trace.reader.stores.readers.cassandra.CassandraReader
import com.expedia.www.haystack.trace.reader.stores.readers.es.ElasticSearchReader
import com.expedia.www.haystack.trace.reader.stores.readers.es.query.{FieldValuesQueryGenerator, TraceSearchQueryGenerator}
import io.searchbox.client.JestResult
import io.searchbox.core.SearchResult
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

class CassandraEsTraceStore(cassandraConfiguration: CassandraConfiguration,
                            esConfiguration: ElasticSearchConfiguration,
                            indexConfiguration: WhitelistIndexFieldConfiguration)
                           (implicit val executor: ExecutionContextExecutor)
  extends TraceStore with MetricsSupport {
  private val ES_AGGREGATIONS_FIELD_NAME = "aggregations"
  private val ES_BUCKETS_FIELD_NAME = "buckets"
  private val ES_KEY_FIELD_NAME = "key"
  private val NESTED_DOC_NAME = "spans"

  private val LOGGER = LoggerFactory.getLogger(classOf[ElasticSearchReader])
  private val traceRejected = metricRegistry.meter("search.trace.rejected")

  private val cassandraReader: CassandraReader = new CassandraReader(cassandraConfiguration)
  private val esReader: ElasticSearchReader = new ElasticSearchReader(esConfiguration)

  private val idRegex = """([a-zA-z0-9-]*)_([a-zA-z0-9]*)""".r

  private val traceSearchQueryGenerator = new TraceSearchQueryGenerator(esConfiguration.indexNamePrefix, esConfiguration.indexType, NESTED_DOC_NAME)
  private val fieldValuesQueryGenerator = new FieldValuesQueryGenerator(esConfiguration.indexNamePrefix, esConfiguration.indexType, NESTED_DOC_NAME)

  override def searchTraces(request: TracesSearchRequest): Future[List[Trace]] = {
    esReader
      .search(traceSearchQueryGenerator.generate(request))
      .flatMap(extractTraces)
  }

  private def extractTraces(result: SearchResult): Future[List[Trace]] = {
    // go through each hit and fetch trace for parsed traceId
    val traceFutures = result.getHits(classOf[java.util.Map[String, String]]).toList
      .flatMap(hit => fetchTrace(hit.source))

    // wait for all Futures to complete and then map them to Traces
    Future
      .sequence(liftToTry(traceFutures))
      .map(_.flatMap(retrieveTriedTrace))
  }

  private def fetchTrace(sourceMap: util.Map[String, String]): Option[Future[Trace]] = {
    parseTraceId(sourceMap) match {
      case Success(traceId) =>
        Some(getTrace(traceId))
      case Failure(ex) =>
        LOGGER.warn("Invalid traceId, rejected searched trace", ex)
        traceRejected.mark()
        None
    }
  }

  override def getTrace(traceId: String): Future[Trace] = {
    cassandraReader.readTrace(traceId)
  }

  private def parseTraceId(sourceMap: util.Map[String, String]): Try[String] = {
    val docId = sourceMap.get(JestResult.ES_METADATA_ID)

    docId match {
      case idRegex(traceId, _) => Success(traceId)
      case _ => Failure(InvalidTraceIdInDocument(docId))
    }
  }

  private def retrieveTriedTrace(triedTrace: Try[Trace]): Option[Trace] = {
    triedTrace match {
      case Success(trace) =>
        Some(trace)
      case Failure(ex) =>
        LOGGER.warn("traceId not found in cassandra, rejected searched trace", ex)
        traceRejected.mark()
        None
    }
  }

  // convert all Futures to Try to make sure they all complete
  private def liftToTry(traceFutures: List[Future[Trace]]): List[Future[Try[Trace]]] = traceFutures.map { f =>
    f.map(Try(_)).recover { case t: Throwable => Failure(t) }
  }

  override def getFieldNames(): Future[List[String]] = {
    Future.successful(indexConfiguration.indexableTags.map(_.name))
  }

  override def getFieldValues(request: FieldValuesRequest): Future[List[String]] = {
    esReader
      .search(fieldValuesQueryGenerator.generate(request))
      .map(extractFieldValues(_, request.getFieldName))
  }

  private def extractFieldValues(result: SearchResult, fieldName: String): List[String] =
    result
      .getJsonObject
      .getAsJsonObject(ES_AGGREGATIONS_FIELD_NAME)
      .getAsJsonObject(NESTED_DOC_NAME)
      .getAsJsonObject(fieldName)
      .getAsJsonArray(ES_BUCKETS_FIELD_NAME)
      .map(element => element.getAsJsonObject.get(ES_KEY_FIELD_NAME).getAsString)
      .toList

  override def close(): Unit = {
    cassandraReader.close()
    esReader.close()
  }
}
