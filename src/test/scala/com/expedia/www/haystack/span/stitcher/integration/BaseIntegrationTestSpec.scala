/*
 *  Copyright 2017 Expedia, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.expedia.www.haystack.span.stitcher.integration

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import java.util.{Properties, UUID}

import com.expedia.open.tracing.Span
import com.expedia.open.tracing.stitch.StitchedSpan
import com.expedia.www.haystack.span.stitcher.integration.serdes.{SpanSerializer, StitchSpanDeserializer}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.kafka.streams.integration.utils.{EmbeddedKafkaCluster, IntegrationTestUtils}
import org.apache.kafka.streams.{KeyValue, StreamsConfig}
import org.scalatest._

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object EmbeddedKafka {
  val CLUSTER = new EmbeddedKafkaCluster(1)
  CLUSTER.start()
}

abstract class BaseIntegrationTestSpec extends WordSpec with GivenWhenThen with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  case class SpanDescription(traceId: String, spanIdPrefix: String)

  protected var scheduler: ScheduledExecutorService = _

  protected val PUNCTUATE_INTERVAL_MS = 2000L
  protected val SPAN_STITCH_WINDOW_MS = 5000
  protected val AUTO_COMMIT_INTERVAL_MS = 3000
  protected val MAX_STITCHED_RECORDS_IN_MEM = 100
  protected val MAX_WAIT_FOR_OUTPUT_MS = 12000

  protected val PRODUCER_CONFIG = new Properties()
  protected val RESULT_CONSUMER_CONFIG = new Properties()
  protected val CHANGELOG_CONSUMER_CONFIG = new Properties()
  protected val STREAMS_CONFIG = new Properties()
  protected val scheduledJobFuture: ScheduledFuture[_] = null

  private val APP_ID = getClass.getSimpleName
  protected val INPUT_TOPIC = "spans"
  protected val OUTPUT_TOPIC = "stitchspans"
  protected val CHANGELOG_TOPIC = s"$APP_ID-StitchedSpanStore-changelog"

  override def beforeAll() {
    scheduler = Executors.newScheduledThreadPool(2)
  }

  override def afterAll(): Unit = {
    scheduler.shutdownNow()
  }

  override def beforeEach() {
    EmbeddedKafka.CLUSTER.createTopic(INPUT_TOPIC, 2, 1)
    EmbeddedKafka.CLUSTER.createTopic(OUTPUT_TOPIC)

    PRODUCER_CONFIG.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EmbeddedKafka.CLUSTER.bootstrapServers)
    PRODUCER_CONFIG.put(ProducerConfig.ACKS_CONFIG, "all")
    PRODUCER_CONFIG.put(ProducerConfig.RETRIES_CONFIG, "0")
    PRODUCER_CONFIG.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
    PRODUCER_CONFIG.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[SpanSerializer])

    RESULT_CONSUMER_CONFIG.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EmbeddedKafka.CLUSTER.bootstrapServers)
    RESULT_CONSUMER_CONFIG.put(ConsumerConfig.GROUP_ID_CONFIG, APP_ID + "-result-consumer")
    RESULT_CONSUMER_CONFIG.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    RESULT_CONSUMER_CONFIG.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    RESULT_CONSUMER_CONFIG.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StitchSpanDeserializer])

    CHANGELOG_CONSUMER_CONFIG.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EmbeddedKafka.CLUSTER.bootstrapServers)
    CHANGELOG_CONSUMER_CONFIG.put(ConsumerConfig.GROUP_ID_CONFIG, APP_ID + "-changelog-consumer")
    CHANGELOG_CONSUMER_CONFIG.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    CHANGELOG_CONSUMER_CONFIG.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    CHANGELOG_CONSUMER_CONFIG.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StitchSpanDeserializer])

    STREAMS_CONFIG.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, EmbeddedKafka.CLUSTER.bootstrapServers)
    STREAMS_CONFIG.put(StreamsConfig.APPLICATION_ID_CONFIG, APP_ID)
    STREAMS_CONFIG.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    STREAMS_CONFIG.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0")
    STREAMS_CONFIG.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "300")
    STREAMS_CONFIG.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams")

    IntegrationTestUtils.purgeLocalStreamsState(STREAMS_CONFIG)
  }

  override def afterEach(): Unit = {
    EmbeddedKafka.CLUSTER.deleteTopicsAndWait(INPUT_TOPIC, OUTPUT_TOPIC)
  }

  def randomSpan(traceId: String,
                 spanId: String = UUID.randomUUID().toString): Span = {
    Span.newBuilder()
      .setTraceId(traceId)
      .setParentSpanId(UUID.randomUUID().toString)
      .setSpanId(spanId)
      .setOperationName("some-op")
      .setStartTime(System.currentTimeMillis())
      .build()
  }

  protected def produceSpansAsync(maxSpans: Int,
                                  produceInterval: FiniteDuration,
                                  spansDescr: List[SpanDescription],
                                  startTimestamp: Long = 0L): ScheduledFuture[_] = {
    var timestamp = startTimestamp
    var idx = 0L
    scheduler.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = {
        if(idx < maxSpans) {
          val spans = spansDescr.map(sd => {
            new KeyValue[String, Span](sd.traceId, randomSpan(sd.traceId, s"${sd.spanIdPrefix}-$idx"))
          }).asJava
          IntegrationTestUtils.produceKeyValuesSynchronouslyWithTimestamp(
            INPUT_TOPIC,
            spans,
            PRODUCER_CONFIG,
            timestamp)
          timestamp = timestamp + (PUNCTUATE_INTERVAL_MS / (maxSpans - 1))
        }
        idx = idx + 1
      }
    }, 0, produceInterval.toMillis, TimeUnit.MILLISECONDS)
  }

  protected def validateChildSpans(stitchedSpan: StitchedSpan,
                                   traceId: String,
                                   spanIdPrefix: String,
                                   childSpanCount: Int): Unit = {
    stitchedSpan.getTraceId shouldBe traceId

    stitchedSpan.getChildSpansCount shouldBe childSpanCount

    (0 until stitchedSpan.getChildSpansCount).toList foreach { idx =>
      stitchedSpan.getChildSpans(idx).getSpanId shouldBe s"$spanIdPrefix-$idx"
      stitchedSpan.getChildSpans(idx).getTraceId shouldBe stitchedSpan.getTraceId
      stitchedSpan.getChildSpans(idx).getParentSpanId should not be null
      stitchedSpan.getChildSpans(idx).getOperationName shouldBe "some-op"
    }
  }
}
