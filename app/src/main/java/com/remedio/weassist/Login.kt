package com.remedio.weassist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var rememberMeCheckbox: CheckBox

    // SharedPreferences keys
    private val PREFS_NAME = "LoginPrefs"
    private val KEY_EMAIL = "email"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER = "rememberMe"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signUpText = findViewById<TextView>(R.id.signUpText)

        auth = FirebaseAuth.getInstance()

        // Load saved credentials if "Remember Me" was checked
        loadSavedCredentials()

        // Handle login button click
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save credentials if "Remember Me" is checked
            if (rememberMeCheckbox.isChecked) {
                saveCredentials(email, password)
            } else {
                clearCredentials()
            }

            loginUser(email, password)
        }

        // Handle sign-up text click
        signUpText.setOnClickListener {
            val intent = Intent(this, registerSelection::class.java)
            startActivity(intent)
        }
    }

    private fun saveCredentials(email: String, password: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_PASSWORD, password)
        editor.putBoolean(KEY_REMEMBER, true)
        editor.apply()
    }

    private fun clearCredentials() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }

    private fun loadSavedCredentials() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEmail = sharedPreferences.getString(KEY_EMAIL, "")
        val savedPassword = sharedPreferences.getString(KEY_PASSWORD, "")
        val rememberMe = sharedPreferences.getBoolean(KEY_REMEMBER, false)

        if (rememberMe) {
            emailInput.setText(savedEmail)
            passwordInput.setText(savedPassword)
            rememberMeCheckbox.isChecked = true
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user: FirebaseUser? = auth.currentUser
                    if (user != null) {
                        if (user.isEmailVerified) {
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, ClientFrontPage::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
