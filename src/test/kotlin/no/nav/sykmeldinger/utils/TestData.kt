package no.nav.sykmeldinger.utils

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.sykmeldinger.sykmelding.model.Adresse
import no.nav.sykmeldinger.sykmelding.model.Behandler
import no.nav.sykmeldinger.sykmelding.model.KontaktMedPasient
import no.nav.sykmeldinger.sykmelding.model.Periodetype
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldingsperiode
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import no.nav.sykmeldinger.utils.TestHelper.Companion.februar
import no.nav.sykmeldinger.utils.TestHelper.Companion.januar

val fnr = "123"
val sykmeldt =
    Sykmeldt(
        fnr = fnr,
        fornavn = "navn",
        mellomnavn = null,
        etternavn = "navnesen",
        foedselsdato = null
    )

val sykmeldingFom = 1.januar(2024)
val sykmeldingTom = 31.januar(2024)
val sykmelding =
    createSykmelding(1.januar(2024) to 15.januar(2024), 16.januar(2024) to 31.januar(2024))
val sykmeldingForlengelse =
    createSykmelding(
        1.februar(2024) to 29.februar(2024),
    )

fun createSykmelding(vararg dates: Pair<LocalDate, LocalDate>): Sykmelding {
    return Sykmelding(
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        legekontorOrgnummer = null,
        arbeidsgiver = null,
        sykmeldingsperioder =
            dates.map {
                Sykmeldingsperiode(
                    it.first,
                    it.second,
                    null,
                    null,
                    null,
                    Periodetype.AKTIVITET_IKKE_MULIG,
                    null,
                    false,
                )
            },
        medisinskVurdering = null,
        prognose = null,
        utdypendeOpplysninger = emptyMap(),
        tiltakArbeidsplassen = null,
        tiltakNAV = null,
        andreTiltak = null,
        meldingTilNAV = null,
        meldingTilArbeidsgiver = null,
        kontaktMedPasient = KontaktMedPasient(null, null),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        behandler =
            Behandler(
                "fornavn",
                null,
                "etternavn",
                Adresse(null, null, null, null, null),
                null,
            ),
        syketilfelleStartDato = null,
        navnFastlege = null,
        egenmeldt = null,
        papirsykmelding = null,
        harRedusertArbeidsgiverperiode = null,
        merknader = null,
        rulesetVersion = null,
        utenlandskSykmelding = null,
    )
}
