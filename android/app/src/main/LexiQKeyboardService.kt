package org.linguistic.assistant

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView

class LexiQKeyboardService : InputMethodService() {
    private var suggestionHeader: TextView? = null

    override fun onCreateInputView(): View {
        // Inflate our custom keyboard layout shell
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null)
        suggestionHeader = layout.findViewById(R.id.suggestionText)
        
        // This is where your loaded native Rust binary hooks in
        // System.loadLibrary("lexiq_compliance") 
        
        return layout
    }

    // This intercepts every keystroke typed into social media in real time
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        val ic: InputConnection = currentInputConnection ?: return
        
        // Grab the last 50 characters typed onto the social media field
        val extractedText = ic.getTextBeforeCursor(50, 0)
        if (!extractedText.isNullOrEmpty()) {
            val currentString = extractedText.toString()
            
            // Real-time evaluation loop checking text strings before posting
            if (currentString.contains("recieve", ignoreCase = true)) {
                suggestionHeader?.text = "💡 Suggestion: Tap to replace 'recieve' with 'receive'"
                suggestionHeader?.setTextColor(android.graphics.Color.YELLOW)
            } else {
                suggestionHeader?.text = "LexiQ Engine: Text clear ✅"
                suggestionHeader?.setTextColor(android.graphics.Color.parseColor("#00E676"))
            }
        }
    }
}
