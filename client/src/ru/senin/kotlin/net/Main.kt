package ru.senin.kotlin.net

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import ru.senin.kotlin.net.client.HttpChatClient
import ru.senin.kotlin.net.client.UdpChatClient
import ru.senin.kotlin.net.client.WebSocketChatClient
import ru.senin.kotlin.net.server.*
import java.net.URL
import kotlin.concurrent.thread

class Parameters : Arkenv() {
    val name : String by argument("--name") {
        description = "Name of user"
    }

    val registryBaseUrl : String by argument("--registry"){
        description = "Base URL of User Registry"
        defaultValue = { "http://localhost:8088" }
    }

    val host : String by argument("--host"){
        description = "Hostname or IP to listen on"
        defaultValue = { "127.0.0.1" }
    }

    val port : Int by argument("--port") {
        description = "Port to listen for on"
        defaultValue = { 8080 }
    }

    val publicUrl : String? by argument("--public-url") {
        description = "Public URL"
    }

    val protocol : Protocol by argument("--protocol") {
        description = "protocol (HTTP, WEBSOCKET, UDP)"
        defaultValue = { Protocol.HTTP }
        mapping = { Protocol.valueOf(it) }
    }
}

val log: Logger = LoggerFactory.getLogger("main")
lateinit var parameters : Parameters

object ServerFactory : ChatServerFactory {
    override fun create(protocol: Protocol, host: String, port: Int): ChatServer {
        return when (protocol) {
            Protocol.HTTP -> HttpChatServer(host, port)
            Protocol.WEBSOCKET -> WebSocketChatServer(host, port)
            Protocol.UDP -> UdpChatServer(host, port)
        }
    }
}

object ClientFactory : ChatClientFactory {
    override fun create(protocol: Protocol, host: String, port: Int): ChatClient {
        return when (protocol) {
            Protocol.HTTP -> HttpChatClient(host, port)
            Protocol.WEBSOCKET -> WebSocketChatClient(host, port)
            Protocol.UDP -> UdpChatClient(host, port)
        }
    }

    override fun supportedProtocols(): Set<Protocol> = setOf( Protocol.HTTP, Protocol.WEBSOCKET, Protocol.UDP )
}

fun main(args: Array<String>) {
    try {
        parameters = Parameters().parse(args)

        if (parameters.help) {
            println(parameters.toString())
            return
        }
        val host = parameters.host
        val port = parameters.port

        // TODO: validate host and port

        val name = parameters.name
        checkUserName(name) ?: throw IllegalArgumentException("Illegal user name '$name'")

        val protocol = parameters.protocol

        // initialize registry interface
        val objectMapper = jacksonObjectMapper()
        val registry = Retrofit.Builder()
            .baseUrl(parameters.registryBaseUrl)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build().create(RegistryApi::class.java)

        // create server engine
        val server = ServerFactory.create(protocol, host, port)
        val chat = Chat(name, registry)
        server.setMessageListener(chat)

        // start server as separate job
        val serverJob = thread {
            server.start()
        }
        try {
            // register our client
            if (parameters.publicUrl != null) {
                val url = URL(parameters.publicUrl)
                registry.register(UserInfo(name, UserAddress(Protocol.valueOf(url.protocol.toUpperCase()), url.host, url.port))).execute()
            }
            else {
                registry.register(UserInfo(name, UserAddress(protocol, host, port))).execute()
            }

            // start
            chat.commandLoop()
        }
        finally {
            registry.unregister(name).execute()
            server.stop()
            serverJob.join()
        }
    }
    catch (e: Exception) {
        log.error("Error! ${e.message}", e)
        println("Error! ${e.message}")
    }
}
