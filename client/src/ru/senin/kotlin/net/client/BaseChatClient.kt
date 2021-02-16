package ru.senin.kotlin.net.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import ru.senin.kotlin.net.server.ChatClient

abstract class BaseChatClient : ChatClient {
    protected val objectMapper = jacksonObjectMapper()
}
