package com.remedio.weassist.Clients

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class ClientEditProfileActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private lateinit var editUsername: EditText
    private lateinit var editFirstName: EditText
    private lateinit var editLastName: EditText
    private lateinit var editAddress: EditText
    private lateinit var editEmail: EditText
    private lateinit var editContactNumber: EditText
    private lateinit var btnSaveChanges: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile_client)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Initialize UI elements
        editUsername = findViewById(R.id.edit_username)
        editFirstName = findViewById(R.id.edit_first_name)
        editLastName = findViewById(R.id.edit_last_name)
        editAddress = findViewById(R.id.edit_address)
        editEmail = findViewById(R.id.edit_email)
        editContactNumber = findViewById(R.id.edit_contact_number)
        btnSaveChanges = findViewById(R.id.btn_save_changes)
        btnCancel = findViewById(R.id.btn_cancel)

        // Load user data from Firebase
        val currentUser = auth.currentUser
        if (currentUser != null) {
            loadUserData(currentUser.uid)
        }

        // Back button functionality
        findViewById<ImageButton>(R.id.back_arrow)?.setOnClickListener {
            finish()
        }

        // Save changes button click
        btnSaveChanges.setOnClickListener {
            if (currentUser != null) {
                saveChanges(currentUser.uid)
            }
        }

        // Cancel button click
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadUserData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    editUsername.setText(snapshot.child("username").getValue(String::class.java) ?: "")
                    editFirstName.setText(snapshot.child("firstName").getValue(String::class.java) ?: "")
                    editLastName.setText(snapshot.child("lastName").getValue(String::class.java) ?: "")
                    editAddress.setText(snapshot.child("location").getValue(String::class.java) ?: "")
                    editEmail.setText(snapshot.child("email").getValue(String::class.java) ?: "")
                    editContactNumber.setText(snapshot.child("phone").getValue(String::class.java) ?: "")
                } else {
                    Toast.makeText(this@ClientEditProfileActivity, "User data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ClientEditProfileActivity, "Error loading data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveChanges(userId: String) {
        val updatedUser = mapOf(
            "username" to editUsername.text.toString().trim(),
            "firstName" to editFirstName.text.toString().trim(),
            "lastName" to editLastName.text.toString().trim(),
            "location" to editAddress.text.toString().trim(),
            "email" to editEmail.text.toString().trim(),
            "phone" to editContactNumber.text.toString().trim()
        )

        database.child(userId).updateChildren(updatedUser)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish() // Close activity after saving
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
