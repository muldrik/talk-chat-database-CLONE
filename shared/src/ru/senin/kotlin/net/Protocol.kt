package ru.senin.kotlin.net

enum class Protocol(val protocol: String, val defaultPort: Int) {
    HTTP("http", 8080),
    WEBSOCKET("ws", 8082),
    UDP("udp", 3000)
}

data class UserAddress(
    val protocol: Protocol,
    val host: String,
    val port: Int = protocol.defaultPort
) {
    override fun toString(): String {
        return "${protocol.protocol}://${host}:${port}"
    }
}

data class UserInfo(val name: String, val address: UserAddress)

data class Message(val user: String, val text: String)

fun checkUserName(name: String) = """^[a-zA-Z0-9_.]*${'$'}""".toRegex().find(name)

fun checkUserAddress(host: String, port: Int): Boolean {
    return (port in 0..65535 && """([0-9]{1,3}[\.]){3}[0-9]{1,3}""".toRegex().find(host) != null)
}