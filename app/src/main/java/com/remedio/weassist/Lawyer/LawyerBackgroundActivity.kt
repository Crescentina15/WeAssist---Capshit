package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.Secretary.SetAppointmentActivity
import com.remedio.weassist.databinding.ActivityLawyerBackgroundBinding

class LawyerBackgroundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerBackgroundBinding
    private lateinit var databaseReference: DatabaseReference
    private var lawyerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLawyerBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backArrow.setOnClickListener { finish() }

        lawyerId = intent.getStringExtra("LAWYER_ID")
        if (lawyerId.isNullOrEmpty()) {
            Toast.makeText(this, "Lawyer ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        retrieveLawyerData(lawyerId!!)

        binding.btnSetAppointment.setOnClickListener {
            val intent = Intent(this, SetAppointmentActivity::class.java)
            intent.putExtra("LAWYER_ID", lawyerId)
            startActivity(intent)
        }

        binding.btnMessage.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("LAWYER_ID", lawyerId)
            }
            startActivity(intent)
        }
    }

    private fun retrieveLawyerData(lawyerId: String) {
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawyer = snapshot.getValue(Lawyer::class.java)
                    lawyer?.let {
                        binding.lawyerName.text = it.name
                        binding.lawyerSpecialization.text = "Specialization: ${it.specialization}"
                        binding.lawyerBio.text = "Bio: ${it.bio}"
                        binding.lawyerExperience.text = "Experience: ${it.experience} years"
                        binding.lawyerLawSchool.text = "Law School: ${it.lawSchool}"
                        binding.lawyerGraduationYear.text = "Graduation Year: ${it.graduationYear}"
                        binding.lawyerCertifications.text = "Certifications: ${it.certifications}"
                        binding.lawyerJurisdiction.text = "Jurisdiction: ${it.jurisdiction}"
                        binding.lawyerRate.text = "Professional Rate: ${it.rate}"

                        // Retrieve `adminUID` from lawyer's details
                        val adminUID = snapshot.child("adminUID").getValue(String::class.java)

                        if (!adminUID.isNullOrEmpty()) {
                            Log.d("LawFirmDetails", "Fetching law firm details for adminUID: $adminUID")
                            retrieveLawFirmFromAdmin(adminUID) // ✅ Pass `adminUID` instead of `lawyerId`
                        } else {
                            Log.e("LawFirmDetails", "No adminUID found for lawyer: $lawyerId")
                            binding.lawyerFirm.text = "Law Firm: Not Available"
                            binding.lawyerLocation.text = "Location: Not Available"
                        }
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


    // ✅ Fetch law firm details using `adminUID` (Not `lawFirm` name)
    private fun retrieveLawFirmFromAdmin(adminUID: String) {
        val firmRef = FirebaseDatabase.getInstance().getReference("law_firm_admin").child(adminUID)
        firmRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firmName = snapshot.child("lawFirm").getValue(String::class.java) ?: "Unknown"
                    val officeAddress = snapshot.child("officeAddress").getValue(String::class.java) ?: "Unknown"

                    binding.lawyerFirm.text = "Law Firm: $firmName"
                    binding.lawyerLocation.text = "Location: $officeAddress"

                    Log.d("LawFirmDetails", "Law Firm: $firmName")
                    Log.d("LawFirmDetails", "Office Address: $officeAddress")
                } else {
                    binding.lawyerFirm.text = "Law Firm: Not Available"
                    binding.lawyerLocation.text = "Location: Not Available"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load firm details", Toast.LENGTH_SHORT).show()
            }
        })
    }

}
