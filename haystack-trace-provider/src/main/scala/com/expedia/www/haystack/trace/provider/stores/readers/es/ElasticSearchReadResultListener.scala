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

package com.expedia.www.haystack.trace.provider.stores.readers.es

import com.codahale.metrics.{Meter, Timer}
import com.expedia.www.haystack.trace.provider.exceptions.ElasticSearchClientError
import io.searchbox.client.{JestResult, JestResultHandler}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Promise

class ElasticSearchReadResultListener(promise: Promise[JestResult],
                                      timer: Timer.Context,
                                      failure: Meter) extends JestResultHandler[JestResult]  {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[ElasticSearchReadResultListener])

  override def completed(result: JestResult): Unit = {
    if(result.getResponseCode >= 300) {
      val ex = new ElasticSearchClientError(result.getResponseCode)
      LOGGER.error(s"Failed in reading from elasticsearch", ex)
      timer.stop()
      failure.mark()
      promise.failure(ex)
    } else {
      timer.stop()
      promise.success(JestResult)
    }
  }

  override def failed(ex: Exception): Unit = {
    LOGGER.error("Failed in reading from elasticsearch", ex)
    failure.mark()
    timer.stop()
    promise.failure(ex)
  }
}