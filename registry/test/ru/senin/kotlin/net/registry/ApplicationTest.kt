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
    private val testHttpAddressUpdated = UserAddress(Protocol.UDP, "127.0.0.1", 8888)
    private val newTestHttpAddress = UserAddress(Protocol.WEBSOCKET, "127.0.0.1", 1234)
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
                assertEquals(mapOf("status" to "ok"), objectMapper.readValue(response.content ?: ""))
            }
        }
    }

    @Test
    fun `register user`() {
        val newTestUserName = "НИКИТА"
        val newTestHttpAddress = UserAddress(Protocol.WEBSOCKET, "127.0.0.1", 7777)
        val newUserData = UserInfo(newTestUserName, newTestHttpAddress)

        tryToRegister(userData, HttpStatusCode.OK)
        tryToRegister(userData, HttpStatusCode.Conflict)
        tryToRegister(newUserData, HttpStatusCode.BadRequest)

    }

    @Test
    fun `list users`() = withRegisteredTestUser {
        handleRequest(HttpMethod.Get, "/v1/users").apply {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(mapOf(userData.name to userData.address), objectMapper.readValue(response.content ?: ""))
        }
    }

    @Test
    fun `update user`() = withRegisteredTestUser {

        tryToUpdate(testUserName, testHttpAddressUpdated)
        tryToUpdate(testUserName, newTestHttpAddress)
        tryToUpdate("klimoza", newTestHttpAddress)

    }

    @Test
    fun `delete user`() = withRegisteredTestUser {

        tryToDelete(testUserName)
        tryToDelete(testUserName)
        tryToDelete("klimoza")
        tryToDelete("1234")

    }

    private fun tryToRegister(user: UserInfo, statusCode: HttpStatusCode) {
        withTestApplication({ testModule() }) {
            handleRequest(HttpMethod.Post, "/v1/users") {
                addHeader("Content-Type", "application/json")
                setBody(objectMapper.writeValueAsString(user))
            }.apply {
                assertEquals(statusCode, response.status())
            }
        }
    }

    private fun tryToUpdate(userName: String, finalAddress: UserAddress) {
        withTestApplication({ testModule() }) {
            handleRequest(HttpMethod.Put, "/v1/users/$userName") {
                addHeader("Content-Type", "application/json")
                setBody(objectMapper.writeValueAsString(finalAddress))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(mapOf("status" to "ok"), objectMapper.readValue(response.content ?: ""))
            }
        }
    }

    private fun tryToDelete(userName: String) {
        withTestApplication({ testModule() }) {
            handleRequest(HttpMethod.Delete, "/v1/users/$userName").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(mapOf("status" to "ok"), objectMapper.readValue(response.content ?: ""))
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
                val info = objectMapper.readValue<HashMap<String, String>>(content)

                assertNotNull(info["status"])
                assertEquals("ok", info["status"])

                this@withTestApplication.block()
            }
        }
    }
}
