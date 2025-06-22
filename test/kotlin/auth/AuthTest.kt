package dumch.com.auth

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import dumch.com.Routes
import dumch.com.auth.TestJwtUtils.makeServiceToken
import dumch.com.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthTest {
    @Test
    fun `proper web client token should return 200`() = testApplication {
        application { module() }

        val response = client.get(Routes.STATUS) {
            header(Headers.AUTH_HEADER, "Bearer ${makeServiceToken("private.pem")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `no header, respond 401`() = testApplication {
        application { module() }
        val response = client.get(Routes.STATUS)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `header with bad format, respond 401`() = testApplication {
        application { module() }
        val response = client.get(Routes.STATUS) {
            header(Headers.AUTH_HEADER, "Bearer asdfasdfdas.asdf")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val response2 = client.get(Routes.STATUS) {
            header(Headers.AUTH_HEADER, "bad format")
        }
        assertEquals(HttpStatusCode.Unauthorized, response2.status)
    }

    // approach one
    @Test
    fun `header is expired, respond 401, make sure logged`() = testApplication {
        val errorMessages = ArrayList<String>()
        (LoggerFactory.getLogger("io.ktor.auth.jwt") as ch.qos.logback.classic.Logger).apply {
            val loggerContext = this.loggerContext
            val appender = object : ConsoleAppender<ILoggingEvent>() {
                override fun doAppend(eventObject: ILoggingEvent?) {
                    super.doAppend(eventObject)
                    errorMessages.add(eventObject.toString())
                }
            }
            appender.apply {
                encoder = PatternLayoutEncoder().apply { context = loggerContext }
                context = loggerContext
                start()
            }
            addAppender(appender)
        }

        application { module() }
        val response = client.get(Routes.STATUS) {
            header(Headers.AUTH_HEADER, "Bearer ${makeServiceToken("private.pem", Date(1, 1, 1))}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue("No expiration error messages found") { errorMessages.any { it.contains("expired") } }
    }

    @Test
    fun `header is expired, respond 401, returns the error`() = testApplication {
        application { module() }
        val response = client.get(Routes.STATUS) {
            val mobileHeader = makeServiceToken("private.pem", Date(1, 1, 1), webClient = null)
            header(Headers.AUTH_HEADER, "Bearer $mobileHeader")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.bodyAsText()
        assertTrue { body.contains("token has expired", ignoreCase = true) }
    }

    @Test
    fun `wrong scope, return 403`() = testApplication {
        application { module() }
        val response = client.get(Routes.ANDROID_ONLY) {
            val iosToken = makeServiceToken("private.pem", os = "IOS")
            header(Headers.AUTH_HEADER, "Bearer $iosToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}