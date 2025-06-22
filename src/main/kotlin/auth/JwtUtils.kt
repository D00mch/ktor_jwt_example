package dumch.com.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/*
 * This code is for demonstration purposes only and should not be used in production without modification.
 * It is not intended for production use and is meant solely to illustrate JWT handling concepts.
 */

private val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")

fun webTokenVerifier(): JWTVerifier {
    val publicKey = stripPublicKey(File("public.pem").readText())
    val algo = makeRSAVerificationAlgorithm(publicKey)
    return JWT.require(algo).build()
}

private fun makeRSAVerificationAlgorithm(publicKeyContent: String): Algorithm {
    val keySpecX509 = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent))
    val pubKey = keyFactory.generatePublic(keySpecX509) as RSAPublicKey
    return Algorithm.RSA256(pubKey, null)
}

private fun stripPublicKey(key: String): String = key
    .replace("\n", "")
    .replace("\r", "")
    .replace("-----BEGIN PUBLIC KEY-----", "")
    .replace("-----END PUBLIC KEY-----", "")