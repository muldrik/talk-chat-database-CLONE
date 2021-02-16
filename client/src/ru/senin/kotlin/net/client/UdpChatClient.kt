package ru.senin.kotlin.net.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.senin.kotlin.net.Message
import java.net.InetSocketAddress

class UdpChatClient(private val host: String, private val port: Int) : BaseChatClient() {
    companion object {
        private val socket =
            aSocket(ActorSelectorManager(Dispatchers.IO))
            .udp()
            .bind(InetSocketAddress(0))
    }

    override fun sendMessage(message: Message) {
         val content = objectMapper.writeValueAsString(message)
         runBlocking {
             socket.outgoing.send(Datagram(buildPacket {
                 writeText(content)
             }, InetSocketAddress(host, port)))
         }
    }
}
