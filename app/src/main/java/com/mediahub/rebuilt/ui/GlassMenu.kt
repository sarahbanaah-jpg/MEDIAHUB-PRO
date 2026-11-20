package com.mediahub.rebuilt.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mediahub.rebuilt.R

/**
 * Frosted-glass dropdown menu (glassmorphism).
 * Dark muted panel, soft green accent, subtle top sheen.
 */
object GlassMenu {

    data class Item(
        val label: String,
        val destructive: Boolean = false,
        val onClick: () -> Unit
    )

    fun show(anchor: View, items: List<Item>) {
        if (items.isEmpty()) return
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = ContextCompat.getDrawable(context, R.drawable.bg_glass_dropdown)
            elevation = dp(18).toFloat()
        }

        val popup = PopupWindow(
            panel,
            dp(220),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(20).toFloat()
            setBackgroundDrawable(
                GradientDrawable().apply { setColor(0x00000000) }
            )
        }

        items.forEachIndexed { index, item ->
            val row = TextView(context).apply {
                text = item.label
                textSize = 14f
                setTypeface(null, Typeface.NORMAL)
                setTextColor(
                    if (item.destructive) 0xFFFB7185.toInt()
                    else 0xFFFFFFFF.toInt()
                )
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = ContextCompat.getDrawable(context, R.drawable.bg_glass_menu_item)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    popup.dismiss()
                    item.onClick()
                }
            }
            panel.addView(row)
            if (index < items.lastIndex) {
                panel.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                        marginStart = dp(12)
                        marginEnd = dp(12)
                    }
                    setBackgroundColor(0x22A8FFCE)
                })
            }
        }

        popup.showAsDropDown(anchor, 0, dp(6), Gravity.END)
    }
}
