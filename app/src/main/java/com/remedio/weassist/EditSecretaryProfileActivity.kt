package com.remedio.weassist

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class EditSecretaryProfileActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var progressBar: ProgressBar  // Ensure it's properly initialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_secretary_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("secretaries")

        // Initialize UI elements
        backButton = findViewById(R.id.back_arrow)
        nameEditText = findViewById(R.id.edit_name)
        emailEditText = findViewById(R.id.edit_email)
        phoneEditText = findViewById(R.id.edit_phone)
        saveButton = findViewById(R.id.btn_save_changes)
        cancelButton = findViewById(R.id.btn_cancel)
        progressBar = findViewById(R.id.progress_bar) // Ensure it's initialized

        progressBar.visibility = View.GONE  // Hide initially

        // Load Secretary Data
        val currentUser = auth.currentUser
        currentUser?.let {
            loadSecretaryData(it.uid)
        }

        // Back Button Click: Close Activity
        backButton.setOnClickListener {
            finish()
        }

        // Save Button Click: Update Profile
        saveButton.setOnClickListener {
            saveProfileData()
        }

        // Cancel Button Click: Close Activity
        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun loadSecretaryData(userId: String) {
        progressBar.visibility = View.VISIBLE
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE
                if (snapshot.exists()) {
                    nameEditText.setText(snapshot.child("name").getValue(String::class.java) ?: "")
                    emailEditText.setText(snapshot.child("email").getValue(String::class.java) ?: "")
                    phoneEditText.setText(snapshot.child("phone").getValue(String::class.java) ?: "")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                showToast("Database error: ${error.message}")
            }
        })
    }

    private fun saveProfileData() {
        val newName = nameEditText.text.toString().trim()
        val newEmail = emailEditText.text.toString().trim()
        val newPhone = phoneEditText.text.toString().trim()
        val currentUser = auth.currentUser

        if (newName.isEmpty() || newEmail.isEmpty() || newPhone.isEmpty()) {
            showToast("All fields are required")
            return
        }

        if (currentUser != null) {
            val userId = currentUser.uid
            val updates = mapOf(
                "name" to newName,
                "email" to newEmail,
                "phone" to newPhone
            )

            progressBar.visibility = View.VISIBLE
            database.child(userId).updateChildren(updates)
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    showToast("Profile updated successfully")
                    finish()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    showToast("Failed to update profile: ${e.message}")
                }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
