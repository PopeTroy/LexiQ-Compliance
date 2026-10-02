package org.linguistic.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Quick Settings Tile Controller for Lexi Vision / VLA Mode
 */
class LexiVisionTileService : TileService() {

    private var engine: OnnxEngine? = null

    override fun onCreate() {
        super.onCreate()
        thread {
            try {
                engine = OnnxEngine(applicationContext)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        try {
            if (tile.state == Tile.STATE_INACTIVE) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Lexi Vision: ON"
                
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("ACTION_START_VLA", true)
                }
                
                if (launchIntent != null) {
                    startActivityAndCollapse(launchIntent)
                }
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Lexi Vision"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Lexi Vision"
        }
        
        tile.updateTile()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE 
        tile.label = "Lexi Vision"
        tile.subtitle = "Tap to launch"
        tile.updateTile()
    }
}

/**
 * Spatial Vision Element Detector utilizing NVIDIA Nim Vision-Language-Action (VLA) Pipeline
 */
class VisionElementDetector(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val API_KEY: String by lazy {
        try {
            val field = BuildConfig::class.java.getField("NVIDIA_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    private val BASE_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private var onnxEngine: OnnxEngine? = null

    init {
        thread {
            try {
                onnxEngine = OnnxEngine(context.applicationContext)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    data class ElementDefinition(
        val elementName: String,
        val definition: String,
        val detailedDescription: String,
        val hazardOrNote: String,
        val vlaBoundingTarget: String = "[Unspecified]"
    )

    fun analyzeTappedElement(
        bitmap: Bitmap,
        normalizedX: Float,
        normalizedY: Float,
        callback: (ElementDefinition) -> Unit
    ) {
        thread {
            try {
                if (API_KEY.isBlank()) {
                    notifyResult(
                        ElementDefinition(
                            elementName = "Configuration Missing",
                            definition = "NVIDIA API key is missing or not declared in BuildConfig.",
                            detailedDescription = "Ensure NVIDIA_API_KEY is defined in build.gradle or environment variables.",
                            hazardOrNote = "None"
                        ),
                        callback
                    )
                    return@thread
                }

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val tapLocationPrompt = """
                    The user tapped on the image at normalized coordinates: X = ${"%.2f".format(normalizedX)}, Y = ${"%.2f".format(normalizedY)} (where 0,0 is top-left and 1,1 is bottom-right).
                    Focus specifically on the object, plant, animal, device, or feature located at or directly under these coordinates.
                    
                    Return a JSON object with this exact structure:
                    {
                      "element_name": "Common/Scientific Name of the object",
                      "definition": "A 1-sentence concise definition of what this item is.",
                      "description": "A detailed 2-3 sentence description covering key features, habitat/use, and characteristics.",
                      "safety_note": "Any relevant toxicity, safety, or operational notes (or 'None')."
                    }
                """.trimIndent()

                val payload = JSONObject().apply {
                    put("model", "meta/llama-3.2-11b-vision-instruct")
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                    put("messages", arrayOf(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", arrayOf(
                                JSONObject().apply {
                                    put("type", "text")
                                    put("text", tapLocationPrompt)
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

                val responseStr = executeHttpRequest(BASE_URL, payload)
                val jsonResponse = JSONObject(responseStr)
                
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    notifyResult(
                        ElementDefinition(
                            elementName = "Analysis Unavailable",
                            definition = "No visual elements detected under coordinates.",
                            detailedDescription = "NVIDIA NIM response returned an empty inference result.",
                            hazardOrNote = "None"
                        ),
                        callback
                    )
                    return@thread
                }

                val contentString = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val resultObj = JSONObject(contentString)
                val telemetryTarget = onnxEngine?.computeCameraTelemetry(16L)?.vlaTargetBoundingBox ?: "[x:0, y:0, w:0, h:0]"

                val elementDef = ElementDefinition(
                    elementName = resultObj.optString("element_name", "Unknown Feature"),
                    definition = resultObj.optString("definition", "No definition available."),
                    detailedDescription = resultObj.optString("description", "No details returned."),
                    hazardOrNote = resultObj.optString("safety_note", "None"),
                    vlaBoundingTarget = telemetryTarget
                )

                notifyResult(elementDef, callback)

            } catch (e: Throwable) {
                e.printStackTrace()
                notifyResult(
                    ElementDefinition(
                        elementName = "Detection Error",
                        definition = "Could not parse element at tap target.",
                        detailedDescription = "Check network status or API key configuration.",
                        hazardOrNote = "None"
                    ),
                    callback
                )
            }
        }
    }

    private fun notifyResult(result: ElementDefinition, callback: (ElementDefinition) -> Unit) {
        mainHandler.post {
            callback(result)
        }
    }

    private fun executeHttpRequest(urlString: String, jsonBody: JSONObject): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $API_KEY")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            conn.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                "{}"
            }
        } catch (e: Throwable) {
            "{}"
        }
    }
}
