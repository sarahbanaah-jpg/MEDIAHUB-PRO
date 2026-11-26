package com.mediahub.rebuilt.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.mediahub.rebuilt.ui.MainActivity

class LoginActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var name: EditText
    private lateinit var status: TextView
    private var createMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser != null) { enterApp(); return }
        buildScreen()
    }

    private fun buildScreen() {
        val bg = Color.rgb(8, 8, 12)
        val white = Color.WHITE
        val muted = Color.rgb(165, 160, 178)
        val accent = Color.rgb(255, 91, 137)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(28))
            setBackgroundColor(bg)
        }
        val brand = TextView(this).apply {
            text = "MEDIAHUB"; textSize = 30f; setTextColor(white)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val pro = TextView(this).apply {
            text = "PRO"; textSize = 13f; letterSpacing = .32f
            setTextColor(accent); gravity = Gravity.CENTER
        }
        val heading = TextView(this).apply {
            text = "Welcome back"; textSize = 23f; setTextColor(white)
            gravity = Gravity.CENTER; setPadding(0, dp(34), 0, dp(6))
        }
        val sub = TextView(this).apply {
            text = "Sign in or create an account to continue"; textSize = 14f
            setTextColor(muted); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(22))
        }
        name = field("Full name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        email = field("Email address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        status = TextView(this).apply { setTextColor(accent); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) }
        val action = Button(this).apply {
            text = "SIGN IN"; isAllCaps = false; textSize = 16f
            setTextColor(white); setBackgroundColor(accent)
            setOnClickListener { submit() }
        }
        val toggle = TextView(this).apply {
            text = "New here? Create an account"; textSize = 14f; setTextColor(white)
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(12))
            setOnClickListener { createMode = !createMode; name.visibility = if (createMode) View.VISIBLE else View.GONE
                heading.text = if (createMode) "Create your account" else "Welcome back"
                action.text = if (createMode) "CREATE ACCOUNT" else "SIGN IN"
                text = if (createMode) "Already registered? Sign in" else "New here? Create an account"
                status.text = ""
            }
        }
        val forgot = TextView(this).apply {
            text = "Forgot password?"; textSize = 14f; setTextColor(muted); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(12)); setOnClickListener { resetPassword() }
        }
        root.addView(brand); root.addView(pro); root.addView(heading); root.addView(sub)
        root.addView(name, matchWrap()).also { name.visibility = View.GONE }
        root.addView(email, matchWrap()); root.addView(password, matchWrap())
        root.addView(status, matchWrap()); root.addView(action, matchWrap())
        root.addView(toggle, matchWrap()); root.addView(forgot, matchWrap())
        setContentView(root)
    }

    private fun field(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText; inputType = type; textSize = 15f; setSingleLine(true)
        setTextColor(Color.WHITE); setHintTextColor(Color.rgb(145, 140, 155))
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(28, 26, 34)); cornerRadius = dp(13).toFloat()
            setStroke(dp(1), Color.rgb(65, 59, 72))
        }
        val lp = LinearLayout.LayoutParams(-1, dp(54)); lp.bottomMargin = dp(12); layoutParams = lp
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun submit() {
        val mail = email.text.toString().trim()
        val pass = password.text.toString()
        if (mail.isBlank() || pass.length < 6 || (createMode && name.text.toString().isBlank())) {
            status.text = if (createMode) "Enter your name, email and a password of at least 6 characters." else "Enter a valid email and password (at least 6 characters)."
            return
        }
        setBusy(true)
        val task = if (createMode) auth.createUserWithEmailAndPassword(mail, pass)
            else auth.signInWithEmailAndPassword(mail, pass)
        task.addOnCompleteListener { result ->
            if (!result.isSuccessful) {
                setBusy(false)
                status.text = result.exception?.localizedMessage ?: "Authentication failed. Please try again."
                return@addOnCompleteListener
            }
            val user = result.result.user
            if (createMode && user != null) {
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.text.toString().trim())
                    .build()
                user.updateProfile(profile).addOnCompleteListener {
                    user.sendEmailVerification().addOnCompleteListener {
                        setBusy(false)
                        status.text = if (it.isSuccessful) "Verification email sent. You can continue, but verify your email to enable owner administration." else "Account created. You can resend email verification later."
                        enterApp()
                    }
                }
            } else {
                setBusy(false)
                enterApp()
            }
        }
    }

    private fun resetPassword() {
        val mail = email.text.toString().trim()
        if (mail.isBlank()) { status.text = "Enter your email address first."; return }
        setBusy(true)
        auth.sendPasswordResetEmail(mail).addOnCompleteListener {
            setBusy(false)
            status.text = if (it.isSuccessful) "Password reset email sent." else (it.exception?.localizedMessage ?: "Could not send reset email.")
        }
    }

    private fun setBusy(busy: Boolean) {
        status.text = if (busy) "Please wait…" else status.text
    }

    private fun enterApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
