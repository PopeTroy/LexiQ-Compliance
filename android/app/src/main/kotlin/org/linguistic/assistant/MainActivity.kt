package org.linguistic.assistant

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView

class LexiQKeyboardService : InputMethodService() {
    private var suggestionHeader: TextView? = null

    override fun onCreateInputView(): View {
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null)
        suggestionHeader = layout.findViewById(R.id.suggestionText)
        return layout
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
}
