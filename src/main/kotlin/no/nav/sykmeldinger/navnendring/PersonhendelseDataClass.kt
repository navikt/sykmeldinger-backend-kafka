package no.nav.sykmeldinger.navnendring

import java.time.Instant
import java.time.LocalDate
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.navn.Navn
import no.nav.person.pdl.leesah.navn.OriginaltNavn

data class PersonhendelseDataClass(
    val hendelseId: String?,
    val personidenter: List<String>?,
    val master: String?,
    val opprettet: Instant?,
    val opplysningstype: String?,
    val endringstype: String?,
    val tidligereHendelseId: String?,
    val navn: NavnDataClass?
)

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
        endringstype = endringstype.name,
        tidligereHendelseId = tidligereHendelseId,
        navn = navn?.let { mapNavnToNavnDataClass(navn) }
    )
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
