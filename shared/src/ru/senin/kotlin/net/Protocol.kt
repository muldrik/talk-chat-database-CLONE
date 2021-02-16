package ru.senin.kotlin.net

sealed class ProtocolOptions
object HttpOptions: ProtocolOptions() {
    const val path = "/v1/message"
    const val healthCheckPath = "/v1/health"
}
object WebSocketOptions: ProtocolOptions() {
    const val path = "/v1/ws/message"
}

enum class Protocol(val scheme: String, val defaultPort: Int) {
    HTTP("http", 8080),
    UDP("udp", 3000),
    WEBSOCKET("ws", 8082)
}

data class UserAddress(
    val protocol: Protocol,
    val host: String,
    val port: Int = protocol.defaultPort
) {
    override fun toString(): String {
        return "${protocol.scheme}://${host}:${port}"
    }
}

data class UserInfo(val name: String, val address: UserAddress)

data class Message(val user: String, val text: String)

fun checkUserName(name: String) = """^[a-zA-Z0-9-_.]+$""".toRegex().find(name)
