package no.nav.sykmeldinger.arbeidsforhold

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Arbeidssted
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ident
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.IdentType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

object ArbeidsforholdServiceTest : FunSpec({
    val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
    val organisasjonsinfoClient = mockk<OrganisasjonsinfoClient>()

    val arbeidsforholdService = ArbeidsforholdService(arbeidsforholdClient, organisasjonsinfoClient)

    beforeTest {
        clearMocks(
            arbeidsforholdClient,
            organisasjonsinfoClient
        )
        coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns getOrganisasjonsinfo()
    }
    context("ArbeidsforholderService") {
        test("getArbeidsforhold returnerer liste med arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321"))),
                    Ansettelsesperiode(startdato = LocalDate.now().minusYears(3), sluttdato = null)
                ),
                AaregArbeidsforhold(
                    2,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusMonths(6),
                        sluttdato = LocalDate.now().minusWeeks(3)
                    )
                ),
                AaregArbeidsforhold(
                    3,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusWeeks(2),
                        sluttdato = LocalDate.now().plusMonths(3)
                    )
                )
            )
            coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns getOrganisasjonsinfo() andThen getOrganisasjonsinfo(
                navn = "Navn 2"
            ) andThen getOrganisasjonsinfo(navn = "Navn 2")

            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")

            arbeidsforhold.size shouldBeEqualTo 3
            arbeidsforhold[0].id shouldBeEqualTo 1
            arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
            arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
            arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusYears(3)
            arbeidsforhold[0].tom shouldBeEqualTo null

            arbeidsforhold[1].id shouldBeEqualTo 3
            arbeidsforhold[1].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[1].orgnummer shouldBeEqualTo "88888888"
            arbeidsforhold[1].juridiskOrgnummer shouldBeEqualTo "999999999"
            arbeidsforhold[1].orgNavn shouldBeEqualTo "Navn 2"
            arbeidsforhold[1].fom shouldBeEqualTo LocalDate.now().minusWeeks(2)
            arbeidsforhold[1].tom shouldBeEqualTo LocalDate.now().plusMonths(3)

            arbeidsforhold[2].id shouldBeEqualTo 2
            arbeidsforhold[2].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[2].orgnummer shouldBeEqualTo "88888888"
            arbeidsforhold[2].juridiskOrgnummer shouldBeEqualTo "999999999"
            arbeidsforhold[2].orgNavn shouldBeEqualTo "Navn 2"
            arbeidsforhold[2].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
            arbeidsforhold[2].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
        }
        test("getArbeidsforhold returnerer tom liste hvis bruker ikke har arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns emptyList()

            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")

            arbeidsforhold.size shouldBeEqualTo 0
        }
        test("getArbeidsforhold filtrerer bort arbeidsforhold med annen type enn underenhet") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Person, listOf(Ident(IdentType.FOLKEREGISTERIDENT, "fnr"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321"))),
                    Ansettelsesperiode(startdato = LocalDate.now().minusYears(3), sluttdato = null)
                ),
                AaregArbeidsforhold(
                    2,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusMonths(6),
                        sluttdato = LocalDate.now().minusWeeks(3)
                    )
                )
            )

            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")

            arbeidsforhold.size shouldBeEqualTo 1
            arbeidsforhold[0].id shouldBeEqualTo 2
            arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[0].orgnummer shouldBeEqualTo "88888888"
            arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "999999999"
            arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
            arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
            arbeidsforhold[0].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
        }
        test("getArbeidsforhold filtrerer bort arbeidsforhold med sluttdato for mer enn 4 måneder siden") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusMonths(6),
                        sluttdato = LocalDate.now().minusWeeks(3)
                    )
                ),
                AaregArbeidsforhold(
                    2,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "88888888"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "999999999"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusYears(6),
                        sluttdato = LocalDate.now().minusMonths(5)
                    )
                )
            )

            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")
            arbeidsforhold.size shouldBeEqualTo 1
            arbeidsforhold[0].id shouldBeEqualTo 1
            arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
            arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
            arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
            arbeidsforhold[0].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
        }
        test("getArbeidsforhold filtrerer bort duplikate arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusMonths(6),
                        sluttdato = LocalDate.now().minusWeeks(3)
                    )
                ),
                AaregArbeidsforhold(
                    2,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789"))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321"))),
                    Ansettelsesperiode(
                        startdato = LocalDate.now().minusMonths(6),
                        sluttdato = LocalDate.now().minusWeeks(3)
                    )
                )
            )

            val arbeidsforhold = arbeidsforholdService.getArbeidsforhold("12345678901")
            arbeidsforhold.size shouldBeEqualTo 1
            arbeidsforhold[0].id shouldBeEqualTo 1
            arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
            arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
            arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusMonths(6)
            arbeidsforhold[0].tom shouldBeEqualTo LocalDate.now().minusWeeks(3)
        }
    }
})
