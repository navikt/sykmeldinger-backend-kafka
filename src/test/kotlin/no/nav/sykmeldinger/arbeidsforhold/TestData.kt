package no.nav.sykmeldinger.arbeidsforhold

import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.model.Navn
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.model.Organisasjonsinfo

fun getOrganisasjonsinfo(navn: String = "Navn 1"): Organisasjonsinfo {
    return Organisasjonsinfo(
        Navn(
            navn,
            null,
            null,
            null,
            null,
            null,
        ),
    )
}
