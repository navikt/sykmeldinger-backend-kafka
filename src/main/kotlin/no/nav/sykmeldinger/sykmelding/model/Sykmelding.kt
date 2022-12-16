package no.nav.sykmeldinger.sykmelding.model

import java.time.LocalDate
import java.time.OffsetDateTime

data class Sykmelding(
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val arbeidsgiver: Arbeidsgiver?,
    val sykmeldingsperioder: List<Sykmeldingsperiode>,
    val medisinskVurdering: MedisinskVurdering?,
    val prognose: Prognose?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>,
    val tiltakArbeidsplassen: String?,
    val tiltakNAV: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNav?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasient,
    val behandletTidspunkt: OffsetDateTime,
    val behandler: Behandler,
    val syketilfelleStartDato: LocalDate?,
    val navnFastlege: String?,
    val egenmeldt: Boolean?,
    val papirsykmelding: Boolean?,
    val harRedusertArbeidsgiverperiode: Boolean?,
    val merknader: List<Merknad>?,
    val rulesetVersion: String?,
    val utenlandskSykmelding: UtenlandskSykmelding?
)
