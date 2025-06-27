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
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// try uncomment the default jwt imports in main/kotlin/auth/Authentication.kt and rerun the tests
class AuthTest {
    @RepeatedTest(2)
    fun `proper web client token should return 200`(repetitionInfo: RepetitionInfo) = testApplication {
        application { module() }

        val routePath = if (repetitionInfo.currentRepetition == 1) Routes.STATUS else Routes.STATUS_MOB_FIRST
        val response = client.get(routePath) {
            header(Headers.AUTH_HEADER, "Bearer ${makeServiceToken("private.pem")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @RepeatedTest(2)
    fun `no header, respond 401`(repetitionInfo: RepetitionInfo) = testApplication {
        application { module() }

        val routePath = if (repetitionInfo.currentRepetition == 1) Routes.STATUS else Routes.STATUS_MOB_FIRST
        val response = client.get(routePath)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @RepeatedTest(2)
    fun `header with bad format, respond 401`(repetitionInfo: RepetitionInfo) = testApplication {
        application { module() }
        val routePath = if (repetitionInfo.currentRepetition == 1) Routes.STATUS else Routes.STATUS_MOB_FIRST
        run {
            val response = client.get(Routes.STATUS) {
                header(Headers.AUTH_HEADER, "Bearer asdfasdfdas.asdf")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            checkProperErrorBody(response)
        }
        run {
            val response = client.get(routePath) {
                header(Headers.AUTH_HEADER, "bad format")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            checkProperErrorBody(response)
        }
    }

    @RepeatedTest(2)
    fun `header with no scope claim, respond 401`(repetitionInfo: RepetitionInfo) = testApplication {
        application { module() }
        val routePath = if (repetitionInfo.currentRepetition == 1) Routes.STATUS else Routes.STATUS_MOB_FIRST
        run {
            val response = client.get(routePath) {
                header(Headers.AUTH_HEADER, "Bearer ${makeServiceToken("private.pem", webClient = null, os = null)}")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            checkProperErrorBody(response)
        }
    }

    @RepeatedTest(2)
    fun `header is expired, respond 401, make sure logged`(repetitionInfo: RepetitionInfo) = testApplication {
        val errorMessages: List<String> = ArrayList<String>().apply {
            val list = this
            (LoggerFactory.getLogger("auth.jwt") as ch.qos.logback.classic.Logger).apply {
                val loggerContext = this.loggerContext
                val appender = object : ConsoleAppender<ILoggingEvent>() {
                    override fun doAppend(eventObject: ILoggingEvent?) {
                        super.doAppend(eventObject)
                        list.add(eventObject.toString())
                    }
                }
                appender.apply {
                    encoder = PatternLayoutEncoder().apply { context = loggerContext }
                    context = loggerContext
                    start()
                }
                addAppender(appender)
            }
        }

        application { module() }
        val routePath = if (repetitionInfo.currentRepetition == 1) Routes.STATUS else Routes.STATUS_MOB_FIRST
        val response = client.get(routePath) {
            header(Headers.AUTH_HEADER, "Bearer ${makeServiceToken("private.pem", Date(1, 1, 1))}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue("No expiration error messages found") { errorMessages.any { it.contains("expired") } }
    }

    @RepeatedTest(2)
    fun `header is expired, respond 401, returns the error`(repetitionInfo: RepetitionInfo) = testApplication {
        application { module() }
        val routePath = if (repetitionInfo.currentRepetition == 1) Routes.STATUS else Routes.STATUS_MOB_FIRST
        val response = client.get(routePath) {
            val mobileHeader = makeServiceToken("private.pem", Date(1, 1, 1), webClient = null)
            header(Headers.AUTH_HEADER, "Bearer $mobileHeader")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = checkProperErrorBody(response)
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

    @Test
    fun `deep throat test`() = testApplication {
        application { module() }
        val response = client.get("deepthroat") {
            val iosToken = makeServiceToken("private.pem", os = "IOS", webClient = null)
            header(Headers.AUTH_HEADER, "Bearer $iosToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    /** Only `challenge` respond with [DetailedErrorResponse] */
    private suspend fun checkProperErrorBody(response2: HttpResponse): String {
        val body = response2.bodyAsText()
        println(body)
        assertTrue { body.contains("message") && body.contains("\"ok\": false") }
        return body
    }
}