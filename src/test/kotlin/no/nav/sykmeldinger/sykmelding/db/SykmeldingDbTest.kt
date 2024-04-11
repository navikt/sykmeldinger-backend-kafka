package no.nav.sykmeldinger.sykmelding.db

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.sykmeldinger.TestDB
import no.nav.sykmeldinger.utils.TestHelper.Companion.januar
import no.nav.sykmeldinger.utils.fnr
import no.nav.sykmeldinger.utils.sykmelding
import no.nav.sykmeldinger.utils.sykmeldingForlengelse
import no.nav.sykmeldinger.utils.sykmeldt
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingDbTest :
    FunSpec({
        val testDb = TestDB.database
        val sykmeldingDb = SykmeldingDb(testDb)
        beforeTest { TestDB.clearAllData() }

        context("sykmeldingPerioder") {
            test("henter sykmelding med perioder") {
                val sykmelding = sykmelding
                sykmeldingDb.saveOrUpdate(
                    "1",
                    sykmelding,
                    sykmeldt,
                    ValidationResult(Status.OK, emptyList())
                )
                val sykmeldingerKunPerioder = sykmeldingDb.getSykmeldingerKunPerioder(fnr)

                sykmeldingerKunPerioder!!.size shouldBeEqualTo 1
                sykmeldingerKunPerioder.first().sykmeldingsperioder.first().fom shouldBeEqualTo
                    1.januar(2024)
                sykmeldingerKunPerioder.last().sykmeldingsperioder.last().tom shouldBeEqualTo
                    31.januar(2024)
            }
            test("henter sykmelding uten perioder") {
                val sykmeldingerKunPerioder = sykmeldingDb.getSykmeldingerKunPerioder(fnr)
                sykmeldingerKunPerioder shouldBeEqualTo null
            }
            test("henter sykmelding fom og tom med perioder") {
                val sykmelding = sykmelding
                sykmeldingDb.saveOrUpdate(
                    "1",
                    sykmelding,
                    sykmeldt,
                    ValidationResult(Status.OK, emptyList())
                )
                val lastSykmeldingFomTom = sykmeldingDb.getLastSykmeldingFomTom(fnr)

                lastSykmeldingFomTom shouldBeEqualTo (1.januar(2024) to 31.januar(2024))
            }
            test("henter sykmelding fom og tom uten perioder") {
                val lastSykmeldingFomTom = sykmeldingDb.getLastSykmeldingFomTom(fnr)
                lastSykmeldingFomTom shouldBeEqualTo null
            }
            test("henter siste sykmelding i db med flere sykmeldinger") {
                sykmeldingDb.saveOrUpdate(
                    "1",
                    sykmelding,
                    sykmeldt,
                    ValidationResult(Status.OK, emptyList())
                )
                sykmeldingDb.saveOrUpdate(
                    "2",
                    sykmeldingForlengelse,
                    sykmeldt,
                    ValidationResult(Status.OK, emptyList())
                )
                val lastSykmeldingFomTom = sykmeldingDb.getLastSykmeldingFomTom(fnr)
                lastSykmeldingFomTom shouldBeEqualTo (1.januar(2024) to 31.januar(2024))
            }
        }
    })
