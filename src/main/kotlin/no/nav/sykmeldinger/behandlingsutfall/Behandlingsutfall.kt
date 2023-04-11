package no.nav.sykmeldinger.behandlingsutfall

import no.nav.syfo.model.RuleInfo

data class Behandlingsutfall(
    val sykmeldingId: String,
    val status: String,
    val ruleHits: List<RuleInfo>,
)
