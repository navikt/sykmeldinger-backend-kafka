package no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model

data class AaregArbeidsforhold(
    val navArbeidsforholdId: Int,
    val arbeidssted: Arbeidssted,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode
)
