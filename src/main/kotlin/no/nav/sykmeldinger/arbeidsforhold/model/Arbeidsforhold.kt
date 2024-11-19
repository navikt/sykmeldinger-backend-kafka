package no.nav.sykmeldinger.arbeidsforhold.model

import java.time.LocalDate

enum class ArbeidsforholdType {
    FORENKLET_OPPGJOERSORDNING,
    FRILANSER_OPPDRAGSTAKER_HONORAR_PERSONER_MM,
    MARITIMT_ARBEIDSFORHOLD,
    ORDINAERT_ARBEIDSFORHOLD,
    PENSJON_OG_ANDRE_TYPER_YTELSER_UTEN_ANSETTELSESFORHOLD;

    companion object {
        fun parse(kode: String): ArbeidsforholdType {
            return when (kode) {
                "forenkletOppgjoersordning" -> FORENKLET_OPPGJOERSORDNING
                "frilanserOppdragstakerHonorarPersonerMm" ->
                    FRILANSER_OPPDRAGSTAKER_HONORAR_PERSONER_MM
                "maritimtArbeidsforhold" -> MARITIMT_ARBEIDSFORHOLD
                "ordinaertArbeidsforhold" -> ORDINAERT_ARBEIDSFORHOLD
                "pensjonOgAndreTyperYtelserUtenAnsettelsesforhold" ->
                    PENSJON_OG_ANDRE_TYPER_YTELSER_UTEN_ANSETTELSESFORHOLD
                else -> throw IllegalArgumentException("Incorrect arbeidsforhold type $kode")
            }
        }
    }
}

data class Arbeidsforhold(
    val id: Int,
    val fnr: String,
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val orgNavn: String,
    val fom: LocalDate,
    val tom: LocalDate?,
    val type: ArbeidsforholdType?,
)
