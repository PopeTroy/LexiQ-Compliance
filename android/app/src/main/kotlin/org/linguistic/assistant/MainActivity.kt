package org.linguistic.assistant

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LexiQKeyboardService : InputMethodService() {
    private var suggestionHeader: TextView? = null
    private var tikiIcon: TextView? = null
    private var container: LinearLayout? = null
    
    // Layout tracking modes: 0 = QWERTY, 1 = Digits, 2 = Symbols
    private var currentMode = 0 

    // Neon State Styling Colors
    private var currentBtnBg = "#00E676" // Neon Green default
    private var currentTxtColor = "#000000" // Black default text

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
        
        renderKeyboardLayout()
        return layout
    }

    private fun renderKeyboardLayout() {
        container?.removeAllViews()

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
                    transformationMethod = null // Prevent capitalization overrides
                    minimumWidth = 0
                    minimumHeight = 0
                    setPadding(0, 30, 0, 30)
                    
                    // Assign layout metrics based on functional width mappings
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

                    // Apply dynamic neon coloring styles
                    applyNeonStyle(this, key)

                    setOnClickListener {
                        handleKeyPress(key)
                    }
                }
                rowLayout.addView(button)
            }
            container?.addView(rowLayout)
        }
    }

    private fun applyNeonStyle(button: Button, key: String) {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            
            // System control keys keep a distinct structural dark look
            if (key in listOf("DEL", "ENTER", "SPACE", "123", "ABC", "SYM", "🗿")) {
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(2, Color.parseColor("#3A3A3C"))
                button.setTextColor(Color.WHITE)
            } else {
                // Alpha characters glow based on grammar processing evaluation rules
                setColor(Color.parseColor(currentBtnBg))
                setStroke(3, Color.parseColor("#FFFFFF")) // Bright edge highlight
                button.setTextColor(Color.parseColor(currentTxtColor))
            }
        }
        button.background = shape
    }

    private fun handleKeyPress(key: String) {
        val ic: InputConnection = currentInputConnection ?: return
        when (key) {
            "DEL" -> ic.deleteSurroundingText(1, 0)
            "SPACE" -> ic.commitText(" ", 1)
            "ENTER" -> ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            "123" -> { currentMode = 1; renderKeyboardLayout() }
            "ABC" -> { currentMode = 0; renderKeyboardLayout() }
            "SYM" -> { currentMode = 2; renderKeyboardLayout() }
            "🗿" -> ic.commitText("🗿", 1) // Injects structural Tiki mask symbol directly
            else -> ic.commitText(key, 1)
        }
        
        // Trigger live text ingestion analysis string loop instantly
        evaluateInputTextStream(ic)
    }

    private fun evaluateInputTextStream(ic: InputConnection) {
        val extractedText = ic.getTextBeforeCursor(60, 0) ?: ""
        val textString = extractedText.toString().lowercase()

        // Real-Time Evaluation Routing Logic
        if (textString.endsWith("recieve") || textString.contains("recieve ")) {
            // Bad Spelling -> Neon Yellow State
            currentBtnBg = "#FFEB3B" 
            currentTxtColor = "#000000"
            tikiIcon?.text = "💥" // Expression status shift
            suggestionHeader?.text = "💡 Spellcheck: Replace 'recieve' with 'receive'"
            suggestionHeader?.setTextColor(Color.parseColor("#FFEB3B"))
        } else if (textString.endsWith("its a") || textString.contains("its a ")) {
            // Bad Grammar -> Neon Red Buttons / Neon White Typography
            currentBtnBg = "#FF1744" 
            currentTxtColor = "#FFFFFF" 
            tikiIcon?.text = "❌"
            suggestionHeader?.text = "💡 Grammar Check: Replace 'its a' with 'it's a'"
            suggestionHeader?.setTextColor(Color.parseColor("#FF1744"))
        } else {
            // Good to go English -> Neon Green State
            currentBtnBg = "#00E676" 
            currentTxtColor = "#000000"
            tikiIcon?.text = "🗿"
            suggestionHeader?.text = "LexiQ Engine: Text clear ✅"
            suggestionHeader?.setTextColor(Color.parseColor("#00E676"))
        }

        // Re-render layout keys to paint updated glow color states cleanly
        renderKeyboardLayout()
    }
}
