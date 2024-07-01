package no.nav.sykmeldinger.pdl

import java.time.Instant
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.navn.Navn
import no.nav.person.pdl.leesah.navn.OriginaltNavn

data class PersonhendelseDataClass(
    val hendelseId: String,
    val personidenter: List<String>,
    val master: String?,
    val opprettet: Instant?,
    val opplysningstype: String?,
    val endringstype: EndringstypeDataClass,
    val tidligereHendelseId: String?,
    val navn: NavnDataClass?
)

enum class EndringstypeDataClass {
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
        endringstype = toEndringstype(endringstype),
        tidligereHendelseId = tidligereHendelseId,
        navn = navn?.let { mapNavnToNavnDataClass(navn) }
    )
}

private fun toEndringstype(endringstype: Endringstype): EndringstypeDataClass {
    return when (endringstype) {
        Endringstype.OPPRETTET -> EndringstypeDataClass.OPPRETTET
        Endringstype.KORRIGERT -> EndringstypeDataClass.KORRIGERT
        Endringstype.ANNULLERT -> EndringstypeDataClass.ANNULLERT
        Endringstype.OPPHOERT -> EndringstypeDataClass.OPPHOERT
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
    )
}

fun mapOriginaltNavnToOriginaltNavnDataClass(originaltNavn: OriginaltNavn): OriginaltNavnDataClass {
    return OriginaltNavnDataClass(
        fornavn = originaltNavn.fornavn,
        mellomnavn = originaltNavn.mellomnavn,
        etternavn = originaltNavn.etternavn
    )
}
