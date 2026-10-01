package org.linguistic.assistant

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NimMicroserviceOrchestrator {

    private val API_KEY = "YOUR_NVIDIA_NIM_KEY"
    private val BASE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"

    /**
     * Instance #1 & #2: Guardrailed Text & System Intent Routing
     */
    fun processTextCommand(userPrompt: String, callback: (JSONObject) -> Unit) {
        thread {
            try {
                val payload = JSONObject().apply {
                    put("model", "meta/llama-3.1-70b-instruct")
                    put("messages", arrayOf(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", "You are LexiQ System Controller. Output strict JSON tool actions.")
                        },
                        JSONObject().apply { put("role", "user"); put("content", userPrompt) }
                    ))
                }
                val response = executeHttpRequest(BASE_URL, payload)
                callback(JSONObject(response))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Instance #3: NVIDIA Vision NIM for Plant and Animal Camera Identification
     */
    fun processCameraFrameForFloraFauna(bitmap: Bitmap, callback: (String) -> Unit) {
        thread {
            try {
                // Convert Android camera frame bitmap to Base64 JPEG string
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val payload = JSONObject().apply {
                    put("model", "meta/llama-3.2-11b-vision-instruct")
                    put("messages", arrayOf(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", arrayOf(
                                JSONObject().apply {
                                    put("type", "text")
                                    put("text", "Identify any plants or animals in this image. Provide a brief breakdown including species name, habitat, and safety/toxicity notes.")
                                },
                                JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:image/jpeg;base64,$base64Image")
                                    })
                                }
                            ))
                        }
                    ))
                }

                val response = executeHttpRequest(BASE_URL, payload)
                val json = JSONObject(response)
                val analysisText = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                callback(analysisText)
            } catch (e: Exception) {
                e.printStackTrace()
                callback("Vision NIM Telemetry Unavailable")
            }
        }
    }

    private fun executeHttpRequest(urlString: String, jsonBody: JSONObject): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $API_KEY")
        conn.doOutput = true

        conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

        return if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            "{}"
        }
    }
}
