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
import com.google.firebase.database.*

class Login : AppCompatActivity() {

    private lateinit var database: DatabaseReference
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

        database = FirebaseDatabase.getInstance().getReference("Users")

        // Load saved credentials if "Remember Me" was checked
        loadSavedCredentials()

        // Handle login button click
        loginButton.setOnClickListener {
            val emailOrUsername = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (emailOrUsername.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save credentials if "Remember Me" is checked
            if (rememberMeCheckbox.isChecked) {
                saveCredentials(emailOrUsername, password)
            } else {
                clearCredentials()
            }

            loginUser(emailOrUsername, password)
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

    private fun loginUser(emailOrUsername: String, password: String) {
        // Query the database for the email or username
        database.orderByChild("email").equalTo(emailOrUsername)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            val userPassword = userSnapshot.child("password").value.toString()
                            if (userPassword == password) {
                                Toast.makeText(
                                    this@Login,
                                    "Login successful!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Redirect to the main screen or dashboard
                                startActivity(Intent(this@Login, ClientFrontPage::class.java))
                                finish()
                                return
                            }
                        }
                        Toast.makeText(this@Login, "Incorrect password", Toast.LENGTH_SHORT).show()
                    } else {
                        // Check for username instead of email
                        database.orderByChild("username").equalTo(emailOrUsername)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        for (userSnapshot in snapshot.children) {
                                            val userPassword =
                                                userSnapshot.child("password").value.toString()
                                            if (userPassword == password) {
                                                Toast.makeText(
                                                    this@Login,
                                                    "Login successful!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                startActivity(
                                                    Intent(
                                                        this@Login,
                                                        ClientFrontPage::class.java
                                                    )
                                                )
                                                finish()
                                                return
                                            }
                                        }
                                        Toast.makeText(
                                            this@Login,
                                            "Incorrect password",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@Login,
                                            "Account not found",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(
                                        this@Login,
                                        "Database error: ${error.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@Login, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}