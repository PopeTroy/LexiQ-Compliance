package org.linguistic.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.math.sqrt

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
 * Spatial Vision Element Detector with Downloadable Local VLM/Grounding Model Profiles
 * under filesDir/vlm_models/<archiveFolder>, Brus/Euclidean rectification, Pop-Up Cards,
 * and NVIDIA NIM Vision-Language fallback/enrichment.
 */
class VisionElementDetector(private val context: Context) {

    data class VisionModelProfile(
        val id: String,
        val displayName: String,
        val archiveFolder: String,
        val footprintHint: String,
        val nimFallbackModel: String = "meta/llama-3.2-11b-vision-instruct",
        val artifactUrls: Map<String, String>
    )

    data class ElementDefinition(
        val elementName: String,
        val definition: String,
        val detailedDescription: String,
        val hazardOrNote: String,
        val vlaBoundingTarget: String = "[Unspecified]",
        val scientificName: String = "N/A",
        val engineeringName: String = "N/A"
    )

    data class DetectedPopupItem(
        val elementName: String,
        val scientificName: String,
        val engineeringName: String,
        val miniDescription: String,
        val hazardOrNote: String,
        val boundingBox: String,
        val confidence: Float = 0.90f
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isDownloadingModels = AtomicBoolean(false)
    private var activeModelIndex = 0

    private val vlmModels = listOf(
        VisionModelProfile(
            id = "kosmos-2-patch14-224",
            displayName = "Kosmos-2 Grounding VLM",
            archiveFolder = "kosmos_2_patch14_224",
            footprintHint = "Grounded Scene & Box",
            artifactUrls = mapOf(
                "config.json" to "https://huggingface.co/microsoft/kosmos-2-patch14-224/resolve/main/config.json",
                "preprocessor_config.json" to "https://huggingface.co/microsoft/kosmos-2-patch14-224/resolve/main/preprocessor_config.json"
            )
        ),
        VisionModelProfile(
            id = "grounding-dino-tiny",
            displayName = "Grounding DINO Tiny",
            archiveFolder = "grounding_dino_tiny",
            footprintHint = "Open-Set Object Detection",
            artifactUrls = mapOf(
                "config.json" to "https://huggingface.co/IDEA-Research/grounding-dino-tiny/resolve/main/config.json"
            )
        ),
        VisionModelProfile(
            id = "llama-nemotron-embed-vl-1b-v2",
            displayName = "Nemotron Embed VL 1B v2",
            archiveFolder = "llama_nemotron_embed_vl_1b_v2",
            footprintHint = "Multimodal Retrieval & Taxonomy",
            artifactUrls = mapOf(
                "config.json" to "https://huggingface.co/nvidia/llama-nemotron-embed-vl-1b-v2/resolve/main/config.json"
            )
        )
    )

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
                downloadAllVlmModelsAsync()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun currentVisionProfile(): VisionModelProfile = vlmModels[activeModelIndex]

    fun modelDirectoryFor(profile: VisionModelProfile): File {
        return File(File(context.filesDir, "vlm_models"), profile.archiveFolder)
    }

    fun isModelDownloadedLocally(profile: VisionModelProfile): Boolean {
        return try {
            val dir = modelDirectoryFor(profile)
            dir.exists() && dir.isDirectory &&
                (File(dir, "manifest.json").exists() ||
                    (dir.listFiles()?.any { it.isFile && it.length() > 0L } == true))
        } catch (_: Throwable) {
            false
        }
    }

    fun downloadedModelCount(): Int = vlmModels.count { isModelDownloadedLocally(it) }

    fun cycleVisionModel(): VisionModelProfile {
        activeModelIndex = (activeModelIndex + 1) % vlmModels.size
        return currentVisionProfile()
    }

    fun downloadAllVlmModelsAsync(onComplete: ((Int, Int) -> Unit)? = null) {
        if (!isDownloadingModels.compareAndSet(false, true)) {
            mainHandler.post { onComplete?.invoke(downloadedModelCount(), vlmModels.size) }
            return
        }

        thread {
            try {
                val rootDir = File(context.filesDir, "vlm_models").apply { mkdirs() }
                for (profile in vlmModels) {
                    val targetDir = File(rootDir, profile.archiveFolder).apply { mkdirs() }
                    var fetchedFiles = 0

                    for ((fileName, urlString) in profile.artifactUrls) {
                        val outFile = File(targetDir, fileName)
                        if (!outFile.exists() || outFile.length() == 0L) {
                            if (downloadArtifactToFile(urlString, outFile)) {
                                fetchedFiles++
                            }
                        } else {
                            fetchedFiles++
                        }
                    }

                    File(targetDir, "manifest.json").writeText(
                        """
                        {
                          "id": "${profile.id}",
                          "name": "${profile.displayName}",
                          "footprint": "${profile.footprintHint}",
                          "nim_fallback_model": "${profile.nimFallbackModel}",
                          "artifacts_ready": $fetchedFiles,
                          "synced_at_ms": ${System.currentTimeMillis()}
                        }
                        """.trimIndent()
                    )
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                isDownloadingModels.set(false)
                val ready = downloadedModelCount()
                mainHandler.post { onComplete?.invoke(ready, vlmModels.size) }
            }
        }
    }

    private fun downloadArtifactToFile(urlString: String, destination: File): Boolean {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                conn.disconnect()
                destination.exists() && destination.length() > 0L
            } else {
                conn.disconnect()
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Applies a Brus-style radial/Euclidean coordinate adjustment on normalized [0..1]
     * coordinates relative to the image center (0.5, 0.5).
     */
    fun rectifyNormalizedPoint(
        x: Float,
        y: Float,
        k1: Float = -0.04f,
        k2: Float = 0.01f
    ): PointF {
        val cx = 0.5f
        val cy = 0.5f
        val dx = x - cx
        val dy = y - cy
        val r2 = dx * dx + dy * dy
        val radialScale = 1.0f + (k1 * r2) + (k2 * r2.pow(2))
        val rx = (cx + dx * radialScale).coerceIn(0f, 1f)
        val ry = (cy + dy * radialScale).coerceIn(0f, 1f)
        return PointF(rx, ry)
    }

    fun euclideanDistance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    fun buildTalkBackSummary(items: List<DetectedPopupItem>): String {
        if (items.isEmpty()) {
            return "No objects or structures identified in the current view."
        }
        return items.take(4).joinToString(" ") { item ->
            "${item.elementName} (${item.scientificName}, ${item.engineeringName}): ${item.miniDescription}"
        }
    }

    fun analyzeTappedElement(
        bitmap: Bitmap,
        normalizedX: Float,
        normalizedY: Float,
        callback: (ElementDefinition) -> Unit
    ) {
        thread {
            try {
                val activeProfile = currentVisionProfile()
                if (!isModelDownloadedLocally(activeProfile)) {
                    downloadAllVlmModelsAsync()
                }

                if (API_KEY.isBlank()) {
                    val telemetryTarget = onnxEngine?.computeCameraBoundingBox(16L)?.vlaTargetBoundingBox
                        ?: "[x:0.25, y:0.25, w:0.50, h:0.50]"
                    notifyResult(
                        ElementDefinition(
                            elementName = "${activeProfile.displayName} Local Profile",
                            definition = "Local model manifest staged (${downloadedModelCount()}/${vlmModels.size}); cloud NIM key not set.",
                            detailedDescription = "Active profile '${activeProfile.id}' (${activeProfile.footprintHint}) is staged under vlm_models/${activeProfile.archiveFolder}.",
                            hazardOrNote = "None",
                            vlaBoundingTarget = telemetryTarget,
                            scientificName = activeProfile.id,
                            engineeringName = activeProfile.footprintHint
                        ),
                        callback
                    )
                    return@thread
                }

                val rectified = rectifyNormalizedPoint(normalizedX, normalizedY)
                val base64Image = encodeBitmapToBase64(bitmap)

                val tapLocationPrompt = """
                    The user tapped on the image at rectified normalized coordinates: X = ${"%.2f".format(rectified.x)}, Y = ${"%.2f".format(rectified.y)} (where 0,0 is top-left and 1,1 is bottom-right).
                    Focus specifically on the object, plant, animal, device, material, or structural/cosmogenesis feature located at or directly under these coordinates.

                    Return a JSON object with this exact structure:
                    {
                      "element_name": "Common/Everyday Name of the item",
                      "scientific_name": "Taxonomic, chemical, or physical/cosmological scientific name (or 'N/A')",
                      "engineering_name": "Engineering, material, mechanical, or architectural designation (or 'N/A')",
                      "definition": "A 1-sentence concise definition of what this item is.",
                      "description": "A detailed 2-3 sentence description covering key features, context, and characteristics.",
                      "safety_note": "Any relevant toxicity, safety, or operational notes (or 'None')."
                    }
                """.trimIndent()

                val payload = buildVisionChatPayload(base64Image, tapLocationPrompt, activeProfile.nimFallbackModel)
                val responseStr = executeHttpRequest(BASE_URL, payload)
                val contentString = extractAssistantContent(responseStr)

                if (contentString.isBlank()) {
                    notifyResult(
                        ElementDefinition(
                            elementName = "Analysis Unavailable",
                            definition = "No visual elements detected under coordinates.",
                            detailedDescription = "Vision inference returned an empty result.",
                            hazardOrNote = "None"
                        ),
                        callback
                    )
                    return@thread
                }

                val resultObj = JSONObject(contentString)
                val telemetryTarget = onnxEngine?.computeCameraBoundingBox(16L)?.vlaTargetBoundingBox
                    ?: "[x:0, y:0, w:0, h:0]"

                val elementDef = ElementDefinition(
                    elementName = resultObj.optString("element_name", "Unknown Feature"),
                    definition = resultObj.optString("definition", "No definition available."),
                    detailedDescription = resultObj.optString("description", "No details returned."),
                    hazardOrNote = resultObj.optString("safety_note", "None"),
                    vlaBoundingTarget = telemetryTarget,
                    scientificName = resultObj.optString("scientific_name", "N/A"),
                    engineeringName = resultObj.optString("engineering_name", "N/A")
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

    /**
     * Scans the camera view for multiple visible elements and returns structured pop-up cards
     * containing mini descriptions, scientific names, and engineering names.
     */
    fun scanScenePopups(
        bitmap: Bitmap,
        maxPopups: Int = 6,
        callback: (List<DetectedPopupItem>) -> Unit
    ) {
        thread {
            try {
                val activeProfile = currentVisionProfile()
                if (!isModelDownloadedLocally(activeProfile)) {
                    downloadAllVlmModelsAsync()
                }

                if (API_KEY.isBlank()) {
                    val box = onnxEngine?.computeCameraBoundingBox(16L)?.vlaTargetBoundingBox
                        ?: "[x:0.20, y:0.20, w:0.60, h:0.60]"
                    mainHandler.post {
                        callback(
                            listOf(
                                DetectedPopupItem(
                                    elementName = activeProfile.displayName,
                                    scientificName = activeProfile.id,
                                    engineeringName = activeProfile.footprintHint,
                                    miniDescription = "Staged local VLM profile (${downloadedModelCount()}/${vlmModels.size} ready).",
                                    hazardOrNote = "None",
                                    boundingBox = box,
                                    confidence = 0.85f
                                )
                            )
                        )
                    }
                    return@thread
                }

                val base64Image = encodeBitmapToBase64(bitmap)
                val scenePrompt = """
                    Analyze everything visible in this camera view (up to $maxPopups prominent objects, organisms, materials, devices, or structures).
                    For each item, provide a concise mini description plus its scientific name and engineering name where applicable.

                    Return a JSON object with this exact structure:
                    {
                      "items": [
                        {
                          "element_name": "Name of visible item",
                          "scientific_name": "Scientific / taxonomic / chemical / physical name or N/A",
                          "engineering_name": "Engineering / material / functional name or N/A",
                          "mini_description": "Short 1-sentence summary of what it is and its role",
                          "safety_note": "Safety or hazard note or None",
                          "bbox": "[x:0.10, y:0.20, w:0.30, h:0.25]",
                          "confidence": 0.92
                        }
                      ]
                    }
                """.trimIndent()

                val payload = buildVisionChatPayload(base64Image, scenePrompt, activeProfile.nimFallbackModel)
                val responseStr = executeHttpRequest(BASE_URL, payload)
                val contentString = extractAssistantContent(responseStr)
                if (contentString.isBlank()) {
                    mainHandler.post { callback(emptyList()) }
                    return@thread
                }

                val root = JSONObject(contentString)
                val arr: JSONArray = root.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<DetectedPopupItem>()

                for (i in 0 until minOf(arr.length(), maxPopups)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    items.add(
                        DetectedPopupItem(
                            elementName = obj.optString("element_name", "Observed Entity"),
                            scientificName = obj.optString("scientific_name", "N/A"),
                            engineeringName = obj.optString("engineering_name", "N/A"),
                            miniDescription = obj.optString("mini_description", "No summary available."),
                            hazardOrNote = obj.optString("safety_note", "None"),
                            boundingBox = obj.optString("bbox", "[x:0.00, y:0.00, w:0.00, h:0.00]"),
                            confidence = obj.optDouble("confidence", 0.90).toFloat().coerceIn(0f, 1f)
                        )
                    )
                }

                mainHandler.post { callback(items) }
            } catch (e: Throwable) {
                e.printStackTrace()
                mainHandler.post { callback(emptyList()) }
            }
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildVisionChatPayload(
        base64Image: String,
        promptText: String,
        modelName: String = "meta/llama-3.2-11b-vision-instruct"
    ): JSONObject {
        return JSONObject().apply {
            put("model", modelName)
            put("response_format", JSONObject().apply { put("type", "json_object") })
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", promptText)
                                    }
                                )
                                .put(
                                    JSONObject().apply {
                                        put("type", "image_url")
                                        put(
                                            "image_url",
                                            JSONObject().apply {
                                                put("url", "data:image/jpeg;base64,$base64Image")
                                            }
                                        )
                                    }
                                )
                        )
                    }
                )
            )
        }
    }

    private fun extractAssistantContent(responseStr: String): String {
        val jsonResponse = JSONObject(responseStr)
        val choices = jsonResponse.optJSONArray("choices")
        if (choices == null || choices.length() == 0) return ""
        return choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content", "")
            ?: ""
    }

    private fun notifyResult(result: ElementDefinition, callback: (ElementDefinition) -> Unit) {
        mainHandler.post {
            callback(result)
        }
    }

    private fun executeHttpRequest(urlString: String, jsonBody: JSONObject): String {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $API_KEY")
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
            }

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
