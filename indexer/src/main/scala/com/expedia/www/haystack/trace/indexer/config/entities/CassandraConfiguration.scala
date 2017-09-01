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

import com.datastax.driver.core.ConsistencyLevel

case class SocketConfiguration(maxConnectionPerHost: Int,
                               keepAlive: Boolean,
                               connectionTimeoutMillis: Int,
                               readTimeoutMills: Int)

/**
  * defines the configuration parameters for cassandra
  * @param endpoints: list of cassandra endpoints
  * @param autoDiscoverEnabled: if autodiscovery is enabled, then 'endpoints' config parameter will be ignored
  * @param awsNodeDiscovery: discovery configuration for aws, optional. This is applied only if autoDiscoverEnabled is true
  * @param keyspace: cassandra keyspance
  * @param tableName: cassandra table name
  * @param autoCreateSchema: apply cql and create keyspace and tables if not exist, optional
  * @param consistencyLevel: consistency level of writes
  * @param recordTTLInSec: record ttl in seconds
  * @param socket: socket configuration like maxConnections, timeouts and keepAlive
  * @param maxInFlightRequests: defines the max parallel writes to cassandra
  */
case class CassandraConfiguration(endpoints: List[String],
                                  autoDiscoverEnabled: Boolean,
                                  awsNodeDiscovery: Option[AwsNodeDiscoveryConfiguration],
                                  keyspace: String,
                                  tableName: String,
                                  autoCreateSchema: Option[String],
                                  consistencyLevel: ConsistencyLevel,
                                  recordTTLInSec: Int,
                                  socket: SocketConfiguration,
                                  maxInFlightRequests: Int)
