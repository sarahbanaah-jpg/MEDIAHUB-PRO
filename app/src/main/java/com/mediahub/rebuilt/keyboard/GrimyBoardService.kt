package com.mediahub.rebuilt.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

/** System-wide GRIMY BOARD keyboard. Text is committed through Android's InputConnection. */
class GrimyBoardService : InputMethodService() {
    private var caps = false
    private var emojiMode = false
    private var root: LinearLayout? = null
    private val dark = Color.rgb(13, 12, 19)
    private val emojis = listOf("😀","😂","🥹","😍","🥰","😘","😎","😭","😡","🤔","🤫","🤗","🫶","❤️","🔥","✨","💯","🙏","💀","👀","💔","💕","🎉","👍","👏","🥳","😴","🤝","💪","🌹","🖤","😈")

    override fun onCreateInputView(): android.view.View {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(7), dp(5), dp(8)); setBackgroundColor(dark) }
        render()
        return root!!
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) { super.onStartInputView(info, restarting); emojiMode = false; render() }

    private fun render() {
        val panel = root ?: return
        panel.removeAllViews()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(5), 0, dp(5), dp(5)) }
        header.addView(label("GRIMY BOARD", 12f, 0xFFFF3789.toInt(), true), LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(key(if (emojiMode) "ABC" else "☺") { emojiMode = !emojiMode; render() }, LinearLayout.LayoutParams(dp(42), dp(34)))
        panel.addView(header)
        if (emojiMode) {
            emojis.chunked(8).forEach { group ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
                group.forEach { emoji -> row.addView(key(emoji) { currentInputConnection?.commitText(emoji, 1) }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }) }
                panel.addView(row)
            }
            return
        }
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { index, chars ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            if (index == 2) row.addView(key("⇧") { caps = !caps; render() }, LinearLayout.LayoutParams(dp(42), dp(47)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
            chars.forEach { ch ->
                val shown = if (caps) ch.uppercase() else ch.toString()
                row.addView(key(shown) { currentInputConnection?.commitText(shown, 1); if (caps) { caps = false; render() } }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
            }
            if (index == 2) row.addView(key("⌫") { currentInputConnection?.deleteSurroundingText(1, 0) }, LinearLayout.LayoutParams(dp(42), dp(47)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
            panel.addView(row)
        }
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER }
        bottom.addView(key("?123") { currentInputConnection?.commitText("?123", 1) }, LinearLayout.LayoutParams(dp(52), dp(44)))
        bottom.addView(key(",") { currentInputConnection?.commitText(",", 1) }, LinearLayout.LayoutParams(dp(34), dp(44)))
        bottom.addView(key("English · GRIMY") { currentInputConnection?.commitText(" ", 1) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        bottom.addView(key(".") { currentInputConnection?.commitText(".", 1) }, LinearLayout.LayoutParams(dp(34), dp(44)))
        bottom.addView(key("↵") {
            val ic = currentInputConnection
            val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
            if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) ic?.commitText("\n", 1) else ic?.performEditorAction(action)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        panel.addView(bottom)
    }

    private fun key(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = if (text.length > 2) 11f else 19f
        setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = GradientDrawable().apply { setColor(if (text == "English · GRIMY") 0xFF312736.toInt() else 0xFF25222C.toInt()); cornerRadius = dp(9).toFloat(); setStroke(dp(1), 0x22FFFFFF) }
        isClickable = true; isFocusable = true
        setOnClickListener { animate().scaleX(.94f).scaleY(.94f).setDuration(65).withEndAction { scaleX = 1f; scaleY = 1f }.start(); click() }
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text=text; textSize=size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD); gravity=Gravity.CENTER_VERTICAL }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}