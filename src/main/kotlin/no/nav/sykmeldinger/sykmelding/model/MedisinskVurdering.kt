package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate

data class MedisinskVurdering(
    val hovedDiagnose: Diagnose?,
    val biDiagnoser: List<Diagnose>,
    val annenFraversArsak: AnnenFraversArsak?,
    val svangerskap: Boolean,
    val yrkesskade: Boolean,
    val yrkesskadeDato: LocalDate?,
)
