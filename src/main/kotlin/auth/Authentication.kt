package dumch.com.auth

import authentication.jwt.* // comment out this and uncomment the line below
// import io.ktor.server.auth.jwt.* // and run the test again to see the default ktor jwt behavior
import dumch.com.auth.Headers.AUTH_HEADER
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

object JwtClaims {
    const val WEB_CLIENT_ID = "web_id"
    const val SCOPES = "scopes"
    const val OS = "os"
}

object Headers {
    const val AUTH_HEADER = "CUSTOM_AUTH_HEADER"
}

object Clients {
    const val WEB_CLIENT = "web_client"
    const val MOBILE_CLIENT = "mob_client"
}

object Principal {
    class WebClient(val id: Int, val scope: List<String>)
    class MobileClient(val os: String, val scope: List<String>)
}

fun Application.configureAuthentication() {
    install(Authentication) {
        val environment = this@configureAuthentication.environment
        setupWebJwt(environment)
        setupMobileJwt()
    }
}

private fun AuthenticationConfig.setupWebJwt(environment: ApplicationEnvironment) {
    jwt(Clients.WEB_CLIENT) {
        authHeader { call ->
            call.request.header(AUTH_HEADER)?.let { parseAuthorizationHeader(it) }
        }

        verifier { httpAuthHeader ->
            if (httpAuthHeader is HttpAuthHeader.Single) {
                webTokenVerifier()
            } else {
                environment.log.error("HttpAuthHeader is not Single")
                null
            }
        }

        validate { credential: JWTCredential ->
            val expectedClientId = /* get this from DB, Secret Manager, or whatever */ 1
            val clientIdClaim = credential.payload.getClaim(JwtClaims.WEB_CLIENT_ID)
            if (clientIdClaim.isMissing) throw IllegalArgumentException("No web client id claim")
            val clientIdFromPayload = clientIdClaim.asInt()
            if (expectedClientId != clientIdFromPayload) return@validate null
            val scopes: List<String> = credential.payload.getClaim(JwtClaims.SCOPES).asList(String::class.java)
                ?: throw Exception("No scopes")
            Principal.WebClient(clientIdFromPayload, scopes)
        }

        defaultChallenge()
    }
}

private fun AuthenticationConfig.setupMobileJwt() {
    jwt(Clients.MOBILE_CLIENT) {
        authHeader { call -> call.request.header(AUTH_HEADER)?.let { parseAuthorizationHeader(it) } }
        verifier { _ -> webTokenVerifier() }
        validate { credential: JWTCredential ->
            val os = credential.payload.getClaim(JwtClaims.OS).asString() ?: throw Exception("No os in scope")
            val scopes: MutableList<String> = credential.payload.getClaim(JwtClaims.SCOPES).asList(String::class.java)
                ?: return@validate null
            Principal.MobileClient(os, scopes)
        }
        defaultChallenge()
    }
}

private fun JWTAuthenticationProvider.Config.defaultChallenge() {
    challenge { _, _ ->
        val msg: String = call.authentication.allFailures.firstNotNullOf {
            when (it) {
                is AuthenticationFailedCause.Error -> it.message
                is AuthenticationFailedCause.InvalidCredentials -> "Invalid credential"
                is AuthenticationFailedCause.NoCredentials -> "No credentials"
            }
        }
        call.respond(status = HttpStatusCode.Unauthorized, DetailedErrorResponse(msg))
    }
}

@Serializable
class DetailedErrorResponse(val message: String?, val ok: Boolean = false)
