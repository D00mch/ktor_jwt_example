package dumch.com

import dumch.com.auth.configureAuthentication
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused") // used by ktor
fun Application.module() {
    configureStatusPages()
    configureAuthentication()
    configureJson()
    configureRouting()
}
