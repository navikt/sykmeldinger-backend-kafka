package no.nav.sykmeldinger.identendring

import no.nav.sykmeldinger.arbeidsforhold.model.Arbeidsforhold
import no.nav.sykmeldinger.sykmelding.model.Adresse
import no.nav.sykmeldinger.sykmelding.model.AktivitetIkkeMulig
import no.nav.sykmeldinger.sykmelding.model.ArbeidsrelatertArsak
import no.nav.sykmeldinger.sykmelding.model.ArbeidsrelatertArsakType
import no.nav.sykmeldinger.sykmelding.model.Behandler
import no.nav.sykmeldinger.sykmelding.model.KontaktMedPasient
import no.nav.sykmeldinger.sykmelding.model.Periodetype
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldingsperiode
import no.nav.sykmeldinger.sykmelding.model.Sykmeldt
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun getSykmeldt(fnr: String): Sykmeldt =
    Sykmeldt(
        fnr = fnr,
        fornavn = "Annet",
        mellomnavn = "Mellomnavn",
        etternavn = "Etternavn",
    )

fun getArbeidsforhold(fnr: String) =
    Arbeidsforhold(
        id = 1,
        fnr = fnr,
        orgnummer = "888888888",
        juridiskOrgnummer = "999999999",
        orgNavn = "Bedriften AS",
        fom = LocalDate.now().minusYears(3),
        tom = null,
    )

fun getSykmelding(): Sykmelding {
    return Sykmelding(
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        legekontorOrgnummer = null,
        arbeidsgiver = null,
        sykmeldingsperioder = listOf(
            Sykmeldingsperiode(
                fom = LocalDate.now().minusWeeks(1),
                tom = LocalDate.now().plusDays(5),
                gradert = null,
                behandlingsdager = null,
                innspillTilArbeidsgiver = null,
                type = Periodetype.AKTIVITET_IKKE_MULIG,
                aktivitetIkkeMulig = AktivitetIkkeMulig(
                    medisinskArsak = null,
                    arbeidsrelatertArsak = ArbeidsrelatertArsak(
                        beskrivelse = "",
                        arsak = listOf(ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING),
                    ),
                ),
                reisetilskudd = false,
            ),
        ),
        medisinskVurdering = null,
        prognose = null,
        utdypendeOpplysninger = emptyMap(),
        tiltakArbeidsplassen = null,
        tiltakNAV = null,
        andreTiltak = null,
        meldingTilNAV = null,
        meldingTilArbeidsgiver = null,
        kontaktMedPasient = KontaktMedPasient(
            kontaktDato = null,
            begrunnelseIkkeKontakt = null,
        ),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        behandler = Behandler(
            fornavn = "Doktor",
            mellomnavn = null,
            etternavn = "Doktorsen",
            adresse = Adresse(null, null, null, null, null),
            tlf = null,
        ),
        syketilfelleStartDato = null,
        navnFastlege = null,
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = false,
        merknader = null,
        rulesetVersion = null,
        utenlandskSykmelding = null,
    )
}
