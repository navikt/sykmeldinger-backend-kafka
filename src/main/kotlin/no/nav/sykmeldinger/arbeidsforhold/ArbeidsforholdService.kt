package no.nav.sykmeldinger.arbeidsforhold

import java.time.LocalDate
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold

class ArbeidsforholdService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient,
    private val arbeidsforholdDb: ArbeidsforholdDb,
) {
    suspend fun insertOrUpdate(arbeidsforhold: Arbeidsforhold) {
        arbeidsforholdDb.insertOrUpdate(arbeidsforhold)
    }

    suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }

        val arbeidsgiverList = ArrayList<Arbeidsforhold>()
        arbeidsgivere
            .filter {
                it.arbeidssted.type == ArbeidsstedType.Underenhet &&
                    arbeidsforholdErGyldig(it.ansettelsesperiode)
            }
            .sortedWith(
                compareByDescending(nullsLast()) { it.ansettelsesperiode.sluttdato },
            )
            .forEach { aaregArbeidsforhold ->
                val organisasjonsinfo =
                    organisasjonsinfoClient.getOrganisasjonsnavn(
                        aaregArbeidsforhold.arbeidssted.getOrgnummer()
                    )
                arbeidsgiverList.add(
                    Arbeidsforhold(
                        id = aaregArbeidsforhold.navArbeidsforholdId,
                        fnr = fnr,
                        orgnummer = aaregArbeidsforhold.arbeidssted.getOrgnummer(),
                        juridiskOrgnummer =
                            aaregArbeidsforhold.opplysningspliktig.getJuridiskOrgnummer(),
                        orgNavn = organisasjonsinfo.navn.getNameAsString(),
                        fom = aaregArbeidsforhold.ansettelsesperiode.startdato,
                        tom = aaregArbeidsforhold.ansettelsesperiode.sluttdato,
                    ),
                )
            }
        return arbeidsgiverList.distinctBy {
            listOf(it.fnr, it.orgnummer, it.juridiskOrgnummer, it.orgNavn, it.fom, it.tom)
        }
    }

    fun getArbeidsforholdFromDb(fnr: String): List<Arbeidsforhold> {
        return arbeidsforholdDb.getArbeidsforhold(fnr)
    }

    fun deleteArbeidsforhold(id: Int) {
        arbeidsforholdDb.deleteArbeidsforhold(id)
    }

    private fun arbeidsforholdErGyldig(ansettelsesperiode: Ansettelsesperiode): Boolean {
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        return ansettelsesperiode.sluttdato == null ||
            ansettelsesperiode.sluttdato.isAfter(ansettelsesperiodeFom)
    }
}
