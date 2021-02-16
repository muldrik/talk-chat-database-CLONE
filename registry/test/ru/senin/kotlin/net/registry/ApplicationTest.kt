package ru.senin.kotlin.net.registry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.senin.kotlin.net.Protocol
import ru.senin.kotlin.net.UserAddress
import ru.senin.kotlin.net.UserInfo
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

fun Application.testModule() {

    (environment.config as MapApplicationConfig).apply {
        // define test environment here
    }
    module(testing = true)
}

class ApplicationTest {
    private val objectMapper = jacksonObjectMapper()
    private val testUserName = "pupkin"
    private val testHttpAddress = UserAddress(Protocol.HTTP, "127.0.0.1", 9999)
    private val userData = UserInfo(testUserName, testHttpAddress)

    @BeforeEach
    fun clearRegistry() {
        Registry.users.clear()
    }

    @Test
    fun `health endpoint`() {
        withTestApplication({ testModule() }) {
            handleRequest(HttpMethod.Get, "/v1/health").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("OK", response.content)
            }
        }
    }

    @Test
    fun `register user`() = withRegisteredTestUser { }

    @Test
    fun `list users`() = withRegisteredTestUser {
        handleRequest {
            method = HttpMethod.Get
            uri = "/v1/users"
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content ?: fail("No response content")
            val users = objectMapper.readValue<Map<String, UserAddress>>(content)
            assertEquals(1, users.size)
            assertEquals(testUserName, users.keys.first())
            assertEquals(testHttpAddress, users[testUserName])
        }
    }

    @Test
    fun `delete user`() = withRegisteredTestUser {
        handleRequest {
            method = HttpMethod.Delete
            uri = "/v1/users/$testUserName"
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            handleRequest {
                method = HttpMethod.Get
                uri = "/v1/users"
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val users = objectMapper.readValue<Map<String, *>>(content)
                assertEquals(0, users.size)
            }
        }
    }

    private fun withRegisteredTestUser(block: TestApplicationEngine.() -> Unit) {
        withTestApplication({ testModule() }) {
            handleRequest {
                method = HttpMethod.Post
                uri = "/v1/users"
                addHeader("Content-type", "application/json")
                setBody(objectMapper.writeValueAsString(userData))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = response.content ?: fail("No response content")
                val info = objectMapper.readValue<HashMap<String,String>>(content)

                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                this@withTestApplication.block()
            }
        }
    }
}
