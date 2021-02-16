package ru.senin.kotlin.net.registry

import com.fasterxml.jackson.databind.SerializationFeature
import io.github.rybalkinsd.kohttp.ext.httpGet
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import ru.senin.kotlin.net.*
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

fun checkHttpUser(address: UserAddress): Boolean {
    val response = "http://${address.host}:${address.port}".httpGet()
    return response.isSuccessful
}

fun checkWebSocketUser(address: UserAddress): Boolean {
    return runBlocking {
        val client = HttpClient {
            install(WebSockets)
        }
        try {
            client.ws(
                method = HttpMethod.Get,
                host = address.host,
                port = address.port,
            ) {}
            return@runBlocking true
        } catch (e: Exception) {
            return@runBlocking false
        }
    }
}

fun checkUdpUser(address: UserAddress): Boolean {
    return runBlocking {
        var numberOfAttempts = 0
        var socket: ConnectedDatagramSocket
        while (true) {
            try {
                socket = aSocket(ActorSelectorManager(Dispatchers.IO)).udp()
                    .connect(InetSocketAddress(address.host, address.port))
                break
            } catch (e: Exception) {
                numberOfAttempts++
                if (numberOfAttempts == 30)
                    return@runBlocking false
            }
        }
        val channel = socket.openWriteChannel()

        val x = (0 until Int.MAX_VALUE).random()
        val hostAddress = UserAddress(Protocol.UDP, "127.0.0.1", 8088)
        channel.writeStringUtf8("CHECK ID=${x} ADDRESS=${hostAddress.host}:${hostAddress.port}")

        val rChannel = socket.openReadChannel()
        try {
            val y = rChannel.readInt()
            return@runBlocking (x == y)
        } catch (e: Exception) {
            return@runBlocking false
        }
    }
}

fun checkUser(address: UserAddress): Boolean {
    return when (address.protocol) {
        Protocol.HTTP -> checkHttpUser(address)
        Protocol.WEBSOCKET -> checkWebSocketUser(address)
        else -> checkUdpUser(address)
    }
}

fun main(args: Array<String>) {
    thread {
        while (true) {
            Thread.sleep(1000 * 120)
            for ((user, address) in Registry.users) {
                Registry.numberOfAttempts[user]?.let {
                    if (!checkUser(address))
                        Registry.numberOfAttempts[user] = it + 1
                    else
                        Registry.numberOfAttempts[user] = 0
                }

                if (Registry.numberOfAttempts[user] == 3)
                    deleteUser(user)
            }
        }
    }
    EngineMain.main(args)
}

object Registry {
    val users = ConcurrentHashMap<String, UserAddress>()
    val numberOfAttempts = ConcurrentHashMap<String, Int>()
}

fun deleteUser(name: String) {
    Registry.users.remove(name)
    Registry.numberOfAttempts.remove(name)
}

@Suppress("UNUSED_PARAMETER")
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "invalid argument")
        }
        exception<UserAlreadyRegisteredException> { cause ->
            call.respond(HttpStatusCode.Conflict, cause.message ?: "user already registered")
        }
        exception<IllegalUserNameException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "illegal user name")
        }
    }
    routing {
        get("/v1/health") {
            call.respond(mapOf("status" to "ok"))
        }

        post("/v1/users") {
            val user = call.receive<UserInfo>()
            val name = user.name
            checkUserName(name) ?: throw IllegalUserNameException()
            if (Registry.users.containsKey(name)) {
                throw UserAlreadyRegisteredException()
            }
            Registry.users[name] = user.address
            Registry.numberOfAttempts[name] = 0
            call.respond(mapOf("status" to "ok"))
        }

        get("/v1/users") {
            call.respond(Registry.users)
        }

        put("/v1/users/{name}") {
            val address = call.receive<UserAddress>()
            val name: String = call.parameters["name"].toString()
            checkUserName(name) ?: throw IllegalUserNameException()
            Registry.users[name] = address
            Registry.numberOfAttempts[name] = 0
            call.respond(mapOf("status" to "ok"))
        }

        delete("/v1/users/{name}") {
            val name: String = call.parameters["name"].toString()
            deleteUser(name)
            call.respond(mapOf("status" to "ok"))
        }
    }
}

class UserAlreadyRegisteredException : RuntimeException("User already registered")
class IllegalUserNameException : RuntimeException("Illegal user name")