package ru.senin.kotlin.net

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import ru.senin.kotlin.net.client.UdpChatClient
import ru.senin.kotlin.net.server.ChatMessageListener
import ru.senin.kotlin.net.server.HttpChatServer
import ru.senin.kotlin.net.server.UdpChatServer
import ru.senin.kotlin.net.server.WebSocketChatServer
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.test.assertEquals


class HttpTest {
    private val objectMapper = jacksonObjectMapper()
    private val testUserName = "pupkin"
    private val testString = "welcome to the club buddy"
    private val testMessage = Message(testUserName, testString)

    @Test
    fun `test http message receiving`() {
        val server = HttpChatServer("0.0.0.0", 8089)
        withTestApplication(server.configureModule()) {
            handleRequest(HttpMethod.Post, "/v1/message") {
                addHeader("Content-Type", "application/json")
                setBody(objectMapper.writeValueAsString(testMessage))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(mapOf("status" to "ok"), objectMapper.readValue(response.content ?: ""))
            }
        }
    }
}

class WebSocketTest {
    private val objectMapper = jacksonObjectMapper()
    private val testUserName = "Dreamtail"
    private val testStrings = listOf(
        "У каждой истории есть начало и конец.",
        "У каждой истории есть своя канва, синопсис, содержание, ключевые моменты, прологи и эпилоги.",
        "И нет такой книги, в которой при каждом новом прочтении не открывались бы вещи, на которые раньше не обращал внимания.",
        "У каждой истории есть начало и конец.", "Почти у каждой..."
    )
    private val testMessages = testStrings.map { it -> Message(testUserName, it) }

    @Test
    fun `test websocket message receiving`() {
        val wsServer = WebSocketChatServer("0.0.0.0", 8086)
        withTestApplication(wsServer.configureModule()) {
            handleWebSocketConversation("/v1/ws/message") { incoming, outgoing ->
                for (message in testMessages) {
                    outgoing.send(Frame.Text(withContext(Dispatchers.IO) {
                        objectMapper.writeValueAsString(message)
                    }))
                    assertEquals("OK", (incoming.receive() as Frame.Text).readText())
                }
            }
        }
    }
}

class UdpTest {
    object UdpTestListener : ChatMessageListener {
        var lastReceivedMessage: Message? = null

        override fun messageReceived(message: Message) {
            lastReceivedMessage = message
        }
    }

    private val udpServer = UdpChatServer("0.0.0.0", 3000)
    private val udpClient = UdpChatClient("0.0.0.0", 3000)

    init {
        thread {
            udpServer.start()
        }
        sleep(2000)
        udpServer.setMessageListener(UdpTestListener)
    }

    @Test
    fun `test udp message receiving`() {
        val message = Message("GhostUser", "I want this delivered!")
        udpClient.sendMessage(message)
        sleep(500)
        assertEquals(message, (udpServer.listener as UdpTestListener).lastReceivedMessage)
        udpServer.stop()
    }
}