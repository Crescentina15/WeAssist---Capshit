package com.remedio.weassist.Clients

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class ClientEditProfileActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var profileImageView: ImageView
    private lateinit var btnUploadImage: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile_client)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        profileImageView = findViewById(R.id.profile_picture)
        btnUploadImage = findViewById(R.id.btn_change_picture)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            fetchProfileImage(userId) // Load existing profile image
        }

        btnUploadImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val userId = auth.currentUser?.uid ?: return@let
            uploadImageToCloudinary(it, userId)
        }
    }

    private fun fetchProfileImage(userId: String) {
        database.child(userId).child("profileImageUrl").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val imageUrl = snapshot.getValue(String::class.java)
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this@ClientEditProfileActivity).load(imageUrl).into(profileImageView)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ClientEditProfileActivity, "Failed to load profile image", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun uploadImageToCloudinary(imageUri: Uri, userId: String) {
        MediaManager.get().upload(imageUri)
            .option("folder", "profile_images")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val imageUrl = resultData?.get("secure_url").toString()
                    saveImageUrlToFirebase(userId, imageUrl)
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Toast.makeText(this@ClientEditProfileActivity, "Upload failed: ${error?.description}", Toast.LENGTH_SHORT).show()
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveImageUrlToFirebase(userId: String, imageUrl: String) {
        database.child(userId).child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Glide.with(this@ClientEditProfileActivity).load(imageUrl).into(profileImageView)
                Toast.makeText(this, "Profile image updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update image: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
