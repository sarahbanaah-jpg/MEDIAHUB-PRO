package com.mediahub.rebuilt.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import com.mediahub.rebuilt.ui.LiquidGlass

/** GRIMY BOARD — liquid glass, font styles, suggestions, emoji, voice. */
class GrimyBoardService : InputMethodService() {

    private var caps = false
    private var capsLock = false
    private var symbols = false
    private var mode = 0 // 0 keys, 1 emoji, 2 clipboard, 3 numpad, 4 font styles
    private var emojiTab = 0
    private var oneHand = 0
    private var language = "en"
    private var root: LinearLayout? = null
    private val clipboardItems = mutableListOf<String>()
    private val pinnedClips = mutableListOf<String>()
    private val recentEmoji = mutableListOf<String>()
    private val undoStack = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private var lastSpaceTap = 0L
    private var lastShiftTap = 0L
    private var keyPopup: PopupWindow? = null
    private var speech: SpeechRecognizer? = null
    private var currentWord = StringBuilder()
    private var lastWord = ""

    private val prefs by lazy { getSharedPreferences("grimy_board", Context.MODE_PRIVATE) }
    private val theme get() = prefs.getString("theme", "pink") ?: "pink"
    private val textStyle get() = prefs.getString("text_style", "normal") ?: "normal"
    private val keyPopupEnabled get() = prefs.getBoolean("key_popup", true)
    private val numberRow get() = prefs.getBoolean("numbers", true)
    private val haptics get() = prefs.getBoolean("haptics", true)
    private val hapticLevel get() = prefs.getInt("haptic_level", 1)
    private val keySound get() = prefs.getBoolean("sound", false)
    private val doubleSpacePeriod get() = prefs.getBoolean("double_space", true)
    private val suggestionsOn get() = prefs.getBoolean("suggestions", true)
    private val incognito get() = prefs.getBoolean("incognito", false)
    private val height get() = prefs.getInt("height", 48).coerceIn(40, 58)

    private val accent: Int get() = when (theme) {
        "neon" -> 0xFF00E6BE.toInt(); "glass" -> 0xFFBE96FF.toInt(); "grimy" -> 0xFFFF2D95.toInt(); else -> 0xFFFF3789.toInt()
    }
    private val bg: Int get() = when (theme) {
        "amoled" -> Color.BLACK; "neon" -> 0xFF081216.toInt(); "glass" -> 0xFF120F1A.toInt(); "grimy" -> 0xFF0A0612.toInt(); else -> 0xFF0D0C13.toInt()
    }
    private val keyBg: Int get() = when (theme) {
        "amoled" -> 0xFF141414.toInt(); "neon" -> 0xFF102226.toInt(); "glass" -> 0xFF2A2436.toInt(); "grimy" -> 0xFF1A1028.toInt(); else -> 0xFF25222C.toInt()
    }

    private val grimyStamps = listOf("🖤 GRIMY", "✨ MediaHub", "🎧 Now Playing", "🔥 Grimy Mode", "💜 Stay Grimy", "🚀 Level Up", "💎 Premium", "🌙 Night Vibes")
    private val facesEmoji = listOf("😀", "😂", "🥹", "😍", "🥰", "😘", "😎", "😭", "😡", "🤔", "🤫", "🤗", "😊", "😉", "🥳", "😴")
    private val heartsEmoji = listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💔")
    private val moreEmoji = listOf("👍", "👏", "🙏", "💪", "🤝", "🙌", "🎉", "🎊", "📱", "💻", "🎮", "📷", "🎬", "💬", "📌", "⚠️")
    private val longPressMap = mapOf(
        "a" to listOf("á", "à", "â", "ä", "ã"), "e" to listOf("é", "è", "ê", "ë"),
        "i" to listOf("í", "ì", "î", "ï"), "o" to listOf("ó", "ò", "ô", "ö"),
        "u" to listOf("ú", "ù", "û", "ü"), "n" to listOf("ñ"), "c" to listOf("ç")
    )
    private val swahiliHint = listOf("habari", "asante", "karibu", "sasa", "nzuri", "pole")

    override fun onCreateInputView(): View {
        loadRecents(); loadPins()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(6))
            setBackgroundColor(bg)
        }
        render()
        try { root?.let { LiquidGlass.apply(it, 1.0f) } } catch (_: Exception) {}
        return root!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        mode = 0; if (!capsLock) caps = false; symbols = false; currentWord.clear(); dismissKeyPopup(); render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissKeyPopup(); stopVoice(); super.onFinishInputView(finishingInput)
    }

    private fun loadRecents() {
        recentEmoji.clear(); if (incognito) return
        val raw = prefs.getString("recent_emoji", "") ?: ""
        if (raw.isNotBlank()) recentEmoji.addAll(raw.split("|").filter { it.isNotBlank() }.take(16))
    }
    private fun loadPins() {
        pinnedClips.clear()
        val raw = prefs.getString("pinned_clips", "") ?: ""
        if (raw.isNotBlank()) pinnedClips.addAll(raw.split("\u0001").filter { it.isNotBlank() }.take(8))
    }
    private fun saveRecents() { if (!incognito) prefs.edit().putString("recent_emoji", recentEmoji.joinToString("|")).apply() }
    private fun savePins() { prefs.edit().putString("pinned_clips", pinnedClips.joinToString("\u0001")).apply() }
    private fun rememberEmoji(e: String) {
        if (incognito) return; recentEmoji.remove(e); recentEmoji.add(0, e)
        while (recentEmoji.size > 16) recentEmoji.removeAt(recentEmoji.lastIndex); saveRecents()
    }

    private fun styleText(raw: String): String = GrimyTextStyles.apply(raw, textStyle)

    private fun render() {
        val p = root ?: return
        p.removeAllViews(); p.setBackgroundColor(bg)
        try { LiquidGlass.apply(p, 1.0f) } catch (_: Exception) {}
        if (suggestionsOn && mode == 0) p.addView(buildSuggestionsBar())
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label(if (incognito) "GRIMY · PRIVATE" else "GRIMY BOARD", 11f, accent, true), LinearLayout.LayoutParams(0, dp(32), 1f))
        head.addView(key("↶", showBubble = false) { undo() }, LinearLayout.LayoutParams(dp(36), dp(32)))
        head.addView(key("123", showBubble = false) { mode = if (mode == 3) 0 else 3; render() }, LinearLayout.LayoutParams(dp(40), dp(32)))
        head.addView(key(if (oneHand == 0) "⊏" else if (oneHand == 1) "◀" else "▶", showBubble = false) { oneHand = (oneHand + 1) % 3; render() }, LinearLayout.LayoutParams(dp(36), dp(32)))
        head.addView(key("🎙", showBubble = false) { startVoice() }, LinearLayout.LayoutParams(dp(36), dp(32)))
        head.addView(key("▤", showBubble = false) { mode = if (mode == 2) 0 else 2; render() }, LinearLayout.LayoutParams(dp(36), dp(32)))
        head.addView(key("☺", showBubble = false) { mode = if (mode == 1) 0 else 1; emojiTab = 0; render() }, LinearLayout.LayoutParams(dp(36), dp(32)))
        // Font style studio
        head.addView(key("Aa", highlight = mode == 4 || textStyle != "normal", showBubble = false) {
            mode = if (mode == 4) 0 else 4; render()
        }, LinearLayout.LayoutParams(dp(38), dp(32)))
        head.addView(key("⚙", showBubble = false) {
            startActivity(Intent(this, GrimyBoardSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, LinearLayout.LayoutParams(dp(36), dp(32)))
        p.addView(head)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        when (mode) {
            1 -> renderEmoji(content)
            2 -> renderClipboard(content)
            3 -> renderNumpad(content)
            4 -> renderTextStyles(content)
            else -> renderKeys(content)
        }
        when (oneHand) {
            1 -> {
                val row = LinearLayout(this)
                row.addView(content, LinearLayout.LayoutParams(0, -2, 0.72f))
                row.addView(View(this), LinearLayout.LayoutParams(0, -2, 0.28f))
                p.addView(row)
            }
            2 -> {
                val row = LinearLayout(this)
                row.addView(View(this), LinearLayout.LayoutParams(0, -2, 0.28f))
                row.addView(content, LinearLayout.LayoutParams(0, -2, 0.72f))
                p.addView(row)
            }
            else -> p.addView(content)
        }
    }

    private fun renderTextStyles(p: LinearLayout) {
        p.addView(label("GRIMY FONT STUDIO", 15f, accent, true))
        p.addView(label("Unicode styles — recipients can read them without this app.", 11f, Color.LTGRAY, false))
        val current = textStyle
        val preview = TextView(this).apply {
            text = GrimyTextStyles.apply("Hi, welcome to GRIMY BOARD!", current)
            textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(8), dp(14), dp(8), dp(14)); background = glass(keyBg, accent)
        }
        p.addView(preview, LinearLayout.LayoutParams(-1, dp(60)).apply { setMargins(0, dp(6), 0, dp(8) }})
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        GrimyTextStyles.styles.forEach { style ->
            val selected = style.id == current
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = glass(if (selected) 0xFF392033.toInt() else keyBg, if (selected) accent else 0x22FFFFFF)
                isClickable = true
                setOnClickListener {
                    prefs.edit().putString("text_style", style.id).apply()
                    mode = 0; render()
                    Toast.makeText(this@GrimyBoardService, "${style.name} on", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(TextView(this).apply {
                text = style.name; textSize = 12f; setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = style.sample; textSize = 18f; setTextColor(accent)
            })
            list.addView(row, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(0, dp(2), 0, dp(2)) })
        }
        scroll.addView(list)
        p.addView(scroll, LinearLayout.LayoutParams(-1, dp(200)))
        p.addView(key("Use style · Back to keyboard", highlight = true, showBubble = false) {
            mode = 0; render()
        }, LinearLayout.LayoutParams(-1, dp(38)))
    }

    private fun buildSuggestionsBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4)); background = glass(0xCC1A1020.toInt(), 0x33FFFFFF)
        }
        val suggestions = if (language == "sw" && currentWord.isEmpty()) swahiliHint.take(3)
        else GrimySuggestions.suggest(currentWord.toString(), lastWord)
        suggestions.forEach { s ->
            bar.addView(TextView(this).apply {
                text = styleText(s); textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8)); background = glass(keyBg, accent)
                setOnClickListener { applySuggestion(s) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        return bar
    }

    private fun applySuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) ic.deleteSurroundingText(currentWord.length, 0)
        pushUndo(); ic.commitText(styleText(word) + " ", 1); lastWord = word; currentWord.clear(); feedback(); render()
    }

    private fun renderKeys(p: LinearLayout) {
        if (numberRow) addRow(p, "1234567890", height - 4)
        val rows = if (symbols) listOf("@#$%&-*_()", "!?:;/=\\[]", "<>~^|{}'\"") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { i, s ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            if (i == 2) {
                val shiftLabel = when { symbols -> "ABC"; capsLock -> "⇪"; else -> "⇧" }
                r.addView(key(shiftLabel, highlight = caps || capsLock, showBubble = false) {
                    if (symbols) { symbols = false; render() } else {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastShiftTap < 350) { capsLock = !capsLock; caps = capsLock }
                        else { if (capsLock) { capsLock = false; caps = false } else caps = !caps }
                        lastShiftTap = now; render()
                    }
                }, LinearLayout.LayoutParams(dp(44), dp(height)).apply { setMargins(2, 2, 2, 2) })
            }
            s.forEach { ch ->
                val base = ch.toString(); val shown = if (caps && !symbols) base.uppercase() else base
                val alts = longPressMap[base.lowercase()] ?: emptyList()
                r.addView(key(shown, longPressAlternates = alts, showBubble = true) {
                    typeChar(shown); if (caps && !capsLock) { caps = false; render() }
                }, LinearLayout.LayoutParams(0, dp(height), 1f).apply { setMargins(2, 2, 2, 2) })
            }
            if (i == 2) r.addView(key("⌫", showBubble = false, longPressAction = { startDeleteRepeat() }, longPressEnd = { stopDeleteRepeat() }) { deleteOnce() }, LinearLayout.LayoutParams(dp(44), dp(height)).apply { setMargins(2, 2, 2, 2) })
            p.addView(r)
        }
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER }
        bottom.addView(key(if (symbols) "ABC" else "?123", showBubble = false) { symbols = !symbols; render() }, LinearLayout.LayoutParams(dp(52), dp(44)))
        bottom.addView(key(if (language == "en") "EN" else "SW", showBubble = false) { language = if (language == "en") "sw" else "en"; render() }, LinearLayout.LayoutParams(dp(40), dp(44)))
        bottom.addView(key(",", showBubble = true) { typeChar(",") }, LinearLayout.LayoutParams(dp(32), dp(44)))
        bottom.addView(key("English · GRIMY", highlight = true, showBubble = false) { onSpace() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(3, 0, 3, 0) })
        bottom.addView(key(".", showBubble = true) { typeChar(".") }, LinearLayout.LayoutParams(dp(32), dp(44)))
        bottom.addView(key("↵", showBubble = false) {
            val a = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
            if (a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED) {
                pushUndo(); currentInputConnection?.commitText("\n", 1)
            } else currentInputConnection?.performEditorAction(a)
            currentWord.clear(); render()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        p.addView(bottom)
    }

    private fun renderNumpad(p: LinearLayout) {
        p.addView(label("Number pad", 12f, accent, true))
        listOf("123", "456", "789", "*0#").forEach { row ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            row.forEach { c -> r.addView(key(c.toString(), showBubble = true, bigEmoji = true) { typeChar(c.toString()) }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(4, 4, 4, 4) }) }
            p.addView(r)
        }
        val b = LinearLayout(this)
        b.addView(key("⌫", showBubble = false) { deleteOnce() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        b.addView(key("ABC", showBubble = false) { mode = 0; render() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        p.addView(b)
    }

    private fun renderEmoji(p: LinearLayout) {
        p.addView(label("YOUR GRIMY EMOJIS", 12f, accent, true))
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER }
        listOf("Mine", "Stock", "Stamps", "Faces", "Hearts").forEachIndexed { i, name ->
            val active = emojiTab == i
            tabs.addView(TextView(this).apply {
                text = name; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(if (active) Color.BLACK else Color.WHITE)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = GradientDrawable().apply { setColor(if (active) accent else keyBg); cornerRadius = dp(12).toFloat() }
                setOnClickListener { emojiTab = i; render() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(2, 2, 2, 2) })
        }
        p.addView(tabs)
        val sc = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        when (emojiTab) {
            0 -> GrimyCustomEmoji.pack.chunked(5).forEach { row ->
                val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
                row.forEach { e -> r.addView(customEmojiTile(e), LinearLayout.LayoutParams(0, dp(56), 1f).apply { setMargins(2, 2, 2, 2) }) }
                repeat(5 - row.size) { r.addView(View(this), LinearLayout.LayoutParams(0, dp(56), 1f)) }
                box.addView(r)
            }
            1 -> addEmojiGrid(box, facesEmoji + moreEmoji)
            2 -> grimyStamps.forEach { s -> box.addView(key(s, showBubble = false) { commit(s) }, LinearLayout.LayoutParams(-1, dp(40)).apply { setMargins(0, 2, 0, 2) }) }
            3 -> addEmojiGrid(box, facesEmoji)
            else -> addEmojiGrid(box, heartsEmoji)
        }
        sc.addView(box); p.addView(sc, LinearLayout.LayoutParams(-1, dp(200)))
        p.addView(key("ABC", showBubble = false) { mode = 0; render() }, LinearLayout.LayoutParams(-1, dp(36)))
    }

    private fun customEmojiTile(emoji: GrimyEmoji): View {
        val size = dp(48); val bmp = GrimyCustomEmoji.bitmap(emoji, size)
        val wrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER; background = glass(keyBg, 0x33FFFFFF); isClickable = true
            setOnClickListener { commit(emoji.shortcode); rememberEmoji(emoji.shortcode); feedback() }
        }
        wrap.addView(ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = LinearLayout.LayoutParams(size, size) })
        return wrap
    }

    private fun addEmojiGrid(box: LinearLayout, pack: List<String>) {
        pack.chunked(8).forEach { row ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            row.forEach { e -> r.addView(key(e, showBubble = true) { commitEmoji(e) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(2, 2, 2, 2) }) }
            repeat(8 - row.size) { r.addView(View(this), LinearLayout.LayoutParams(0, dp(44), 1f)) }
            box.addView(r)
        }
    }

    private fun renderClipboard(p: LinearLayout) {
        p.addView(label(if (incognito) "Clipboard off in Private mode" else "Clipboard", 11f, accent, true))
        if (!incognito) clipboardItems.forEach { v ->
            p.addView(key(v.take(40), showBubble = false) { commit(v); mode = 0; render() }, LinearLayout.LayoutParams(-1, dp(38)).apply { setMargins(0, 2, 0, 2) })
        }
        val r = LinearLayout(this)
        r.addView(key("Read", showBubble = false) { readClipboard() }, LinearLayout.LayoutParams(0, dp(36), 1f))
        r.addView(key("Clear", showBubble = false) { clipboardItems.clear(); render() }, LinearLayout.LayoutParams(0, dp(36), 1f))
        r.addView(key("ABC", showBubble = false) { mode = 0; render() }, LinearLayout.LayoutParams(0, dp(36), 1f))
        p.addView(r)
    }

    private fun typeChar(ch: String) {
        pushUndo()
        // Style only letters so numbers/punctuation stay plain
        val out = if (ch.length == 1 && ch[0].isLetter()) styleText(ch) else ch
        commit(out)
        if (ch.length == 1 && ch[0].isLetter()) currentWord.append(ch)
        else if (ch == " " || ch == "." || ch == "," || ch == "!" || ch == "?") {
            if (currentWord.isNotEmpty()) { lastWord = currentWord.toString(); currentWord.clear() }
        } else currentWord.clear()
        if (suggestionsOn) render()
    }

    private fun commitEmoji(e: String) { pushUndo(); commit(e); rememberEmoji(e) }
    private fun pushUndo() {
        try {
            val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString() ?: return
            if (undoStack.isEmpty() || undoStack.last() != before) {
                undoStack.addLast(before); while (undoStack.size > 20) undoStack.removeFirst()
            }
        } catch (_: Exception) {}
    }
    private fun undo() {
        val ic = currentInputConnection ?: return; if (undoStack.isEmpty()) return
        val target = undoStack.removeLast()
        try {
            val now = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
            if (now.length >= target.length) ic.deleteSurroundingText(now.length - target.length, 0)
        } catch (_: Exception) {}
        currentWord.clear(); feedback(); render()
    }

    private fun startVoice() {
        if (speech != null) { stopVoice(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice not available", Toast.LENGTH_SHORT).show(); return
        }
        try {
            speech = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            pushUndo(); commit(styleText(text) + " ")
                            lastWord = text.split(" ").lastOrNull().orEmpty()
                        }
                        stopVoice(); render()
                    }
                    override fun onError(error: Int) { stopVoice() }
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "sw") "sw" else Locale.getDefault().toString())
                })
                Toast.makeText(this, "Listening…", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Mic permission needed", Toast.LENGTH_SHORT).show(); stopVoice()
        }
    }
    private fun stopVoice() { try { speech?.destroy() } catch (_: Exception) {}; speech = null }

    private fun addRow(p: LinearLayout, s: String, h: Int) {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
        s.forEach { c -> r.addView(key(c.toString(), showBubble = true) { typeChar(c.toString()) }, LinearLayout.LayoutParams(0, dp(h), 1f).apply { setMargins(2, 1, 2, 1) }) }
        p.addView(r)
    }

    private fun onSpace() {
        val now = SystemClock.uptimeMillis()
        if (doubleSpacePeriod && now - lastSpaceTap < 320) {
            pushUndo(); currentInputConnection?.deleteSurroundingText(1, 0)
            currentInputConnection?.commitText(". ", 1); currentWord.clear()
        } else typeChar(" ")
        lastSpaceTap = now; render()
    }

    private fun deleteOnce() {
        pushUndo(); val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
        if (currentWord.isNotEmpty()) currentWord.deleteAt(currentWord.length - 1)
        feedback(); if (suggestionsOn) render()
    }
    private val deleteRunnable = object : Runnable {
        override fun run() { if (!deleteRepeating) return; deleteOnce(); handler.postDelayed(this, 55) }
    }
    private fun startDeleteRepeat() { deleteRepeating = true; handler.postDelayed(deleteRunnable, 280) }
    private fun stopDeleteRepeat() { deleteRepeating = false; handler.removeCallbacks(deleteRunnable) }

    private fun readClipboard() {
        if (incognito) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val v = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (v.isNotEmpty() && !clipboardItems.contains(v)) {
            clipboardItems.add(0, v); while (clipboardItems.size > 10) clipboardItems.removeAt(clipboardItems.lastIndex)
        }
        render()
    }

    private fun commit(v: String) { currentInputConnection?.commitText(v, 1); feedback() }
    private fun feedback() {
        if (haptics) try {
            val ms = when (hapticLevel) { 0 -> 8; 2 -> 20; else -> 12 }
            val vib = if (android.os.Build.VERSION.SDK_INT >= 31)
                getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26)
                vib.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vib.vibrate(ms.toLong())
        } catch (_: Exception) {}
        if (keySound) try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.4f)
        } catch (_: Exception) {}
    }

    private fun showKeyPopup(anchor: View, label: String) {
        if (!keyPopupEnabled || label.isBlank() || label.length > 4) return
        dismissKeyPopup()
        val bubble = TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 28f; setTextColor(Color.WHITE)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF2A2433.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(2), accent)
            }
        }
        keyPopup = PopupWindow(bubble, -2, -2, false).apply { isOutsideTouchable = false; isFocusable = false }
        try { keyPopup?.showAsDropDown(anchor, (anchor.width / 2) - dp(28), -(anchor.height + dp(56))) } catch (_: Exception) {}
    }
    private fun dismissKeyPopup() { try { keyPopup?.dismiss() } catch (_: Exception) {}; keyPopup = null }

    private fun key(
        t: String, highlight: Boolean = false, showBubble: Boolean = false, bigEmoji: Boolean = false,
        longPressAlternates: List<String> = emptyList(), longPressAction: (() -> Unit)? = null,
        longPressEnd: (() -> Unit)? = null, click: () -> Unit
    ): TextView = TextView(this).apply {
        text = t; gravity = Gravity.CENTER
        textSize = when { bigEmoji -> 22f; t.length > 6 -> 11f; t.length > 2 -> 13f; else -> 18f }
        setTextColor(Color.WHITE)
        background = glass(if (highlight || t.contains("GRIMY")) accent else keyBg, if (highlight) 0x66FFFFFF else 0x22FFFFFF)
        isClickable = true; isFocusable = true
        isLongClickable = longPressAlternates.isNotEmpty() || longPressAction != null
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { if (showBubble) showKeyPopup(v, t); longPressAction?.invoke(); false }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dismissKeyPopup(); longPressEnd?.invoke(); false }
                else -> false
            }
        }
        setOnClickListener {
            animate().scaleX(0.93f).scaleY(0.93f).setDuration(45).withEndAction { scaleX = 1f; scaleY = 1f }.start()
            dismissKeyPopup(); click()
        }
        if (longPressAlternates.isNotEmpty()) setOnLongClickListener {
            dismissKeyPopup()
            val popup = PopupWindow(this@GrimyBoardService)
            val row = LinearLayout(this@GrimyBoardService).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6))
                background = glass(0xFF1A1820.toInt(), accent)
            }
            longPressAlternates.forEach { a ->
                row.addView(key(a, showBubble = false) { typeChar(a); popup.dismiss() }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(3, 0, 3, 0) })
            }
            popup.contentView = HorizontalScrollView(this@GrimyBoardService).apply { addView(row) }
            popup.isOutsideTouchable = true; popup.isFocusable = true
            popup.showAsDropDown(this, 0, -dp(52)); true
        }
    }

    private fun glass(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(10).toFloat(); setStroke(dp(1), stroke)
    }
    private fun label(t: String, s: Float, c: Int, b: Boolean) = TextView(this).apply {
        text = t; textSize = s; setTextColor(c); if (b) setTypeface(null, Typeface.BOLD)
        setPadding(dp(6), dp(3), dp(6), dp(3))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
