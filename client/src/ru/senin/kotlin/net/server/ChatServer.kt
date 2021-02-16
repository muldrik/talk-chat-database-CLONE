package ru.senin.kotlin.net.server

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.jackson.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import ru.senin.kotlin.net.Message
import ru.senin.kotlin.net.log
import java.net.InetSocketAddress


interface ChatMessageListener {
    fun messageReceived(message: Message)
}

interface ChatServer {
    var listener: ChatMessageListener?

    fun start()
    fun stop()
    fun setMessageListener(listener: ChatMessageListener)
}

class HttpChatServer(private val host: String, private val port: Int) : ChatServer {
    override var listener: ChatMessageListener? = null
    private val engine = createEngine()

    private fun createEngine(): NettyApplicationEngine {
        val applicationEnvironment = applicationEngineEnvironment {
            log = LoggerFactory.getLogger("http-server")
            classLoader = ApplicationEngineEnvironment::class.java.classLoader
            connector {
                this.host = this@HttpChatServer.host
                this.port = this@HttpChatServer.port
            }
            module(configureModule())
        }
        return NettyApplicationEngine(applicationEnvironment)
    }

    override fun start() {
        engine.start(true)
    }

    override fun stop() {
        engine.stop(1000, 2000)
    }

    override fun setMessageListener(listener: ChatMessageListener) {
        this.listener = listener
    }

    fun configureModule(): Application.() -> Unit = {
        install(CallLogging) {
            level = Level.DEBUG
            filter { call -> call.request.path().startsWith("/") }
        }

        install(DefaultHeaders) {
            header("X-Engine", "Ktor") // will send this header with each response
        }

        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }

        routing {
            get("/v1/health") {
                call.respond(mapOf("status" to "ok"))
            }

            post("/v1/message") {
                try {
                    call.receive<Message>()
                    call.respond(mapOf("status" to "ok"))
                } catch (e: Throwable) {
                    log.error("Error! ${e.message}", e)
                }

            }

            install(StatusPages) {
                exception<IllegalArgumentException> {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}

class WebSocketChatServer(private val host: String, private val port: Int) : ChatServer {
    override var listener: ChatMessageListener? = null
    private val engine = createEngine()

    private fun createEngine(): NettyApplicationEngine {
        val applicationEnvironment = applicationEngineEnvironment {
            log = LoggerFactory.getLogger("websocket-server")
            classLoader = ApplicationEngineEnvironment::class.java.classLoader
            connector {
                this.host = this@WebSocketChatServer.host
                this.port = this@WebSocketChatServer.port
            }
            module(configureModule())
        }
        return NettyApplicationEngine(applicationEnvironment)
    }

    override fun start() {
        engine.start(true)
    }

    override fun stop() {
        engine.stop(1000, 2000)
    }

    override fun setMessageListener(listener: ChatMessageListener) {
        this.listener = listener
    }

    fun configureModule(): Application.() -> Unit = {
        install(CallLogging) {
            level = Level.DEBUG
            filter { call -> call.request.path().startsWith("/") }
        }

        install(DefaultHeaders) {
            header("X-Engine", "Ktor") // will send this header with each response
        }

        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }

        install(WebSockets)

        routing {

            webSocket("/v1/ws/message") {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        try {
                            frame.readText()
                        } catch (e: Throwable) {
                            log.error("Error! ${e.message}", e)
                        }
                    }
                    outgoing.send(Frame.Text("OK"))
                }
            }

            install(StatusPages) {
                exception<IllegalArgumentException> {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }
}

class UdpChatServer(host: String, port: Int) : ChatServer {
    private var isListening = false
    override var listener: ChatMessageListener? = null
    private val server = createServer(host, port)

    override fun start() {
        isListening = true
        listen()
    }

    override fun stop() {
        isListening = false
        server.close()
    }

    override fun setMessageListener(listener: ChatMessageListener) {
        this.listener = listener
    }

    private fun createServer(host: String, port: Int): BoundDatagramSocket {
        while (true) {
            try {
                return aSocket(ActorSelectorManager(Dispatchers.IO))
                    .udp()
                    .bind(InetSocketAddress(host, port))
            } catch (e: Exception) {
                // A workaround for a known ktor bug where a websocket may sometimes fail to bind
            }
        }
    }

    private fun listen() {
        try {
            runBlocking {
                while (!server.isClosed && isListening) {
                    val datagram = server.receive()
                    val text = datagram.packet.readText()
                    listener?.messageReceived(jacksonObjectMapper().readValue(text))
                }
            }
        } catch (e: Throwable) {
            // When the server is closed manually with stop() the exception is suppressed, otherwise logged
            if (isListening) {
                log.error("Error! ${e.message}", e)
            }
        }
    }
}

// Send test message using curl:
// curl -v -X POST http://localhost:8080/v1/message -H "Content-type: application/json" -d '{ "user":"ivanov", "text":"Hello!"}'
