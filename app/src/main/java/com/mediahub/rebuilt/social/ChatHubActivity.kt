package com.mediahub.rebuilt.social

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Signed-in user directory and conversation launcher. */
class ChatHubActivity : AppCompatActivity() {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var list: LinearLayout
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(34), dp(18), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF160B31.toInt(), 0xFF08080D.toInt()))
        }
        root.addView(TextView(this).apply {
            text = "Messages"
            textSize = 28f; setTextColor(-1); setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "People using MEDIAHUB-PRO"
            textSize = 13f; setTextColor(0xFFBBAED5.toInt())
            setPadding(0, dp(5), 0, dp(18))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@") ?: "MediaHub user"
        db.collection("public_profiles").document(user.uid).set(
            mapOf("uid" to user.uid, "displayName" to name,
                "lastSeen" to com.google.firebase.Timestamp.now()),
            com.google.firebase.firestore.SetOptions.merge()
        )
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("users").document(user.uid).collection("devices").document(token)
                .set(mapOf("token" to token, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
        }
        listener = db.collection("public_profiles").addSnapshotListener { snap, error ->
            if (error != null) {
                Toast.makeText(this, "Could not load users: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                return@addSnapshotListener
            }
            list.removeAllViews()
            val profiles = snap?.documents.orEmpty()
                .filter { it.id != user.uid }
                .sortedBy { it.getString("displayName")?.lowercase().orEmpty() }
            if (profiles.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "No other registered users yet. Invite someone to install MEDIAHUB-PRO."
                    setTextColor(0xFFBBAED5.toInt()); textSize = 14f
                    setPadding(0, dp(24), 0, dp(24))
                })
            }
            profiles.forEach { doc ->
                val uid = doc.id
                val display = doc.getString("displayName") ?: "MediaHub user"

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = GradientDrawable().apply {
                        setColor(0xFF201632.toInt()); cornerRadius = dp(18).toFloat()
                    }
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        startActivity(Intent(this@ChatHubActivity, ChatActivity::class.java)
                            .putExtra(ChatActivity.EXTRA_UID, uid)
                            .putExtra(ChatActivity.EXTRA_NAME, display))
                    }
                }
                val avatar = TextView(this).apply {
                    text = display.take(1).uppercase(); textSize = 20f
                    gravity = Gravity.CENTER; setTextColor(-1)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(0xFF9C4DFF.toInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                }
                row.addView(avatar)
                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
                }
                details.addView(TextView(this).apply {
                    text = display; textSize = 16f; setTextColor(-1)
                    setTypeface(null, Typeface.BOLD)
                })
                details.addView(TextView(this).apply {
                    text = "MEDIAHUB-PRO member"; textSize = 12f; setTextColor(0xFFBBAED5.toInt())
                })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = "CHAT  ›"; textSize = 11f; setTextColor(0xFFFF69A8.toInt())
                })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
    }

    override fun onDestroy() { listener?.remove(); super.onDestroy() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
