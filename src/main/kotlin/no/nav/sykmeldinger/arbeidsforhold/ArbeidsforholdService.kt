package no.nav.sykmeldinger.arbeidsforhold

import java.time.LocalDate
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.model.ArbeidsforholdType

class ArbeidsforholdService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val organisasjonsinfoClient: OrganisasjonsinfoClient,
    private val arbeidsforholdDb: ArbeidsforholdDb,
) {
    suspend fun insertOrUpdate(arbeidsforhold: Arbeidsforhold) {
        arbeidsforholdDb.insertOrUpdate(arbeidsforhold)
    }

    suspend fun updateArbeidsforhold(fnr: String) {
        val arbeidsforhold = getArbeidsforhold(fnr)
        val arbeidsforholdFraDb = getArbeidsforholdFromDb(fnr)

        val slettesfraDb =
            getArbeidsforholdSomSkalSlettes(
                arbeidsforholdDb = arbeidsforholdFraDb,
                arbeidsforholdAareg = arbeidsforhold,
            )

        if (slettesfraDb.isNotEmpty()) {
            slettesfraDb.forEach {
                /*   log.info(
                    "Sletter utdatert arbeidsforhold med id $it",
                )
                secureLog.info(
                    "Sletter fra arbeidsforhold, siden db og areg ulike, fnr: $fnr, arbeidsforholdId: $it",
                )*/
                deleteArbeidsforhold(it)
            }
        }
        arbeidsforhold.forEach { insertOrUpdate(it) }
    }

    fun getArbeidsforholdSomSkalSlettes(
        arbeidsforholdAareg: List<Arbeidsforhold>,
        arbeidsforholdDb: List<Arbeidsforhold>
    ): List<Int> {
        if (
            arbeidsforholdDb.size == arbeidsforholdAareg.size &&
                arbeidsforholdDb.toHashSet() == arbeidsforholdAareg.toHashSet()
        ) {
            return emptyList()
        }

        val arbeidsforholdAaregMap: HashMap<Int, Arbeidsforhold> =
            HashMap(arbeidsforholdAareg.associateBy { it.id })
        val arbeidsforholdDbMap: HashMap<Int, Arbeidsforhold> =
            HashMap(arbeidsforholdDb.associateBy { it.id })

        return arbeidsforholdDbMap.filter { arbeidsforholdAaregMap[it.key] == null }.keys.toList()
    }

    suspend fun getArbeidsforhold(fnr: String): List<Arbeidsforhold> {
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(fnr = fnr)

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }

        val arbeidsgiverList =
            arbeidsgivere
                .filter { it.arbeidssted.type == ArbeidsstedType.Underenhet }
                .filter { arbeidsforholdErGyldig(it.ansettelsesperiode) }
                .sortedWith(
                    compareByDescending(nullsLast()) { it.ansettelsesperiode.sluttdato },
                )
                .map { aaregArbeidsforhold ->
                    val orgnavn =
                        organisasjonsinfoClient.getOrgnavn(
                            aaregArbeidsforhold.arbeidssted.getOrgnummer()
                        )
                    val arbeidsforholdType = ArbeidsforholdType.parse(aaregArbeidsforhold.type.kode)
                    Arbeidsforhold(
                        id = aaregArbeidsforhold.navArbeidsforholdId,
                        fnr = fnr,
                        orgnummer = aaregArbeidsforhold.arbeidssted.getOrgnummer(),
                        juridiskOrgnummer =
                            aaregArbeidsforhold.opplysningspliktig.getJuridiskOrgnummer(),
                        orgNavn = orgnavn,
                        fom = aaregArbeidsforhold.ansettelsesperiode.startdato,
                        tom = aaregArbeidsforhold.ansettelsesperiode.sluttdato,
                        type = arbeidsforholdType,
                    )
                }
        return arbeidsgiverList
    }

    suspend fun getArbeidsforholdFromDb(fnr: String): List<Arbeidsforhold> {
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

    suspend fun deleteArbeidsforholdIds(deleted: List<Int>) {
        arbeidsforholdDb.deleteArbeidsforholdIds(deleted)
    }
}
