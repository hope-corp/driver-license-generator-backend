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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

@Serializable
data class LicenseRequest(
    val firstName: String,
    val surname: String,
    val dob: String,
    val issueDate: String,
    val gender: String,
    val state: String,
    val city: String,
    val address: String,
    val licenseClass: String,
    val customDocNumber: String? = null
)

@Serializable
data class LicenseResponse(
    val licenseNumber: String,
    val firstName: String,
    val surname: String,
    val dob: String,
    val issueDate: String,
    val expDate: String,
    val gender: String,
    val state: String,
    val city: String,
    val address: String,
    val licenseClass: String,
    val barcodeBase64: String
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
                    <title>PaPaGuy Driver License System</title>
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js"></script>
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f172a; color: #f8fafc; padding: 20px; display: flex; flex-direction: column; align-items: center; }
                        .main-layout { display: flex; flex-wrap: wrap; gap: 30px; justify-content: center; max-width: 1050px; width: 100%; }
                        .form-container { flex: 1; min-width: 320px; background: #1e293b; padding: 25px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.4); }
                        h2 { text-align: center; color: #38bdf8; margin-top: 0; }
                        .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
                        label { display: block; margin-top: 10px; margin-bottom: 4px; font-size: 13px; color: #94a3b8; }
                        input, select { width: 100%; padding: 9px; border-radius: 6px; border: 1px solid #475569; background: #0f172a; color: #fff; box-sizing: border-box; }
                        input[readonly] { background: #334155; color: #cbd5e1; cursor: not-allowed; }
                        button { width: 100%; padding: 12px; border-radius: 6px; border: none; background: #0284c7; color: white; font-weight: bold; cursor: pointer; margin-top: 20px; }
                        button:hover { background: #0369a1; }
                        
                        /* ID Card Styling */
                        .card-preview-area { flex: 1; min-width: 360px; display: flex; flex-direction: column; align-items: center; }
                        #licenseCard {
                            width: 390px; height: 250px; background: linear-gradient(135deg, #e0f2fe 0%, #ffffff 100%);
                            border-radius: 12px; border: 2px solid #cbd5e1; padding: 15px; box-sizing: border-box;
                            color: #0f172a; position: relative; box-shadow: 0 10px 25px rgba(0,0,0,0.5); display: none;
                        }
                        .card-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0284c7; padding-bottom: 4px; margin-bottom: 8px; }
                        .card-header h3 { margin: 0; font-size: 16px; color: #0369a1; text-transform: uppercase; }
                        .card-header span { font-size: 11px; font-weight: bold; background: #0284c7; color: white; padding: 2px 6px; border-radius: 4px; }
                        .card-body { display: flex; gap: 12px; }
                        .photo-box { width: 85px; height: 105px; background: #cbd5e1; border: 1px solid #94a3b8; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 10px; color: #475569; }
                        .details { flex: 1; font-size: 11px; line-height: 1.4; }
                        .details strong { color: #0369a1; display: inline-block; width: 70px; }
                        .barcode-container { margin-top: 8px; text-align: center; }
                        .barcode-container img { width: 100%; height: 42px; object-fit: fill; }
                        #downloadBtn { background: #16a34a; display: none; margin-top: 15px; width: 390px; }
                        #downloadBtn:hover { background: #15803d; }
                    </style>
                </head>
                <body>
                    <h2>PaPaGuy Driver License System</h2>
                    <div class="main-layout">
                        <!-- Form Area -->
                        <div class="form-container">
                            <div class="grid-2">
                                <div>
                                    <label>First Name</label>
                                    <input type="text" id="firstName" value="John">
                                </div>
                                <div>
                                    <label>Surname (Last Name)</label>
                                    <input type="text" id="surname" value="Doe">
                                </div>
                            </div>

                            <div class="grid-2">
                                <div>
                                    <label>Date of Birth</label>
                                    <input type="date" id="dob" value="1995-08-15">
                                </div>
                                <div>
                                    <label>Gender</label>
                                    <select id="gender">
                                        <option value="M">Male (M)</option>
                                        <option value="F">Female (F)</option>
                                        <option value="X">Non-Binary (X)</option>
                                    </select>
                                </div>
                            </div>

                            <div class="grid-2">
                                <div>
                                    <label>Date of Issue</label>
                                    <input type="date" id="issueDate" onchange="calcExpiry()">
                                </div>
                                <div>
                                    <label>Date of Expiry (Auto +5 yrs)</label>
                                    <input type="date" id="expDate" readonly>
                                </div>
                            </div>

                            <label>Document Number (Optional / Auto-generated if empty)</label>
                            <input type="text" id="docNumber" placeholder="Leave blank for auto-generate">

                            <div class="grid-2">
                                <div>
                                    <label>State</label>
                                    <select id="state" onchange="updateCities()"></select>
                                </div>
                                <div>
                                    <label>City</label>
                                    <select id="city"></select>
                                </div>
                            </div>

                            <label>Street Address</label>
                            <input type="text" id="address" value="742 Evergreen Terrace">

                            <label>Class</label>
                            <select id="licenseClass">
                                <option value="C - Standard">C - Standard</option>
                                <option value="A - Commercial">A - Commercial</option>
                                <option value="M - Motorcycle">M - Motorcycle</option>
                            </select>

                            <button onclick="generateCard()">Generate ID Card</button>
                        </div>

                        <!-- Card Preview -->
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
                                        <div><strong>GENDER:</strong> <span id="cardGender"></span></div>
                                        <div><strong>CLASS:</strong> <span id="cardClass"></span></div>
                                        <div><strong>ISSUE:</strong> <span id="cardIssue"></span></div>
                                        <div><strong>EXPIRY:</strong> <span id="cardExp"></span></div>
                                        <div><strong>CITY:</strong> <span id="cardCity"></span></div>
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
                        // Database of 50 US States and Major Cities
                        const stateCitiesMap = {
                            "AL": ["Birmingham", "Montgomery", "Huntsville", "Mobile"],
                            "AK": ["Anchorage", "Fairbanks", "Juneau", "Sitka"],
                            "AZ": ["Phoenix", "Tucson", "Mesa", "Chandler"],
                            "AR": ["Little Rock", "Fort Smith", "Fayetteville", "Springdale"],
                            "CA": ["Los Angeles", "San Francisco", "San Diego", "San Jose", "Sacramento"],
                            "CO": ["Denver", "Colorado Springs", "Aurora", "Fort Collins"],
                            "CT": ["Bridgeport", "Stamford", "New Haven", "Hartford"],
                            "DE": ["Wilmington", "Dover", "Newark", "Middletown"],
                            "FL": ["Miami", "Orlando", "Tampa", "Jacksonville", "Tallahassee"],
                            "GA": ["Atlanta", "Augusta", "Columbus", "Macon", "Savannah"],
                            "HI": ["Honolulu", "Pearl City", "Hilo", "Kailua"],
                            "ID": ["Boise", "Meridian", "Nampa", "Idaho Falls"],
                            "IL": ["Chicago", "Aurora", "Joliet", "Naperville", "Springfield"],
                            "IN": ["Indianapolis", "Fort Wayne", "Evansville", "South Bend"],
                            "IA": ["Des Moines", "Cedar Rapids", "Davenport", "Sioux City"],
                            "KS": ["Wichita", "Overland Park", "Kansas City", "Topeka"],
                            "KY": ["Louisville", "Lexington", "Bowling Green", "Owensboro"],
                            "LA": ["New Orleans", "Baton Rouge", "Shreveport", "Lafayette"],
                            "ME": ["Portland", "Lewiston", "Bangor", "South Portland"],
                            "MD": ["Baltimore", "Frederick", "Rockville", "Gaithersburg", "Annapolis"],
                            "MA": ["Boston", "Worcester", "Springfield", "Cambridge"],
                            "MI": ["Detroit", "Grand Rapids", "Warren", "Sterling Heights", "Lansing"],
                            "MN": ["Minneapolis", "Saint Paul", "Rochester", "Duluth"],
                            "MS": ["Jackson", "Gulfport", "Southaven", "Biloxi"],
                            "MO": ["Kansas City", "St. Louis", "Springfield", "Columbia"],
                            "MT": ["Billings", "Missoula", "Great Falls", "Bozeman"],
                            "NE": ["Omaha", "Lincoln", "Bellevue", "Grand Island"],
                            "NV": ["Las Vegas", "Henderson", "Reno", "North Las Vegas"],
                            "NH": ["Manchester", "Nashua", "Concord", "Dover"],
                            "NJ": ["Newark", "Jersey City", "Paterson", "Elizabeth", "Trenton"],
                            "NM": ["Albuquerque", "Las Cruces", "Rio Rancho", "Santa Fe"],
                            "NY": ["New York City", "Buffalo", "Rochester", "Yonkers", "Albany"],
                            "NC": ["Charlotte", "Raleigh", "Greensboro", "Durham"],
                            "ND": ["Fargo", "Bismarck", "Grand Forks", "Minot"],
                            "OH": ["Columbus", "Cleveland", "Cincinnati", "Toledo", "Akron"],
                            "OK": ["Oklahoma City", "Tulsa", "Norman", "Broken Arrow"],
                            "OR": ["Portland", "Salem", "Eugene", "Gresham"],
                            "PA": ["Philadelphia", "Pittsburgh", "Allentown", "Erie", "Harrisburg"],
                            "RI": ["Providence", "Warwick", "Cranston", "Pawtucket"],
                            "SC": ["Charleston", "Columbia", "North Charleston", "Greenville"],
                            "SD": ["Sioux Falls", "Rapid City", "Aberdeen", "Brookings"],
                            "TN": ["Nashville", "Memphis", "Knoxville", "Chattanooga"],
                            "TX": ["Houston", "San Antonio", "Dallas", "Austin", "Fort Worth"],
                            "UT": ["Salt Lake City", "West Valley City", "Provo", "West Jordan"],
                            "VT": ["Burlington", "South Burlington", "Rutland", "Barre"],
                            "VA": ["Virginia Beach", "Norfolk", "Chesapeake", "Richmond"],
                            "WA": ["Seattle", "Spokane", "Tacoma", "Vancouver", "Olympia"],
                            "WV": ["Charleston", "Huntington", "Morgantown", "Parkersburg"],
                            "WI": ["Milwaukee", "Madison", "Green Bay", "Kenosha"],
                            "WY": ["Cheyenne", "Casper", "Laramie", "Gillette"]
                        };

                        // Populate States
                        const stateSelect = document.getElementById('state');
                        Object.keys(stateCitiesMap).forEach(st => {
                            const opt = document.createElement('option');
                            opt.value = st;
                            opt.innerText = st;
                            stateSelect.appendChild(opt);
                        });
                        stateSelect.value = "CA";

                        function updateCities() {
                            const st = stateSelect.value;
                            const citySelect = document.getElementById('city');
                            citySelect.innerHTML = '';
                            stateCitiesMap[st].forEach(c => {
                                const opt = document.createElement('option');
                                opt.value = c;
                                opt.innerText = c;
                                citySelect.appendChild(opt);
                            });
                        }
                        updateCities();

                        // Set Default Issue Date (Today) and Auto-Calculate Expiry Date
                        const today = new Date().toISOString().split('T')[0];
                        document.getElementById('issueDate').value = today;

                        function calcExpiry() {
                            const issueVal = document.getElementById('issueDate').value;
                            if (issueVal) {
                                const d = new Date(issueVal);
                                d.setFullYear(d.getFullYear() + 5);
                                document.getElementById('expDate').value = d.toISOString().split('T')[0];
                            }
                        }
                        calcExpiry();

                        async function generateCard() {
                            const payload = {
                                firstName: document.getElementById('firstName').value,
                                surname: document.getElementById('surname').value,
                                dob: document.getElementById('dob').value,
                                issueDate: document.getElementById('issueDate').value,
                                gender: document.getElementById('gender').value,
                                state: document.getElementById('state').value,
                                city: document.getElementById('city').value,
                                address: document.getElementById('address').value,
                                licenseClass: document.getElementById('licenseClass').value,
                                customDocNumber: document.getElementById('docNumber').value || null
                            };

                            const res = await fetch('/generate', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(payload)
                            });
                            const data = await res.json();

                            document.getElementById('cardStateTitle').innerText = data.state;
                            document.getElementById('cardDl').innerText = data.licenseNumber;
                            document.getElementById('cardName').innerText = data.surname + ', ' + data.firstName;
                            document.getElementById('cardDob').innerText = data.dob;
                            document.getElementById('cardGender').innerText = data.gender;
                            document.getElementById('cardClass').innerText = data.licenseClass;
                            document.getElementById('cardIssue').innerText = data.issueDate;
                            document.getElementById('cardExp').innerText = data.expDate;
                            document.getElementById('cardCity').innerText = data.city;
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

            // Handle Document Number
            val firstChar = params.firstName.firstOrNull()?.uppercaseChar() ?: 'X'
            val lastChar = params.surname.firstOrNull()?.uppercaseChar() ?: 'X'
            val licenseNumber = if (!params.customDocNumber.isNull transatlanticBlank()) {
                params.customDocNumber
            } else {
                "${params.state}-$firstChar$lastChar-${(100000..999999).random()}"
            }

            // Auto-calculate Expiry Date (+5 years from Issue Date)
            val expDate = try {
                val issue = LocalDate.parse(params.issueDate)
                issue.plusYears(5).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: Exception) {
                "2031-09-23"
            }

            // AAMVA PDF417 Encoded Format Data String
            val rawBarcodeText = "ANSI 636000010002DL00390200DL${licenseNumber}100${params.surname.uppercase()},${params.firstName.uppercase()} DOB:${params.dob} EXP:${expDate} GENDER:${params.gender}"
            val barcodeBase64 = generatePDF417Base64(rawBarcodeText)

            call.respond(
                LicenseResponse(
                    licenseNumber = licenseNumber,
                    firstName = params.firstName.uppercase(),
                    surname = params.surname.uppercase(),
                    dob = params.dob,
                    issueDate = params.issueDate,
                    expDate = expDate,
                    gender = params.gender,
                    state = params.state,
                    city = params.city,
                    address = params.address,
                    licenseClass = params.licenseClass,
                    barcodeBase64 = "data:image/png;base64,$barcodeBase64"
                )
            )
        }

        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }
    }
}

private fun String?.isNullOrBlank(): Boolean = this == null || this.trim().isEmpty()

fun generatePDF417Base64(text: String): String {
    val writer = PDF417Writer()
    val bitMatrix = writer.encode(text, BarcodeFormat.PDF_417, 320, 80)
    val outputStream = ByteArrayOutputStream()
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream)
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}
