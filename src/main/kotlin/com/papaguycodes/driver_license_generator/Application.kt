package com.papaguycodes.driver_license_generator

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LicenseRequest(val state: String, val firstName: String, val lastName: String)

@Serializable
data class LicenseResponse(val licenseNumber: String)

@Serializable
data class ValidationRequest(val state: String, val licenseNumber: String)

@Serializable
data class ValidationResponse(val valid: Boolean)

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

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.localizedMessage ?: "Internal error")))
        }
    }

    routing {
        // Web Form Dashboard at Root URL
        get("/") {
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>PaPaGuy Driver License System</title>
                    <style>
                        body { font-family: Arial, sans-serif; background: #0f172a; color: #f8fafc; padding: 20px; display: flex; justify-content: center; }
                        .container { width: 100%; max-width: 500px; background: #1e293b; padding: 25px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); }
                        h2 { text-align: center; color: #38bdf8; margin-top: 0; }
                        .section { margin-bottom: 25px; padding-bottom: 20px; border-bottom: 1px solid #334155; }
                        label { display: block; margin-bottom: 6px; font-size: 14px; color: #94a3b8; }
                        input, select { width: 100%; padding: 10px; margin-bottom: 12px; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: #fff; box-sizing: border-box; }
                        button { width: 100%; padding: 12px; border-radius: 6px; border: none; background: #0284c7; color: white; font-weight: bold; cursor: pointer; }
                        button:hover { background: #0369a1; }
                        .result { margin-top: 10px; padding: 10px; border-radius: 6px; font-family: monospace; word-break: break-all; display: none; }
                        .success { background: #065f46; color: #34d399; }
                        .error { background: #881337; color: #fecdd3; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h2>PaPaGuy License System</h2>
                        
                        <!-- Generator Form -->
                        <div class="section">
                            <h3>Generate License</h3>
                            <label>State Code (2 Letters)</label>
                            <input type="text" id="genState" placeholder="e.g. CA, NY, TX" maxlength="2" value="CA">
                            <label>First Name</label>
                            <input type="text" id="genFirst" placeholder="John">
                            <label>Last Name</label>
                            <input type="text" id="genLast" placeholder="Doe">
                            <button onclick="generateLicense()">Generate License Number</button>
                            <div id="genResult" class="result"></div>
                        </div>

                        <!-- Validation Form -->
                        <div>
                            <h3>Validate License</h3>
                            <label>State Code</label>
                            <input type="text" id="valState" placeholder="e.g. CA" maxlength="2" value="CA">
                            <label>License Number</label>
                            <input type="text" id="valNumber" placeholder="e.g. CA-JD-123456">
                            <button onclick="validateLicense()">Validate License Number</button>
                            <div id="valResult" class="result"></div>
                        </div>
                    </div>

                    <script>
                        async function generateLicense() {
                            const resDiv = document.getElementById('genResult');
                            const payload = {
                                state: document.getElementById('genState').value.toUpperCase(),
                                firstName: document.getElementById('genFirst').value,
                                lastName: document.getElementById('genLast').value
                            };
                            const res = await fetch('/generate', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(payload)
                            });
                            const data = await res.json();
                            resDiv.style.display = 'block';
                            resDiv.className = 'result success';
                            resDiv.innerText = 'Generated Number: ' + (data.licenseNumber || data.error);
                        }

                        async function validateLicense() {
                            const resDiv = document.getElementById('valResult');
                            const payload = {
                                state: document.getElementById('valState').value.toUpperCase(),
                                licenseNumber: document.getElementById('valNumber').value
                            };
                            const res = await fetch('/validate', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(payload)
                            });
                            const data = await res.json();
                            resDiv.style.display = 'block';
                            if (data.valid) {
                                resDiv.className = 'result success';
                                resDiv.innerText = 'Status: VALID LICENSE';
                            } else {
                                resDiv.className = 'result error';
                                resDiv.innerText = 'Status: INVALID LICENSE';
                            }
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html)
        }

        // Generate Route
        post("/generate") {
            val params = call.receive<LicenseRequest>()
            val license = generateLicense(params.state, params.firstName, params.lastName)
            call.respond(LicenseResponse(license))
        }

        // Validate Route
        post("/validate") {
            val params = call.receive<ValidationRequest>()
            val isValid = validateLicenseNumber(params.state, params.licenseNumber)
            call.respond(ValidationResponse(isValid))
        }

        // Health check endpoint for Railway
        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }
    }
}

fun generateLicense(state: String, firstName: String, lastName: String): String {
    val firstChar = firstName.firstOrNull()?.uppercaseChar() ?: 'X'
    val lastChar = lastName.firstOrNull()?.uppercaseChar() ?: 'X'
    val initials = "$firstChar$lastChar"
    val randomDigits = (100000..999999).random().toString()
    return "${state.uppercase()}-$initials-$randomDigits"
}

fun validateLicenseNumber(state: String, licenseNumber: String): Boolean {
    val patterns = mapOf(
        "AL" to Regex("^AL-[A-Z]{2}-\\d{6}$"),
        "AK" to Regex("^AK-[A-Z]{2}-\\d{6}$"),
        "AZ" to Regex("^AZ-[A-Z]{2}-\\d{6}$"),
        "AR" to Regex("^AR-[A-Z]{2}-\\d{6}$"),
        "CA" to Regex("^CA-[A-Z]{2}-\\d{6}$"),
        "CO" to Regex("^CO-[A-Z]{2}-\\d{6}$"),
        "CT" to Regex("^CT-[A-Z]{2}-\\d{6}$"),
        "DE" to Regex("^DE-[A-Z]{2}-\\d{6}$"),
        "FL" to Regex("^FL-[A-Z]{2}-\\d{6}$"),
        "GA" to Regex("^GA-[A-Z]{2}-\\d{6}$"),
        "HI" to Regex("^HI-[A-Z]{2}-\\d{6}$"),
        "ID" to Regex("^ID-[A-Z]{2}-\\d{6}$"),
        "IL" to Regex("^IL-[A-Z]{2}-\\d{6}$"),
        "IN" to Regex("^IN-[A-Z]{2}-\\d{6}$"),
        "IA" to Regex("^IA-[A-Z]{2}-\\d{6}$"),
        "KS" to Regex("^KS-[A-Z]{2}-\\d{6}$"),
        "KY" to Regex("^KY-[A-Z]{2}-\\d{6}$"),
        "LA" to Regex("^LA-[A-Z]{2}-\\d{6}$"),
        "ME" to Regex("^ME-[A-Z]{2}-\\d{6}$"),
        "MD" to Regex("^MD-[A-Z]{2}-\\d{6}$"),
        "MA" to Regex("^MA-[A-Z]{2}-\\d{6}$"),
        "MI" to Regex("^MI-[A-Z]{2}-\\d{6}$"),
        "MN" to Regex("^MN-[A-Z]{2}-\\d{6}$"),
        "MS" to Regex("^MS-[A-Z]{2}-\\d{6}$"),
        "MO" to Regex("^MO-[A-Z]{2}-\\d{6}$"),
        "MT" to Regex("^MT-[A-Z]{2}-\\d{6}$"),
        "NE" to Regex("^NE-[A-Z]{2}-\\d{6}$"),
        "NV" to Regex("^NV-[A-Z]{2}-\\d{6}$"),
        "NH" to Regex("^NH-[A-Z]{2}-\\d{6}$"),
        "NJ" to Regex("^NJ-[A-Z]{2}-\\d{6}$"),
        "NM" to Regex("^NM-[A-Z]{2}-\\d{6}$"),
        "NY" to Regex("^NY-[A-Z]{2}-\\d{6}$"),
        "NC" to Regex("^NC-[A-Z]{2}-\\d{6}$"),
        "ND" to Regex("^ND-[A-Z]{2}-\\d{6}$"),
        "OH" to Regex("^OH-[A-Z]{2}-\\d{6}$"),
        "OK" to Regex("^OK-[A-Z]{2}-\\d{6}$"),
        "OR" to Regex("^OR-[A-Z]{2}-\\d{6}$"),
        "PA" to Regex("^PA-[A-Z]{2}-\\d{6}$"),
        "RI" to Regex("^RI-[A-Z]{2}-\\d{6}$"),
        "SC" to Regex("^SC-[A-Z]{2}-\\d{6}$"),
        "SD" to Regex("^SD-[A-Z]{2}-\\d{6}$"),
        "TN" to Regex("^TN-[A-Z]{2}-\\d{6}$"),
        "TX" to Regex("^TX-[A-Z]{2}-\\d{6}$"),
        "UT" to Regex("^UT-[A-Z]{2}-\\d{6}$"),
        "VT" to Regex("^VT-[A-Z]{2}-\\d{6}$"),
        "VA" to Regex("^VA-[A-Z]{2}-\\d{6}$"),
        "WA" to Regex("^WA-[A-Z]{2}-\\d{6}$"),
        "WV" to Regex("^WV-[A-Z]{2}-\\d{6}$"),
        "WI" to Regex("^WI-[A-Z]{2}-\\d{6}$"),
        "WY" to Regex("^WY-[A-Z]{2}-\\d{6}$"),
        "DC" to Regex("^DC-[A-Z]{2}-\\d{6}$")
    )
    val regex = patterns[state.uppercase()] ?: return false
    return regex.matches(licenseNumber)
}
