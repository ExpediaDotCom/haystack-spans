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

package com.expedia.www.haystack.span.collector.unit

import com.expedia.open.tracing.Span
import com.expedia.open.tracing.buffer.SpanBuffer
import com.expedia.www.haystack.span.collector.serdes.SpanBufferDeserializer
import org.scalatest.{FunSpec, Matchers}

class SpanBufferDeserializerSpec extends FunSpec with Matchers {
  val traceId = "TRACEID_1"
  val spanId = "SPANID_1"
  val parentId = "PARENTID_1"

  describe("SpanBuffer deserializer") {
    it("should deserialize the span buffer proto bytes") {
      val deser = new SpanBufferDeserializer()
      val span = Span.newBuilder().setTraceId(traceId).setDuration(100).setSpanId(spanId).setParentSpanId(parentId)
      val spanBuffer = SpanBuffer.newBuilder().setTraceId(traceId).addChildSpans(span).build()
      val obj = deser.deserialize(spanBuffer.toByteArray)
      obj.getTraceId shouldEqual traceId
      obj.getChildSpansCount shouldBe 1
      obj.getChildSpans(0).getTraceId shouldEqual traceId
      obj.getChildSpans(0).getParentSpanId shouldEqual parentId
      obj.getChildSpans(0).getSpanId shouldEqual spanId
      obj.getChildSpans(0).getDuration shouldBe 100
    }

    it("should return null if deserialize the empty data bytes") {
      val deser = new SpanBufferDeserializer()
      deser.deserialize(Array.emptyByteArray) shouldBe null
    }

    it("should return null if deserialize the illegal data bytes") {
      val span = Span.newBuilder().setTraceId(traceId).setDuration(100).setSpanId(spanId).setParentSpanId(parentId).build()
      val deser = new SpanBufferDeserializer()
      deser.deserialize(span.toByteArray) shouldBe null
    }
  }
}