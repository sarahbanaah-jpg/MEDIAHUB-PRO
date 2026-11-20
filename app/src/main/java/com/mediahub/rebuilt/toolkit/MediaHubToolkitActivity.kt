package com.mediahub.rebuilt.toolkit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Twenty offline media planning, calculation and workflow utilities. */
class MediaHubToolkitActivity : AppCompatActivity() {
    private val tools = listOf(
        "Storage converter" to "Convert MB, GB and MiB",
        "Audio file estimator" to "Estimate audio size from bitrate and duration",
        "Video file estimator" to "Estimate video size from bitrate and duration",
        "Aspect-ratio calculator" to "Calculate dimensions and ratios",
        "Recording-time planner" to "Estimate recording time from free storage",
        "Bitrate calculator" to "Calculate average bitrate from file size",
        "Resolution reference" to "SD through 8K dimensions",
        "Frame-rate reference" to "Understand common FPS choices",
        "Audio format guide" to "MP3, AAC, WAV, FLAC and Opus",
        "Video format guide" to "MP4, MKV, H.264, HEVC and AV1",
        "Export presets" to "Practical sharing and archive settings",
        "Filename cleaner" to "Clean unsafe filename characters",
        "Batch rename preview" to "Preview numbered filenames",
        "Storage planner" to "Estimate how many files fit",
        "Backup checklist" to "A safer media backup workflow",
        "Privacy checklist" to "Review metadata and sharing risks",
        "Subtitle guide" to "SRT, VTT, timing and encoding",
        "Compression guide" to "Reduce size while preserving quality",
        "Duplicate cleanup guide" to "Find duplicates safely",
        "Sharing safety guide" to "Prepare files before sharing"
    )
    private val pink = 0xFFFF6B9F.toInt()
    private val muted = 0xFFB9B6C3.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF08080D.toInt()) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(38), dp(18), dp(36)) }
        scroll.addView(root)
        root.addView(TextView(this).apply { text = "MEDIAHUB TOOLKIT"; textSize = 24f; setTextColor(-1); typeface = Typeface.DEFAULT_BOLD })
        root.addView(TextView(this).apply { text = "20 offline tools for media creators and everyday file management."; textSize = 14f; setTextColor(muted); setPadding(0, dp(8), 0, dp(20)) })
        tools.forEachIndexed { index, pair ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply { setColor(0xFF15151D.toInt()); cornerRadius = dp(17).toFloat(); setStroke(dp(1), 0x33FFFFFF) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                isClickable = true; isFocusable = true
                setOnClickListener { openTool(index) }
            }
            card.addView(TextView(this).apply { text = "%02d  ·  %s".format(index + 1, pair.first); textSize = 16f; setTextColor(pink); typeface = Typeface.DEFAULT_BOLD })
            card.addView(TextView(this).apply { text = pair.second; textSize = 13f; setTextColor(muted); setPadding(0, dp(5), 0, 0) })
            root.addView(card)
        }
        setContentView(scroll)
    }

    private fun openTool(i: Int) {
        when (i) {
            0 -> one("Storage converter", "Value in MB", "1024") { s -> s.toDoubleOrNull()?.takeIf { it >= 0 }?.let { "%.3f GB · %.3f GiB".format(it / 1000, it / 1024) } ?: "Enter a valid non-negative number." }
            1 -> two("Audio file estimator", "Bitrate (kbps)", "128", "Duration (minutes)", "60") { a,b -> val r=a.toDoubleOrNull(); val m=b.toDoubleOrNull(); if(r==null||m==null||r<0||m<0) "Invalid values." else size(r*m*60*1000/8) }
            2 -> two("Video file estimator", "Total bitrate (Mbps)", "5", "Duration (minutes)", "10") { a,b -> val r=a.toDoubleOrNull(); val m=b.toDoubleOrNull(); if(r==null||m==null||r<0||m<0) "Invalid values." else size(r*m*60*1000000/8) }
            3 -> two("Aspect-ratio calculator", "Width (pixels)", "1920", "Height (pixels)", "1080") { a,b -> val w=a.toDoubleOrNull(); val h=b.toDoubleOrNull(); if(w==null||h==null||w<=0||h<=0) "Enter positive dimensions." else "Aspect ratio: %.4f : 1".format(w/h) }
            4 -> two("Recording-time planner", "Free storage (GB)", "5", "Video bitrate (Mbps)", "8") { a,b -> val g=a.toDoubleOrNull(); val r=b.toDoubleOrNull(); if(g==null||r==null||g<0||r<=0) "Invalid values." else "Approximate recording time: %.1f minutes".format(g*1000*8/r/60) }
            5 -> two("Bitrate calculator", "File size (MB)", "100", "Duration (minutes)", "5") { a,b -> val mb=a.toDoubleOrNull(); val m=b.toDoubleOrNull(); if(mb==null||m==null||mb<0||m<=0) "Invalid values." else "Average bitrate: %.0f kbps".format(mb*8000/m/60) }
            6 -> guide("Resolution reference", "480p SD: 854×480\n720p HD: 1280×720\n1080p Full HD: 1920×1080\n1440p QHD: 2560×1440\n4K UHD: 3840×2160\n8K UHD: 7680×4320\nHigher resolution requires more storage and processing.")
            7 -> guide("Frame-rate reference", "24 FPS: cinematic motion.\n25/30 FPS: common video and social sharing.\n50/60 FPS: smoother sport and action.\n120 FPS+: useful for slow motion.\nKeep export frame rate aligned with the source when possible.")
            8 -> guide("Audio format guide", "MP3: widely compatible lossy audio.\nAAC: efficient lossy audio, common on phones.\nOpus: efficient for speech and streaming.\nWAV: usually uncompressed PCM and large.\nFLAC: lossless compression. Converting a lossy file to FLAC does not restore lost detail.")
            9 -> guide("Video format guide", "MP4 and MKV are containers. H.264 offers broad compatibility. HEVC/H.265 can reduce size but requires compatible decoding. AV1 is efficient but device support varies. Choose a codec your playback devices support.")
            10 -> guide("Export presets", "Messaging: 720p, H.264, AAC, moderate bitrate.\nSocial sharing: 1080p, H.264 or HEVC, AAC.\nArchive: retain original quality and source files.\nSlow motion: capture at high frame rate before editing. Preview output before deleting originals.")
            11 -> one("Filename cleaner", "Enter a filename", "My video: final?.mp4") { s -> s.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), "_") }
            12 -> two("Batch rename preview", "Base name", "Holiday", "Number of files (1–50)", "5") { a,b -> val n=b.toIntOrNull()?.takeIf { it in 1..50 }; if(n==null) "Enter a number from 1 to 50." else (1..n).joinToString("\n") { "%s_%03d".format(a.ifBlank { "Media" },it) } }
            13 -> two("Storage planner", "Available storage (GB)", "20", "Average file size (MB)", "250") { a,b -> val g=a.toDoubleOrNull(); val s=b.toDoubleOrNull(); if(g==null||s==null||g<0||s<=0) "Invalid values." else "Approximately %d files fit.".format((g*1000/s).toInt()) }
            14 -> guide("Backup checklist", "1. Keep the original media.\n2. Make a second copy on a separate device.\n3. Verify copied files open correctly.\n4. Keep important files in a trusted off-device backup.\n5. Test restore occasionally. A private folder on one phone is not a backup.")
            15 -> guide("Privacy checklist", "Check photo location metadata before public sharing. Review filenames and visible notifications. Avoid sharing private folders or full-device backups. Use a strong device screen lock. Share a copy, not your only original. Review app permissions periodically.")
            16 -> guide("Subtitle guide", "SRT is widely supported plain text; VTT is common on the web. Use UTF-8 encoding. Check timing after trimming or changing playback speed. Keep sidecar subtitle filenames aligned with the video.")
            17 -> guide("Compression guide", "Start from the original. Reduce resolution only when the destination does not need the original. Adjust bitrate gradually and preview detail and motion. Avoid repeated lossy exports. Keep a source copy until the result is checked.")
            18 -> guide("Duplicate cleanup guide", "Compare file size, duration, resolution and modified date. Similar filenames do not prove duplicates. Preview both files and keep the best original. Delete only after confirming a separate backup exists.")
            19 -> guide("Sharing safety guide", "Check the recipient and selected files. Review private metadata. Prefer a copy or compressed export. Avoid public links for sensitive content. Confirm transfer completion before deleting the local original.")
        }
    }

    private fun size(bytes: Double): String = "Estimated size: %.2f MB · %.2f MiB".format(bytes/1000000.0, bytes/1048576.0)
    private fun one(title:String,label:String,initial:String,calc:(String)->String) {
        val field=EditText(this).apply { setText(initial); hint=label; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle(title).setMessage(label).setView(field).setPositiveButton("Calculate") { _,_ -> result(title,calc(field.text.toString())) }.setNegativeButton("Cancel",null).show()
    }
    private fun two(title:String,l1:String,v1:String,l2:String,v2:String,calc:(String,String)->String) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0) }
        val a=EditText(this).apply { hint=l1; setText(v1); setSingleLine(true) }
        val b=EditText(this).apply { hint=l2; setText(v2); setSingleLine(true) }
        box.addView(a); box.addView(b)
        AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Calculate") { _,_ -> result(title,calc(a.text.toString(),b.text.toString())) }.setNegativeButton("Cancel",null).show()
    }
    private fun guide(title:String,body:String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Done",null).setNeutralButton("Copy") { _,_ -> copy(body) }.show()
    }
    private fun result(title:String,body:String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Done",null).setNeutralButton("Copy result") { _,_ -> copy(body) }.show()
    }
    private fun copy(value:String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MediaHub",value))
        Toast.makeText(this,"Copied to clipboard",Toast.LENGTH_SHORT).show()
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
