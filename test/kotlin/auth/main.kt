package dumch.com.auth

import java.util.*

// use it to generate keys
fun main() {
    // do not store private key in resources!
    val serviceToken = TestJwtUtils.makeServiceToken("private.pem", Date(2030, 1, 1))
    println(serviceToken)
}
