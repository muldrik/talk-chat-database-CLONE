package ru.senin.kotlin.net.server

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.slf4j.event.Level
import ru.senin.kotlin.net.HttpOptions
import ru.senin.kotlin.net.Message

class HttpChatServer(host: String, port: Int) : NettyChatServer(host, port) {

    override fun configureModule(): Application.() -> Unit = {
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
            get(HttpOptions.healthCheckPath) {
                call.respondText("OK", contentType = ContentType.Text.Plain)
            }
            post(HttpOptions.path) {
                val message = call.receive<Message>()
                listener?.messageReceived(message.user, message.text)
                call.respond(mapOf("status" to "ok"))
            }
            install(StatusPages) {
                exception<IllegalArgumentException> {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
        }
    }

}

