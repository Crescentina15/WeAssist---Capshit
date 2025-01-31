package com.remedio.weassist

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

class registerSelection : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_selection)

        val userName = findViewById<EditText>(R.id.userName)
        val firstName = findViewById<EditText>(R.id.firstName)
        val lastName = findViewById<EditText>(R.id.lastName)
        val location = findViewById<EditText>(R.id.location)
        val passWord = findViewById<EditText>(R.id.passWord)
        val conPassword = findViewById<EditText>(R.id.conPassword)
        val email = findViewById<EditText>(R.id.Email)
        val number = findViewById<EditText>(R.id.number)
        val registerButton = findViewById<Button>(R.id.RegisterButton)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        registerButton.setOnClickListener {
            val username = userName.text.toString().trim()
            val firstName = firstName.text.toString().trim()
            val lastName = lastName.text.toString().trim()
            val address = location.text.toString().trim()
            val password = passWord.text.toString().trim()
            val confirmPassword = conPassword.text.toString().trim()
            val userEmail = email.text.toString().trim()
            val phoneNumber = number.text.toString().trim()

            if (username.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || address.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || userEmail.isEmpty() || phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Register user in Firebase Authentication
            auth.createUserWithEmailAndPassword(userEmail, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser: FirebaseUser? = auth.currentUser
                        firebaseUser?.let { user ->
                            user.sendEmailVerification().addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    saveUserData(user.uid, username, firstName, lastName, address, userEmail, phoneNumber)
                                    Toast.makeText(this, "Registration successful. Please verify your email.", Toast.LENGTH_LONG).show()

                                    // Redirect to Login activity
                                    val intent = Intent(this@registerSelection, Login::class.java)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun saveUserData(userId: String, username: String, firstName: String, lastName: String, address: String, email: String, phone: String) {
        val user = User(userId, username, firstName, lastName, address, email, phone)
        database.child(userId).setValue(user)
    }

    data class User(
        val userId: String,
        val username: String,
        val firstName: String,
        val lastName: String,
        val location: String,
        val email: String,
        val phone: String
    )
}
