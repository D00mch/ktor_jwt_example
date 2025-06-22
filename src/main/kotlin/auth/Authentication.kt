package dumch.com.auth

import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import dumch.com.auth.Headers.AUTH_HEADER
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
            val clientIdFromPayload = credential.payload.getClaim(JwtClaims.WEB_CLIENT_ID).asInt()
            if (expectedClientId != clientIdFromPayload) return@validate null
            val scopes: MutableList<String> = credential.payload.getClaim(JwtClaims.SCOPES).asList(String::class.java)
                ?: return@validate null
            Principal.WebClient(clientIdFromPayload, scopes)
        }

        challenge { _, _ ->
            printErrors()
            call.respondText(status = HttpStatusCode.Unauthorized) { "Authentication error, but what exactly?" }
        }
    }
}

private fun AuthenticationConfig.setupMobileJwt() {
    jwt(Clients.MOBILE_CLIENT) {
        val spyVerifier = SpyJwtVerifier(webTokenVerifier())
        authHeader { call ->
            call.request.header(AUTH_HEADER)?.let { parseAuthorizationHeader(it) }
        }
        verifier { _ -> spyVerifier }
        validate { credential: JWTCredential ->
            val os: String = credential.payload.getClaim(JwtClaims.OS).asString() ?: return@validate null
            val scopes: MutableList<String> = credential.payload.getClaim(JwtClaims.SCOPES).asList(String::class.java)
                ?: return@validate null
            Principal.MobileClient(os, scopes)
        }
        challenge { _, _ ->
            spyVerifier.exception?.let { e ->
                call.application.log.error("Verification error")
                call.respond(HttpStatusCode.Unauthorized, DetailedErrorResponse(e.toString()))
                return@let
            }
            call.respond(
                HttpStatusCode.Unauthorized,
                DetailedErrorResponse(call.authentication.allFailures.first().toString())
            )
        }
    }
}

@Serializable
class DetailedErrorResponse(val message: String?)

/**
 * All we can get from this block is
 * 1. No creds
 * 2. Bad creds
 *
 * But no reasons what's really wrong
 */
private fun JWTChallengeContext.printErrors() {
    val failures: List<AuthenticationFailedCause> = call.authentication.allFailures
    val errors: List<AuthenticationFailedCause.Error> = call.authentication.allErrors
    failures.forEach { call.application.log.error("failure: $it") }
    errors.forEach { call.application.log.error("error: $it") }
}

class SpyJwtVerifier(private val delegate: JWTVerifier) : JWTVerifier {
    var exception: Exception? = null
        private set

    override fun verify(token: String?): DecodedJWT = try {
        delegate.verify(token)
    } catch (e: Exception) {
        exception = e
        throw e
    }

    override fun verify(jwt: DecodedJWT?): DecodedJWT = try {
        delegate.verify(jwt)
    } catch (e: Exception) {
        exception = e
        throw e
    }
}
