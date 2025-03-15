/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.okidoki.eldia.presentation

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.okidoki.eldia.R
import com.okidoki.eldia.presentation.theme.EldiaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material.CircularProgressIndicator
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    private val qrCodeBitmap = mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.IO).launch {
            val cookies = makePostLoginRequest()
            qrCodeBitmap.value = makeGetDashboardRequest(cookies)
        }
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp("Android", qrCodeBitmap.value)
        }
    }

    fun generateQRCode(content: String): Bitmap {
        val qrCodeWriter = QRCodeWriter()
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
        )
        val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)

        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun makePostLoginRequest(): Map<String, String> {
        val url = URL("https://www.dia.es/api/v1/eservice-back/login") // replace with your API URL
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("X-Id-Application-From", "app") // Add your custom header here

        connection.doOutput = true

        val jsonInputString =
            """{"are_consents_accepted":false,"are_legal_conditions_accepted":true,"email":"${Constants.username}","password":"${Constants.password}"}"""

        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
            writer.write(jsonInputString)
            writer.flush()
        }

        val responseCode = connection.responseCode
        println("POST Response Code : $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK) { //success
            connection.inputStream.bufferedReader().use { reader ->
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                println("Response : $response")
            }

            // Print the cookies
            val cookies = connection.headerFields["Set-Cookie"]
            if (cookies != null) {
                for (cookie in cookies) {
                    println("Cookie: $cookie")
                }
            }
        } else {
            println("POST request not worked")
        }

        // Create a map to store the cookies
        val cookiesMap = mutableMapOf<String, String>()

        // Get the cookies
        val cookies = connection.headerFields["Set-Cookie"]
        if (cookies != null) {
            for (cookie in cookies) {
                val parts = cookie.split(";")[0].split("=")
                if (parts.size == 2) {
                    cookiesMap[parts[0]] = parts[1]
                }
            }
        }

        // Return the cookies
        return cookiesMap
    }

    fun makeGetDashboardRequest(cookies: Map<String, String>): Bitmap? {
        val url =
            URL("https://www.dia.es/api/v1/eservice-back/customer/current/main-dashboard/reduced")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("X-Id-Application-From", "8")
        connection.setRequestProperty("order_source", "app")
        connection.setRequestProperty("X-Marketing-Cookies-Accepted", "1")


        // Set the cookies as headers
        for ((name, value) in cookies) {
            connection.setRequestProperty(name, value)
        }

        val responseCode = connection.responseCode
        println("GET Response Code : $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK) { //success
            connection.inputStream.bufferedReader().use { reader ->
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }

                println("Response : $response")
                // Find the qr_code value using a regular expression
                val regex = """qr_code":\s*"([^"]*)",""".toRegex()
                val matchResult = regex.find(response.toString())
                val qrCode = matchResult?.groups?.get(1)?.value
                if (qrCode != null) {
                    println("QR Code: $qrCode")

                    // Generate a QR code from the qr_code value
                    val qrCodeBitmap = generateQRCode(qrCode)

                    // Return the QR code bitmap
                    return qrCodeBitmap
                } else {
                    println("QR Code not found")
                }
            }
        } else {
            println("GET request not worked")
        }
        return null
    }

    @Composable
    fun QRCodeDisplay(qrCodeBitmap: Bitmap) {
        Image(
            bitmap = qrCodeBitmap.asImageBitmap(),
            contentDescription = "QR Code"
        )
    }

    @Composable
    fun WearApp(greetingName: String, qrCodeBitmap: Bitmap?) {
        EldiaTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                TimeText()
                if (qrCodeBitmap != null) {
                    QRCodeDisplay(qrCodeBitmap)
                } else {
                    CircularProgressIndicator()
                }
            }
        }
    }

    @Composable
    fun Greeting(greetingName: String) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = stringResource(R.string.hello_world, greetingName)
        )
    }
}

