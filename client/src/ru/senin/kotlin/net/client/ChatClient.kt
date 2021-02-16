package ru.senin.kotlin.net.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import ru.senin.kotlin.net.HttpApi
import ru.senin.kotlin.net.Message
import ru.senin.kotlin.net.log
import java.net.InetSocketAddress
import java.util.*


interface ChatClient {
    fun sendMessage(message: Message)
}

class HttpChatClient(host: String, port: Int) : ChatClient {
    private val objectMapper = jacksonObjectMapper()
    private val httpApi: HttpApi = Retrofit.Builder()
        .baseUrl("http://$host:$port")
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build()
        .create(HttpApi::class.java)

    override fun sendMessage(message: Message) {
        val response = httpApi.sendMessage(message).execute()
        if (!response.isSuccessful) {
            println("${response.code()} ${response.message()}")
        }
    }
}

class WebSocketChatClient(private val host: String, private val port: Int) : ChatClient {
    private val objectMapper = jacksonObjectMapper()

    private val messageQueue: Queue<Message> = LinkedList<Message>()
    private var socketHangupSignal = false
    private var socketIsOpen = false
    private var client: HttpClient = HttpClient {
        install(WebSockets)
    }

    private fun runWebSocket() = runBlocking {
        val startTime = Calendar.getInstance().time.time
        socketIsOpen = true
        client.ws(
            method = HttpMethod.Get,
            host = host,
            port = port,
            path = "/v1/ws/message"
        ) {
            while (!socketHangupSignal && socketIsOpen) {
                while (messageQueue.isNotEmpty()) {
                    try {
                        val message = messageQueue.remove()
                        val jsonMessage = withContext(Dispatchers.IO) {
                            objectMapper.writeValueAsString(message)
                        }
                        send(Frame.Text(jsonMessage))
                    } catch (e: Throwable) {
                        log.error("Error! ${e.message}", e)
                    }
                }
                if (Calendar.getInstance().time.time - startTime > 120 * 1000) {
                    socketHangupSignal = true
                }
            }
            socketIsOpen = false
            socketHangupSignal = false
        }
    }

    override fun sendMessage(message: Message) {
        messageQueue.add(message)
        if (!socketIsOpen) {
            runWebSocket()
        }
    }
}

class UdpChatClient(private val host: String, private val port: Int) : ChatClient {

    private fun createClient(): ConnectedDatagramSocket {
        while (true) {
            try {
                return aSocket(ActorSelectorManager(Dispatchers.IO))
                    .udp()
                    .connect(InetSocketAddress(host, port))
            } catch (e: Exception) {
                // A workaround for a known ktor bug where a websocket may sometimes fail to bind
            }
        }
    }

    override fun sendMessage(message: Message) = runBlocking {
        val socket = createClient()
        val output = socket.openWriteChannel(autoFlush = true)

        output.write(withContext(Dispatchers.IO) {
            jacksonObjectMapper().writeValueAsString(message)
        })
    }
}

