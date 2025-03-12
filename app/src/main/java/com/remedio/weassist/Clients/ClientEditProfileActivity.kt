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
    private lateinit var btnSaveChanges: Button
    private lateinit var btnCancel: Button
    private lateinit var editUsername: EditText
    private lateinit var editFirstName: EditText
    private lateinit var editLastName: EditText
    private lateinit var editAddress: EditText
    private lateinit var editEmail: EditText
    private lateinit var editContactNumber: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile_client)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        profileImageView = findViewById(R.id.profile_picture)
        btnUploadImage = findViewById(R.id.btn_change_picture)
        btnSaveChanges = findViewById(R.id.btn_save_changes)
        btnCancel = findViewById(R.id.btn_cancel)

        editUsername = findViewById(R.id.edit_username)
        editFirstName = findViewById(R.id.edit_first_name)
        editLastName = findViewById(R.id.edit_last_name)
        editAddress = findViewById(R.id.edit_address)
        editEmail = findViewById(R.id.edit_email)
        editContactNumber = findViewById(R.id.edit_contact_number)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            fetchProfileData(userId)
        }

        btnUploadImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSaveChanges.setOnClickListener {
            userId?.let { saveChanges(it) }
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun fetchProfileData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    editUsername.setText(snapshot.child("username").getValue(String::class.java))
                    editFirstName.setText(snapshot.child("firstName").getValue(String::class.java))
                    editLastName.setText(snapshot.child("lastName").getValue(String::class.java))
                    editAddress.setText(snapshot.child("address").getValue(String::class.java))
                    editEmail.setText(snapshot.child("email").getValue(String::class.java))
                    editContactNumber.setText(snapshot.child("contactNumber").getValue(String::class.java))

                    val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@ClientEditProfileActivity).load(imageUrl).into(profileImageView)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ClientEditProfileActivity, "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveChanges(userId: String) {
        val updates = mapOf(
            "username" to editUsername.text.toString().trim(),
            "firstName" to editFirstName.text.toString().trim(),
            "lastName" to editLastName.text.toString().trim(),
            "address" to editAddress.text.toString().trim(),
            "email" to editEmail.text.toString().trim(),
            "contactNumber" to editContactNumber.text.toString().trim()
        )

        database.child(userId).updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val userId = auth.currentUser?.uid ?: return@let
            uploadImageToCloudinary(it, userId)
        }
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
