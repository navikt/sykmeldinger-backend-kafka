package no.nav.sykmeldinger.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?,
)

data class ResponseData(
    val person: PersonResponse?,
    val hentIdenter: Identliste?,
)

data class PersonResponse(
    val navn: List<Navn>?,
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
)

data class Identliste(
    val identer: List<IdentInformasjon>,
) {
    val fnr: String? =
        identer.firstOrNull { it.gruppe == "FOLKEREGISTERIDENT" && !it.historisk }?.ident
}

data class IdentInformasjon(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String,
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?,
)

data class ErrorLocation(
    val line: String?,
    val column: String?,
)

data class ErrorExtension(
    val code: String?,
    val details: ErrorDetails?,
    val classification: String?,
)

data class ErrorDetails(
    val type: String? = null,
    val cause: String? = null,
    val policy: String? = null,
)
