package no.nav.sykmeldinger

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-backend-kafka"),
    val databaseUsername: String = getEnvVar("DB_SYKMELDINGER_KAFKA_USER_USERNAME"),
    val databasePassword: String = getEnvVar("DB_SYKMELDINGER_KAFKA_USER_PASSWORD"),
    val dbHost: String = getEnvVar("DB_SYKMELDINGER_KAFKA_USER_HOST"),
    val dbPort: String = getEnvVar("DB_SYKMELDINGER_KAFKA_USER_PORT"),
    val dbName: String = getEnvVar("DB_SYKMELDINGER_KAFKA_USER_DATABASE"),
    val cloudSqlInstance: String = getEnvVar("CLOUD_SQL_INSTANCE"),
    val statusTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE")
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
