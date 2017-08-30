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

package com.expedia.www.haystack.span.bufferer.integration

import java.util

import com.expedia.open.tracing.buffer.SpanBuffer
import com.expedia.www.haystack.span.bufferer.StreamTopology
import com.expedia.www.haystack.span.bufferer.config.entities.{ChangelogConfiguration, KafkaConfiguration, SpanBufferConfiguration}
import org.apache.kafka.streams.integration.utils.IntegrationTestUtils
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp
import org.apache.kafka.streams.processor.TopologyBuilder.AutoOffsetReset
import org.apache.kafka.streams.{KeyValue, StreamsConfig}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

class SpanBufferingTopologySpec extends BaseIntegrationTestSpec {
  private val MAX_CHILD_SPANS = 5
  private val TRACE_ID = "unique-trace-id"

  "Span Buffering Topology" should {
    s"consume spans from '$INPUT_TOPIC' topic and buffer them together for every unique traceId" in {
      Given("a set of spans with spanBuffering and kafka specific configurations")
      val spanBufferConfig = SpanBufferConfiguration(
        INITIAL_STORE_CAPACITY,
        MAX_SPAN_BUFFERS_IN_MEM,
        PUNCTUATE_INTERVAL_MS,
        SPAN_BUFFERING_WINDOW_MS,
        AUTO_COMMIT_INTERVAL_MS)
      val kafkaConfig = KafkaConfiguration(new StreamsConfig(STREAMS_CONFIG),
        OUTPUT_TOPIC,
        INPUT_TOPIC,
        AutoOffsetReset.EARLIEST,
        new FailOnInvalidTimestamp,
        ChangelogConfiguration(enabled = false))

      When(s"spans are produced in '$INPUT_TOPIC' topic async, and kafka-streams topology is started")
      val SPAN_ID_PREFIX = "span-id"
      produceSpansAsync(MAX_CHILD_SPANS,
        1.seconds,
        List(SpanDescription(TRACE_ID, SPAN_ID_PREFIX)))
      val topology = new StreamTopology(kafkaConfig, spanBufferConfig)
      topology.start()

      Then(s"we should read one span buffer object from '$OUTPUT_TOPIC' topic")
      val result: util.List[KeyValue[String, SpanBuffer]] =
          IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(RESULT_CONSUMER_CONFIG, OUTPUT_TOPIC, 1, MAX_WAIT_FOR_OUTPUT_MS)
      validate(result, MAX_CHILD_SPANS, SPAN_ID_PREFIX)

      repeatTestWithNewerSpanIds()
      topology.close() shouldBe true
    }
  }

  // this test is useful to check if we are not emitting the old spans if the same traceId reappears later
  private def repeatTestWithNewerSpanIds(): Unit = {
    Given(s"a set of new span ids and same traceId '$TRACE_ID'")
    val SPAN_ID_2_PREFIX = "span-id-2"
    When(s"these spans are produced in '$INPUT_TOPIC' topic on the currently running topology")
    produceSpansAsync(MAX_CHILD_SPANS,
      1.seconds,
      List(SpanDescription(TRACE_ID, SPAN_ID_2_PREFIX)), startTimestamp = SPAN_BUFFERING_WINDOW_MS + 100L)

    Then(s"we should read see newer spans in the buffered object from '$OUTPUT_TOPIC' topic")
    val result: util.List[KeyValue[String, SpanBuffer]] =
      IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(RESULT_CONSUMER_CONFIG, OUTPUT_TOPIC, 1, MAX_WAIT_FOR_OUTPUT_MS)

    validate(result, MAX_CHILD_SPANS, SPAN_ID_2_PREFIX)
  }

  // validate the span buffers
  private def validate(records: util.List[KeyValue[String, SpanBuffer]],
                                   childSpanCount: Int,
                                   spanIdPrefix: String): Unit = {
    // expect only one span buffer object
    records.size() shouldBe 1
    validateChildSpans(records.head.value, TRACE_ID, spanIdPrefix, MAX_CHILD_SPANS)
  }
}
