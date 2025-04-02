package com.remedio.weassist.Secretary

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.Lawyer.Lawyer
import com.remedio.weassist.R
import com.squareup.picasso.Picasso

class AddBackgroundActivity : AppCompatActivity() {

    private lateinit var databaseReference: DatabaseReference
    private lateinit var lawyerProfile: ImageView
    private lateinit var lawyerName: TextView
    private lateinit var lawSchool: EditText
    private lateinit var graduationYear: EditText
    private lateinit var certifications: EditText
    private lateinit var licenseNumber: EditText
    private lateinit var jurisdiction: EditText
    private lateinit var experience: EditText
    private lateinit var employer: EditText
    private lateinit var bio: EditText
    private lateinit var rate: EditText
    private lateinit var submitButton: Button

    private var lawyerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_background)

        // Initialize Firebase reference
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers")

        // Get Views
        lawyerProfile = findViewById(R.id.lawyersProfile)
        lawyerName = findViewById(R.id.lawyerName)
        lawSchool = findViewById(R.id.lawSchool)
        graduationYear = findViewById(R.id.graduationYear)
        certifications = findViewById(R.id.certifications)
        licenseNumber = findViewById(R.id.licenseNumber)
        jurisdiction = findViewById(R.id.jurisdiction)
        experience = findViewById(R.id.experience)
        employer = findViewById(R.id.employer)
        bio = findViewById(R.id.bio)
        rate = findViewById(R.id.rate)
        submitButton = findViewById(R.id.submitButton)

        // Get lawyer ID from Intent
        lawyerId = intent.getStringExtra("LAWYER_ID")

        if (lawyerId != null) {
            retrieveLawyerData(lawyerId!!)
        }

        submitButton.setOnClickListener {
            saveLawyerBackground()
        }
    }

    private fun retrieveLawyerData(lawyerId: String) {
        databaseReference.child(lawyerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val lawyer = snapshot.getValue(Lawyer::class.java)
                        lawyer?.let {
                            lawyerName.text = it.name
                            lawSchool.setText(it.lawSchool ?: "")
                            graduationYear.setText(it.graduationYear ?: "")
                            certifications.setText(it.certifications ?: "")
                            licenseNumber.setText(it.licenseNumber ?: "")
                            jurisdiction.setText(it.jurisdiction ?: "")
                            experience.setText(it.experience ?: "")
                            employer.setText(it.employer ?: "")
                            bio.setText(it.bio ?: "")
                            rate.setText(it.rate ?: "")

                            // Load profile image if available - updated to use profileImageUrl
                            it.profileImageUrl?.let { imageUrl ->
                                if (imageUrl.isNotEmpty()) {
                                    Picasso.get()
                                        .load(imageUrl)
                                        .placeholder(R.drawable.account_circle_24) // fallback image
                                        .error(R.drawable.account_circle_24) // error image
                                        .into(lawyerProfile)
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        applicationContext,
                        "Failed to load lawyer data.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun saveLawyerBackground() {
        if (lawyerId.isNullOrEmpty()) {
            Toast.makeText(this, "Lawyer ID not found!", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "lawSchool" to lawSchool.text.toString(),
            "graduationYear" to graduationYear.text.toString(),
            "certifications" to certifications.text.toString(),
            "licenseNumber" to licenseNumber.text.toString(),
            "jurisdiction" to jurisdiction.text.toString(),
            "experience" to experience.text.toString(),
            "employer" to employer.text.toString(),
            "bio" to bio.text.toString(),
            "rate" to rate.text.toString()
        )

        databaseReference.child(lawyerId!!).updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Background saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Failed to save background.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}