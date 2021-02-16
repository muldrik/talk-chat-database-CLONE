package ru.senin.kotlin.net.server

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

abstract class BaseChatServer : ChatServer {
    val objectMapper = jacksonObjectMapper()
    var listener: ChatMessageListener? = null

    override fun setMessageListener(listener: ChatMessageListener) {
        this.listener = listener
    }
}