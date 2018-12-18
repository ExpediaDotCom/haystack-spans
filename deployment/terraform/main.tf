module "trace-indexer" {
  source = "trace-indexer"
  indexer_image = "expediadotcom/haystack-trace-indexer:${var.traces["version"]}"
  storage_backend_image = "expediadotcom/haystack-trace-backend-cassandra:${var.traces["version"]}"
  replicas = "${var.traces["indexer_instances"]}"
  enabled = "${var.traces["enabled"]}"
  cpu_limit = "${var.traces["indexer_cpu_limit"]}"
  cpu_request = "${var.traces["indexer_cpu_request"]}"
  memory_limit = "${var.traces["indexer_memory_limit"]}"
  memory_request = "${var.traces["indexer_memory_request"]}"
  jvm_memory_limit = "${var.traces["indexer_jvm_memory_limit"]}"
  env_vars = "${var.traces["indexer_environment_overrides"]}"
  elasticsearch_template = "${var.traces["indexer_elasticsearch_template"]}"
  namespace = "${var.namespace}"
  kafka_endpoint = "${var.kafka_hostname}:${var.kafka_port}"
  elasticsearch_port = "${var.elasticsearch_port}"
  elasticsearch_hostname = "${var.elasticsearch_hostname}"
  cassandra_hostname = "${var.cassandra_hostname}"
  graphite_hostname = "${var.graphite_hostname}"
  graphite_port = "${var.graphite_port}"
  graphite_enabled = "${var.graphite_enabled}"
  node_selector_label = "${var.node_selector_label}"
  kubectl_executable_name = "${var.kubectl_executable_name}"
  kubectl_context_name = "${var.kubectl_context_name}"
}

module "trace-reader" {
  source = "trace-reader"
  reader_image = "expediadotcom/haystack-trace-reader:${var.traces["version"]}"
  storage_backend_image = "expediadotcom/haystack-trace-backend-cassandra:${var.traces["version"]}"
  replicas = "${var.traces["reader_instances"]}"
  namespace = "${var.namespace}"
  elasticsearch_endpoint = "${var.elasticsearch_hostname}:${var.elasticsearch_port}"
  cassandra_hostname = "${var.cassandra_hostname}"
  graphite_hostname = "${var.graphite_hostname}"
  graphite_port = "${var.graphite_port}"
  graphite_enabled = "${var.graphite_enabled}"
  enabled = "${var.traces["enabled"]}"
  node_selector_label = "${var.node_selector_label}"
  kubectl_executable_name = "${var.kubectl_executable_name}"
  kubectl_context_name = "${var.kubectl_context_name}"
  cpu_limit = "${var.traces["reader_cpu_limit"]}"
  cpu_request = "${var.traces["reader_cpu_request"]}"
  memory_limit = "${var.traces["reader_memory_limit"]}"
  memory_request = "${var.traces["reader_memory_request"]}"
  jvm_memory_limit = "${var.traces["reader_jvm_memory_limit"]}"
  env_vars = "${var.traces["reader_environment_overrides"]}"
}

module "es-indices" {
  source = "es-indices"
  enabled = "${var.traces["enabled"]}"
  namespace = "${var.namespace}"
  node_selector_label = "${var.node_selector_label}"
  kubectl_executable_name = "${var.kubectl_executable_name}"
  kubectl_context_name = "${var.kubectl_context_name}"
  elasticsearch_port = "${var.elasticsearch_port}"
  elasticsearch_hostname = "${var.elasticsearch_hostname}"
}
