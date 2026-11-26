package com.mediahub.rebuilt.admin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AdminActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private val white = 0xFFFFFFFF.toInt()
    private val muted = 0xFFB8ADC9.toInt()
    private val accent = 0xFFFF2D95.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isAdmin()) { finish(); return }
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF08080D.toInt()) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "ADMIN CONTROL CENTER"
            setTextColor(white); textSize = 25f; setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Owner account · ${auth.currentUser?.email.orEmpty()}"
            setTextColor(accent); textSize = 13f; setPadding(0, dp(8), 0, dp(16))
        })
        status = TextView(this).apply { setTextColor(muted); textSize = 13f; text = "Loading admin data…" }
        root.addView(status)
        section("DEVICE DIRECTORY")
        button("Refresh registered devices") { loadDevices() }
        section("APP ANNOUNCEMENTS")
        button("Publish announcement") { composeAnnouncement() }
        button("View announcements") { loadAnnouncements() }
        button("Close admin center") { finish() }
        setContentView(scroll)
        loadDevices()
    }

    private fun isAdmin(): Boolean =
        auth.currentUser?.email?.equals(ADMIN_EMAIL, ignoreCase = true) == true

    private fun loadDevices() {
        status.text = "Loading registered devices…"
        db.collection("device_directory")
            .orderBy("lastSeen", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200).get()
            .addOnSuccessListener { snapshot ->
                clearDynamicCards()
                status.text = "Registered installations: ${snapshot.size()}"
                snapshot.documents.forEachIndexed { index, doc ->
                    val title = "${doc.getString("manufacturer").orEmpty()} ${doc.getString("model").orEmpty()}".trim()
                    val detail = "Android ${doc.getString("androidVersion").orEmpty()} · MediaHub ${doc.getString("appVersion").orEmpty()}\nRecord ID: ${doc.id}"
                    card("${index + 1}. ${title.ifBlank { "Unknown device" }}", detail) {
                        AlertDialog.Builder(this)
                            .setTitle("Remove device record?")
                            .setMessage("This removes the selected installation from the public device directory.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove") { _, _ ->
                                db.collection("device_directory").document(doc.id).delete()
                                    .addOnSuccessListener { loadDevices() }
                                    .addOnFailureListener { status.text = "Delete failed: ${it.localizedMessage}" }
                            }.show()
                    }
                }
            }
            .addOnFailureListener { status.text = "Could not load devices: ${it.localizedMessage ?: "Check Firestore rules"}" }
    }

    private fun composeAnnouncement() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }
        val title = EditText(this).apply { hint = "Announcement title"; setSingleLine(true) }
        val message = EditText(this).apply {
            hint = "Announcement message"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        box.addView(title); box.addView(message)
        AlertDialog.Builder(this).setTitle("New app announcement").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Publish", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val t = title.text.toString().trim()
                        val m = message.text.toString().trim()
                        if (t.isBlank() || m.isBlank()) {
                            title.error = if (t.isBlank()) "Required" else null
                            message.error = if (m.isBlank()) "Required" else null
                            return@setOnClickListener
                        }
                        val data = hashMapOf<String, Any>(
                            "title" to t.take(100),
                            "message" to m.take(2000),
                            "createdBy" to ADMIN_EMAIL,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("announcements").add(data)
                            .addOnSuccessListener { status.text = "Announcement published."; dialog.dismiss() }
                            .addOnFailureListener { status.text = "Publish failed: ${it.localizedMessage}" }
                    }
                }
                dialog.show()
            }
    }

    private fun loadAnnouncements() {
        status.text = "Loading announcements…"
        db.collection("announcements").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50).get()
            .addOnSuccessListener { snapshot ->
                clearDynamicCards()
                status.text = "Announcements: ${snapshot.size()}"
                snapshot.documents.forEach { doc ->
                    card(doc.getString("title").orEmpty(), doc.getString("message").orEmpty()) {
                        AlertDialog.Builder(this).setTitle("Delete announcement?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                db.collection("announcements").document(doc.id).delete()
                                    .addOnSuccessListener { loadAnnouncements() }
                                    .addOnFailureListener { status.text = "Delete failed: ${it.localizedMessage}" }
                            }.show()
                    }
                }
            }
            .addOnFailureListener { status.text = "Could not load announcements: ${it.localizedMessage}" }
    }

    private fun clearDynamicCards() {
        val keep = root.childCount
        for (i in keep - 1 downTo 0) {
            val child = root.getChildAt(i)
            if (child.tag == "admin_dynamic") root.removeViewAt(i)
        }
    }

    private fun section(label: String) {
        root.addView(TextView(this).apply {
            text = label; setTextColor(accent); textSize = 11f
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(18), 0, dp(8))
        })
    }

    private fun button(label: String, action: () -> Unit) {
        root.addView(TextView(this).apply {
            text = label; tag = "admin_static"; setTextColor(white); textSize = 14f
            setPadding(dp(15), dp(15), dp(15), dp(15)); gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF160E28.toInt()); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x28FFFFFF)
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        })
    }

    private fun card(title: String, detail: String, onLongAction: () -> Unit) {
        root.addView(TextView(this).apply {
            text = "$title\n$detail\n\nTap and hold to manage"
            tag = "admin_dynamic"; setTextColor(white); textSize = 13f
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(dp(14), dp(13), dp(14), dp(13))
            background = GradientDrawable().apply {
                setColor(0xFF160E28.toInt()); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x28FFFFFF)
            }
            setOnLongClickListener { onLongAction(); true }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ADMIN_EMAIL = "leonelsquare7@gmail.com"
    }
}
