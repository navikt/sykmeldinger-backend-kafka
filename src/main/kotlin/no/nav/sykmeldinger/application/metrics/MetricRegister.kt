package no.nav.sykmeldinger.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "sykmeldingerbackend_kafka"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val HTTP_CLIENT_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("client_calls_duration_seconds")
    .help("durations for outgoing requests in seconds")
    .register()

val NL_TOPIC_COUNTER: Counter = Counter.build()
    .labelNames("status")
    .name("nl_topic_counter")
    .namespace(METRICS_NS)
    .help("Counts NL-messages from kafka (new or deleted)")
    .register()
