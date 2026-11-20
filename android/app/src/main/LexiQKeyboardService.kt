package org.linguistic.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LexiQKeyboardService : InputMethodService() {
    private var suggestionHeader: TextView? = null
    private var tikiIcon: TextView? = null
    private var micButton: Button? = null
    private var container: LinearLayout? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isDownloadingAll = AtomicBoolean(false)
    private val isRecordingAudio = AtomicBoolean(false)

    private var currentMode = 0
    private var activeModelIndex = 0
    private var wordBuffer = StringBuilder()

    // Cached key buttons to avoid rebuilding view hierarchy on every keystroke
    private val keyButtons = mutableListOf<Pair<String, Button>>()

    // Active Parakeet transducer handle (com.k2fsa.sherpa.onnx.OfflineRecognizer when AAR is linked)
    private var activeRecognizerInstance: Any? = null
    private var activeRecognizerModelId: String? = null
    private var loadedTokens: Map<Int, String> = emptyMap()
    private var audioRecord: AudioRecord? = null
    private var recordedPcmStream: ByteArrayOutputStream? = null

    // Neon State Styling Colors
    private var currentBtnBg = "#00E676" // Neon Green default
    private var currentTxtColor = "#000000" // Black default text

    data class ParakeetModelProfile(
        val id: String,
        val displayName: String,
        val archiveFolder: String,
        val footprintHint: String,
        val modelType: String = "nemo_transducer",
        val sampleRate: Int = 16000,
        val artifactUrls: Map<String, String>
    )

    data class ParakeetBundlePaths(
        val directory: File,
        val encoderPath: String?,
        val decoderPath: String?,
        val joinerPath: String?,
        val tokensPath: String?
    ) {
        val isCompleteTransducer: Boolean
            get() = encoderPath != null && decoderPath != null && joinerPath != null && tokensPath != null
    }

    // All Parakeet model profiles staged under filesDir/parakeet_models/<archiveFolder>
    private val parakeetModels = listOf(
        ParakeetModelProfile(
            id = "parakeet-110m-int8",
            displayName = "Parakeet 110M INT8",
            archiveFolder = "parakeet_110m_int8",
            footprintHint = "Light Local",
            artifactUrls = mapOf(
                "tokens.txt" to "https://huggingface.co/akm9191/sherpa-onnx-parakeet-tdt-110m-int8/resolve/main/tokens.txt"
            )
        ),
        ParakeetModelProfile(
            id = "parakeet-tdt-0.6b-v2-int8",
            displayName = "Parakeet TDT 0.6B v2 INT8",
            archiveFolder = "parakeet_tdt_06b_v2_int8",
            footprintHint = "EN High-Acc",
            artifactUrls = mapOf(
                "tokens.txt" to "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/tokens.txt"
            )
        ),
        ParakeetModelProfile(
            id = "parakeet-tdt-0.6b-v3-int8",
            displayName = "Parakeet TDT 0.6B v3 INT8",
            archiveFolder = "parakeet_tdt_06b_v3_int8",
            footprintHint = "Multi-Lang",
            artifactUrls = mapOf(
                "encoder.onnx" to "https://huggingface.co/fcreait/parakeet-tdt-0.6b-v3-int8-onnx/resolve/main/encoder.onnx",
                "decoder.onnx" to "https://huggingface.co/fcreait/parakeet-tdt-0.6b-v3-int8-onnx/resolve/main/decoder.onnx",
                "tokens.txt" to "https://huggingface.co/fcreait/parakeet-tdt-0.6b-v3-int8-onnx/resolve/main/tokens.txt"
            )
        ),
        ParakeetModelProfile(
            id = "parakeet-unified-en-0.6b-int8",
            displayName = "Parakeet Unified EN 0.6B INT8",
            archiveFolder = "parakeet_unified_en_06b_int8",
            footprintHint = "Punct & Case",
            artifactUrls = mapOf(
                "tokens.txt" to "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/tokens.txt"
            )
        ),
        ParakeetModelProfile(
            id = "parakeet-ultra-sherpa-onnx-int8",
            displayName = "Parakeet Ultra 0.6B INT8",
            archiveFolder = "parakeet_ultra_sherpa_onnx_int8",
            footprintHint = "Ultra Acc",
            artifactUrls = mapOf(
                "tokens.txt" to "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/tokens.txt"
            )
        ),
        ParakeetModelProfile(
            id = "parakeet-redux-sherpa-onnx",
            displayName = "Parakeet Redux 1.58-bit",
            archiveFolder = "parakeet_redux_sherpa_onnx",
            footprintHint = "Compact 25-Lang",
            artifactUrls = mapOf(
                "tokens.txt" to "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/tokens.txt"
            )
        )
    )

    private val spellCorrections = mapOf(
        "keybord" to "keyboard",
        "recieve" to "receive",
        "seperate" to "separate",
        "definately" to "definitely",
        "grammer" to "grammar"
    )

    private val grammarFixes = listOf(
        Regex("\\bthats\\b", RegexOption.IGNORE_CASE) to "that's",
        Regex("\\bits a\\b", RegexOption.IGNORE_CASE) to "it's a",
        Regex("\\bdont\\b", RegexOption.IGNORE_CASE) to "don't",
        Regex("\\bcant\\b", RegexOption.IGNORE_CASE) to "can't"
    )

    private val antiTrollReplacements = listOf(
        Regex("\\bstfu\\b", RegexOption.IGNORE_CASE) to "please keep this respectful",
        Regex("\\bnoob\\b", RegexOption.IGNORE_CASE) to "newcomer",
        Regex("\\btrash\\b", RegexOption.IGNORE_CASE) to "not working well for me",
        Regex("\\bratio\\b", RegexOption.IGNORE_CASE) to "let's look at the numbers",
        Regex("\\bcry about it\\b", RegexOption.IGNORE_CASE) to "let's move past that",
        Regex("\\bstay mad\\b", RegexOption.IGNORE_CASE) to "no need to argue"
    )

    private val spamLinkRegex = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE)
    private val repeatedCharRegex = Regex("(.)\\1{3,}")
    private val repeatedWordRegex = Regex("\\b(\\w+)(\\s+\\1){2,}\\b", RegexOption.IGNORE_CASE)

    private val keysRow1 = arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val keysRow2 = arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val keysRow3 = arrayOf("z", "x", "c", "v", "b", "n", "m", "DEL")
    private val keysRow4 = arrayOf("123", "🗿", "SPACE", "SYM", "ENTER")

    private val digitsRow1 = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val digitsRow2 = arrayOf("-", "/", ":", ";", "(", ")", "\$", "&", "@", "\"")
    private val digitsRow3 = arrayOf(".", ",", "?", "!", "'", "DEL")
    private val digitsRow4 = arrayOf("ABC", "🗿", "SPACE", "SYM", "ENTER")

    private val symbolsRow1 = arrayOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val symbolsRow2 = arrayOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")
    private val symbolsRow3 = arrayOf(".", ",", "?", "!", "'", "DEL")
    private val symbolsRow4 = arrayOf("ABC", "🗿", "SPACE", "123", "ENTER")

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null)
        suggestionHeader = layout.findViewById(R.id.suggestionText)
        tikiIcon = layout.findViewById(R.id.tikiIcon)
        container = layout.findViewById(R.id.keyboardContainer)

        setupHeaderMicButton(layout)
        updateModelStatusBanner()
        renderKeyboardLayout()
        downloadAllParakeetModelsAsync()
        return layout
    }

    private fun setupHeaderMicButton(rootLayout: View) {
        val existingId = resources.getIdentifier("micButton", "id", packageName)
        val foundButton = if (existingId != 0) rootLayout.findViewById<Button>(existingId) else null

        val targetButton = foundButton ?: run {
            val headerParent = suggestionHeader?.parent as? ViewGroup
            if (headerParent != null) {
                Button(this).apply {
                    text = "🎤"
                    textSize = 15f
                    transformationMethod = null
                    minimumWidth = 0
                    minimumHeight = 0
                    setPadding(24, 8, 24, 8)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    headerParent.addView(this)
                }
            } else {
                null
            }
        }

        micButton = targetButton
        updateMicButtonState(isRecording = false)

        targetButton?.setOnClickListener {
            togglePushToTalkRecording()
        }
    }

    fun togglePushToTalkRecording() {
        if (isRecordingAudio.get()) {
            stopAndTranscribeRecording()
        } else {
            startPushToTalkRecording()
        }
    }

    private fun updateMicButtonState(isRecording: Boolean) {
        val btn = micButton ?: return
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            if (isRecording) {
                setColor(Color.parseColor("#FF1744"))
                setStroke(2, Color.parseColor("#FFFFFF"))
                btn.setTextColor(Color.WHITE)
                btn.text = "⏹"
            } else {
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(2, Color.parseColor("#00E676"))
                btn.setTextColor(Color.parseColor("#00E676"))
                btn.text = "🎤"
            }
        }
        btn.background = drawable
    }

    override fun onDestroy() {
        stopRecordingWithoutTranscribe()
        releaseParakeetRecognizer()
        super.onDestroy()
    }

    fun currentParakeetProfile(): ParakeetModelProfile = parakeetModels[activeModelIndex]

    fun modelDirectoryFor(profile: ParakeetModelProfile): File {
        return File(File(filesDir, "parakeet_models"), profile.archiveFolder)
    }

    fun resolveParakeetBundle(profile: ParakeetModelProfile): ParakeetBundlePaths {
        val dir = modelDirectoryFor(profile)
        val candidates = listOfNotNull(dir, dir.listFiles()?.firstOrNull { it.isDirectory })

        fun findFile(vararg names: String): String? {
            for (folder in candidates) {
                for (name in names) {
                    val f = File(folder, name)
                    if (f.exists() && f.isFile && f.length() > 0L) {
                        return f.absolutePath
                    }
                }
            }
            return null
        }

        return ParakeetBundlePaths(
            directory = dir,
            encoderPath = findFile("encoder.int8.onnx", "encoder.onnx"),
            decoderPath = findFile("decoder.int8.onnx", "decoder.onnx"),
            joinerPath = findFile("joiner.int8.onnx", "joiner.onnx"),
            tokensPath = findFile("tokens.txt")
        )
    }

    fun isModelDownloadedLocally(profile: ParakeetModelProfile): Boolean {
        return try {
            val dir = modelDirectoryFor(profile)
            val bundle = resolveParakeetBundle(profile)
            dir.exists() && dir.isDirectory && (File(dir, "manifest.json").exists() || bundle.tokensPath != null)
        } catch (_: Throwable) {
            false
        }
    }

    private fun downloadedCount(): Int = parakeetModels.count { isModelDownloadedLocally(it) }

    fun initializeParakeetRuntime(profile: ParakeetModelProfile = currentParakeetProfile()): Boolean {
        return try {
            if (activeRecognizerModelId == profile.id && activeRecognizerInstance != null) {
                return true
            }
            releaseParakeetRecognizer()

            val bundle = resolveParakeetBundle(profile)
            bundle.tokensPath?.let { path ->
                loadedTokens = parseTokensFile(File(path))
            }

            if (!bundle.isCompleteTransducer) {
                activeRecognizerModelId = profile.id
                return false
            }

            val recognizerCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            val configCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            val modelConfigCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            val transducerConfigCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig")
            val featConfigCls = Class.forName("com.k2fsa.sherpa.onnx.FeatureConfig")

            val transducerConfig = transducerConfigCls.getDeclaredConstructor().newInstance()
            setFieldIfPresent(transducerConfig, "encoder", bundle.encoderPath)
            setFieldIfPresent(transducerConfig, "decoder", bundle.decoderPath)
            setFieldIfPresent(transducerConfig, "joiner", bundle.joinerPath)

            val modelConfig = modelConfigCls.getDeclaredConstructor().newInstance()
            setFieldIfPresent(modelConfig, "transducer", transducerConfig)
            setFieldIfPresent(modelConfig, "tokens", bundle.tokensPath)
            setFieldIfPresent(modelConfig, "modelType", profile.modelType)
            setFieldIfPresent(modelConfig, "numThreads", 2)

            val featConfig = featConfigCls.getDeclaredConstructor().newInstance()
            setFieldIfPresent(featConfig, "sampleRate", profile.sampleRate)
            setFieldIfPresent(featConfig, "featureDim", 80)

            val recognizerConfig = configCls.getDeclaredConstructor().newInstance()
            setFieldIfPresent(recognizerConfig, "modelConfig", modelConfig)
            setFieldIfPresent(recognizerConfig, "featConfig", featConfig)

            val ctor = recognizerCls.getDeclaredConstructor(configCls)
            activeRecognizerInstance = ctor.newInstance(recognizerConfig)
            activeRecognizerModelId = profile.id
            true
        } catch (_: Throwable) {
            activeRecognizerInstance = null
            activeRecognizerModelId = profile.id
            false
        }
    }

    fun startPushToTalkRecording(maxDurationMs: Long = 5000L): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            suggestionHeader?.text = "Parakeet Mic: RECORD_AUDIO permission required"
            suggestionHeader?.setTextColor(Color.parseColor("#FF1744"))
            updateMicButtonState(isRecording = false)
            return false
        }
        if (!isRecordingAudio.compareAndSet(false, true)) return false

        val sampleRate = currentParakeetProfile().sampleRate
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            isRecordingAudio.set(false)
            updateMicButtonState(isRecording = false)
            return false
        }

        return try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                isRecordingAudio.set(false)
                updateMicButtonState(isRecording = false)
                return false
            }

            val pcmOut = ByteArrayOutputStream()
            audioRecord = recorder
            recordedPcmStream = pcmOut
            recorder.startRecording()

            mainHandler.post {
                updateMicButtonState(isRecording = true)
                suggestionHeader?.text = "Parakeet Listening (${currentParakeetProfile().displayName})..."
                suggestionHeader?.setTextColor(Color.parseColor("#FFEB3B"))
            }

            thread {
                val readBuf = ByteArray(minBuf)
                val startTime = System.currentTimeMillis()
                while (isRecordingAudio.get() && (System.currentTimeMillis() - startTime) < maxDurationMs) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read > 0) {
                        pcmOut.write(readBuf, 0, read)
                    }
                }
                if (isRecordingAudio.get()) {
                    stopAndTranscribeRecording()
                }
            }
            true
        } catch (_: Throwable) {
            stopRecordingWithoutTranscribe()
            false
        }
    }

    fun stopAndTranscribeRecording(): String {
        if (!isRecordingAudio.compareAndSet(true, false)) {
            mainHandler.post { updateMicButtonState(isRecording = false) }
            return ""
        }

        val pcmBytes = try {
            audioRecord?.stop()
            audioRecord?.release()
            recordedPcmStream?.toByteArray() ?: ByteArray(0)
        } catch (_: Throwable) {
            ByteArray(0)
        } finally {
            audioRecord = null
            recordedPcmStream = null
        }

        if (pcmBytes.size < 2) {
            mainHandler.post {
                updateMicButtonState(isRecording = false)
                updateModelStatusBanner()
            }
            return ""
        }

        val samples = FloatArray(pcmBytes.size / 2)
        var i = 0
        var idx = 0
        while (i + 1 < pcmBytes.size && idx < samples.size) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val shortSample = ((high shl 8) or low).toShort().toInt()
            samples[idx] = (shortSample / 32768.0f).coerceIn(-1.0f, 1.0f)
            i += 2
            idx++
        }

        val text = transcribePcm16kMono(samples, currentParakeetProfile().sampleRate)
        mainHandler.post {
            updateMicButtonState(isRecording = false)
            if (text.isBlank()) {
                updateModelStatusBanner()
            }
        }
        return text
    }

    private fun stopRecordingWithoutTranscribe() {
        isRecordingAudio.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {
        } finally {
            audioRecord = null
            recordedPcmStream = null
        }
        mainHandler.post { updateMicButtonState(isRecording = false) }
    }

    fun transcribePcm16kMono(samples: FloatArray, sampleRate: Int = 16000): String {
        val profile = currentParakeetProfile()
        initializeParakeetRuntime(profile)

        val recognizer = activeRecognizerInstance ?: return ""
        return try {
            val stream = recognizer.javaClass.getMethod("createStream").invoke(recognizer)
            val acceptMethod = stream.javaClass.getMethod(
                "acceptWaveform",
                FloatArray::class.java,
                Int::class.javaPrimitiveType
            )
            acceptMethod.invoke(stream, samples, sampleRate)

            val decodeMethod = recognizer.javaClass.getMethod("decode", stream.javaClass)
            decodeMethod.invoke(recognizer, stream)

            val result = recognizer.javaClass.getMethod("getResult", stream.javaClass).invoke(recognizer, stream)
            val text = result?.javaClass?.getMethod("getText")?.invoke(result) as? String ?: ""

            try {
                stream.javaClass.getMethod("release").invoke(stream)
            } catch (_: Throwable) {
            }

            if (text.isNotBlank()) {
                mainHandler.post { commitVoiceTranscript(text) }
            }
            text
        } catch (_: Throwable) {
            ""
        }
    }

    fun commitVoiceTranscript(transcript: String) {
        val ic: InputConnection? = currentInputConnection
        ic?.commitText(transcript, 1)
        wordBuffer.append(transcript)
        evaluateInputTextStream()
    }

    fun releaseParakeetRecognizer() {
        try {
            activeRecognizerInstance?.let { inst ->
                inst.javaClass.methods.firstOrNull { it.name == "release" && it.parameterCount == 0 }?.invoke(inst)
            }
        } catch (_: Throwable) {
        } finally {
            activeRecognizerInstance = null
            activeRecognizerModelId = null
        }
    }

    private fun parseTokensFile(file: File): Map<Int, String> {
        if (!file.exists() || !file.canRead()) return emptyMap()
        return try {
            val map = mutableMapOf<Int, String>()
            file.readLines().forEachIndexed { idx, line ->
                val parts = line.trimEnd().split(" ")
                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    val token = parts[0].replace("▁", " ")
                    val id = parts.getOrNull(1)?.toIntOrNull() ?: idx
                    map[id] = token
                }
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun setFieldIfPresent(target: Any, fieldName: String, value: Any?) {
        try {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: Throwable) {
        }
    }

    fun downloadAllParakeetModelsAsync() {
        if (!isDownloadingAll.compareAndSet(false, true)) return

        thread {
            try {
                val rootDir = File(filesDir, "parakeet_models").apply { mkdirs() }
                parakeetModels.forEachIndexed { index, profile ->
                    mainHandler.post {
                        suggestionHeader?.text =
                            "Downloading Parakeet (${index + 1}/${parakeetModels.size}): ${profile.displayName}..."
                        suggestionHeader?.setTextColor(Color.parseColor("#FFEB3B"))
                    }

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

                    val bundle = resolveParakeetBundle(profile)
                    val manifestFile = File(targetDir, "manifest.json")
                    manifestFile.writeText(
                        """
                        {
                          "id": "${profile.id}",
                          "name": "${profile.displayName}",
                          "model_type": "${profile.modelType}",
                          "sample_rate": ${profile.sampleRate},
                          "footprint": "${profile.footprintHint}",
                          "transducer_complete": ${bundle.isCompleteTransducer},
                          "tokens_present": ${bundle.tokensPath != null},
                          "artifacts_ready": $fetchedFiles,
                          "synced_at_ms": ${System.currentTimeMillis()}
                        }
                        """.trimIndent()
                    )
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                isDownloadingAll.set(false)
                mainHandler.post {
                    initializeParakeetRuntime(currentParakeetProfile())
                    updateModelStatusBanner()
                }
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

    private fun cycleParakeetModel(): String {
        activeModelIndex = (activeModelIndex + 1) % parakeetModels.size
        val profile = currentParakeetProfile()
        val runtimeBound = initializeParakeetRuntime(profile)
        val stateLabel = when {
            runtimeBound -> "ASR Ready"
            isModelDownloadedLocally(profile) -> "Tokens Ready (${loadedTokens.size})"
            else -> "Syncing"
        }
        return "${profile.displayName} • $stateLabel (${downloadedCount()}/${parakeetModels.size})"
    }

    private fun updateModelStatusBanner() {
        val profile = currentParakeetProfile()
        val countText = "${downloadedCount()}/${parakeetModels.size} Parakeet"
        suggestionHeader?.text = "[${profile.footprintHint} | $countText] LexiQ Engine: Text clear ✅"
        suggestionHeader?.setTextColor(Color.parseColor("#00E676"))
    }

    private fun renderKeyboardLayout() {
        container?.removeAllViews()
        keyButtons.clear()

        val rows = when (currentMode) {
            1 -> arrayOf(digitsRow1, digitsRow2, digitsRow3, digitsRow4)
            2 -> arrayOf(symbolsRow1, symbolsRow2, symbolsRow3, symbolsRow4)
            else -> arrayOf(keysRow1, keysRow2, keysRow3, keysRow4)
        }

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }

            for (key in row) {
                val button = Button(this).apply {
                    text = key
                    textSize = 16f
                    transformationMethod = null
                    minimumWidth = 0
                    minimumHeight = 0
                    setPadding(0, 30, 0, 30)

                    val weight = when (key) {
                        "SPACE" -> 3.0f
                        "DEL", "ENTER", "123", "ABC", "SYM" -> 1.5f
                        else -> 1.0f
                    }

                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, weight
                    ).apply {
                        setMargins(4, 6, 4, 6)
                    }

                    applyNeonStyle(this, key)

                    setOnClickListener {
                        handleKeyPress(key)
                    }
                }
                keyButtons.add(key to button)
                rowLayout.addView(button)
            }
            container?.addView(rowLayout)
        }
    }

    private fun refreshKeyStyles() {
        if (keyButtons.isEmpty()) {
            renderKeyboardLayout()
            return
        }
        for ((key, button) in keyButtons) {
            applyNeonStyle(button, key)
        }
    }

    private fun applyNeonStyle(button: Button, key: String) {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f

            if (key in listOf("DEL", "ENTER", "SPACE", "123", "ABC", "SYM", "🗿")) {
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(2, Color.parseColor("#3A3A3C"))
                button.setTextColor(Color.WHITE)
            } else {
                setColor(Color.parseColor(currentBtnBg))
                setStroke(3, Color.parseColor("#FFFFFF"))
                button.setTextColor(Color.parseColor(currentTxtColor))
            }
        }
        button.background = shape
    }

    private fun handleKeyPress(key: String) {
        val ic: InputConnection = currentInputConnection ?: return
        when (key) {
            "DEL" -> {
                ic.deleteSurroundingText(1, 0)
                if (wordBuffer.isNotEmpty()) {
                    wordBuffer.deleteCharAt(wordBuffer.length - 1)
                }
            }
            "SPACE" -> {
                ic.commitText(" ", 1)
                wordBuffer.append(" ")
            }
            "ENTER" -> {
                val parsed = parseBeforePostSequence(wordBuffer.toString())
                if (parsed.changed && wordBuffer.isNotEmpty()) {
                    val existingBeforeCursor = ic.getTextBeforeCursor(wordBuffer.length + 16, 0)?.toString() ?: ""
                    val matchLen = minOf(existingBeforeCursor.length, wordBuffer.length)
                    if (matchLen > 0) {
                        ic.deleteSurroundingText(matchLen, 0)
                    }
                    ic.commitText(parsed.cleanedText, 1)
                }
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                wordBuffer.clear()
            }
            "123" -> { currentMode = 1; renderKeyboardLayout(); return }
            "ABC" -> { currentMode = 0; renderKeyboardLayout(); return }
            "SYM" -> { currentMode = 2; renderKeyboardLayout(); return }
            "🗿" -> {
                if (downloadedCount() < parakeetModels.size) {
                    downloadAllParakeetModelsAsync()
                }
                val modelStatus = cycleParakeetModel()
                currentBtnBg = "#00E676"
                currentTxtColor = "#000000"
                tikiIcon?.text = "🗿"
                suggestionHeader?.text = "Parakeet: $modelStatus"
                suggestionHeader?.setTextColor(Color.parseColor("#00E676"))
                refreshKeyStyles()
                return
            }
            else -> {
                ic.commitText(key, 1)
                wordBuffer.append(key)
            }
        }

        evaluateInputTextStream()
    }

    private data class ParseResult(
        val cleanedText: String,
        val changed: Boolean,
        val reason: String,
        val severity: Int // 0 = clean, 1 = spellcheck, 2 = grammar/anti-troll/anti-spam
    )

    private fun parseBeforePostSequence(raw: String): ParseResult {
        if (raw.isBlank()) {
            return ParseResult(raw, false, "Text clear ✅", 0)
        }

        var working = raw
        var spellHit: String? = null
        var grammarHit: String? = null
        var trollOrSpamHit = false

        // 1. Offline Spellcheck Pass
        for ((typo, fixed) in spellCorrections) {
            val regex = Regex("\\b${Regex.escape(typo)}\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(working)) {
                working = regex.replace(working) { match ->
                    preserveCase(match.value, fixed)
                }
                if (spellHit == null) {
                    spellHit = "Spellcheck: '$typo' ➔ '$fixed'"
                }
            }
        }

        // 2. Offline Grammar & Punctuation Pass
        for ((pattern, replacement) in grammarFixes) {
            if (pattern.containsMatchIn(working)) {
                working = pattern.replace(working) { match ->
                    preserveCase(match.value, replacement)
                }
                if (grammarHit == null) {
                    grammarHit = "Grammar: '${pattern.pattern.replace("\\b", "")}' ➔ '$replacement'"
                }
            }
        }

        // 3. Social Media Anti-Troll Pre-Post Parse
        for ((trollPattern, calmReplacement) in antiTrollReplacements) {
            if (trollPattern.containsMatchIn(working)) {
                working = trollPattern.replace(working, calmReplacement)
                trollOrSpamHit = true
            }
        }

        // 4. Anti-Spam Pass (URL flood, repeated words/chars, excessive shouting punctuation/caps)
        if (spamLinkRegex.containsMatchIn(working)) {
            working = spamLinkRegex.replace(working, "[link-removed]")
            trollOrSpamHit = true
        }
        if (repeatedWordRegex.containsMatchIn(working)) {
            working = repeatedWordRegex.replace(working) { it.groupValues[1] }
            trollOrSpamHit = true
        }
        if (repeatedCharRegex.containsMatchIn(working)) {
            working = repeatedCharRegex.replace(working) { "${it.groupValues[1]}${it.groupValues[1]}" }
            trollOrSpamHit = true
        }

        val collapsedPunct = working.replace(Regex("!{3,}")) { "!" }.replace(Regex("\\?{3,}")) { "?" }
        if (collapsedPunct != working) {
            working = collapsedPunct
            trollOrSpamHit = true
        }

        val letters = working.filter { it.isLetter() }
        if (letters.length >= 8) {
            val upperRatio = letters.count { it.isUpperCase() }.toFloat() / letters.length
            if (upperRatio > 0.75f) {
                working = working.lowercase().replaceFirstChar { it.uppercase() }
                trollOrSpamHit = true
            }
        }

        val changed = working != raw
        return when {
            trollOrSpamHit -> ParseResult(working, changed, "Anti-Troll & Anti-Spam: Cleaned for post", 2)
            grammarHit != null -> ParseResult(working, changed, "💡 $grammarHit", 2)
            spellHit != null -> ParseResult(working, changed, "💡 $spellHit", 1)
            else -> ParseResult(working, false, "LexiQ Engine: Text clear ✅", 0)
        }
    }

    private fun preserveCase(source: String, replacement: String): String {
        return if (source.firstOrNull()?.isUpperCase() == true) {
            replacement.replaceFirstChar { it.uppercase() }
        } else {
            replacement
        }
    }

    private fun evaluateInputTextStream() {
        val currentRaw = wordBuffer.toString()
        val parsed = parseBeforePostSequence(currentRaw)

        when (parsed.severity) {
            1 -> {
                currentBtnBg = "#FFEB3B"
                currentTxtColor = "#000000"
                tikiIcon?.text = "💥"
                suggestionHeader?.text = parsed.reason
                suggestionHeader?.setTextColor(Color.parseColor("#FFEB3B"))
            }
            2 -> {
                currentBtnBg = "#FF1744"
                currentTxtColor = "#FFFFFF"
                tikiIcon?.text = "❌"
                suggestionHeader?.text = parsed.reason
                suggestionHeader?.setTextColor(Color.parseColor("#FF1744"))
            }
            else -> {
                currentBtnBg = "#00E676"
                currentTxtColor = "#000000"
                tikiIcon?.text = "🗿"
                suggestionHeader?.text = "${currentParakeetProfile().displayName}: ${parsed.reason}"
                suggestionHeader?.setTextColor(Color.parseColor("#00E676"))
            }
        }

        refreshKeyStyles()
    }
}
