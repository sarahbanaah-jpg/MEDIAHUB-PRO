package com.mediahub.rebuilt.social

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions
import java.io.File
import java.util.UUID

/** One-to-one cloud chat. Messages are transport-encrypted by Firebase/TLS, not end-to-end encrypted. */
class ChatActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_UID = "peer_uid"
        const val EXTRA_NAME = "peer_name"
        private const val MAX_UPLOAD = 15L * 1024L * 1024L
    }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private lateinit var myUid: String
    private lateinit var peerUid: String
    private lateinit var peerName: String
    private lateinit var chatId: String
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: TextView
    private var listener: ListenerRegistration? = null
    private var chatSettingsListener: ListenerRegistration? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var pendingViewLimit = 0

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) chooseViewLimit { limit -> upload(uri, "image", limit) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = auth.currentUser
        if (user == null) { finish(); return }
        myUid = user.uid
        peerUid = intent.getStringExtra(EXTRA_UID).orEmpty()
        peerName = intent.getStringExtra(EXTRA_NAME) ?: "Chat"
        if (peerUid.isBlank() || peerUid == myUid) { finish(); return }
        chatId = listOf(myUid, peerUid).sorted().joinToString("_")
        buildUi()
        ensureChat()
        listen()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(30), dp(14), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF160B31.toInt(), 0xFF08080D.toInt()))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(action("‹") { finish() })
        header.addView(TextView(this).apply {
            text = peerName; textSize = 19f; setTextColor(-1); setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        header.addView(action("✦") { askAi() })
        header.addView(action("⏱") { chooseDisappearingTime() })
        header.addView(action("☎") { sendCallInvite("voice") })
        header.addView(action("▣") { sendCallInvite("video") })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Messages are not end-to-end encrypted."
            textSize = 11f; setTextColor(0xFFFFC66D.toInt())
            setPadding(dp(8), dp(6), 0, dp(8))
        })
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isFillViewport = true; addView(messages)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF241A34.toInt()); cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        bar.addView(action("＋") { imagePicker.launch("image/*") })
        input = EditText(this).apply {
            hint = "Message…"; setTextColor(-1); setHintTextColor(0xFFBBAED5.toInt())
            textSize = 14f; maxLines = 4; setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(action("1×") { chooseViewLimit { pendingViewLimit = it; Toast.makeText(this, if (it == 0) "Normal message" else "Next text will be view-limited: $it", Toast.LENGTH_SHORT).show() } })
        bar.addView(action("🎙") { toggleVoiceRecording() })
        send = action("➤") { sendText() }
        bar.addView(send)
        root.addView(bar)
        setContentView(root)
    }

    private fun action(label: String, click: () -> Unit) = TextView(this).apply {
        text = label; textSize = 18f; gravity = Gravity.CENTER
        setTextColor(0xFFFF69A8.toInt()); setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { click() }
    }

    private fun ensureChat() {
        db.collection("chats").document(chatId).set(
            mapOf("members" to listOf(myUid, peerUid), "updatedAt" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    private fun listen() {
        chatSettingsListener = db.collection("chats").document(chatId).addSnapshotListener { doc, _ ->
            val seconds = (doc?.getLong("disappearSeconds") ?: 0L).toInt()
            getSharedPreferences("mediahub_chat", 0).edit().putInt("disappear_seconds_$chatId", seconds).apply()
        }
        listener = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt").limitToLast(150)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Toast.makeText(this, "Chat error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                messages.removeAllViews()
                snap?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    if (data["deletedForEveryone"] == true) {
                        addBubble("Message deleted", data["senderId"] == myUid, 0xFF8E849B.toInt())
                        return@forEach
                    }
                    val deletedFor = data["deletedFor"] as? List<*>
                    if (deletedFor?.contains(myUid) == true) return@forEach
                    val expires = data["expiresAt"] as? Timestamp
                    if (expires != null && expires.toDate().time <= System.currentTimeMillis()) return@forEach
                    val sender = data["senderId"] == myUid
                    val type = data["type"] as? String ?: "text"
                    val limit = (data["viewLimit"] as? Long)?.toInt() ?: 0
                    val count = (data["viewCount"] as? Long)?.toInt() ?: 0
                    val alreadyViewed = limit > 0 && count >= limit && !sender
                    val text = when {
                        alreadyViewed -> "Message opened"
                        type == "image" -> "▧  Photo · tap to view"
                        type == "voice" -> "▶  Voice message"
                        type == "text" && limit > 0 && !sender -> "◉  Tap to view"
                        else -> data["text"] as? String ?: ""
                    }
                    val bubble = addBubble(text, sender, if (sender) 0xFFFF69A8.toInt() else -1)
                    if (!alreadyViewed && type == "text" && limit > 0 && !sender) {
                        bubble.setOnClickListener { openEphemeralText(doc.id, data, limit) }
                    } else if (!alreadyViewed && (type == "image" || type == "voice")) {
                        bubble.setOnClickListener { openAttachment(doc.id, data, type, limit, count) }
                    }
                    bubble.setOnLongClickListener {
                        showMessageActions(doc.id, data, sender); true
                    }
                }
                db.collection("chats").document(chatId)
                    .update("readAt.$myUid", FieldValue.serverTimestamp())
            }
    }

    private fun addBubble(text: String, mine: Boolean, color: Int): TextView {
        val bubble = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(color)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(if (mine) 0xFF4B2456.toInt() else 0xFF29223A.toInt())
                cornerRadius = dp(18).toFloat()
            }
        }
        messages.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            bottomMargin = dp(7); marginStart = dp(32); marginEnd = dp(32)
        })
        return bubble
    }

    private fun sendText() {
        val value = input.text.toString().trim()
        if (value.isEmpty()) return
        val limit = pendingViewLimit
        pendingViewLimit = 0
        val payload = messagePayload("text", value, null, limit)
        db.collection("chats").document(chatId).collection("messages").add(payload)
            .addOnSuccessListener {
                input.setText("")
                db.collection("chats").document(chatId)
                    .update("updatedAt", FieldValue.serverTimestamp())
            }
            .addOnFailureListener { Toast.makeText(this, "Message failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    private fun messagePayload(type: String, text: String, url: String?, viewLimit: Int): Map<String, Any?> {
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences("mediahub_chat", 0)
        val seconds = prefs.getInt("disappear_seconds_$chatId", 0)
        return hashMapOf(
            "senderId" to myUid, "receiverId" to peerUid, "type" to type,
            "text" to text, "storagePath" to url, "createdAt" to FieldValue.serverTimestamp(),
            "viewLimit" to viewLimit, "viewCount" to 0,
            "viewedBy" to emptyList<String>(), "deletedFor" to emptyList<String>(),
            "deletedForEveryone" to false,
            "expiresAt" to if (seconds > 0) Timestamp(java.util.Date(now + seconds * 1000L)) else null
        )
    }

    private fun chooseViewLimit(done: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle("Photo visibility")
            .setItems(arrayOf("Normal", "View once", "View twice")) { _, which ->
                done(when (which) { 1 -> 1; 2 -> 2; else -> 0 })
            }.show()
    }

    private fun upload(uri: Uri, type: String, viewLimit: Int) {
        val mime = contentResolver.getType(uri) ?: if (type == "image") "image/jpeg" else "audio/mp4"
        val ref = storage.reference.child("chat_media/$chatId/${UUID.randomUUID()}")
        ref.putFile(uri).addOnSuccessListener {
            db.collection("chats").document(chatId).collection("messages")
                .add(messagePayload(type, "", ref.path, viewLimit))
        }.addOnFailureListener {
            Toast.makeText(this, "Upload failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openEphemeralText(id: String, data: Map<String, Any>, limit: Int) {
        val ref = db.collection("chats").document(chatId).collection("messages").document(id)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val count = (snap.getLong("viewCount") ?: 0L).toInt()
            if (count >= limit) throw IllegalStateException("View limit reached")
            tx.update(ref, "viewCount", count + 1)
            tx.update(ref, "viewedBy", FieldValue.arrayUnion(myUid))
        }.addOnSuccessListener {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            AlertDialog.Builder(this).setTitle("View-limited message")
                .setMessage(data["text"] as? String ?: "")
                .setPositiveButton("Close", null).show()
                .setOnDismissListener { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }.addOnFailureListener {
            Toast.makeText(this, "This message has reached its view limit.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAttachment(id: String, data: Map<String, Any>, type: String, limit: Int, count: Int) {
        val mine = data["senderId"] == myUid
        if (limit > 0 && !mine) {
            val ref = db.collection("chats").document(chatId).collection("messages").document(id)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = (snap.getLong("viewCount") ?: 0L).toInt()
                if (current >= limit) throw IllegalStateException("View limit reached")
                tx.update(ref, "viewCount", current + 1)
                tx.update(ref, "viewedBy", FieldValue.arrayUnion(myUid))
            }.addOnFailureListener {
                Toast.makeText(this, "This view-limited message has already been opened.", Toast.LENGTH_SHORT).show()
            }.addOnSuccessListener { loadAttachment(data, type, limit > 0) }
        } else {
            loadAttachment(data, type, limit > 0)
        }
    }

    private fun loadAttachment(data: Map<String, Any>, type: String, protected: Boolean) {
        val path = data["storagePath"] as? String ?: return
        if (protected) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val ref = storage.reference.child(path)
        if (type == "image") {
            ref.getBytes(MAX_UPLOAD).addOnSuccessListener { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val image = ImageView(this).apply {
                    setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                    maxHeight = dp(520); maxWidth = dp(340)
                }
                AlertDialog.Builder(this).setTitle(if (protected) "View-limited photo" else "Photo")
                    .setView(image).setPositiveButton("Close", null).show()
                    .setOnDismissListener { if (protected) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }.addOnFailureListener {
                if (protected) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Toast.makeText(this, "Could not load photo: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            val file = File(cacheDir, "voice-playback-${UUID.randomUUID()}.m4a")
            ref.getFile(file).addOnSuccessListener {
                val player = MediaPlayer()
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener { it.release(); file.delete() }
                player.prepare(); player.start()
                AlertDialog.Builder(this).setTitle("Voice message")
                    .setMessage("Playing voice recording…")
                    .setPositiveButton("Close") { _, _ -> try { player.stop() } catch (_: Exception) {}; player.release(); file.delete() }
                    .show().setOnDismissListener { if (protected) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }.addOnFailureListener {
                if (protected) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                Toast.makeText(this, "Could not load voice message.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun chooseDisappearingTime() {
        val labels = arrayOf("Off", "1 minute", "1 hour", "1 day", "7 days")
        val seconds = intArrayOf(0, 60, 3600, 86400, 604800)
        AlertDialog.Builder(this).setTitle("Disappearing messages")
            .setItems(labels) { _, which ->
                db.collection("chats").document(chatId).update("disappearSeconds", seconds[which])
                    .addOnSuccessListener {
                        getSharedPreferences("mediahub_chat", 0).edit()
                            .putInt("disappear_seconds_$chatId", seconds[which]).apply()
                        Toast.makeText(this, "Disappearing messages: ${labels[which]}", Toast.LENGTH_SHORT).show()
                    }
            }.show()
    }

    private fun askAi() {
        val prompt = input.text.toString().trim()
        if (prompt.isBlank()) {
            Toast.makeText(this, "Type a question for MediaHub AI first.", Toast.LENGTH_SHORT).show()
            return
        }
        addBubble("✦ MediaHub AI is thinking…", true, 0xFFFF69A8.toInt())
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("chatAssistant")
            .call(mapOf("prompt" to prompt))
            .addOnSuccessListener { result ->
                val answer = (result.getData() as? Map<*, *>)?.get("answer") as? String
                    ?: "I couldn't create a response."
                addBubble("✦ MediaHub AI\n$answer", false, -1)
                input.setText("")
            }.addOnFailureListener {
                addBubble("AI unavailable: ${it.localizedMessage}", false, 0xFFFFC66D.toInt())
            }
    }

    private fun showMessageActions(id: String, data: Map<String, Any>, mine: Boolean) {
        val items = if (mine) arrayOf("Delete for me", "Delete for everyone") else arrayOf("Delete for me")
        AlertDialog.Builder(this).setItems(items) { _, which ->
            val ref = db.collection("chats").document(chatId).collection("messages").document(id)
            if (mine && which == 1) {
                ref.update(mapOf("deletedForEveryone" to true, "text" to "Message deleted",
                    "storagePath" to null, "deletedAt" to FieldValue.serverTimestamp()))
            } else {
                ref.update("deletedFor", FieldValue.arrayUnion(myUid))
            }
        }.show()
    }

    private fun toggleVoiceRecording() {
        if (recorder != null) {
            try {
                recorder?.stop(); recorder?.release(); recorder = null
                val file = recordingFile
                if (file != null && file.exists() && file.length() > 0) {
                    val limit = pendingViewLimit
                    pendingViewLimit = 0
                    upload(Uri.fromFile(file), "voice", limit)
                    Toast.makeText(this, "Voice message sent", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Recording was too short.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestAudio.launch(Manifest.permission.RECORD_AUDIO); return
        }
        startRecording()
    }

    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
    }

    private fun startRecording() {
        val file = File(cacheDir, "voice-${UUID.randomUUID()}.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare(); r.start()
            recorder = r; recordingFile = file
            Toast.makeText(this, "Recording… tap microphone again to send.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            Toast.makeText(this, "Could not start recording: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendCallInvite(kind: String) {
        db.collection("chats").document(chatId).collection("calls").add(mapOf(
            "callerId" to myUid, "calleeId" to peerUid, "kind" to kind,
            "status" to "ringing", "createdAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            Toast.makeText(this, "${kind.replaceFirstChar { it.uppercase() }} call invitation sent. Live audio/video transport still needs WebRTC/TURN setup.", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        listener?.remove()
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release(); recorder = null
        super.onDestroy()
    }
}
