package no.nav.sykmeldinger.sykmelding

import no.nav.sykmeldinger.sykmelding.db.SykmeldingDb
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt

class SykmeldingService(
    private val sykmeldingDb: SykmeldingDb,
) {
    fun saveOrUpdate(
        sykmeldingId: String,
        sykmelding: Sykmelding,
        sykmeldt: Sykmeldt,
        okSykmelding: Boolean
    ) {
        return sykmeldingDb.saveOrUpdate(sykmeldingId, sykmelding, sykmeldt, okSykmelding)
    }

    fun deleteSykmelding(sykmeldingId: String) {
        return sykmeldingDb.deleteSykmelding(sykmeldingId)
    }
}
