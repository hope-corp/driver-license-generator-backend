package com.papaguycodes.driver_license_generator

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {
        get("/") {
            call.respondText("Driver License Generator API is live!")
        }

        get("/health") {
            call.respond(mapOf("status" to "UP", "service" to "Driver License Generator"))
        }

        post("/api/generate") {
            // Placeholder endpoint for driver license generation
            call.respond(mapOf("status" to "success", "message" to "License processing endpoint ready"))
        }
    }
}
