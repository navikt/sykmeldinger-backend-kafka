package no.nav.sykmeldinger

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.sykmeldinger.application.ApplicationState
import no.nav.sykmeldinger.application.api.registerNaisApi
import org.amshove.kluent.shouldBeEqualTo

object SelftestSpec :
    FunSpec({
        context("Successfull liveness and readyness tests") {
            test("Returns ok on is_alive") {
                testApplication {
                    application {
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { route("internal") { registerNaisApi(applicationState) } }
                    }
                    val response = client.get("/internal/is_alive")

                    response.status shouldBeEqualTo HttpStatusCode.OK
                    response.bodyAsText() shouldBeEqualTo "I'm alive! :)"
                }
            }
            test("Returns ok in is_ready") {
                testApplication {
                    application {
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { route("internal") { registerNaisApi(applicationState) } }
                    }
                    val response = client.get("/internal//is_ready")

                    response.status shouldBeEqualTo HttpStatusCode.OK
                    response.bodyAsText() shouldBeEqualTo "I'm ready! :)"
                }
            }
        }
        context("Unsuccessful liveness and readyness") {
            test("Returns internal server error when liveness check fails") {
                testApplication {
                    application {
                        val applicationState = ApplicationState()
                        applicationState.ready = false
                        applicationState.alive = false
                        routing { route("internal") { registerNaisApi(applicationState) } }
                    }
                    val response = client.get("/internal/is_alive")

                    response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.bodyAsText() shouldBeEqualTo "I'm dead x_x"
                }
            }

            test("Returns internal server error when readyness check fails") {
                testApplication {
                    application {
                        val applicationState = ApplicationState()
                        applicationState.ready = false
                        applicationState.alive = false
                        routing { route("internal") { registerNaisApi(applicationState) } }
                    }

                    val response = client.get("/internal/is_ready")

                    response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.bodyAsText() shouldBeEqualTo "Please wait! I'm not ready :("
                }
            }
        }
    })
