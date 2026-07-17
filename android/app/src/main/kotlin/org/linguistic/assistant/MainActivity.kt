package org.linguistic.assistant

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView

class LexiQKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {
    private var suggestionHeader: TextView? = null
    private var keyboardView: KeyboardView? = null

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null)
        suggestionHeader = layout.findViewById(R.id.suggestionText)
        
        // Find the keyboard rendering panel and inject the QWERTY layout definitions
        keyboardView = layout.findViewById(R.id.keyboard)
        val keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)
        
        return layout
    }

    // Handles writing the key directly into social media inputs when tapped
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic: InputConnection = currentInputConnection ?: return
        when (primaryCode) {
            -5 -> ic.deleteSurroundingText(1, 0) // Delete character
            10 -> ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            else -> ic.commitText(primaryCode.toChar().toString(), 1) // Commit standard string char
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        val ic: InputConnection = currentInputConnection ?: return
        val extractedText = ic.getTextBeforeCursor(50, 0)
        
        if (!extractedText.isNullOrEmpty()) {
            val currentString = extractedText.toString()
            
            if (currentString.contains("recieve", ignoreCase = true)) {
                suggestionHeader?.text = "💡 Suggestion: Tap to replace 'recieve' with 'receive'"
                suggestionHeader?.setTextColor(android.graphics.Color.YELLOW)
            } else if (currentString.contains("its a", ignoreCase = true)) {
                suggestionHeader?.text = "💡 Suggestion: Replace 'its a' with 'it's a'"
                suggestionHeader?.setTextColor(android.graphics.Color.YELLOW)
            } else {
                suggestionHeader?.text = "LexiQ Engine: Text clear ✅"
                suggestionHeader?.setTextColor(android.graphics.Color.parseColor("#00E676"))
            }
        }
    }

    // Abstract stubs required by the Keyboard interface
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
