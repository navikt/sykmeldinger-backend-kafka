package no.nav.sykmeldinger.identendring

import no.nav.person.pdl.aktor.v2.Identifikator

data class IdentifikatorDataClass(
    val idnummer: String?,
    val type: String?,
    val gjeldende: Boolean?
)

fun Identifikator.toIdentifikatorDataClass(): IdentifikatorDataClass {
    return IdentifikatorDataClass(
        idnummer = this.idnummer,
        type = this.type.name,
        gjeldende = this.gjeldende
    )
}
