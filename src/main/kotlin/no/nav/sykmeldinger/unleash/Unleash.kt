package no.nav.syfo.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val defaultConfig =
    UnleashConfig.builder()
        .appName("sykmeldinger-backend-kafka")
        .instanceId(System.getenv("HOSTNAME"))
        .unleashAPI("${System.getenv("UNLEASH_SERVER_API_URL")}/api")
        .apiKey(System.getenv("UNLEASH_SERVER_API_TOKEN"))
        .environment(System.getenv("UNLEASH_SERVER_API_ENV"))
        .synchronousFetchOnInitialisation(true)
        .build()

fun getUnleash(config: UnleashConfig = defaultConfig) = DefaultUnleash(config)

fun createUnleashStateHandler(
    unleash: Unleash = getUnleash(),
    toggle: String,
    onToggledOn: suspend () -> Unit,
    onToggledOff: suspend () -> Unit,
    scope: CoroutineScope
) {
    var previousState = false
    scope.launch(Dispatchers.IO) {
        while (isActive) {
            val isFeatureEnabled = unleash.isEnabled(toggle)

            if (previousState != isFeatureEnabled) {
                previousState = isFeatureEnabled
                if (isFeatureEnabled) {
                    onToggledOn()
                } else {
                    onToggledOff()
                }
            }

            delay(10.seconds)
        }
    }
}
