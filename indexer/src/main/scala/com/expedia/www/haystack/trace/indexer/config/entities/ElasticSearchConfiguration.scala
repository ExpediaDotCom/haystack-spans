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

package com.expedia.www.haystack.trace.indexer.config.entities

/**
  * defines the config parameters for elastic search writes
  * @param endpoint: http endpoint to connect
  * @param indexTemplateJson: template as json that will be applied when the app runs, this is optional
  * @param consistencyLevel: consistency level of writes, for e.g. one, quoram
  * @param indexNamePrefix: prefix for naming the elastic search index
  * @param indexType: elastic search index type
  * @param connectionTimeoutMillis: connection timeout in millis
  * @param readTimeoutMillis: read timeout in millis
  * @param maxInFlightRequests: max writes that can be run in parallel
  */
case class ElasticSearchConfiguration(endpoint: String,
                                      indexTemplateJson: Option[String],
                                      consistencyLevel: String,
                                      indexNamePrefix: String,
                                      indexType: String,
                                      connectionTimeoutMillis: Int,
                                      readTimeoutMillis: Int,
                                      maxInFlightRequests: Int)
