package no.nav.sykmeldinger.pdl

import java.time.Instant
import java.time.LocalDate
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.navn.Navn
import no.nav.person.pdl.leesah.navn.OriginaltNavn

data class PersonhendelseDataClass(
    val hendelseId: String,
    val personidenter: List<String>,
    val master: String?,
    val opprettet: Instant?,
    val opplysningstype: String?,
    val endringstype: Endringstype,
    val tidligereHendelseId: String?,
    val navn: NavnDataClass?
)

enum class Endringstype {
    OPPRETTET,
    KORRIGERT,
    ANNULLERT,
    OPPHOERT
}

data class NavnDataClass(
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
    val forkortetNavn: String?,
    val originaltNavn: OriginaltNavnDataClass?,
    val gyldigFraOgMed: LocalDate?
)

data class OriginaltNavnDataClass(
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
)

fun Personhendelse.toDataClass(): PersonhendelseDataClass {
    return PersonhendelseDataClass(
        hendelseId = hendelseId,
        personidenter = personidenter,
        master = master,
        opprettet = opprettet,
        opplysningstype = opplysningstype,
        endringstype = endringstype.toEndringstype(),
        tidligereHendelseId = tidligereHendelseId,
        navn = navn?.let { mapNavnToNavnDataClass(navn) }
    )
}

private fun no.nav.person.pdl.leesah.Endringstype.toEndringstype(): Endringstype {
    return when (this) {
        no.nav.person.pdl.leesah.Endringstype.OPPRETTET -> Endringstype.OPPRETTET
        no.nav.person.pdl.leesah.Endringstype.KORRIGERT -> Endringstype.KORRIGERT
        no.nav.person.pdl.leesah.Endringstype.ANNULLERT -> Endringstype.ANNULLERT
        no.nav.person.pdl.leesah.Endringstype.OPPHOERT -> Endringstype.OPPHOERT
    }
}

fun mapNavnToNavnDataClass(navn: Navn): NavnDataClass {
    return NavnDataClass(
        fornavn = navn.fornavn,
        mellomnavn = navn.mellomnavn,
        etternavn = navn.etternavn,
        forkortetNavn = navn.forkortetNavn,
        originaltNavn =
            navn.originaltNavn?.let {
                mapOriginaltNavnToOriginaltNavnDataClass(navn.originaltNavn)
            },
        gyldigFraOgMed = navn.gyldigFraOgMed
    )
}

fun mapOriginaltNavnToOriginaltNavnDataClass(originaltNavn: OriginaltNavn): OriginaltNavnDataClass {
    return OriginaltNavnDataClass(
        fornavn = originaltNavn.fornavn,
        mellomnavn = originaltNavn.mellomnavn,
        etternavn = originaltNavn.etternavn
    )
}
