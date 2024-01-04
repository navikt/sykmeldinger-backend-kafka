package no.nav.sykmeldinger.pdl.error

class PersonNotFoundInPdl(override val message: String) : Exception(message)

class PersonNameNotFoundInPdl(override val message: String) : Exception(message)
