package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import android.graphics.Color
import java.net.NetworkInterface
import java.net.InetAddress
import java.util.*
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var qrImageView: ImageView
    private lateinit var generateButton: Button
    private lateinit var receivedTextView: TextView
    private lateinit var copyButton: Button
    private lateinit var statusTextView: TextView

    private var serverSocket: ServerSocket? = null
    private var isServerRunning = false
    private var currentReceivedText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        qrImageView = findViewById(R.id.qrImageView)
        generateButton = findViewById(R.id.generateButton)
        receivedTextView = findViewById(R.id.receivedTextView)
        copyButton = findViewById(R.id.copyButton)
        statusTextView = findViewById(R.id.statusTextView)

        // Set initial focus to generate button
        generateButton.requestFocus()
    }

    private fun setupClickListeners() {
        generateButton.setOnClickListener {
            generateQRCode()
        }

        copyButton.setOnClickListener {
            copyToClipboard()
        }
    }

    private fun generateQRCode() {
        try {
            val port = findAvailablePort()
            val ipAddress = getLocalIpAddress()

            if (ipAddress != null) {
                val connectionUrl = "http://$ipAddress:$port"

                // Generate QR Code
                val qrCodeBitmap = createQRCode(connectionUrl)
                qrImageView.setImageBitmap(qrCodeBitmap)

                // Start server
                startServer(port)

                statusTextView.text = "QR کد تولید شد. آماده دریافت پیام در آدرس: $connectionUrl"

            } else {
                statusTextView.text = "خطا: نمی‌توان آدرس IP را تشخیص داد"
            }
        } catch (e: Exception) {
            statusTextView.text = "خطا در تولید QR کد: ${e.message}"
        }
    }

    private fun createQRCode(text: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix: BitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress && address.hostAddress.indexOf(':') < 0) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun findAvailablePort(): Int {
        return try {
            val socket = ServerSocket(0)
            val port = socket.localPort
            socket.close()
            port
        } catch (e: IOException) {
            8080 // fallback port
        }
    }

    private fun startServer(port: Int) {
        if (isServerRunning) {
            stopServer()
        }

        thread {
            try {
                serverSocket = ServerSocket(port)
                isServerRunning = true

                while (isServerRunning && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleClient(clientSocket)
                    } catch (e: IOException) {
                        if (isServerRunning) {
                            runOnUiThread {
                                statusTextView.text = "خطا در اتصال سرور: ${e.message}"
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    statusTextView.text = "خطا در راه‌اندازی سرور: ${e.message}"
                }
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        thread {
            try {
                val input = clientSocket.getInputStream().bufferedReader()
                val output = clientSocket.getOutputStream().bufferedWriter()

                val requestLine = input.readLine()
                if (requestLine != null && requestLine.startsWith("GET")) {

                    // Skip headers
                    var line = input.readLine()
                    while (line != null && line.isNotEmpty()) {
                        line = input.readLine()
                    }

                    // Send HTML response
                    val htmlResponse = """
                        <!DOCTYPE html>
                        <html dir="rtl">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>ارسال پیام به تلویزیون</title>
                            <style>
                                body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; text-align: center; }
                                textarea { width: 100%; height: 100px; margin: 10px 0; padding: 10px; font-size: 16px; }
                                button { padding: 15px 30px; font-size: 18px; background: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; }
                                button:hover { background: #0056b3; }
                                .status { margin-top: 20px; padding: 10px; border-radius: 5px; }
                                .success { background: #d4edda; color: #155724; }
                                .error { background: #f8d7da; color: #721c24; }
                            </style>
                        </head>
                        <body>
                            <h1>ارسال پیام به تلویزیون</h1>
                            <textarea id="messageText" placeholder="پیام خود را اینجا بنویسید..."></textarea><br>
                            <button onclick="sendMessage()">ارسال پیام</button>
                            <div id="status"></div>
                            
                            <script>
                                function sendMessage() {
                                    const message = document.getElementById('messageText').value;
                                    if (!message.trim()) {
                                        showStatus('لطفاً پیامی بنویسید', 'error');
                                        return;
                                    }
                                    
                                    fetch('/send', {
                                        method: 'POST',
                                        headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify({ message: message })
                                    })
                                    .then(response => response.json())
                                    .then(data => {
                                        if (data.success) {
                                            showStatus('پیام با موفقیت ارسال شد!', 'success');
                                            document.getElementById('messageText').value = '';
                                        } else {
                                            showStatus('خطا در ارسال پیام', 'error');
                                        }
                                    })
                                    .catch(error => {
                                        showStatus('خطا در اتصال: ' + error, 'error');
                                    });
                                }
                                
                                function showStatus(message, type) {
                                    const status = document.getElementById('status');
                                    status.textContent = message;
                                    status.className = 'status ' + type;
                                    setTimeout(() => { status.textContent = ''; status.className = ''; }, 3000);
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()

                    output.write("HTTP/1.1 200 OK\r\n")
                    output.write("Content-Type: text/html; charset=UTF-8\r\n")
                    output.write("Content-Length: ${htmlResponse.toByteArray(Charsets.UTF_8).size}\r\n")
                    output.write("\r\n")
                    output.write(htmlResponse)
                    output.flush()

                } else if (requestLine != null && requestLine.startsWith("POST /send")) {

                    // Read headers to get content length
                    var contentLength = 0
                    var line = input.readLine()
                    while (line != null && line.isNotEmpty()) {
                        if (line.startsWith("Content-Length:")) {
                            contentLength = line.substring(16).trim().toInt()
                        }
                        line = input.readLine()
                    }

                    // Read POST data
                    val postData = CharArray(contentLength)
                    input.read(postData, 0, contentLength)
                    val jsonData = String(postData)

                    // Extract message from JSON (simple parsing)
                    val messageStart = jsonData.indexOf("\"message\":\"") + 11
                    val messageEnd = jsonData.indexOf("\"", messageStart)
                    val message = if (messageStart > 10 && messageEnd > messageStart) {
                        jsonData.substring(messageStart, messageEnd)
                    } else {
                        "پیام نامعلوم"
                    }

                    // Update UI
                    runOnUiThread {
                        currentReceivedText = message
                        receivedTextView.text = message
                        copyButton.visibility = android.view.View.VISIBLE
                        statusTextView.text = "پیام دریافت شد!"
                    }

                    // Send JSON response
                    val response = """{"success": true}"""
                    output.write("HTTP/1.1 200 OK\r\n")
                    output.write("Content-Type: application/json\r\n")
                    output.write("Content-Length: ${response.length}\r\n")
                    output.write("\r\n")
                    output.write(response)
                    output.flush()
                }

                clientSocket.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun copyToClipboard() {
        if (currentReceivedText.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Received Message", currentReceivedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "متن کپی شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopServer() {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    // Handle TV remote navigation
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val focusedView = currentFocus
                focusedView?.performClick()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}