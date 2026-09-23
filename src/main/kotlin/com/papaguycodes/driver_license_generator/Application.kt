package com.papaguycodes.driver_license_generator

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LicenseRequest(
    val fullName: String,
    val dob: String,
    val licenseClass: String,
    val address: String
)

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
        // Serves the Interactive HTML Form
        get("/") {
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Driver License Generator</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            background-color: #f4f7f6;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                            margin: 0;
                        }
                        .card {
                            background: #ffffff;
                            padding: 30px;
                            border-radius: 10px;
                            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
                            width: 100%;
                            max-width: 400px;
                        }
                        h2 { margin-top: 0; color: #333; text-align: center; }
                        .form-group { margin-bottom: 15px; }
                        label { display: block; margin-bottom: 5px; color: #666; font-size: 14px; }
                        input, select {
                            width: 100%;
                            padding: 10px;
                            border: 1px solid #ccc;
                            border-radius: 5px;
                            box-sizing: border-box;
                        }
                        button {
                            width: 100%;
                            padding: 12px;
                            background-color: #007bff;
                            color: white;
                            border: none;
                            border-radius: 5px;
                            font-size: 16px;
                            cursor: pointer;
                        }
                        button:hover { background-color: #0056b3; }
                        #response {
                            margin-top: 20px;
                            padding: 10px;
                            border-radius: 5px;
                            display: none;
                            word-wrap: break-word;
                        }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>License Generator</h2>
                        <form id="licenseForm">
                            <div class="form-group">
                                <label for="fullName">Full Name</label>
                                <input type="text" id="fullName" required placeholder="John Doe">
                            </div>
                            <div class="form-group">
                                <label for="dob">Date of Birth</label>
                                <input type="date" id="dob" required>
                            </div>
                            <div class="form-group">
                                <label for="licenseClass">License Class</label>
                                <select id="licenseClass">
                                    <option value="Class C (Standard)">Class C (Standard)</option>
                                    <option value="Class A (Commercial)">Class A (Commercial)</option>
                                    <option value="Class M (Motorcycle)">Class M (Motorcycle)</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label for="address">Address</label>
                                <input type="text" id="address" required placeholder="123 Main St, City">
                            </div>
                            <button type="submit">Generate License</button>
                        </form>
                        <div id="response"></div>
                    </div>

                    <script>
                        document.getElementById('licenseForm').addEventListener('submit', async (e) => {
                            e.preventDefault();
                            const responseDiv = document.getElementById('response');
                            responseDiv.style.display = 'block';
                            responseDiv.style.backgroundColor = '#e2e3e5';
                            responseDiv.innerText = 'Processing request...';

                            const payload = {
                                fullName: document.getElementById('fullName').value,
                                dob: document.getElementById('dob').value,
                                licenseClass: document.getElementById('licenseClass').value,
                                address: document.getElementById('address').value
                            };

                            try {
                                const res = await fetch('/api/generate', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify(payload)
                                });
                                const data = await res.json();
                                responseDiv.style.backgroundColor = '#d4edda';
                                responseDiv.style.color = '#155724';
                                responseDiv.innerText = JSON.stringify(data, null, 2);
                            } catch (err) {
                                responseDiv.style.backgroundColor = '#f8d7da';
                                responseDiv.style.color = '#721c24';
                                responseDiv.innerText = 'Failed to process request.';
                            }
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()

            call.respondText(htmlContent, ContentType.Text.Html)
        }

        // Handles Form Submission (JSON Payload)
        post("/api/generate") {
            val request = call.receive<LicenseRequest>()
            
            // Process license creation logic here...
            val generatedNumber = "DL-" + (10000000..99999999).random()

            call.respond(
                mapOf(
                    "status" to "success",
                    "licenseNumber" to generatedNumber,
                    "holder" to request.fullName,
                    "dob" to request.dob,
                    "class" to request.licenseClass,
                    "address" to request.address,
                    "issuedAt" to "2026-09-23"
                )
            )
        }

        // Health check endpoint
        get("/health") {
            call.respond(mapOf("status" to "UP", "service" to "Driver License Generator"))
        }
    }
}
