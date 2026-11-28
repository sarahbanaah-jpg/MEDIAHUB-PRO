package com.mediahub.rebuilt.keyboard

import android.view.View
import com.mediahub.rebuilt.ui.LiquidGlass

/** Shared hooks so liquid glass can be applied without rewriting the whole IME file. */
object GrimyBoardHooks {
    fun applyGlass(view: View, strength: Float = 1.1f) {
        try {
            LiquidGlass.apply(view, strength)
        } catch (_: Exception) {
        }
    }
}
