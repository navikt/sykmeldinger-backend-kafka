package no.nav.sykmeldinger.arbeidsforhold

import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import java.time.LocalDate

class ArbeidsforholdService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient
) {
    suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }

        val arbeidsgiverList = ArrayList<Arbeidsforhold>()
        arbeidsgivere.filter {
            it.arbeidssted.type == ArbeidsstedType.Underenhet &&
                (
                    it.ansettelsesperiode.sluttdato == null || it.ansettelsesperiode.sluttdato.isAfter(
                        ansettelsesperiodeFom
                    )
                    )
        }.sortedWith(
            compareByDescending(nullsLast()) {
                it.ansettelsesperiode.sluttdato
            }
        ).forEach { aaregArbeidsforhold ->
            val organisasjonsinfo =
                organisasjonsinfoClient.getOrganisasjonsnavn(aaregArbeidsforhold.arbeidssted.getOrgnummer())
            arbeidsgiverList.add(
                Arbeidsforhold(
                    id = aaregArbeidsforhold.navArbeidsforholdId,
                    fnr = fnr,
                    orgnummer = aaregArbeidsforhold.arbeidssted.getOrgnummer(),
                    juridiskOrgnummer = aaregArbeidsforhold.opplysningspliktig.getJuridiskOrgnummer(),
                    orgNavn = organisasjonsinfo.navn.getNameAsString(),
                    fom = aaregArbeidsforhold.ansettelsesperiode.startdato,
                    tom = aaregArbeidsforhold.ansettelsesperiode.sluttdato
                )
            )
        }
        return arbeidsgiverList.distinctBy { listOf(it.fnr, it.orgnummer, it.juridiskOrgnummer, it.orgNavn, it.fom, it.tom) }
    }
}
