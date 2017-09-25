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
package com.expedia.www.haystack.trace.reader.unit.stores.readers.es.query

import com.expedia.open.tracing.api.{Field, FieldValuesRequest}
import com.expedia.www.haystack.trace.reader.stores.readers.es.query.FieldValuesQueryGenerator
import com.expedia.www.haystack.trace.reader.unit.BaseUnitTestSpec

class FieldValuesQueryGeneratorSpec extends BaseUnitTestSpec {
  describe("FieldValuesQueryGenerator") {
    it("should generate valid search queries") {
      Given("a trace search request")
      val `type` = "spans"
      val serviceName = "svcName"
      val tagName = "tagName"
      val request = FieldValuesRequest
        .newBuilder()
        .setFieldName("operation")
        .addFilters(Field.newBuilder().setName("service").setValue(serviceName).build())
        .addFilters(Field.newBuilder().setName("tag").setValue(tagName).build())
        .build()
      val queryGenerator = new FieldValuesQueryGenerator("haystack", `type`, "spans")

      When("generating query")
      val query = queryGenerator.generate(request)

      Then("generate a valid query")
      query.getType should be(`type`)
    }
  }
}
