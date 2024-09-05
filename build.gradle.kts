import org.apache.avro.tool.SpecificCompilerTool
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.sykmeldinger"
version = "1.0.0"

val coroutinesVersion = "1.8.1"
val jacksonVersion = "2.17.2"
val kluentVersion = "1.73"
val ktorVersion = "2.3.12"
val logbackVersion = "1.5.7"
val logstashEncoderVersion = "8.0"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.13.12"
val testContainerVersion = "1.20.1"
val kotlinVersion = "2.0.10"
val kotestVersion = "5.9.1"
val postgresVersion = "42.7.3"
val hikariVersion = "5.1.0"
val googlePostgresVersion = "1.20.0"
val flywayVersion = "10.17.1"
val confluentVersion = "7.7.0"
val commonsCodecVersion = "1.17.1"
val ktfmtVersion = "0.44"
val avroVersion = "1.12.0"
val opentelemetryVersion = "2.6.0"
val snappyJavaVersion = "1.1.10.6"
val commonsCompressVersion = "1.27.0"
val javaVersion = JvmTarget.JVM_21

plugins {
    id("application")
    kotlin("jvm") version "2.0.10"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")


    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion")

    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }
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
      constraints {
          implementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("Due to vulnerabilities, see CVE-2024-26308")
        }
      }
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.flywaydb:flyway-core:$flywayVersion")
    testImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
}

buildscript {
    dependencies {
        classpath("org.apache.avro:avro-tools:1.12.0")
        classpath("org.apache.avro:avro:1.12.0")
    }
}

val avroSchemasDir = "src/main/avro"
val avroCodeGenerationDir = "build/generated-main-avro-custom-java"


sourceSets {
    main {
        java {
            srcDir( file(File(avroCodeGenerationDir)))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = javaVersion
    }
}

tasks {

    register("customAvroCodeGeneration") {
        inputs.dir(avroSchemasDir)
        outputs.dir(avroCodeGenerationDir)

        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.ERROR)

        doLast {
            SpecificCompilerTool().run(
                System.`in`, System.out, System.err,
                listOf(
                    "-encoding",
                    "UTF-8",
                    "-string",
                    "-fieldVisibility",
                    "private",
                    "-noSetters",
                    "schema",
                    "$projectDir/$avroSchemasDir",
                    "$projectDir/$avroCodeGenerationDir",
                ),
            )
        }

    }

    compileKotlin {
        dependsOn("customAvroCodeGeneration")
    }
    compileTestKotlin {
        dependsOn("customAvroCodeGeneration")
    }

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
        dependsOn("customAvroCodeGeneration")
    }

    test {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        dependsOn("customAvroCodeGeneration")
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle()
        }
        check {
            dependsOn("spotlessApply")
            dependsOn("customAvroCodeGeneration")
        }
    }
}
