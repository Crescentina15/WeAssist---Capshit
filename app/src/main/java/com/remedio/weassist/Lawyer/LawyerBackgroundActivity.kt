package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.ChatActivity
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
                        binding.lawyerSpecialization.text = "Specialty: ${it.specialization}"
                        binding.lawyerBio.text = "Bio: ${it.bio}"
                        binding.lawyerExperience.text = "Experience: ${it.experience} years"
                        binding.lawyerLawSchool.text = "Law School: ${it.lawSchool}"
                        binding.lawyerGraduationYear.text = "Graduation Year: ${it.graduationYear}"
                        binding.lawyerCertifications.text = "Certifications: ${it.certifications}"
                        binding.lawyerJurisdiction.text = "Jurisdiction: ${it.jurisdiction}"
                        binding.lawyerRate.text = "Professional Rate: ${it.rate}"

                        // Debugging logs
                        Log.d("LawyerBackground", "Lawyer ID: $lawyerId")
                        Log.d("LawyerBackground", "Law Firm ID: ${it.lawFirm}")

                        // Retrieve and display law firm details
                        retrieveFirmDetails(it.lawFirm)
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

    private fun retrieveFirmDetails(lawFirmId: String?) {
        if (lawFirmId.isNullOrEmpty()) {
            binding.lawyerFirm.text = "Law Firm: Not Available"
            binding.lawyerLocation.text = "Location: Not Available"
            return
        }

        val firmRef = FirebaseDatabase.getInstance().getReference("firms").child(lawFirmId)
        firmRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firmName = snapshot.child("lawFirm").getValue(String::class.java) ?: "Unknown"
                    val officeAddress = snapshot.child("officeAddress").getValue(String::class.java) ?: "Unknown"

                    binding.lawyerFirm.text = "Law Firm: $firmName"
                    binding.lawyerLocation.text = "Location: $officeAddress"

                    // Debugging logs
                    Log.d("LawyerBackground", "Law Firm Name: $firmName")
                    Log.d("LawyerBackground", "Office Address: $officeAddress")
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
