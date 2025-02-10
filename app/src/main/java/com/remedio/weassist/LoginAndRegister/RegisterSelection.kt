package com.remedio.weassist.LoginAndRegister

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R

class RegisterSelection : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_selection)

        val usernameInput = findViewById<EditText>(R.id.userName)
        val firstNameInput = findViewById<EditText>(R.id.firstName)
        val lastNameInput = findViewById<EditText>(R.id.lastName)
        val locationInput = findViewById<EditText>(R.id.location)
        val passwordInput = findViewById<EditText>(R.id.passWord)
        val confirmPasswordInput = findViewById<EditText>(R.id.conPassword)
        val emailInput = findViewById<EditText>(R.id.Email)
        val phoneInput = findViewById<EditText>(R.id.number)
        val registerButton = findViewById<Button>(R.id.RegisterButton)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        registerButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val firstName = firstNameInput.text.toString().trim()
            val lastName = lastNameInput.text.toString().trim()
            val location = locationInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()

            if (username.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || location.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || email.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create User in Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser: FirebaseUser? = auth.currentUser
                        firebaseUser?.let { user ->
                            user.sendEmailVerification()
                                .addOnCompleteListener { verificationTask ->
                                    if (verificationTask.isSuccessful) {
                                        saveUserData(user.uid, username, firstName, lastName, location, email, phone)
                                        Toast.makeText(this, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                                        auth.signOut() // Log the user out after registration
                                        startActivity(Intent(this, Login::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Failed to send verification email: ${verificationTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun saveUserData(userId: String, username: String, firstName: String, lastName: String, location: String, email: String, phone: String) {
        val user = User(userId, username, firstName, lastName, location, email, phone)

        database.child(userId).setValue(user)
            .addOnSuccessListener {
                Toast.makeText(this, "User registered successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save user data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    data class User(
        val userId: String = "",
        val username: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val location: String = "",
        val email: String = "",
        val phone: String = ""
    )
}
