package no.nav.sykmeldinger.sykmelding

import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.sykmeldinger.sykmelding.model.Adresse
import no.nav.sykmeldinger.sykmelding.model.AktivitetIkkeMulig
import no.nav.sykmeldinger.sykmelding.model.AnnenFraverGrunn
import no.nav.sykmeldinger.sykmelding.model.AnnenFraversArsak
import no.nav.sykmeldinger.sykmelding.model.Arbeidsgiver
import no.nav.sykmeldinger.sykmelding.model.ArbeidsrelatertArsak
import no.nav.sykmeldinger.sykmelding.model.ArbeidsrelatertArsakType
import no.nav.sykmeldinger.sykmelding.model.Behandler
import no.nav.sykmeldinger.sykmelding.model.Diagnose
import no.nav.sykmeldinger.sykmelding.model.ErIArbeid
import no.nav.sykmeldinger.sykmelding.model.ErIkkeIArbeid
import no.nav.sykmeldinger.sykmelding.model.Gradert
import no.nav.sykmeldinger.sykmelding.model.KontaktMedPasient
import no.nav.sykmeldinger.sykmelding.model.MedisinskArsak
import no.nav.sykmeldinger.sykmelding.model.MedisinskArsakType
import no.nav.sykmeldinger.sykmelding.model.MedisinskVurdering
import no.nav.sykmeldinger.sykmelding.model.MeldingTilNav
import no.nav.sykmeldinger.sykmelding.model.Merknad
import no.nav.sykmeldinger.sykmelding.model.Periodetype
import no.nav.sykmeldinger.sykmelding.model.Prognose
import no.nav.sykmeldinger.sykmelding.model.SporsmalSvar
import no.nav.sykmeldinger.sykmelding.model.SvarRestriksjon
import no.nav.sykmeldinger.sykmelding.model.Sykmelding
import no.nav.sykmeldinger.sykmelding.model.Sykmeldingsperiode
import no.nav.sykmeldinger.sykmelding.model.UtenlandskSykmelding
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset

class SykmeldingMapper private constructor() {

    companion object {
        private val koronaForsteFraDato = LocalDate.of(2020, Month.MARCH, 15)
        private val koronaForsteTilDato = LocalDate.of(2021, Month.OCTOBER, 1)
        private val koronaAndreFraDato = LocalDate.of(2021, Month.NOVEMBER, 30)
        private val koronaAndreTilDato = LocalDate.of(2022, Month.JULY, 1)
        private val diagnoserSomGirRedusertArbgiverPeriode = listOf("R991", "U071", "U072", "A23", "R992")

        fun mapToSykmelding(receivedSykmelding: ReceivedSykmelding): Sykmelding {
            val skjermesForPasient = receivedSykmelding.sykmelding.skjermesForPasient
            return Sykmelding(
                mottattTidspunkt = receivedSykmelding.mottattDato.atOffset(ZoneOffset.UTC),
                legekontorOrgnummer = receivedSykmelding.legekontorOrgNr,
                arbeidsgiver = Arbeidsgiver(
                    navn = receivedSykmelding.sykmelding.arbeidsgiver.navn,
                    stillingsprosent = receivedSykmelding.sykmelding.arbeidsgiver.stillingsprosent,
                ),
                sykmeldingsperioder = receivedSykmelding.sykmelding.perioder.map { it.toSykmeldingsPeriode(receivedSykmelding.sykmelding.id) },
                medisinskVurdering = if (skjermesForPasient) { null } else { receivedSykmelding.sykmelding.medisinskVurdering.toMedisinskVurdering() },
                prognose = receivedSykmelding.sykmelding.prognose?.toPrognose(),
                utdypendeOpplysninger = if (skjermesForPasient) { emptyMap() } else { toUtdypendeOpplysninger(receivedSykmelding.sykmelding.utdypendeOpplysninger) },
                tiltakArbeidsplassen = receivedSykmelding.sykmelding.tiltakArbeidsplassen,
                tiltakNAV = if (skjermesForPasient) { null } else { receivedSykmelding.sykmelding.tiltakNAV },
                andreTiltak = if (skjermesForPasient) { null } else { receivedSykmelding.sykmelding.andreTiltak },
                meldingTilNAV = if (skjermesForPasient || receivedSykmelding.sykmelding.meldingTilNAV == null) {
                    null
                } else {
                    MeldingTilNav(
                        bistandUmiddelbart = receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart ?: false,
                        beskrivBistand = receivedSykmelding.sykmelding.meldingTilNAV?.beskrivBistand,
                    )
                },
                meldingTilArbeidsgiver = receivedSykmelding.sykmelding.meldingTilArbeidsgiver,
                kontaktMedPasient = KontaktMedPasient(
                    kontaktDato = receivedSykmelding.sykmelding.kontaktMedPasient.kontaktDato,
                    begrunnelseIkkeKontakt = receivedSykmelding.sykmelding.kontaktMedPasient.begrunnelseIkkeKontakt,
                ),
                behandletTidspunkt = receivedSykmelding.sykmelding.behandletTidspunkt.atOffset(ZoneOffset.UTC),
                behandler = toBehandler(receivedSykmelding),
                syketilfelleStartDato = receivedSykmelding.sykmelding.syketilfelleStartDato,
                navnFastlege = receivedSykmelding.sykmelding.navnFastlege,
                egenmeldt = receivedSykmelding.sykmelding.avsenderSystem.navn == "Egenmeldt",
                papirsykmelding = receivedSykmelding.sykmelding.avsenderSystem.navn == "Papirsykmelding",
                harRedusertArbeidsgiverperiode = receivedSykmelding.sykmelding.medisinskVurdering.getHarRedusertArbeidsgiverperiode(receivedSykmelding.sykmelding.perioder),
                merknader = receivedSykmelding.merknader?.map { Merknad(beskrivelse = it.beskrivelse, type = it.type) },
                rulesetVersion = receivedSykmelding.rulesetVersion,
                utenlandskSykmelding = receivedSykmelding.utenlandskSykmelding?.let { UtenlandskSykmelding(it.land) },
            )
        }

        private fun Periode.toSykmeldingsPeriode(sykmelidngId: String): Sykmeldingsperiode {
            return Sykmeldingsperiode(
                fom = fom,
                tom = tom,
                behandlingsdager = behandlingsdager,
                gradert = gradert?.let { Gradert(it.grad, it.reisetilskudd) },
                innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
                type = finnPeriodetype(this, sykmelidngId),
                aktivitetIkkeMulig = aktivitetIkkeMulig?.let { toAktivietIkkeMulig(it) },
                reisetilskudd = reisetilskudd,
            )
        }

        private fun toAktivietIkkeMulig(it: no.nav.syfo.model.AktivitetIkkeMulig): AktivitetIkkeMulig {
            return AktivitetIkkeMulig(
                medisinskArsak = it.medisinskArsak?.toMedisinskArsak(),
                arbeidsrelatertArsak = it.arbeidsrelatertArsak?.toArbeidsrelatertArsakDto(),
            )
        }
        private fun no.nav.syfo.model.ArbeidsrelatertArsak.toArbeidsrelatertArsakDto(): ArbeidsrelatertArsak {
            return ArbeidsrelatertArsak(
                beskrivelse = beskrivelse,
                arsak = arsak.map { toArbeidsrelatertArsakType(it) },
            )
        }

        private fun toArbeidsrelatertArsakType(arbeidsrelatertArsakType: no.nav.syfo.model.ArbeidsrelatertArsakType): ArbeidsrelatertArsakType {
            return when (arbeidsrelatertArsakType) {
                no.nav.syfo.model.ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING
                no.nav.syfo.model.ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakType.ANNET
            }
        }

        private fun no.nav.syfo.model.MedisinskArsak.toMedisinskArsak(): MedisinskArsak {
            return MedisinskArsak(
                beskrivelse = beskrivelse,
                arsak = arsak.map { toMedisinskArsakTypeDto(it) },
            )
        }

        private fun toMedisinskArsakTypeDto(medisinskArsakType: no.nav.syfo.model.MedisinskArsakType): MedisinskArsakType {
            return when (medisinskArsakType) {
                no.nav.syfo.model.MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING -> MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING
                no.nav.syfo.model.MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND -> MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND
                no.nav.syfo.model.MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET -> MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET
                no.nav.syfo.model.MedisinskArsakType.ANNET -> MedisinskArsakType.ANNET
            }
        }

        private fun finnPeriodetype(periode: Periode, sykmeldingId: String): Periodetype =
            when {
                periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
                periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
                periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
                periode.gradert != null -> Periodetype.GRADERT
                periode.reisetilskudd -> Periodetype.REISETILSKUDD
                else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode for sykmelding med id $sykmeldingId")
            }

        private fun toBehandler(receivedSykmelding: ReceivedSykmelding) = Behandler(
            fornavn = receivedSykmelding.sykmelding.behandler.fornavn,
            mellomnavn = receivedSykmelding.sykmelding.behandler.mellomnavn,
            etternavn = receivedSykmelding.sykmelding.behandler.etternavn,
            adresse = Adresse(
                gate = receivedSykmelding.sykmelding.behandler.adresse.gate,
                postnummer = receivedSykmelding.sykmelding.behandler.adresse.postnummer,
                kommune = receivedSykmelding.sykmelding.behandler.adresse.kommune,
                postboks = receivedSykmelding.sykmelding.behandler.adresse.postboks,
                land = receivedSykmelding.sykmelding.behandler.adresse.land,
            ),
            tlf = receivedSykmelding.sykmelding.behandler.tlf,
        )

        private fun no.nav.syfo.model.MedisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsperioder: List<Periode>): Boolean {
            val sykmeldingsperioderInnenforKoronaregler = sykmeldingsperioder.filter { periodeErInnenforKoronaregler(it.fom, it.tom) }
            if (sykmeldingsperioderInnenforKoronaregler.isEmpty()) {
                return false
            }
            if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose!!.kode)) {
                return true
            } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
                return true
            }
            return checkSmittefare()
        }

        private fun no.nav.syfo.model.MedisinskVurdering.checkSmittefare() =
            annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == no.nav.syfo.model.AnnenFraverGrunn.SMITTEFARE } == true

        private fun periodeErInnenforKoronaregler(fom: LocalDate, tom: LocalDate): Boolean {
            return (fom.isAfter(koronaAndreFraDato) && fom.isBefore(koronaAndreTilDato)) || (fom.isBefore(koronaForsteTilDato) && tom.isAfter(koronaForsteFraDato))
        }

        private fun no.nav.syfo.model.Prognose.toPrognose(): Prognose {
            return Prognose(
                arbeidsforEtterPeriode = arbeidsforEtterPeriode,
                hensynArbeidsplassen = hensynArbeidsplassen,
                erIArbeid = if (erIArbeid != null) {
                    ErIArbeid(
                        egetArbeidPaSikt = erIArbeid!!.egetArbeidPaSikt,
                        annetArbeidPaSikt = erIArbeid!!.annetArbeidPaSikt,
                        arbeidFOM = erIArbeid!!.arbeidFOM,
                        vurderingsdato = erIArbeid!!.vurderingsdato,
                    )
                } else {
                    null
                },
                erIkkeIArbeid = if (erIkkeIArbeid != null) {
                    ErIkkeIArbeid(
                        arbeidsforPaSikt = erIkkeIArbeid!!.arbeidsforPaSikt,
                        arbeidsforFOM = erIkkeIArbeid!!.arbeidsforFOM,
                        vurderingsdato = erIkkeIArbeid!!.vurderingsdato,
                    )
                } else {
                    null
                },
            )
        }

        private fun toUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, no.nav.syfo.model.SporsmalSvar>>): Map<String, Map<String, SporsmalSvar>> {
            return utdypendeOpplysninger.mapValues {
                it.value.mapValues { entry -> entry.value.toSporsmalSvar() }
                    .filterValues { sporsmalSvar -> !sporsmalSvar.restriksjoner.contains(SvarRestriksjon.SKJERMET_FOR_PASIENT) }
            }
        }

        private fun no.nav.syfo.model.SporsmalSvar.toSporsmalSvar(): SporsmalSvar {
            return SporsmalSvar(
                sporsmal = sporsmal,
                svar = svar,
                restriksjoner = restriksjoner.map { it.toSvarRestriksjon() },

            )
        }

        private fun no.nav.syfo.model.SvarRestriksjon.toSvarRestriksjon(): SvarRestriksjon {
            return when (this) {
                no.nav.syfo.model.SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER -> SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER
                no.nav.syfo.model.SvarRestriksjon.SKJERMET_FOR_NAV -> SvarRestriksjon.SKJERMET_FOR_NAV
                no.nav.syfo.model.SvarRestriksjon.SKJERMET_FOR_PASIENT -> SvarRestriksjon.SKJERMET_FOR_PASIENT
            }
        }

        private fun no.nav.syfo.model.MedisinskVurdering.toMedisinskVurdering(): MedisinskVurdering {
            return MedisinskVurdering(
                hovedDiagnose = hovedDiagnose?.toDiagnose(),
                biDiagnoser = biDiagnoser.map { it.toDiagnose() },
                annenFraversArsak = if (annenFraversArsak == null) {
                    null
                } else {
                    AnnenFraversArsak(
                        beskrivelse = annenFraversArsak!!.beskrivelse,
                        grunn = annenFraversArsak!!.grunn.map { it.toAnnenFraverGrunn() },
                    )
                },
                svangerskap = svangerskap,
                yrkesskade = yrkesskade,
                yrkesskadeDato = yrkesskadeDato,
            )
        }

        private fun no.nav.syfo.model.Diagnose.toDiagnose(): Diagnose {
            return Diagnose(
                kode = kode,
                system = system,
                tekst = tekst,
            )
        }

        private fun no.nav.syfo.model.AnnenFraverGrunn.toAnnenFraverGrunn(): AnnenFraverGrunn {
            return when (this) {
                no.nav.syfo.model.AnnenFraverGrunn.ABORT -> AnnenFraverGrunn.ABORT
                no.nav.syfo.model.AnnenFraverGrunn.ARBEIDSRETTET_TILTAK -> AnnenFraverGrunn.ARBEIDSRETTET_TILTAK
                no.nav.syfo.model.AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID -> AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID
                no.nav.syfo.model.AnnenFraverGrunn.BEHANDLING_STERILISERING -> AnnenFraverGrunn.BEHANDLING_STERILISERING
                no.nav.syfo.model.AnnenFraverGrunn.DONOR -> AnnenFraverGrunn.DONOR
                no.nav.syfo.model.AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON -> AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON
                no.nav.syfo.model.AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND -> AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND
                no.nav.syfo.model.AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE -> AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE
                no.nav.syfo.model.AnnenFraverGrunn.SMITTEFARE -> AnnenFraverGrunn.SMITTEFARE
                no.nav.syfo.model.AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET -> AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET
            }
        }
    }
}
