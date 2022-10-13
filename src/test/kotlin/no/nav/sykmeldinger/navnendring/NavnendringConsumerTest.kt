package no.nav.sykmeldinger.navnendring

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDb
import no.nav.sykmeldinger.narmesteleder.db.NarmestelederDbModel
import no.nav.sykmeldinger.pdl.model.Navn
import no.nav.sykmeldinger.pdl.model.PdlPerson
import no.nav.sykmeldinger.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object NavnendringConsumerTest : FunSpec({
    val kafkaConsumer = mockk<KafkaConsumer<String, Personhendelse>>()
    val testDb = TestDB.database
    val narmesteLederDb = NarmestelederDb(testDb)
    val pdlPersonService = mockk<PdlPersonService>()
    val navnendingConsumer = NavnendringConsumer(
        "topic",
        kafkaConsumer,
        ApplicationState(alive = true, ready = true),
        narmesteLederDb,
        pdlPersonService
    )

    beforeEach {
        clearMocks(pdlPersonService)
        coEvery { pdlPersonService.getPerson(any(), any()) } returns PdlPerson(Navn("Fornavn", "Mellomnavn", "Etternavn"), "12345678910")
        TestDB.clearAllData()
    }

    context("NavnendringConsumer-logikk") {
        test("Oppdaterer navn hvis personhendelse er relatert til navn og lederen finnes i db") {
            val nlId = UUID.randomUUID().toString()
            narmesteLederDb.insertOrUpdate(NarmestelederDbModel(nlId, "8888", "brukerFnr", "12345678910", "Leder Ledersen", OffsetDateTime.now()))
            val personhendelse = getPersonhendelse("12345678910", no.nav.person.pdl.leesah.navn.Navn("Fornavn", null, "Etternavn", "Fornavn Etternavn", null, LocalDate.now()))

            navnendingConsumer.handlePersonhendelse(personhendelse)

            coVerify(exactly = 1) { pdlPersonService.getPerson("12345678910", any()) }
            TestDB.getNarmesteleder(nlId)?.navn shouldBeEqualTo "Fornavn Mellomnavn Etternavn"
        }
        test("Oppdaterer ikke navn hvis personhendelse er relatert til navn men lederen ikke finnes i db") {
            val personhendelse = getPersonhendelse("12345678910", no.nav.person.pdl.leesah.navn.Navn("Fornavn", null, "Etternavn", "Fornavn Etternavn", null, LocalDate.now()))

            navnendingConsumer.handlePersonhendelse(personhendelse)

            coVerify(exactly = 0) { pdlPersonService.getPerson(any(), any()) }
        }
        test("Oppdaterer ikke navn hvis personhendelse ikke er relatert til navn") {
            val personhendelse = getPersonhendelse("12345678910", null)

            navnendingConsumer.handlePersonhendelse(personhendelse)

            coVerify(exactly = 0) { pdlPersonService.getPerson(any(), any()) }
        }
    }
})

fun getPersonhendelse(fnr: String, navn: no.nav.person.pdl.leesah.navn.Navn?): Personhendelse {
    return Personhendelse(
        "hendelseId",
        listOf(fnr, "annetfnr"),
        "PDL",
        OffsetDateTime.now().toInstant(),
        "type",
        Endringstype.OPPRETTET,
        "tidligereId",
        navn
    )
}
