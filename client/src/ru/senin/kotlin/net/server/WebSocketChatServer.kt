package ru.senin.kotlin.net.server

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import ru.senin.kotlin.net.Message
import ru.senin.kotlin.net.WebSocketOptions
import java.time.Duration

class WebSocketChatServer(host: String, port: Int) : NettyChatServer(host, port) {

    override fun configureModule(): Application.() -> Unit = {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket(WebSocketOptions.path) {
                while (true) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val content = frame.readText()
                        val message = objectMapper.readValue<Message>(content)
                        UdpChatServer.log.debug("Message: $message")
                        listener?.messageReceived(message.user, message.text)
                        outgoing.send(Frame.Text("ok"))
                    }
                }
            }
        }
    }

}
