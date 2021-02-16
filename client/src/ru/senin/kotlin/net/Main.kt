package ru.senin.kotlin.net

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import ru.senin.kotlin.net.server.ChatServer
import ru.senin.kotlin.net.server.HttpChatServer
import ru.senin.kotlin.net.server.UdpChatServer
import ru.senin.kotlin.net.server.WebSocketChatServer
import java.net.URL
import kotlin.concurrent.thread

class Parameters : Arkenv() {
    val name: String by argument("--name") {
        description = "Name of user"
        defaultValue = { "Pivomaster" }
    }

    val protocol: String by argument("--protocol") {
        description = "Protocol"
        defaultValue = { "HTTP" }
    }

    val registryBaseUrl: String by argument("--registry") {
        description = "Base URL of User Registry"
        defaultValue = { "http://127.0.0.1:8088" }
    }

    val host: String by argument("--host") {
        description = "Hostname or IP to listen on"
        defaultValue = { "0.0.0.0" } // 0.0.0.0 - listen on all network interfaces
    }

    val port: Int by argument("--port") {
        description = "Port to listen for on"
        defaultValue = { Protocol.valueOf(protocol).defaultPort }
    }

    val publicUrl: String? by argument("--public-url") {
        description = "Public URL"
    }

}

val log: Logger = LoggerFactory.getLogger("main")
lateinit var parameters: Parameters

fun main(args: Array<String>) {
    try {
        parameters = Parameters().parse(args)

        if (parameters.help) {
            println(parameters.toString())
            return
        }
        val protocol = Protocol.valueOf(parameters.protocol)
        val host = parameters.host
        val port = parameters.port

        checkUserAddress(host, port) ?: throw IllegalArgumentException("Illegal address '${host}:${port}'")

        val name = parameters.name
        checkUserName(name) ?: throw IllegalArgumentException("Illegal user name '$name'")

        // initialize registry interface
        val objectMapper = jacksonObjectMapper()
        val registry = Retrofit.Builder()
            .baseUrl(parameters.registryBaseUrl)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build().create(RegistryApi::class.java)

        //Check if we can connect the registry
        registry.check().execute()

        // create server engine
        val server: ChatServer = when (protocol) {
            Protocol.WEBSOCKET -> WebSocketChatServer(host, port)
            Protocol.UDP -> UdpChatServer(host, port)
            else -> HttpChatServer(host, port)
        }
        val chat = Chat(name, registry)

        // start server as separate job
        val serverJob = thread {
            server.start()
        }
        try {
            // register our client
            val userAddress = when {
                parameters.publicUrl != null -> {
                    val url = URL(parameters.publicUrl)
                    UserAddress(Protocol.valueOf(url.protocol), url.host, url.port)
                }
                else -> UserAddress(protocol, host, port)
            }
            registry.register(UserInfo(name, userAddress)).execute()

            // start
            chat.commandLoop()
        } finally {
            registry.unregister(name).execute()
            server.stop()
            serverJob.join()
        }
    } catch (e: Exception) {
        log.error("Error! ${e.message}", e)
        println("Error!  ${e.message}")
    }
}