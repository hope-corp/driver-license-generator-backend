package com.papaguycodes.driver_license_generator

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.pdf417.PDF417Writer
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
import java.io.ByteArrayOutputStream
import java.util.Base64

@Serializable
data class LicenseRequest(
    val state: String,
    val city: String,
    val firstName: String,
    val lastName: String,
    val dob: String,
    val address: String,
    val licenseClass: String
)

@Serializable
data class LicenseResponse(
    val licenseNumber: String,
    val state: String,
    val city: String,
    val fullName: String,
    val dob: String,
    val address: String,
    val licenseClass: String,
    val issueDate: String,
    val expDate: String,
    val barcodeBase64: String
)

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
        get("/") {
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>PaPaGuy PDF417 License Generator</title>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js"></script>
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f172a; color: #f8fafc; padding: 20px; display: flex; flex-direction: column; align-items: center; }
                        .main-layout { display: flex; flex-wrap: wrap; gap: 30px; justify-content: center; max-width: 1000px; width: 100%; }
                        .form-container { flex: 1; min-width: 320px; background: #1e293b; padding: 25px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); }
                        h2 { text-align: center; color: #38bdf8; margin-top: 0; }
                        label { display: block; margin-top: 10px; margin-bottom: 4px; font-size: 13px; color: #94a3b8; }
                        input, select { width: 100%; padding: 9px; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: #fff; box-sizing: border-box; }
                        button { width: 100%; padding: 12px; border-radius: 6px; border: none; background: #0284c7; color: white; font-weight: bold; cursor: pointer; margin-top: 15px; }
                        button:hover { background: #0369a1; }
                        
                        /* ID Card Design */
                        .card-preview-area { flex: 1; min-width: 340px; display: flex; flex-direction: column; align-items: center; }
                        #licenseCard {
                            width: 380px; height: 240px; background: linear-gradient(135deg, #e0f2fe 0%, #ffffff 100%);
                            border-radius: 12px; border: 2px solid #cbd5e1; padding: 15px; box-sizing: border-box;
                            color: #0f172a; position: relative; box-shadow: 0 10px 25px rgba(0,0,0,0.5); display: none;
                        }
                        .card-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0284c7; padding-bottom: 5px; margin-bottom: 10px; }
                        .card-header h3 { margin: 0; font-size: 16px; color: #0369a1; text-transform: uppercase; }
                        .card-header span { font-size: 12px; font-weight: bold; background: #0284c7; color: white; padding: 2px 6px; border-radius: 4px; }
                        .card-body { display: flex; gap: 12px; }
                        .photo-box { width: 85px; height: 105px; background: #cbd5e1; border: 1px solid #94a3b8; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 10px; color: #475569; }
                        .details { flex: 1; font-size: 11px; line-height: 1.4; }
                        .details strong { color: #0369a1; display: inline-block; width: 65px; }
                        .barcode-container { margin-top: 10px; text-align: center; }
                        .barcode-container img { width: 100%; height: 42px; object-fit: fill; }
                        #downloadBtn { background: #16a34a; display: none; margin-top: 15px; width: 380px; }
                        #downloadBtn:hover { background: #15803d; }
                    </style>
                </head>
                <body>
                    <h2>PaPaGuy Driver License System</h2>
                    <div class="main-layout">
                        <!-- Form Area -->
                        <div class="form-container">
                            <label>State</label>
                            <select id="state" onchange="updateCities()">
                                <option value="CA">California (CA)</option>
                                <option value="NY">New York (NY)</option>
                                <option value="TX">Texas (TX)</option>
                                <option value="FL">Florida (FL)</option>
                            </select>

                            <label>City</label>
                            <select id="city"></select>

                            <label>First Name</label>
                            <input type="text" id="firstName" value="John">

                            <label>Last Name</label>
                            <input type="text" id="lastName" value="Doe">

                            <label>Date of Birth</label>
                            <input type="date" id="dob" value="1995-08-15">

                            <label>Class</label>
                            <select id="licenseClass">
                                <option value="C - Standard">C - Standard</option>
                                <option value="A - Commercial">A - Commercial</option>
                                <option value="M - Motorcycle">M - Motorcycle</option>
                            </select>

                            <label>Street Address</label>
                            <input type="text" id="address" value="742 Evergreen Terrace">

                            <button onclick="generateCard()">Generate ID Card</button>
                        </div>

                        <!-- Card Render & Download Area -->
                        <div class="card-preview-area">
                            <div id="licenseCard">
                                <div class="card-header">
                                    <h3 id="cardStateTitle">CALIFORNIA</h3>
                                    <span>DRIVER LICENSE</span>
                                </div>
                                <div class="card-body">
                                    <div class="photo-box">PHOTO</div>
                                    <div class="details">
                                        <div><strong>DL NO:</strong> <span id="cardDl"></span></div>
                                        <div><strong>NAME:</strong> <span id="cardName"></span></div>
                                        <div><strong>DOB:</strong> <span id="cardDob"></span></div>
                                        <div><strong>CLASS:</strong> <span id="cardClass"></span></div>
                                        <div><strong>CITY:</strong> <span id="cardCity"></span></div>
                                        <div><strong>EXP:</strong> <span id="cardExp"></span></div>
                                    </div>
                                </div>
                                <div class="barcode-container">
                                    <img id="cardBarcode" src="" alt="PDF417 Barcode">
                                </div>
                            </div>
                            <button id="downloadBtn" onclick="downloadCard()">Download Card Image</button>
                        </div>
                    </div>

                    <script>
                        const cityMap = {
                            "CA": ["Los Angeles", "San Francisco", "San Diego", "San Jose"],
                            "NY": ["New York City", "Buffalo", "Rochester", "Albany"],
                            "TX": ["Houston", "Austin", "Dallas", "San Antonio"],
                            "FL": ["Miami", "Orlando", "Tampa", "Jacksonville"]
                        };

                        function updateCities() {
                            const state = document.getElementById('state').value;
                            const citySelect = document.getElementById('city');
                            citySelect.innerHTML = '';
                            cityMap[state].forEach(c => {
                                const opt = document.createElement('option');
                                opt.value = c;
                                opt.innerText = c;
                                citySelect.appendChild(opt);
                            });
                        }
                        updateCities();

                        async function generateCard() {
                            const payload = {
                                state: document.getElementById('state').value,
                                city: document.getElementById('city').value,
                                firstName: document.getElementById('firstName').value,
                                lastName: document.getElementById('lastName').value,
                                dob: document.getElementById('dob').value,
                                address: document.getElementById('address').value,
                                licenseClass: document.getElementById('licenseClass').value
                            };

                            const res = await fetch('/generate', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(payload)
                            });
                            const data = await res.json();

                            document.getElementById('cardStateTitle').innerText = data.state;
                            document.getElementById('cardDl').innerText = data.licenseNumber;
                            document.getElementById('cardName').innerText = data.fullName;
                            document.getElementById('cardDob').innerText = data.dob;
                            document.getElementById('cardClass').innerText = data.licenseClass;
                            document.getElementById('cardCity').innerText = data.city;
                            document.getElementById('cardExp').innerText = data.expDate;
                            document.getElementById('cardBarcode').src = data.barcodeBase64;

                            document.getElementById('licenseCard').style.display = 'block';
                            document.getElementById('downloadBtn').style.display = 'block';
                        }

                        function downloadCard() {
                            const card = document.getElementById('licenseCard');
                            html2canvas(card, { scale: 2 }).then(canvas => {
                                const link = document.createElement('a');
                                link.download = 'Driver_License.png';
                                link.href = canvas.toDataURL('image/png');
                                link.click();
                            });
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()
            call.respondText(htmlContent, ContentType.Text.Html)
        }

        post("/generate") {
            val params = call.receive<LicenseRequest>()
            
            val firstChar = params.firstName.firstOrNull()?.uppercaseChar() ?: 'X'
            val lastChar = params.lastName.firstOrNull()?.uppercaseChar() ?: 'X'
            val licenseNumber = "${params.state}-$firstChar$lastChar-${(100000..999999).random()}"
            
            val barcodeData = "ANSI 636000010002DL00390200DL${licenseNumber}100${params.lastName},${params.firstName}"
            val barcodeBase64 = generatePDF417Base64(barcodeData)

            call.respond(
                LicenseResponse(
                    licenseNumber = licenseNumber,
                    state = params.state,
                    city = params.city,
                    fullName = "${params.firstName.uppercase()} ${params.lastName.uppercase()}",
                    dob = params.dob,
                    address = params.address,
                    licenseClass = params.licenseClass,
                    issueDate = "2026-09-23",
                    expDate = "2031-09-23",
                    barcodeBase64 = "data:image/png;base64,$barcodeBase64"
                )
            )
        }

        post("/validate") {
            val params = call.receive<ValidationRequest>()
            val isValid = params.licenseNumber.startsWith(params.state)
            call.respond(ValidationResponse(isValid))
        }

        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }
    }
}

// PDF417 Barcode Generator Function
fun generatePDF417Base64(text: String): String {
    val writer = PDF417Writer()
    val bitMatrix = writer.encode(text, BarcodeFormat.PDF_417, 300, 80)
    val outputStream = ByteArrayOutputStream()
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}
