group = "no.nav.sykmeldinger"
version = "1.0.0"

val coroutinesVersion = "1.7.3"
val jacksonVersion = "2.16.1"
val kluentVersion = "1.73"
val ktorVersion = "2.3.7"
val logbackVersion = "1.4.14"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.13.9"
val testContainerVersion = "1.19.3"
val kotlinVersion = "1.9.22"
val kotestVersion = "5.8.0"
val postgresVersion = "42.7.1"
val hikariVersion = "5.1.0"
val googlePostgresVersion = "1.15.1"
val smCommonVersion = "2.0.8"
val flywayVersion = "10.5.0"
val confluentVersion = "7.5.3"
val commonsCodecVersion = "1.16.0"
val ktfmtVersion = "0.44"
val avroVersion = "1.11.3"

plugins {
    id("application")
    kotlin("jvm") version "1.9.22"
    id("com.diffplug.spotless") version "6.24.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

application {
    mainClass.set("no.nav.sykmeldinger.BootstrapKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient version 1.10 from io.ktor:ktor-client-apache")
        }
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion")

    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:1.1.10.5") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }
    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    constraints {
        implementation("org.apache.avro:avro:$avroVersion") {
            because("override transient from io.confluent:kafka-avro-serializer")
        }
    }

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:kafka:$testContainerVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.flywaydb:flyway-core:$flywayVersion")
    testImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
}

tasks {

    shadowJar {
mergeServiceFiles {
     setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
 }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.sykmeldinger.BootstrapKt",
                ),
            )
        }
        dependsOn("generateTestAvroJava")
    }

    test {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
            dependsOn("generateTestAvroJava")
        }
    }
}
