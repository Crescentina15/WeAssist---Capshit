package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.Clients.ClientMessageFragment
import com.remedio.weassist.Secretary.SetAppointmentActivity
import com.remedio.weassist.databinding.ActivityLawyerBackgroundBinding

class LawyerBackgroundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerBackgroundBinding
    private lateinit var databaseReference: DatabaseReference
    private var lawyerId: String? = null
    private var secretaryId: String? = null // Store secretary ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLawyerBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backArrow.setOnClickListener {
            finish()
        }

        lawyerId = intent.getStringExtra("LAWYER_ID")

        if (lawyerId.isNullOrEmpty()) {
            Toast.makeText(this, "Lawyer ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        retrieveLawyerData(lawyerId!!)
    }

    private fun retrieveLawyerData(lawyerId: String) {
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawyer = snapshot.getValue(Lawyer::class.java) // Ensure it matches the updated class
                    lawyer?.let {
                        binding.lawyerName.text = it.name
                        binding.lawyerBio.text = formatText("Bio", it.bio)
                        binding.lawyerExperience.text = formatText("Experience", it.experience)
                        binding.lawyerLawSchool.text = formatText("Law School", it.lawSchool)
                        binding.lawyerGraduationYear.text = formatText("Graduation Year", it.graduationYear)
                        binding.lawyerCertifications.text = formatText("Certifications", it.certifications)
                        binding.lawyerJurisdiction.text = formatText("Jurisdiction", it.jurisdiction)
                        binding.lawyerEmployer.text = formatText("Employer", it.employer)
                        binding.lawyerRate.text = formatText("Rate", it.rate)

                        if (!it.secretaryId.isNullOrEmpty()) {
                            secretaryId = it.secretaryId
                        } else {
                            assignSecretaryToLawyer(lawyerId!!, it.lawFirm)
                        }

                        setupButtonListeners()
                    }
                } else {
                    Toast.makeText(applicationContext, "Lawyer data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load lawyer data", Toast.LENGTH_SHORT).show()
            }
        })


    }

    private fun assignSecretaryToLawyer(lawyerId: String, lawFirm: String) {
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries")

        secretaryRef.orderByChild("lawFirm").equalTo(lawFirm)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (secSnapshot in snapshot.children) {
                            secretaryId = secSnapshot.key // Get the secretary's ID

                            val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)
                            lawyerRef.child("secretaryId").setValue(secretaryId)
                                .addOnSuccessListener {
                                    Toast.makeText(applicationContext, "Secretary assigned!", Toast.LENGTH_SHORT).show()
                                    setupButtonListeners() // Update UI
                                }
                                .addOnFailureListener {
                                    Toast.makeText(applicationContext, "Failed to assign secretary", Toast.LENGTH_SHORT).show()
                                }
                            break
                        }
                    } else {
                        Toast.makeText(applicationContext, "No secretary found for this law firm", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(applicationContext, "Error fetching secretaries", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupButtonListeners() {
        binding.btnSetAppointment.setOnClickListener {
            val intent = Intent(this, SetAppointmentActivity::class.java)
            intent.putExtra("LAWYER_ID", lawyerId)
            startActivity(intent)
        }

        binding.btnMessage.setOnClickListener {
            if (secretaryId.isNullOrEmpty()) {
                Toast.makeText(this, "No secretary available for this lawyer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fragment = ClientMessageFragment().apply {
                arguments = Bundle().apply {
                    putString("SECRETARY_ID", secretaryId) // Send the secretary ID
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun formatText(label: String, value: String?): CharSequence {
        return android.text.Html.fromHtml("<b>$label:</b> ${value ?: "Not available"}")
    }
}
