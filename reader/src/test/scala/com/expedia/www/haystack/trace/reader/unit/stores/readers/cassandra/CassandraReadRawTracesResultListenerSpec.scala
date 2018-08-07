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

package com.expedia.www.haystack.trace.reader.unit.stores.readers.cassandra

import java.nio.ByteBuffer

import com.codahale.metrics.{Meter, Timer}
import com.datastax.driver.core.{ResultSet, ResultSetFuture, Row}
import com.expedia.open.tracing.Span
import com.expedia.open.tracing.api.Trace
import com.expedia.open.tracing.buffer.SpanBuffer
import com.expedia.www.haystack.trace.commons.clients.cassandra.CassandraTableSchema
import com.expedia.www.haystack.trace.reader.stores.readers.cassandra.{CassandraReadRawTracesResultListener, CassandraReadTraceResultListener}
import com.expedia.www.haystack.trace.reader.unit.BaseUnitTestSpec
import io.grpc.{Status, StatusException}
import org.easymock.EasyMock

import scala.collection.JavaConverters._
import scala.concurrent.Promise

class CassandraReadRawTracesResultListenerSpec extends BaseUnitTestSpec {

  describe("cassandra read listener for raw traces") {
    it("should read the rows, de-serialized spans column and return the complete trace") {
      val mockReadResult = mock[ResultSetFuture]
      val resultSet = mock[ResultSet]
      val promise = mock[Promise[Seq[Trace]]]
      val failureMeter = mock[Meter]
      val timer = mock[Timer.Context]

      val mockSpanBufferRow_1 = mock[Row]
      val mockSpanBufferRow_2 = mock[Row]
      val mockSpanBufferRow_3 = mock[Row]

      val span_1 = Span.newBuilder().setTraceId("TRACE_ID1").setSpanId("SPAN_ID_1")
      val span_2 = Span.newBuilder().setTraceId("TRACE_ID1").setSpanId("SPAN_ID_2")
      val spanBuffer_1 = SpanBuffer.newBuilder().setTraceId("TRACE_ID1").addChildSpans(span_1).build()
      val spanBuffer_2 = SpanBuffer.newBuilder().setTraceId("TRACE_ID1").addChildSpans(span_2).build()

      val span_3 = Span.newBuilder().setTraceId("TRACE_ID2").setSpanId("SPAN_ID_3")
      val spanBuffer_3 = SpanBuffer.newBuilder().setTraceId("TRACE_ID2").addChildSpans(span_3).build()


      val capturedTraces = EasyMock.newCapture[Seq[Trace]]()
      expecting {
        timer.close()
        mockReadResult.get().andReturn(resultSet)
        resultSet.all().andReturn(List(mockSpanBufferRow_1, mockSpanBufferRow_2, mockSpanBufferRow_3).asJava)
        mockSpanBufferRow_1.getBytes(CassandraTableSchema.SPANS_COLUMN_NAME).andReturn(ByteBuffer.wrap(spanBuffer_1.toByteArray))
        mockSpanBufferRow_2.getBytes(CassandraTableSchema.SPANS_COLUMN_NAME).andReturn(ByteBuffer.wrap(spanBuffer_2.toByteArray))
        mockSpanBufferRow_3.getBytes(CassandraTableSchema.SPANS_COLUMN_NAME).andReturn(ByteBuffer.wrap(spanBuffer_3.toByteArray))
        promise.success(EasyMock.capture(capturedTraces)).andReturn(promise)
      }

      whenExecuting(mockReadResult, promise, failureMeter, timer, resultSet, mockSpanBufferRow_1, mockSpanBufferRow_2, mockSpanBufferRow_3) {
        val listener = new CassandraReadRawTracesResultListener(mockReadResult, timer, failureMeter, promise)
        listener.run()
        capturedTraces.getValue.map(
          capturedTrace =>
            capturedTrace.getTraceId match {
              case "TRACE_ID1" =>
                capturedTrace.getChildSpansList.asScala.map(_.getSpanId) should contain allOf("SPAN_ID_1", "SPAN_ID_2")
              case "TRACE_ID2" =>
                capturedTrace.getChildSpansList.asScala.map(_.getSpanId) should contain ("SPAN_ID_3")
            }
        )
      }
    }

    it("should read the rows, fail to de-serialized spans column and then return an empty trace") {
      val mockReadResult = mock[ResultSetFuture]
      val resultSet = mock[ResultSet]
      val promise = mock[Promise[Seq[Trace]]]
      val failureMeter = mock[Meter]
      val timer = mock[Timer.Context]

      val mockSpanBufferRow_1 = mock[Row]
      val mockSpanBufferRow_2 = mock[Row]

      val span_1 = Span.newBuilder().setTraceId("TRACE_ID").setSpanId("SPAN_ID_1")
      val spanBuffer_1 = SpanBuffer.newBuilder().setTraceId("TRACE_ID").addChildSpans(span_1).build()

      expecting {
        timer.close()
        failureMeter.mark()
        mockReadResult.get().andReturn(resultSet)
        resultSet.all().andReturn(List(mockSpanBufferRow_1, mockSpanBufferRow_2).asJava)
        mockSpanBufferRow_1.getBytes(CassandraTableSchema.SPANS_COLUMN_NAME).andReturn(ByteBuffer.wrap(spanBuffer_1.toByteArray))
        mockSpanBufferRow_2.getBytes(CassandraTableSchema.SPANS_COLUMN_NAME).andReturn(ByteBuffer.wrap("illegal bytes".getBytes))
        promise.failure(EasyMock.anyObject()).andReturn(promise)
      }

      whenExecuting(mockReadResult, promise, failureMeter, timer, resultSet, mockSpanBufferRow_1, mockSpanBufferRow_2) {
        val listener = new CassandraReadRawTracesResultListener(mockReadResult, timer, failureMeter, promise)
        listener.run()
      }
    }

    it("should return an exception for empty traceId") {
      val mockReadResult = mock[ResultSetFuture]
      val resultSet = mock[ResultSet]
      val promise = mock[Promise[Trace]]
      val failureMeter = mock[Meter]
      val timer = mock[Timer.Context]

      val capturedException = EasyMock.newCapture[StatusException]()
      expecting {
        timer.close()
        failureMeter.mark()
        mockReadResult.get().andReturn(resultSet)
        resultSet.all().andReturn(List[Row]().asJava)
        promise.failure(EasyMock.capture(capturedException)).andReturn(promise)
      }

      whenExecuting(mockReadResult, promise, failureMeter, timer, resultSet) {
        val listener = new CassandraReadTraceResultListener(mockReadResult, timer, failureMeter, promise)
        listener.run()
        capturedException.getValue.getStatus.getCode shouldEqual Status.NOT_FOUND.getCode
      }
    }
  }
}
