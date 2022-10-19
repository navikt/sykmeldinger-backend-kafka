package no.nav.sykmeldinger.arbeidsforhold.kafka

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.arbeidsforhold.ArbeidsforholdService
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ansettelsesperiode
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Arbeidssted
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.ArbeidsstedType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Ident
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.IdentType
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.Opplysningspliktig
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.client.OrganisasjonsinfoClient
import no.nav.sykmeldinger.arbeidsforhold.db.ArbeidsforholdDb
import no.nav.sykmeldinger.arbeidsforhold.getOrganisasjonsinfo
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdHendelse
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.ArbeidsforholdKafka
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Arbeidstaker
import no.nav.sykmeldinger.arbeidsforhold.kafka.model.Endringstype
import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.LocalDate

object ArbeidsforholdConsumerTest : FunSpec({
    val testDb = TestDB.database
    val arbeidsforholdDb = ArbeidsforholdDb(testDb)
    val sykmeldingDb = SykmeldingDb(testDb)
    val kafkaConsumer = mockk<KafkaConsumer<String, ArbeidsforholdHendelse>>()
    val organisasjonsinfoClient = mockk<OrganisasjonsinfoClient>()
    val arbeidsforholdClient = mockk<ArbeidsforholdClient>()
    val arbeidsforholdService = ArbeidsforholdService(arbeidsforholdClient, organisasjonsinfoClient, arbeidsforholdDb)
    val arbeidsforholdConsumer = ArbeidsforholdConsumer(
        kafkaConsumer,
        ApplicationState(alive = true, ready = true),
        "topic",
        sykmeldingDb,
        arbeidsforholdService
    )

    beforeEach {
        TestDB.clearAllData()
        clearMocks(organisasjonsinfoClient, arbeidsforholdClient)
        coEvery { organisasjonsinfoClient.getOrganisasjonsnavn(any()) } returns getOrganisasjonsinfo()
    }

    context("ArbeidsforholdConsumer - handleArbeidsforholdHendelse") {
        test("Lagrer nytt arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true))),
                    Ansettelsesperiode(startdato = LocalDate.now().minusYears(3), sluttdato = null)
                )
            )
            sykmeldingDb.saveOrUpdateSykmeldt(Sykmeldt("12345678901", "Per", null, "Person"))
            val arbeidsforholdHendelse = ArbeidsforholdHendelse(
                id = 34L,
                endringstype = Endringstype.Opprettelse,
                arbeidsforhold = ArbeidsforholdKafka(
                    1,
                    Arbeidstaker(listOf(Ident(IdentType.FOLKEREGISTERIDENT, "12345678901", true)))
                )
            )

            arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

            val arbeidsforhold = arbeidsforholdService.getArbeidsforholdFromDb("12345678901")
            arbeidsforhold.size shouldBeEqualTo 1
            arbeidsforhold[0].id shouldBeEqualTo 1
            arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
            arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
            arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusYears(3)
            arbeidsforhold[0].tom shouldBeEqualTo null

            coVerify { arbeidsforholdClient.getArbeidsforhold(any()) }
        }
        test("Lagrer ikke utdatert arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true))),
                    Ansettelsesperiode(startdato = LocalDate.now().minusYears(3), sluttdato = LocalDate.now().minusYears(1))
                )
            )
            sykmeldingDb.saveOrUpdateSykmeldt(Sykmeldt("12345678901", "Per", null, "Person"))
            val arbeidsforholdHendelse = ArbeidsforholdHendelse(
                id = 34L,
                endringstype = Endringstype.Opprettelse,
                arbeidsforhold = ArbeidsforholdKafka(
                    1,
                    Arbeidstaker(listOf(Ident(IdentType.FOLKEREGISTERIDENT, "12345678901", true)))
                )
            )

            arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

            arbeidsforholdService.getArbeidsforholdFromDb("12345678901").size shouldBeEqualTo 0
            coVerify { arbeidsforholdClient.getArbeidsforhold(any()) }
        }
        test("Oppdaterer eksisterende arbeidsforhold") {
            coEvery { arbeidsforholdClient.getArbeidsforhold(any()) } returns listOf(
                AaregArbeidsforhold(
                    1,
                    Arbeidssted(ArbeidsstedType.Underenhet, listOf(Ident(IdentType.ORGANISASJONSNUMMER, "123456789", true))),
                    Opplysningspliktig(listOf(Ident(IdentType.ORGANISASJONSNUMMER, "987654321", true))),
                    Ansettelsesperiode(startdato = LocalDate.now().minusYears(3), sluttdato = null)
                )
            )
            sykmeldingDb.saveOrUpdateSykmeldt(Sykmeldt("12345678901", "Per", null, "Person"))
            arbeidsforholdService.insertOrUpdate(
                Arbeidsforhold(
                    id = 1,
                    fnr = "12345678901",
                    orgnummer = "123456789",
                    juridiskOrgnummer = "987654321",
                    orgNavn = "Gammel Navn AS",
                    fom = LocalDate.now().minusYears(3),
                    tom = LocalDate.now().minusDays(3)
                )
            )
            val arbeidsforholdHendelse = ArbeidsforholdHendelse(
                id = 34L,
                endringstype = Endringstype.Endring,
                arbeidsforhold = ArbeidsforholdKafka(
                    1,
                    Arbeidstaker(listOf(Ident(IdentType.FOLKEREGISTERIDENT, "12345678901", true)))
                )
            )

            arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

            val arbeidsforhold = arbeidsforholdService.getArbeidsforholdFromDb("12345678901")
            arbeidsforhold.size shouldBeEqualTo 1
            arbeidsforhold[0].id shouldBeEqualTo 1
            arbeidsforhold[0].fnr shouldBeEqualTo "12345678901"
            arbeidsforhold[0].orgnummer shouldBeEqualTo "123456789"
            arbeidsforhold[0].juridiskOrgnummer shouldBeEqualTo "987654321"
            arbeidsforhold[0].orgNavn shouldBeEqualTo "Navn 1"
            arbeidsforhold[0].fom shouldBeEqualTo LocalDate.now().minusYears(3)
            arbeidsforhold[0].tom shouldBeEqualTo null

            coVerify { arbeidsforholdClient.getArbeidsforhold(any()) }
        }
        test("Sletter arbeidsforhold") {
            sykmeldingDb.saveOrUpdateSykmeldt(Sykmeldt("12345678901", "Per", null, "Person"))
            arbeidsforholdService.insertOrUpdate(
                Arbeidsforhold(
                    id = 1,
                    fnr = "12345678901",
                    orgnummer = "123456789",
                    juridiskOrgnummer = "987654321",
                    orgNavn = "Gammel Navn AS",
                    fom = LocalDate.now().minusYears(3),
                    tom = LocalDate.now().minusDays(3)
                )
            )
            val arbeidsforholdHendelse = ArbeidsforholdHendelse(
                id = 34L,
                endringstype = Endringstype.Sletting,
                arbeidsforhold = ArbeidsforholdKafka(
                    1,
                    Arbeidstaker(listOf(Ident(IdentType.FOLKEREGISTERIDENT, "12345678901", true)))
                )
            )

            arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

            arbeidsforholdService.getArbeidsforholdFromDb("12345678901").size shouldBeEqualTo 0
            coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforhold(any()) }
        }
        test("Ignorerer hendelse hvis fnr ikke finnes i databasen fra før") {
            val arbeidsforholdHendelse = ArbeidsforholdHendelse(
                id = 34L,
                endringstype = Endringstype.Opprettelse,
                arbeidsforhold = ArbeidsforholdKafka(
                    15,
                    Arbeidstaker(listOf(Ident(IdentType.FOLKEREGISTERIDENT, "12345678901", true)))
                )
            )

            arbeidsforholdConsumer.handleArbeidsforholdHendelse(arbeidsforholdHendelse)

            arbeidsforholdService.getArbeidsforholdFromDb("12345678901").size shouldBeEqualTo 0
            coVerify(exactly = 0) { arbeidsforholdClient.getArbeidsforhold(any()) }
        }
    }
})
