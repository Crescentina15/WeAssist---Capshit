package com.remedio.weassist.Secretary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R
import java.util.*

class EditSecretaryProfileActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var profilePicture: ImageView
    private lateinit var changePictureButton: Button
    private val PICK_IMAGE_REQUEST = 1
    private var imageUri: Uri? = null

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
        progressBar = findViewById(R.id.progress_bar)
        profilePicture = findViewById(R.id.profile_picture)
        changePictureButton = findViewById(R.id.btn_change_picture)

        progressBar.visibility = View.GONE

        // Load Secretary Data
        auth.currentUser?.let {
            loadSecretaryData(it.uid)
        }

        // Back Button
        backButton.setOnClickListener { finish() }

        // Change Picture Button
        changePictureButton.setOnClickListener { openGallery() }

        // Save Button
        saveButton.setOnClickListener { saveProfileData() }

        // Cancel Button
        cancelButton.setOnClickListener { finish() }
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
                    val imageUrl = snapshot.child("profilePicture").getValue(String::class.java)
                    if (imageUrl != null) {
                        Glide.with(this@EditSecretaryProfileActivity).load(imageUrl).into(profilePicture)
                    }
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

        currentUser?.let { user ->
            val userId = user.uid
            val updates = mapOf("name" to newName, "email" to newEmail, "phone" to newPhone)

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

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            imageUri?.let { uploadToCloudinary(it) }
        }
    }

    private fun uploadToCloudinary(imageUri: Uri) {
        progressBar.visibility = View.VISIBLE

        val requestId = MediaManager.get().upload(imageUri)
            .option("public_id", "profile_${UUID.randomUUID()}")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    progressBar.visibility = View.GONE
                    val imageUrl = resultData["secure_url"].toString()
                    updateProfilePictureInDatabase(imageUrl)
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    progressBar.visibility = View.GONE
                    showToast("Upload failed: ${error.description}")
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    private fun updateProfilePictureInDatabase(imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return

        database.child(userId).child("profilePicture").setValue(imageUrl)
            .addOnSuccessListener {
                showToast("Profile picture updated!")
                Glide.with(this).load(imageUrl).into(profilePicture)
            }
            .addOnFailureListener { e ->
                showToast("Failed to update profile: ${e.message}")
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
