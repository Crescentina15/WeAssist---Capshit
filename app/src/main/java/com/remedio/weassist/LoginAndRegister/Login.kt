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
import com.remedio.weassist.Lawyer.LawyersDashboardActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.SecretaryDashboardActivity
import com.remedio.weassist.Secretary.SecretaryFrontPage

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

        // Load saved credentials if Remember Me was checked
        loadLoginDetails()

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
            startActivity(Intent(this, RegisterSelection::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        if (rememberMeCheckbox.isChecked) {
                            saveLoginDetails(email, password)
                        } else {
                            clearLoginDetails()
                        }
                        fetchAndRedirectUser(user.uid)
                    } else {
                        Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fetchAndRedirectUser(uid: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(uid)
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries").child(uid)
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(uid)

        userRef.get().addOnSuccessListener { userSnapshot ->
            if (userSnapshot.exists()) {
                val role = userSnapshot.child("role").value.toString()
                saveUserRole(role)

                // Redirect based on role
                val intent = when (role) {
                    "secretary" -> Intent(this, SecretaryFrontPage::class.java)
                    "lawyer" -> Intent(this, LawyersDashboardActivity::class.java)
                    else -> Intent(this, ClientFrontPage::class.java)
                }

                startActivity(intent)
                finish()
            } else {
                // Check if the user is a secretary
                secretaryRef.get().addOnSuccessListener { secSnapshot ->
                    if (secSnapshot.exists()) {
                        saveUserRole("secretary")
                        startActivity(Intent(this, SecretaryDashboardActivity::class.java))
                        finish()
                    } else {
                        // Check if the user is a lawyer
                        lawyerRef.get().addOnSuccessListener { lawyerSnapshot ->
                            if (lawyerSnapshot.exists()) {
                                saveUserRole("lawyer")
                                startActivity(Intent(this, LawyersDashboardActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show()
                                auth.signOut()
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveUserRole(role: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("role", role)
        editor.apply()
    }

    private fun saveLoginDetails(email: String, password: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("email", email)
        editor.putString("password", password)
        editor.putBoolean("rememberMe", true)
        editor.apply()
    }

    private fun loadLoginDetails() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEmail = sharedPreferences.getString("email", "")
        val savedPassword = sharedPreferences.getString("password", "")
        val isRemembered = sharedPreferences.getBoolean("rememberMe", false)

        if (isRemembered) {
            emailInput.setText(savedEmail)
            passwordInput.setText(savedPassword)
            rememberMeCheckbox.isChecked = true
        }
    }

    private fun clearLoginDetails() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("email")
        editor.remove("password")
        editor.putBoolean("rememberMe", false)
        editor.apply()
    }
}
