package no.nav.sykmeldinger.pdl.model

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
) {
    fun toFormattedNameString(): String {
        return if (mellomnavn.isNullOrEmpty()) {
            capitalizeFirstLetter("$fornavn $etternavn")
        } else {
            capitalizeFirstLetter("$fornavn $mellomnavn $etternavn")
        }
    }

    private fun capitalizeFirstLetter(string: String): String {
        return string.lowercase()
            .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.titlecaseChar() } }
            .split("-").joinToString("-") { it.replaceFirstChar { char -> char.titlecaseChar() } }
            .trimEnd()
    }
}
