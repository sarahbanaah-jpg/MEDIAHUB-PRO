package com.mediahub.rebuilt.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/** Offline system keyboard. Gboard-style key bubble. No "YOU ARE TYPING" strip. */
class GrimyBoardService : InputMethodService() {

    private var caps = false
    private var capsLock = false
    private var symbols = false
    private var mode = 0
    private var root: LinearLayout? = null
    private val clipboardItems = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private var lastSpaceTap = 0L
    private var lastShiftTap = 0L
    private var keyPopup: PopupWindow? = null

    private val prefs by lazy { getSharedPreferences("grimy_board", Context.MODE_PRIVATE) }
    private val theme get() = prefs.getString("theme", "pink") ?: "pink"
    private val keyPopupEnabled get() = prefs.getBoolean("key_popup", true)
    private val numberRow get() = prefs.getBoolean("numbers", true)
    private val haptics get() = prefs.getBoolean("haptics", true)
    private val keySound get() = prefs.getBoolean("sound", false)
    private val doubleSpacePeriod get() = prefs.getBoolean("double_space", true)
    private val height get() = prefs.getInt("height", 48).coerceIn(40, 58)

    private val accent: Int
        get() = when (theme) {
            "neon" -> 0xFF00E6BE.toInt()
            "glass" -> 0xFFBE96FF.toInt()
            else -> 0xFFFF3789.toInt()
        }

    private val bg: Int
        get() = when (theme) {
            "amoled" -> Color.BLACK
            "neon" -> 0xFF081216.toInt()
            "glass" -> 0xFF120F1A.toInt()
            else -> 0xFF0D0C13.toInt()
        }

    private val keyBg: Int
        get() = when (theme) {
            "amoled" -> 0xFF141414.toInt()
            "neon" -> 0xFF102226.toInt()
            "glass" -> 0xFF2A2436.toInt()
            else -> 0xFF25222C.toInt()
        }

    private val emojiGroups = listOf(
        listOf("😀", "😂", "🥹", "😍", "🥰", "😘", "😎", "😭", "😡", "🤔", "🤫", "🤗", "😊", "😉", "🥳", "😴"),
        listOf("🫶", "❤️", "🔥", "✨", "💯", "🙏", "💀", "👀", "💔", "💕", "🎉", "👍", "👏", "🤝", "💪", "🌹"),
        listOf("🖤", "😈", "🙌", "💖", "⭐", "🌈", "🍀", "🎧", "🎵", "💎", "🦋", "🌸", "🚀", "🌙", "☀️", "⚡"),
        listOf("1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "✅", "❌", "⚠️", "📌", "💬", "📱", "💻", "🎮", "📷", "🎬", "🎶")
    )

    private val longPressMap = mapOf(
        "a" to listOf("á", "à", "â", "ä", "ã", "å", "æ"),
        "e" to listOf("é", "è", "ê", "ë"),
        "i" to listOf("í", "ì", "î", "ï"),
        "o" to listOf("ó", "ò", "ô", "ö", "õ", "ø"),
        "u" to listOf("ú", "ù", "û", "ü"),
        "n" to listOf("ñ"),
        "c" to listOf("ç"),
        "s" to listOf("ß", "$"),
        "y" to listOf("ÿ"),
        "." to listOf("…", "•"),
        "-" to listOf("—", "–", "_"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "$" to listOf("€", "£", "¥")
    )

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(6), dp(5), dp(8))
            setBackgroundColor(bg)
        }
        render()
        return root!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        mode = 0
        if (!capsLock) caps = false
        symbols = false
        dismissKeyPopup()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissKeyPopup()
        super.onFinishInputView(finishingInput)
    }

    private fun render() {
        val p = root ?: return
        p.removeAllViews()
        p.setBackgroundColor(bg)

        // No YOU ARE TYPING strip — Gboard-style key bubble only

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("GRIMY BOARD", 12f, accent, true), LinearLayout.LayoutParams(0, dp(34), 1f))
        head.addView(key("←", showBubble = false) { moveCursor(-1) }, LinearLayout.LayoutParams(dp(40), dp(34)))
        head.addView(key("→", showBubble = false) { moveCursor(1) }, LinearLayout.LayoutParams(dp(40), dp(34)))
        head.addView(key("▤", showBubble = false) { mode = if (mode == 2) 0 else 2; render() }, LinearLayout.LayoutParams(dp(40), dp(34)))
        head.addView(key("☺", showBubble = false) { mode = if (mode == 1) 0 else 1; render() }, LinearLayout.LayoutParams(dp(40), dp(34)))
        head.addView(
            key("⚙", showBubble = false) {
                startActivity(
                    Intent(this, GrimyBoardSettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            LinearLayout.LayoutParams(dp(40), dp(34))
        )
        p.addView(head)

        when (mode) {
            1 -> { renderEmoji(p); return }
            2 -> { renderClipboard(p); return }
        }

        if (numberRow) addRow(p, "1234567890", height - 4)

        val rows = if (symbols) {
            listOf("@#$%&-*_()", "!?:;/=\\[]", "<>~^|{}'\"")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }

        rows.forEachIndexed { i, s ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            if (i == 2) {
                val shiftLabel = when {
                    symbols -> "=\\<"
                    capsLock -> "⇪"
                    else -> "⇧"
                }
                r.addView(
                    key(shiftLabel, highlight = caps || capsLock, showBubble = false) {
                        if (symbols) {
                            symbols = false
                            render()
                        } else {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastShiftTap < 350) {
                                capsLock = !capsLock
                                caps = capsLock
                            } else {
                                if (capsLock) {
                                    capsLock = false
                                    caps = false
                                } else {
                                    caps = !caps
                                }
                            }
                            lastShiftTap = now
                            render()
                        }
                    },
                    LinearLayout.LayoutParams(dp(44), dp(height)).apply { setMargins(2, 2, 2, 2) }
                )
            }
            s.forEach { ch ->
                val base = ch.toString()
                val shown = if (caps && !symbols) base.uppercase() else base
                val alts = longPressMap[base.lowercase()] ?: emptyList()
                r.addView(
                    key(shown, longPressAlternates = alts, showBubble = true) {
                        commit(shown)
                        if (caps && !capsLock) {
                            caps = false
                            render()
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(height), 1f).apply { setMargins(2, 2, 2, 2) }
                )
            }
            if (i == 2) {
                r.addView(
                    key(
                        "⌫",
                        showBubble = false,
                        longPressAction = { startDeleteRepeat() },
                        longPressEnd = { stopDeleteRepeat() }
                    ) { deleteOnce() },
                    LinearLayout.LayoutParams(dp(44), dp(height)).apply { setMargins(2, 2, 2, 2) }
                )
            }
            p.addView(r)
        }

        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER }
        bottom.addView(
            key(if (symbols) "ABC" else "?123", showBubble = false) { symbols = !symbols; render() },
            LinearLayout.LayoutParams(dp(56), dp(44))
        )
        bottom.addView(key(",", showBubble = true) { commit(",") }, LinearLayout.LayoutParams(dp(34), dp(44)))
        bottom.addView(
            key("English · GRIMY", highlight = true, showBubble = false) { onSpace() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(3, 0, 3, 0) }
        )
        bottom.addView(key(".", showBubble = true) { commit(".") }, LinearLayout.LayoutParams(dp(34), dp(44)))
        bottom.addView(
            key("↵", showBubble = false) {
                val a = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                if (a == EditorInfo.IME_ACTION_NONE || a == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    currentInputConnection?.commitText("\n", 1)
                } else {
                    currentInputConnection?.performEditorAction(a)
                }
            },
            LinearLayout.LayoutParams(dp(46), dp(44))
        )
        p.addView(bottom)
    }

    private fun renderEmoji(p: LinearLayout) {
        val sc = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emojiGroups.forEach { group ->
            group.chunked(8).forEach { row ->
                val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
                row.forEach { e ->
                    r.addView(
                        key(e, showBubble = true) { commit(e) },
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(2, 2, 2, 2) }
                    )
                }
                box.addView(r)
            }
        }
        sc.addView(box)
        p.addView(sc, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)))
        p.addView(
            key("ABC", showBubble = false) { mode = 0; render() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        )
    }

    private fun renderClipboard(p: LinearLayout) {
        p.addView(label("Clipboard stays in memory only — not uploaded.", 11f, accent, true))
        if (clipboardItems.isEmpty()) {
            p.addView(label("Use Read clipboard to load copied text.", 12f, Color.LTGRAY, false))
        }
        clipboardItems.forEach { v ->
            p.addView(
                key(v.take(55), showBubble = false) {
                    commit(v)
                    mode = 0
                    render()
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    setMargins(0, 2, 0, 2)
                }
            )
        }
        val r = LinearLayout(this)
        r.addView(key("Read clipboard", showBubble = false) { readClipboard() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        r.addView(key("Clear", showBubble = false) { clipboardItems.clear(); render() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        r.addView(key("ABC", showBubble = false) { mode = 0; render() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        p.addView(r)
    }

    private fun addRow(p: LinearLayout, s: String, h: Int) {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
        s.forEach { c ->
            r.addView(
                key(c.toString(), showBubble = true) { commit(c.toString()) },
                LinearLayout.LayoutParams(0, dp(h), 1f).apply { setMargins(2, 1, 2, 1) }
            )
        }
        p.addView(r)
    }

    private fun onSpace() {
        val now = SystemClock.uptimeMillis()
        if (doubleSpacePeriod && now - lastSpaceTap < 320) {
            val ic = currentInputConnection
            ic?.deleteSurroundingText(1, 0)
            ic?.commitText(". ", 1)
        } else {
            commit(" ")
        }
        lastSpaceTap = now
    }

    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        try {
            val before = ic.getTextBeforeCursor(10000, 0)?.length ?: 0
            val after = ic.getTextAfterCursor(10000, 0)?.length ?: 0
            val pos = (before + delta).coerceIn(0, before + after)
            ic.setSelection(pos, pos)
        } catch (_: Exception) {
        }
        feedback()
    }

    private fun deleteOnce() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected != null && selected.isNotEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        feedback()
    }

    private val deleteRunnable = object : Runnable {
        override fun run() {
            if (!deleteRepeating) return
            deleteOnce()
            handler.postDelayed(this, 55)
        }
    }

    private fun startDeleteRepeat() {
        deleteRepeating = true
        handler.postDelayed(deleteRunnable, 280)
    }

    private fun stopDeleteRepeat() {
        deleteRepeating = false
        handler.removeCallbacks(deleteRunnable)
    }

    private fun readClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val v = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (v.isNotEmpty() && !clipboardItems.contains(v)) {
            clipboardItems.add(0, v)
            while (clipboardItems.size > 10) clipboardItems.removeAt(clipboardItems.lastIndex)
        }
        render()
    }

    private fun commit(v: String) {
        currentInputConnection?.commitText(v, 1)
        feedback()
    }

    private fun feedback() {
        if (haptics) {
            try {
                val vib = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    getSystemService(VibratorManager::class.java).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(12)
                }
            } catch (_: Exception) {
            }
        }
        if (keySound) {
            try {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.4f)
            } catch (_: Exception) {
            }
        }
    }

    /** Gboard-style magnified letter above the key while finger is down. */
    private fun showKeyPopup(anchor: View, label: String) {
        if (!keyPopupEnabled || label.isBlank() || label.length > 3) return
        dismissKeyPopup()
        val bubble = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF2A2433.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), accent)
            }
            elevation = dp(12).toFloat()
        }
        val popup = PopupWindow(
            bubble,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            elevation = dp(12).toFloat()
        }
        keyPopup = popup
        val yOff = -(anchor.height + dp(56))
        val xOff = (anchor.width / 2) - dp(28)
        try {
            popup.showAsDropDown(anchor, xOff, yOff)
        } catch (_: Exception) {
        }
    }

    private fun dismissKeyPopup() {
        try {
            keyPopup?.dismiss()
        } catch (_: Exception) {
        }
        keyPopup = null
    }

    private fun key(
        t: String,
        highlight: Boolean = false,
        showBubble: Boolean = false,
        longPressAlternates: List<String> = emptyList(),
        longPressAction: (() -> Unit)? = null,
        longPressEnd: (() -> Unit)? = null,
        click: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = t
            gravity = Gravity.CENTER
            textSize = when {
                t.length > 6 -> 11f
                t.length > 2 -> 13f
                else -> 19f
            }
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = glass(
                if (highlight || t == "English · GRIMY") accent else keyBg,
                if (highlight) 0x66FFFFFF else 0x22FFFFFF
            )
            isClickable = true
            isFocusable = true
            isLongClickable = longPressAlternates.isNotEmpty() || longPressAction != null

            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (showBubble) showKeyPopup(v, t)
                        if (longPressAction != null) longPressAction.invoke()
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        dismissKeyPopup()
                        longPressEnd?.invoke()
                        false
                    }
                    else -> false
                }
            }

            setOnClickListener {
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(50).withEndAction {
                    scaleX = 1f
                    scaleY = 1f
                }.start()
                dismissKeyPopup()
                click()
            }

            if (longPressAlternates.isNotEmpty()) {
                setOnLongClickListener {
                    dismissKeyPopup()
                    showAlternatesPopup(this, longPressAlternates)
                    true
                }
            }
        }
    }

    private fun showAlternatesPopup(anchor: View, alts: List<String>) {
        val popup = PopupWindow(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = glass(0xFF1A1820.toInt(), accent)
        }
        alts.forEach { a ->
            row.addView(
                key(a, showBubble = false) {
                    commit(a)
                    popup.dismiss()
                },
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(3, 0, 3, 0) }
            )
        }
        val host = HorizontalScrollView(this).apply { addView(row) }
        popup.contentView = host
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.elevation = dp(8).toFloat()
        popup.showAsDropDown(anchor, 0, -dp(52))
    }

    private fun glass(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun label(t: String, s: Float, c: Int, b: Boolean) = TextView(this).apply {
        text = t
        textSize = s
        setTextColor(c)
        if (b) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(4), dp(6), dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
