package authentication.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import com.auth0.jwt.exceptions.*
import com.auth0.jwt.interfaces.*
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import org.slf4j.*
import java.util.*
import kotlin.reflect.*

internal val JWTAuthKey: Any = "JWTAuth"

internal val JWTLogger: Logger = LoggerFactory.getLogger("auth.jwt")

internal class JWTAuthSchemes(val defaultScheme: String, vararg additionalSchemes: String) {
    val schemes = (arrayOf(defaultScheme) + additionalSchemes).toSet()
    val schemesLowerCase = schemes.map { it.lowercase(Locale.getDefault()) }.toSet()

    operator fun contains(scheme: String): Boolean = scheme.lowercase(Locale.getDefault()) in schemesLowerCase
}

abstract class JWTPayloadHolder(val payload: Payload) {

    val issuer: String? get() = payload.issuer
    val subject: String? get() = payload.subject
    val audience: List<String> get() = payload.audience ?: emptyList()
    val expiresAt: Date? get() = payload.expiresAt
    val notBefore: Date? get() = payload.notBefore
    val issuedAt: Date? get() = payload.issuedAt
    val jwtId: String? get() = payload.id

    operator fun get(name: String): String? {
        return payload.getClaim(name).asString()
    }

    fun <T : Any> getClaim(name: String, clazz: KClass<T>): T? {
        return try {
            payload.getClaim(name).`as`(clazz.javaObjectType)
        } catch (ex: JWTDecodeException) {
            null
        }
    }

    fun <T : Any> getListClaim(name: String, clazz: KClass<T>): List<T> {
        return try {
            payload.getClaim(name).asList(clazz.javaObjectType) ?: emptyList()
        } catch (ex: JWTDecodeException) {
            emptyList()
        }
    }
}

class JWTCredential(payload: Payload) : JWTPayloadHolder(payload)

class JWTPrincipal(payload: Payload) : JWTPayloadHolder(payload)

typealias JWTConfigureFunction = Verification.() -> Unit

class JWTAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    private val realm: String = config.realm
    private val schemes: JWTAuthSchemes = config.schemes
    private val authHeader: (ApplicationCall) -> HttpAuthHeader? = config.authHeader
    private val verifier: ((HttpAuthHeader) -> JWTVerifier?) = config.verifier
    private val authenticationFunction = config.authenticationFunction
    private val challengeFunction: JWTAuthChallengeFunction = config.challenge

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        try {
            val token = authHeader(call)
            if (token == null) {
                JWTLogger.debug("JWT authentication failed: No credentials provided")
                context.bearerChallenge(AuthenticationFailedCause.NoCredentials, realm, schemes, challengeFunction)
                return
            }

            val jwtVerifier = verifier(token)!!
            val principal = verifyAndValidate(call, jwtVerifier, token, schemes, authenticationFunction)
            if (principal != null) {
                context.principal(name, principal)
                return
            }

            JWTLogger.debug("JWT authentication failed: Invalid credentials")
            context.bearerChallenge(
                AuthenticationFailedCause.InvalidCredentials,
                realm,
                schemes,
                challengeFunction
            )
        } catch (cause: Throwable) {
            val message = cause.message ?: cause.javaClass.simpleName
            JWTLogger.debug("JWT authentication failed: $message", cause)
            context.bearerChallenge(AuthenticationFailedCause.Error(message), realm, schemes, challengeFunction)
        }
    }

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticationFunction: AuthenticationFunction<JWTCredential> = {
            throw NotImplementedError(
                "JWT auth validate function is not specified. Use jwt { validate { ... } } to fix."
            )
        }

        internal var schemes = JWTAuthSchemes("Bearer")

        internal var authHeader: (ApplicationCall) -> HttpAuthHeader? =
            { call -> call.request.parseAuthorizationHeaderOrNull() }

        internal var verifier: ((HttpAuthHeader) -> JWTVerifier?) = { null }

        internal var challenge: JWTAuthChallengeFunction = { scheme, realm ->
            call.respond(
                UnauthorizedResponse(
                    HttpAuthHeader.Parameterized(
                        scheme,
                        mapOf(HttpAuthHeader.Parameters.Realm to realm)
                    )
                )
            )
        }

        var realm: String = "Ktor Server"

        fun authHeader(block: (ApplicationCall) -> HttpAuthHeader?) {
            authHeader = block
        }

        fun authSchemes(defaultScheme: String = "Bearer", vararg additionalSchemes: String) {
            schemes = JWTAuthSchemes(defaultScheme, *additionalSchemes)
        }

        fun verifier(verifier: JWTVerifier) {
            this.verifier = { verifier }
        }

        fun verifier(verifier: (HttpAuthHeader) -> JWTVerifier?) {
            this.verifier = verifier
        }

        fun verifier(jwkProvider: JwkProvider, issuer: String, configure: JWTConfigureFunction = {}) {
            verifier = { token -> getVerifier(jwkProvider, issuer, token, schemes, configure) }
        }

        fun verifier(jwkProvider: JwkProvider, configure: JWTConfigureFunction = {}) {
            verifier = { token -> getVerifier(jwkProvider, token, schemes, configure) }
        }

        fun verifier(
            issuer: String,
            audience: String,
            algorithm: Algorithm,
            block: Verification.() -> Unit = {}
        ) {
            val verification: Verification = JWT
                .require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)

            verification.apply(block)
            verifier(verification.build())
        }

        fun verifier(issuer: String, block: JWTConfigureFunction = {}) {
            val provider = JwkProviderBuilder(issuer).build()
            verifier = { token -> getVerifier(provider, token, schemes, block) }
        }

        fun validate(validate: suspend ApplicationCall.(JWTCredential) -> Any?) {
            authenticationFunction = validate
        }

        fun challenge(block: JWTAuthChallengeFunction) {
            challenge = block
        }

        internal fun build() = JWTAuthenticationProvider(this)
    }
}

fun AuthenticationConfig.jwt(
    name: String? = null,
    configure: JWTAuthenticationProvider.Config.() -> Unit
) {
    val provider = JWTAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

class JWTChallengeContext(
    val call: ApplicationCall
)

typealias JWTAuthChallengeFunction =
        suspend JWTChallengeContext.(defaultScheme: String, realm: String) -> Unit