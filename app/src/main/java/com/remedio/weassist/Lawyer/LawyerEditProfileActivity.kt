package com.remedio.weassist.Lawyer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

class LawyerEditProfileActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private lateinit var editName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPhone: EditText
    private lateinit var editSpecialization: EditText
    private lateinit var editExperience: EditText
    private lateinit var editLawFirm: EditText
    private lateinit var editLicenseNumber: EditText
    private lateinit var btnSaveChanges: Button
    private lateinit var btnCancel: Button

    private lateinit var profileImage: ImageView
    private lateinit var btnChangeProfileImage: Button
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_edit_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        // Initialize UI elements
        editName = findViewById(R.id.edit_lawyer_name)
        editEmail = findViewById(R.id.edit_lawyer_email)
        editPhone = findViewById(R.id.edit_lawyer_phone)
        editSpecialization = findViewById(R.id.edit_lawyer_specialization)
        editExperience = findViewById(R.id.edit_lawyer_experience)
        editLawFirm = findViewById(R.id.edit_lawyer_law_firm)
        editLicenseNumber = findViewById(R.id.edit_lawyer_license)
        btnSaveChanges = findViewById(R.id.btn_save_lawyer_profile)
        btnCancel = findViewById(R.id.btn_cancel_lawyer_profile)

        profileImage = findViewById(R.id.lawyer_profile_image)
        btnChangeProfileImage = findViewById(R.id.btn_change_profile_image)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            loadLawyerData(currentUser.uid)
        }

        // Back button functionality
        findViewById<ImageButton>(R.id.back_arrow)?.setOnClickListener {
            finish()
        }

        // Change profile picture button
        btnChangeProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 100)
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

    private fun loadLawyerData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    editName.setText(snapshot.child("name").getValue(String::class.java) ?: "")
                    editEmail.setText(snapshot.child("email").getValue(String::class.java) ?: "")
                    editPhone.setText(snapshot.child("phone").getValue(String::class.java) ?: "")
                    editSpecialization.setText(snapshot.child("specialization").getValue(String::class.java) ?: "")
                    editExperience.setText(snapshot.child("experience").getValue(String::class.java) ?: "")
                    editLawFirm.setText(snapshot.child("lawFirm").getValue(String::class.java) ?: "")
                    editLicenseNumber.setText(snapshot.child("licenseNumber").getValue(String::class.java) ?: "")

                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this@LawyerEditProfileActivity).load(profileImageUrl).into(profileImage)
                    }
                } else {
                    Toast.makeText(this@LawyerEditProfileActivity, "Lawyer data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@LawyerEditProfileActivity, "Error loading data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveChanges(userId: String) {
        val updatedLawyer = mapOf(
            "name" to editName.text.toString().trim(),
            "email" to editEmail.text.toString().trim(),
            "phone" to editPhone.text.toString().trim(),
            "specialization" to editSpecialization.text.toString().trim(),
            "experience" to editExperience.text.toString().trim(),
            "lawFirm" to editLawFirm.text.toString().trim(),
            "licenseNumber" to editLicenseNumber.text.toString().trim()
        )

        database.child(userId).updateChildren(updatedLawyer)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish() // Close activity after saving
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            profileImage.setImageURI(imageUri) // Show selected image
            uploadImageToCloudinary(imageUri)
        }
    }

    private fun uploadImageToCloudinary(imageUri: Uri?) {
        imageUri?.let { uri ->
            val uniqueFileName = "lawyer_profile_" + UUID.randomUUID().toString()

            MediaManager.get().upload(uri)
                .option("public_id", uniqueFileName)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<*, *>?) {
                        val imageUrl = resultData?.get("secure_url").toString()
                        saveImageUrlToFirebase(imageUrl)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Toast.makeText(this@LawyerEditProfileActivity, "Upload failed: ${error?.description}", Toast.LENGTH_SHORT).show()
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                        Toast.makeText(this@LawyerEditProfileActivity, "Upload rescheduled", Toast.LENGTH_SHORT).show()
                    }
                }).dispatch()
        }
    }

    private fun saveImageUrlToFirebase(imageUrl: String) {
        val userId = auth.currentUser?.uid ?: return
        database.child(userId).child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update picture", Toast.LENGTH_SHORT).show()
            }
    }
}
