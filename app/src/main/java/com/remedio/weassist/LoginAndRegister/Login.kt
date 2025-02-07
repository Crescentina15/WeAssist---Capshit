package com.remedio.weassist.LoginAndRegister

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
import com.google.firebase.database.*
import com.remedio.weassist.Clients.ClientFrontPage
import com.remedio.weassist.R

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var rememberMeCheckbox: CheckBox

    private val PREFS_NAME = "LoginPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signUpText = findViewById<TextView>(R.id.signUpText)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        signUpText.setOnClickListener {
            startActivity(Intent(this, registerSelection::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        fetchAndSaveUserDetails(user.uid)
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, ClientFrontPage::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fetchAndSaveUserDetails(uid: String) {
        val user = auth.currentUser
        if (user != null) {
            database.child(user.uid).get()
                .addOnSuccessListener {
                    if (it.exists()) {
                        val email = it.child("email").value.toString()
                        val username = it.child("username").value.toString()

                        val sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putString("email", email)
                        editor.putString("username", username)
                        editor.apply()
                    } else {
                        Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

}
