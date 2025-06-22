package dumch.com.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

object TestJwtUtils {

    fun makeServiceToken(
        privKeyPath: String,
        expirationDate: Date = Date(2030, 1, 1),
        webClient: Int? = 1,
        os: String? = "IOS",
    ): String {
        val privateKeyContent = stripPrivateKey(File(privKeyPath).readText())
        val keySpecPKCS8 = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyContent))
        val kf = KeyFactory.getInstance("RSA")
        val privKey = kf.generatePrivate(keySpecPKCS8) as RSAPrivateKey
        val algorithm = Algorithm.RSA256(null, privKey)

        return JWT.create()
            .withIssuer("ktor_jwt_example")
            .withAudience("Habr")
            .withIssuedAt(Date())
            .withExpiresAt(expirationDate)
            .withSubject("test_token")
            .apply { if (webClient != null) withClaim(JwtClaims.WEB_CLIENT_ID, webClient) }
            .apply { if (os != null) withClaim(JwtClaims.OS, os) }
            .withClaim(JwtClaims.SCOPES, listOf("admin", "analytics", "test"))
            .sign(algorithm)
    }

    private fun stripPrivateKey(key: String): String = key
        .replace("\n", "")
        .replace("\r", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
}