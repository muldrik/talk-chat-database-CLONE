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
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
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

interface DataProcessor {
    fun addUser(name: String, userAddress: UserAddress)
    fun deleteUser(name: String)
    fun isUserRegistered(name: String): Boolean
    fun getUsersMap(): Map<String, UserAddress>
    fun clear()
    fun updateAttempts()
}

class HashMapProcessor: DataProcessor {
    private val users = ConcurrentHashMap<String, UserAddress>()
    private val numberOfAttempts = ConcurrentHashMap<String, Int>()

    override fun addUser(name: String, userAddress: UserAddress) {
        users[name] = userAddress
        numberOfAttempts[name] = 0
    }

    override fun deleteUser(name: String) {
        users.remove(name)
        numberOfAttempts.remove(name)
    }

    override fun isUserRegistered(name: String): Boolean {
        return users.containsKey(name)
    }

    override fun getUsersMap(): Map<String, UserAddress> {
        return users
    }

    override fun clear() {
        users.clear()
    }

    override fun updateAttempts() {
        for ((user, address) in users) {
            numberOfAttempts[user]?.let {
                if (!checkUser(address))
                    numberOfAttempts[user] = it + 1
                else
                    numberOfAttempts[user] = 0
            }

            if (numberOfAttempts[user] == 3)
                deleteUser(user)
        }
    }
}


class SqlProcessor: DataProcessor {

    object UserInfos : IntIdTable() {
        val name = varchar("name", 50)
        val protocol: Column<String> = varchar("protocol", 50)
        val host:  Column<String> = varchar("host", 50)
        val port: Column<Int> = integer("port")
        val numberOfAttempts: Column<Int> = integer("number_of_attempts")
    }

    class UserInfo(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UserInfo>(UserInfos)
        var name by UserInfos.name
        var protocol by UserInfos.protocol
        var host by UserInfos.host
        var port by UserInfos.port
        var numberOfAttempts by UserInfos.numberOfAttempts
    }

    init {
        Database.connect("jdbc:h2:./usersDatabase", driver = "org.h2.Driver")

        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(UserInfos)
        }
    }

    fun userToAddress(user: ResultRow): UserAddress {
        val protocol = when(user[UserInfos.protocol]) {
            "http" -> Protocol.HTTP
            "ws" -> Protocol.WEBSOCKET
            "udp" -> Protocol.UDP
            else -> Protocol.HTTP
        }
        return UserAddress(protocol, user[UserInfos.host], user[UserInfos.port])
    }

    override fun addUser(name: String, userAddress: UserAddress) {
        transaction {
            UserInfo.new {
                this.name = name
                protocol = userAddress.protocol.protocol
                host = userAddress.host
                port = userAddress.port
                numberOfAttempts = 0
            }
        }
    }

    override fun deleteUser(name: String) {
        transaction {
            UserInfos.deleteWhere { UserInfos.name eq name }
        }
    }

    override fun isUserRegistered(name: String): Boolean {
        var result = false
        transaction {
            result = (UserInfos.select { UserInfos.name eq name }.count() > 0)
        }
        return result
    }

    override fun getUsersMap(): Map<String, UserAddress> {
        lateinit var result: Map<String, UserAddress>
        transaction {
            println(UserInfos.selectAll().associateBy({it[UserInfos.name]}, {userToAddress(it)}).toString())
            result = UserInfos.selectAll().associateBy({it[UserInfos.name]}, {userToAddress(it)})
        }
        return result
    }

    override fun clear() {
        transaction {
            UserInfos.deleteAll()
        }
    }

    override fun updateAttempts() {
        transaction {
            UserInfos.selectAll().forEach { user ->
                user[UserInfos.numberOfAttempts].let {
                    if (!checkUser(userToAddress(user)))
                        user[UserInfos.numberOfAttempts] = it + 1
                    else
                        user[UserInfos.numberOfAttempts] = 0
                }
            }
            UserInfos.deleteWhere { UserInfos.numberOfAttempts greater 3 }
        }
    }

}

var Registry: DataProcessor = SqlProcessor()

fun main(args: Array<String>) {

    thread {
        while (true) {
            Thread.sleep(1000 * 120)
            Registry.updateAttempts()

        }
    }
    EngineMain.main(args)
}

@Suppress("UNUSED_PARAMETER")
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    //val database: String = environment.config.property("database").getString()
    /*Registry = when(database) {
        "sql" -> TODO()
        else -> HashMapProcessor()
    }*/
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
            if (Registry.isUserRegistered(name)) {
                throw UserAlreadyRegisteredException()
            }
            Registry.addUser(name, user.address)
            call.respond(mapOf("status" to "ok"))
        }

        get("/v1/users") {
            call.respond(Registry.getUsersMap())
        }

        put("/v1/users/{name}") {
            val address = call.receive<UserAddress>()
            val name: String = call.parameters["name"].toString()
            checkUserName(name) ?: throw IllegalUserNameException()
            Registry.addUser(name, address)
            call.respond(mapOf("status" to "ok"))
        }

        delete("/v1/users/{name}") {
            val name: String = call.parameters["name"].toString()
            Registry.deleteUser(name)
            call.respond(mapOf("status" to "ok"))
        }
    }
}

class UserAlreadyRegisteredException : RuntimeException("User already registered")
class IllegalUserNameException : RuntimeException("Illegal user name")