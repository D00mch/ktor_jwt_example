package dumch.com

import dumch.com.auth.Clients
import dumch.com.auth.Principal
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Routes {
    const val STATUS = "status"
    const val ANDROID_ONLY = "android_only"
}

fun Application.configureRouting() {
    routing {
        authenticate(
            Clients.MOBILE_CLIENT,
            Clients.WEB_CLIENT,
            strategy = AuthenticationStrategy.FirstSuccessful
        ) {
            get(Routes.STATUS) {
                call.respond(ResponseStatus("OK"))
            }
        }

        authenticate(Clients.MOBILE_CLIENT, strategy = AuthenticationStrategy.Required) {
            get(Routes.ANDROID_ONLY) {
                val client = requireNotNull(call.principal<Principal.MobileClient>())
                if (client.os.equals("ios", ignoreCase = true)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                call.respond(ResponseStatus("OK"))
            }
        }
    }
}

@Serializable
class ResponseStatus(val status: String)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, e ->
            call.application.log.error("Error", e)
        }
    }
}

fun Application.configureJson() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }
}