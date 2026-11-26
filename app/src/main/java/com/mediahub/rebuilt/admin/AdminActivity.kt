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
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(32)) }
        scroll.addView(root)
        root.addView(TextView(this).apply { text = "ADMIN CONTROL CENTER"; setTextColor(white); textSize = 25f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "OWNER / SUPER ADMIN · ${auth.currentUser?.email.orEmpty()}"; setTextColor(accent); textSize = 13f; setPadding(0, dp(8), 0, dp(16)) })
        status = TextView(this).apply { setTextColor(muted); textSize = 13f; text = "Loading admin data…" }
        root.addView(status)
        section("OVERVIEW")
        button("Refresh dashboard") { loadDashboard() }
        section("USER MANAGEMENT")
        button("View all registered users") { loadUsers() }
        button("View banned accounts") { loadBannedUsers() }
        section("DEVICE MANAGEMENT")
        button("Refresh registered devices") { loadDevices() }
        section("ANNOUNCEMENTS")
        button("Publish announcement") { composeAnnouncement() }
        button("View / delete announcements") { loadAnnouncements() }
        section("ADMIN RECOMMENDATIONS")
        card("Security", "Use bans for policy violations, record a reason, and review bans regularly. Owner access is restricted to this account.")
        card("Operations", "Check the user and device directories after each release. Publish clear maintenance notices before service interruptions.")
        button("Close admin center") { finish() }
        setContentView(scroll)
        loadDashboard()
    }

    private fun isAdmin() = auth.currentUser?.email?.equals(ADMIN_EMAIL, ignoreCase = true) == true

    private fun loadDashboard() {
        status.text = "Loading dashboard…"
        db.collection("users").get().addOnSuccessListener { users ->
            db.collection("user_bans").get().addOnSuccessListener { bans ->
                db.collection("device_directory").get().addOnSuccessListener { devices ->
                    status.text = "Users: ${users.size()}  ·  Banned: ${bans.size()}  ·  Registered devices: ${devices.size()}"
                }.addOnFailureListener { status.text = "Users: ${users.size()} · Bans: ${bans.size()} · Device count unavailable." }
            }.addOnFailureListener { status.text = "Users: ${users.size()} · Ban count unavailable." }
        }.addOnFailureListener { status.text = "Dashboard unavailable: ${it.localizedMessage}" }
    }

    private fun loadUsers() {
        status.text = "Loading user directory…"
        db.collection("users").orderBy("lastSeen", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(200).get()
            .addOnSuccessListener { snapshot ->
                clearDynamicCards()
                status.text = "Registered users shown: ${snapshot.size()}"
                snapshot.documents.forEach { doc ->
                    val email = doc.getString("email").orEmpty()
                    val name = doc.getString("displayName").orEmpty().ifBlank { "MediaHub user" }
                    val detail = "UID: ${doc.id}\nEmail: $email\nLast seen: ${doc.getTimestamp("lastSeen")?.toDate() ?: "Not available"}"
                    card(name, detail) {
                        if (doc.id == auth.currentUser?.uid) {
                            AlertDialog.Builder(this).setMessage("You cannot ban the owner account.").setPositiveButton("OK", null).show()
                        } else manageUser(doc.id, email, name)
                    }
                }
            }.addOnFailureListener { status.text = "Could not load users: ${it.localizedMessage}" }
    }

    private fun manageUser(uid: String, email: String, name: String) {
        val reason = EditText(this).apply { hint = "Reason for ban (required)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 2 }
        AlertDialog.Builder(this).setTitle("Manage $name")
            .setMessage("$email\n\nChoose an action. A ban prevents this app's normal login flow.")
            .setView(reason)
            .setNeutralButton("Cancel", null)
            .setNegativeButton("Unban") { _, _ ->
                db.collection("user_bans").document(uid).delete()
                    .addOnSuccessListener { audit("UNBAN", uid, email, "Ban removed"); status.text = "User unbanned."; loadDashboard() }
                    .addOnFailureListener { status.text = "Unban failed: ${it.localizedMessage}" }
            }
            .setPositiveButton("Ban user", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val why = reason.text.toString().trim()
                        if (why.isBlank()) { reason.error = "A reason is required"; return@setOnClickListener }
                        val data = hashMapOf<String, Any>("uid" to uid, "email" to email, "reason" to why.take(500),
                            "bannedBy" to ADMIN_EMAIL, "bannedAt" to FieldValue.serverTimestamp())
                        db.collection("user_bans").document(uid).set(data)
                            .addOnSuccessListener { audit("BAN", uid, email, why); dialog.dismiss(); status.text = "Ban saved for $email."; loadDashboard() }
                            .addOnFailureListener { status.text = "Ban failed: ${it.localizedMessage}" }
                    }
                }
                dialog.show()
            }
    }

    private fun loadBannedUsers() {
        status.text = "Loading bans…"
        db.collection("user_bans").limit(200).get().addOnSuccessListener { snapshot ->
            clearDynamicCards()
            status.text = "Banned accounts: ${snapshot.size()}"
            snapshot.documents.forEach { doc ->
                card(doc.getString("email").orEmpty().ifBlank { doc.id },
                    "Reason: ${doc.getString("reason").orEmpty()}\nBanned: ${doc.getTimestamp("bannedAt")?.toDate() ?: "Unknown"}") {
                    AlertDialog.Builder(this).setTitle("Remove this ban?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Unban") { _, _ ->
                            db.collection("user_bans").document(doc.id).delete()
                                .addOnSuccessListener { audit("UNBAN", doc.id, doc.getString("email").orEmpty(), "Ban removed from ban list"); loadBannedUsers() }
                                .addOnFailureListener { status.text = "Unban failed: ${it.localizedMessage}" }
                        }.show()
                }
            }
        }.addOnFailureListener { status.text = "Could not load bans: ${it.localizedMessage}" }
    }

    private fun audit(action: String, uid: String, email: String, reason: String) {
        db.collection("admin_audit").add(hashMapOf("action" to action, "targetUid" to uid, "targetEmail" to email,
            "reason" to reason.take(500), "actor" to ADMIN_EMAIL, "createdAt" to FieldValue.serverTimestamp()))
    }

    private fun loadDevices() {
        status.text = "Loading registered devices…"
        db.collection("device_directory").orderBy("lastSeen", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(200).get()
            .addOnSuccessListener { snapshot ->
                clearDynamicCards()
                status.text = "Registered installations: ${snapshot.size()}"
                snapshot.documents.forEachIndexed { index, doc ->
                    val title = "${doc.getString("manufacturer").orEmpty()} ${doc.getString("model").orEmpty()}".trim()
                    val detail = "Android ${doc.getString("androidVersion").orEmpty()} · MediaHub ${doc.getString("appVersion").orEmpty()}\nRecord ID: ${doc.id}"
                    card("${index + 1}. ${title.ifBlank { "Unknown device" }}", detail) {
                        AlertDialog.Builder(this).setTitle("Remove device record?")
                            .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                                db.collection("device_directory").document(doc.id).delete()
                                    .addOnSuccessListener { loadDevices() }
                                    .addOnFailureListener { status.text = "Delete failed: ${it.localizedMessage}" }
                            }.show()
                    }
                }
            }.addOnFailureListener { status.text = "Could not load devices: ${it.localizedMessage}" }
    }

    private fun composeAnnouncement() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val title = EditText(this).apply { hint = "Announcement title"; setSingleLine(true) }
        val message = EditText(this).apply { hint = "Announcement message"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 3 }
        box.addView(title); box.addView(message)
        AlertDialog.Builder(this).setTitle("New app announcement").setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Publish", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val t = title.text.toString().trim(); val m = message.text.toString().trim()
                        if (t.isBlank() || m.isBlank()) { title.error = if (t.isBlank()) "Required" else null; message.error = if (m.isBlank()) "Required" else null; return@setOnClickListener }
                        db.collection("announcements").add(hashMapOf<String, Any>("title" to t.take(100), "message" to m.take(2000),
                            "createdBy" to ADMIN_EMAIL, "createdAt" to FieldValue.serverTimestamp()))
                            .addOnSuccessListener { status.text = "Announcement published."; audit("ANNOUNCEMENT", it.id, t, "Published"); dialog.dismiss() }
                            .addOnFailureListener { status.text = "Publish failed: ${it.localizedMessage}" }
                    }
                }
                dialog.show()
            }
    }

    private fun loadAnnouncements() {
        status.text = "Loading announcements…"
        db.collection("announcements").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener { snapshot ->
                clearDynamicCards(); status.text = "Announcements: ${snapshot.size()}"
                snapshot.documents.forEach { doc ->
                    card(doc.getString("title").orEmpty(), doc.getString("message").orEmpty()) {
                        AlertDialog.Builder(this).setTitle("Delete announcement?").setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                db.collection("announcements").document(doc.id).delete()
                                    .addOnSuccessListener { audit("DELETE_ANNOUNCEMENT", doc.id, doc.getString("title").orEmpty(), "Deleted"); loadAnnouncements() }
                                    .addOnFailureListener { status.text = "Delete failed: ${it.localizedMessage}" }
                            }.show()
                    }
                }
            }.addOnFailureListener { status.text = "Could not load announcements: ${it.localizedMessage}" }
    }

    private fun clearDynamicCards() {
        for (i in root.childCount - 1 downTo 0) if (root.getChildAt(i).tag == "admin_dynamic") root.removeViewAt(i)
    }
    private fun section(label: String) { root.addView(TextView(this).apply { text = label; setTextColor(accent); textSize = 11f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(18), 0, dp(8)) }) }
    private fun button(label: String, action: () -> Unit) {
        root.addView(TextView(this).apply {
            text = label; setTextColor(white); textSize = 14f; setPadding(dp(15), dp(15), dp(15), dp(15)); gravity = Gravity.CENTER_VERTICAL
            background = panel(); setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        })
    }
    private fun card(title: String, detail: String, onLongAction: (() -> Unit)? = null) {
        root.addView(TextView(this).apply {
            text = if (onLongAction == null) "$title\n$detail" else "$title\n$detail\n\nTap and hold to manage"
            tag = "admin_dynamic"; setTextColor(white); textSize = 13f; setPadding(dp(14), dp(13), dp(14), dp(13)); background = panel()
            onLongAction?.let { action -> setOnLongClickListener { action(); true } }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        })
    }
    private fun panel() = GradientDrawable().apply { setColor(0xFF160E28.toInt()); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0x28FFFFFF) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val ADMIN_EMAIL = "leonelsquare7@gmail.com" }
}
