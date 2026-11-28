package com.mediahub.rebuilt.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** CART3R.JAILBREAK: offline code workspace and safe project scaffolder. */
class CarterCommandCenterActivity : AppCompatActivity() {
    private val red = 0xFFFF2448.toInt()
    private val muted = 0xFFB69AA3.toInt()
    private lateinit var root: LinearLayout
    private lateinit var editor: EditText
    private lateinit var language: Spinner
    private lateinit var result: TextView
    private val languages = listOf("Kotlin", "JavaScript", "Python", "HTML", "CSS", "JSON")
    private val sections = listOf("DASHBOARD", "COMMAND DIRECTORY", "CODE LAB", "PROJECT GENERATOR", "DEVELOPER TOOLS", "SECURITY CENTER", "BUILD & RELEASE", "TERMS OF SERVICE", "SETTINGS", "ABOUT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF080607.toInt()) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(28), dp(16), dp(32)) }
        scroll.addView(root)
        setContentView(scroll)
        showSection("DASHBOARD")
    }

    private fun showSection(name: String) {
        root.removeAllViews()
        root.addView(label("CART3R.JAILBREAK", 24f, red, true))
        root.addView(label("DEVELOPER COMMAND CENTER  ·  MEDIAHUB-PRO", 11f, muted))
        sections.forEach { key -> root.addView(card(key, if (key == name) "ACTIVE SECTION" else "OPEN") { showSection(key) }) }
        root.addView(label(name, 21f, red, true))
        when (name) {
            "DASHBOARD" -> {
                root.addView(card("WORKSPACE STATUS", "OFFLINE-READY · Local editor and project scaffolder") {})
                root.addView(card("CODE LAB", "Edit snippets, choose a language, run static checks and save locally") { showSection("CODE LAB") })
                root.addView(card("PROJECT GENERATOR", "Create starter folders and source files in app-private storage") { showSection("PROJECT GENERATOR") })
                root.addView(card("BUILD & RELEASE", "Review the repository and open its GitHub Actions build page") { showSection("BUILD & RELEASE") })
                root.addView(label("No source code is executed on the device. Static checks inspect text only.", 13f, muted))
            }
            "COMMAND DIRECTORY" -> listOf("/help", "/menu", "/code", "/debug", "/explain", "/optimize", "/review", "/build", "/status", "/reset").forEach { root.addView(card(it, "Tap to copy") { copy(it) }) }
            "CODE LAB" -> buildCodeLab()
            "PROJECT GENERATOR" -> buildProjectGenerator()
            "DEVELOPER TOOLS" -> {
                listOf("Code workspace" to "Write and save local snippets.", "Static code review" to "Detect a few risky patterns and common TODO markers.", "Project scaffolder" to "Generate starter files for Kotlin, JavaScript, Python or web projects.", "Documentation helper" to "Copy a project README outline.").forEach { (a,b) -> root.addView(card(a,b) {}) }
                root.addView(card("COPY README OUTLINE", "Project overview · Setup · Usage · Tests · License") { copy("# Project\n\n## Overview\nDescribe what this project does.\n\n## Setup\nList prerequisites and installation steps.\n\n## Usage\nAdd examples.\n\n## Tests\nDescribe how to run tests.\n\n## License\nSpecify the license.") })
            }
            "SECURITY CENTER" -> {
                listOf("Validate all external input", "Keep API keys out of source control", "Use HTTPS and least-privilege permissions", "Review dependencies and licenses", "Test only systems you own or are authorized to assess").forEach { root.addView(card("SECURE CODING", it) {}) }
                root.addView(label("Static checks are advisory and are not a full security audit.", 13f, muted))
            }
            "BUILD & RELEASE" -> {
                root.addView(card("ANDROID BUILD", "The Android APK is built by the repository's configured GitHub Actions workflow.") {})
                root.addView(card("OPEN GITHUB ACTIONS", "View build status, logs and downloadable artifacts") {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarahbanaah-jpg/MEDIAHUB-PRO/actions")))
                })
                root.addView(card("RELEASES", "Check published APK releases and version notes") {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarahbanaah-jpg/MEDIAHUB-PRO/releases")))
                })
                root.addView(label("This screen does not claim a build succeeded. Confirm a green workflow and published release on GitHub.", 13f, muted))
            }
            "TERMS OF SERVICE" -> root.addView(label("Use this workspace lawfully. Respect privacy, licenses and intellectual property. Only assess systems you own or have explicit permission to test. Generated files remain in this app's private storage.", 14f, muted))
            "SETTINGS" -> root.addView(card("CLEAR WORKSPACE", "Delete locally generated CART3R project files") { confirmClearProjects() })
            "ABOUT" -> root.addView(label("CART3R.JAILBREAK · MEDIAHUB-PRO Pro developer suite\nOffline code editor · Static review · Starter project generator · GitHub build links.", 14f, muted))
        }
    }

    private fun buildCodeLab() {
        root.addView(label("LANGUAGE", 13f, muted, true))
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@CarterCommandCenterActivity, android.R.layout.simple_spinner_dropdown_item, languages)
        }
        root.addView(language)
        editor = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF8B7A82.toInt())
            hint = "Write or paste code here…"; textSize = 13f; gravity = Gravity.TOP
            minLines = 10; setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF160B10.toInt())
        }
        root.addView(editor, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(10); bottomMargin = dp(10) })
        root.addView(card("LOAD STARTER SNIPPET", "Insert a small example for the selected language") { editor.setText(sample(language.selectedItem.toString())) })
        root.addView(card("STATIC REVIEW", "Inspect code text for common issues; does not execute it") { analyzeCode() })
        root.addView(card("SAVE SNIPPET", "Save in app-private CART3R/snippets storage") { saveSnippet() })
        result = label("Review results appear here.", 13f, muted)
        root.addView(result)
    }

    private fun buildProjectGenerator() {
        root.addView(label("Choose a starter project. Files are written only inside this app's private storage.", 13f, muted))
        val choices = listOf("Kotlin Android module", "JavaScript app", "Python utility", "HTML/CSS website")
        choices.forEach { choice ->
            root.addView(card(choice, "Generate a starter folder with source and README files") { generateProject(choice) })
        }
        root.addView(card("OPEN PROJECT STORAGE", "Show generated project folder path") {
            val dir = File(filesDir, "CART3R/projects")
            Toast.makeText(this, dir.absolutePath, Toast.LENGTH_LONG).show()
        })
    }

    private fun sample(lang: String) = when (lang) {
        "Kotlin" -> "fun main() {\n    println(\"Hello from CART3R\")\n}\n"
        "JavaScript" -> "function greet(name) {\n  return `Hello, ${'$'}{name}`;\n}\nconsole.log(greet('CART3R'));\n"
        "Python" -> "def greet(name):\n    return f\"Hello, {name}\"\n\nprint(greet(\"CART3R\"))\n"
        "HTML" -> "<!doctype html>\n<html lang=\"en\">\n<head><meta charset=\"utf-8\"><title>Starter</title></head>\n<body><h1>Hello, CART3R</h1></body>\n</html>\n"
        "CSS" -> "body {\n  font-family: sans-serif;\n  margin: 2rem;\n}\n"
        else -> "{\n  \"name\": \"carter-starter\",\n  \"version\": \"1.0.0\"\n}\n"
    }

    private fun analyzeCode() {
        val code = editor.text.toString()
        val findings = mutableListOf<String>()
        if (code.isBlank()) findings.add("• No code entered.")
        if (Regex("(?i)api[_-]?key|secret[_-]?key|password\\s*=\\s*['\"]").containsMatchIn(code)) findings.add("• Possible hard-coded secret. Move credentials to secure configuration.")
        if (Regex("(?i)http://").containsMatchIn(code)) findings.add("• Plain HTTP URL found. Prefer HTTPS where supported.")
        if (Regex("(?i)eval\\s*\\(|Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder").containsMatchIn(code)) findings.add("• Dynamic execution/process launch pattern found. Review its necessity and input handling.")
        if (Regex("(?i)TODO|FIXME").containsMatchIn(code)) findings.add("• TODO/FIXME marker found.")
        if (code.lines().any { it.length > 160 }) findings.add("• At least one long line (>160 characters). Consider formatting.")
        if (findings.isEmpty()) findings.add("• No configured text-pattern warnings found. This is not proof the code is safe or correct.")
        result.text = findings.joinToString("\n")
    }

    private fun saveSnippet() {
        val content = editor.text.toString()
        if (content.isBlank()) { toast("Enter code before saving"); return }
        val dir = File(filesDir, "CART3R/snippets").apply { mkdirs() }
        val ext = when (language.selectedItem.toString()) { "Kotlin" -> "kt"; "JavaScript" -> "js"; "Python" -> "py"; "HTML" -> "html"; "CSS" -> "css"; else -> "json" }
        val file = File(dir, "snippet_${System.currentTimeMillis()}.$ext")
        file.writeText(content)
        toast("Saved privately: ${file.name}")
    }

    private fun generateProject(choice: String) {
        val safeName = "starter_${System.currentTimeMillis()}"
        val dir = File(File(filesDir, "CART3R/projects"), safeName).apply { mkdirs() }
        val files = when (choice) {
            "Kotlin Android module" -> mapOf("README.md" to "# Kotlin Android Starter\n\nAdd this module to an Android Gradle project and configure its namespace.\n", "src/main/java/MainActivity.kt" to "package starter\n\n// Starter only: integrate with your app's Activity and Gradle setup.\nclass MainActivity\n")
            "JavaScript app" -> mapOf("README.md" to "# JavaScript Starter\n", "src/index.js" to "export function greet(name) { return `Hello, ${'$'}{name}`; }\n", "package.json" to "{\n  \"name\": \"$safeName\",\n  \"version\": \"1.0.0\",\n  \"type\": \"module\"\n}\n")
            "Python utility" -> mapOf("README.md" to "# Python Starter\n", "main.py" to "def main():\n    print(\"Hello from CART3R\")\n\nif __name__ == \"__main__\":\n    main()\n")
            else -> mapOf("README.md" to "# Web Starter\n", "index.html" to "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><link rel=\"stylesheet\" href=\"styles.css\"><title>Starter</title></head><body><main><h1>Hello from CART3R</h1></main></body></html>\n", "styles.css" to "body { font-family: system-ui, sans-serif; margin: 2rem; }\n")
        }
        files.forEach { (path, content) -> File(dir, path).apply { parentFile?.mkdirs(); writeText(content) } }
        toast("Created ${files.size} files in ${dir.name}")
    }

    private fun confirmClearProjects() {
        android.app.AlertDialog.Builder(this).setTitle("Clear generated projects?")
            .setMessage("This permanently deletes files generated by CART3R in this app's private project folder.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                File(filesDir, "CART3R/projects").deleteRecursively()
                File(filesDir, "CART3R/snippets").deleteRecursively()
                toast("Local workspace cleared")
            }.show()
    }

    private fun card(title: String, subtitle: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = "$title\n$subtitle"; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = GradientDrawable().apply { setColor(0xFF160B10.toInt()); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0xFF642337.toInt()) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
            isClickable = true; isFocusable = true; setOnClickListener { action() }
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun copy(value: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CART3R.JAILBREAK", value))
        toast("Copied to clipboard")
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
