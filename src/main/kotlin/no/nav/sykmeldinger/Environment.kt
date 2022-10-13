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
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val bekreftetTopic: String = "teamsykmelding.syfo-bekreftet-sykmelding",
    val sendtTopic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val narmestelederLeesahTopic: String = "teamsykmelding.syfo-narmesteleder-leesah",
    val navnendringTopic: String = "pdl.leesah-v1",
    val schemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val kafkaSchemaRegistryUsername: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val kafkaSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    val historiskTopic: String = "teamsykmelding.sykmelding-historisk",
    val behandlingsutfallTopic: String = "teamsykmelding.sykmelding-behandlingsutfall",
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val eregUrl: String = getEnvVar("EREG_URL"),
    val aaregUrl: String = getEnvVar("AAREG_URL"),
    val aaregScope: String = getEnvVar("AAREG_SCOPE"),
    val aktorV2Topic: String = "aktor-v2"
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
