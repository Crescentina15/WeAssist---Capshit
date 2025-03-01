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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLawyerBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            val fragment = ClientMessageFragment().apply {
                arguments = Bundle().apply {
                    putString("LAWYER_ID", lawyerId)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
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
                        binding.lawyerBio.text = it.bio ?: "No bio available"
                        binding.lawyerExperience.text = it.experience ?: "No experience available"
                        binding.lawyerLawSchool.text = it.lawSchool ?: "No law school available"
                        binding.lawyerGraduationYear.text = it.graduationYear ?: "No graduation year available"
                        binding.lawyerCertifications.text = it.certifications ?: "No certifications available"
                        binding.lawyerJurisdiction.text = it.jurisdiction ?: "No jurisdiction available"
                        binding.lawyerEmployer.text = it.employer ?: "No employer available"
                        binding.lawyerRate.text = it.rate ?: "No rate available"
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
}