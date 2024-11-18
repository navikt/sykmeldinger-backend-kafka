package no.nav.sykmeldinger.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "sykmeldingerbackend_kafka"

val HTTP_HISTOGRAM: Histogram =
    Histogram.Builder()
        .labelNames("path")
        .name("requests_duration_seconds")
        .help("http requests durations for incoming requests in seconds")
        .register()

val HTTP_CLIENT_HISTOGRAM: Histogram =
    Histogram.Builder()
        .labelNames("path")
        .name("client_calls_duration_seconds")
        .help("durations for outgoing requests in seconds")
        .register()

val NL_TOPIC_COUNTER: Counter =
    Counter.build()
        .labelNames("status")
        .name("nl_topic_counter")
        .namespace(METRICS_NS)
        .help("Counts NL-messages from kafka (new or deleted)")
        .register()

val NL_NAVN_COUNTER: Counter =
    Counter.build()
        .name("nl_navn_counter")
        .namespace(METRICS_NS)
        .help("Antall endrede navn for NL")
        .register()

val NYTT_FNR_COUNTER: Counter =
    Counter.build()
        .namespace(METRICS_NS)
        .name("nytt_fnr_count")
        .help("Antall endrede fnr som har blitt oppdatert i databasen")
        .register()

val SLETTET_ARBFORHOLD_COUNTER: Counter =
    Counter.build()
        .name("slettet_arbf_counter")
        .namespace(METRICS_NS)
        .help("Antall slettede arbeidsforhold")
        .register()

val ARBEIDSFORHOLD_TYPE_COUNTER: Counter =
    Counter.build()
        .name("arbeidsforhold_type")
        .namespace(METRICS_NS)
        .help("Forskjellige typer arbeidsforhold")
        .labelNames("type")
        .register()
