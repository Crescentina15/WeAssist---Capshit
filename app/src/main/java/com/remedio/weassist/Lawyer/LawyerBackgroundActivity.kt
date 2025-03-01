package com.remedio.weassist.Lawyer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R
import com.remedio.weassist.databinding.ActivityLawyerBackgroundBinding

class LawyerBackgroundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerBackgroundBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivityLawyerBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the lawyer's details from the intent
        val lawyer = intent.getParcelableExtra<Lawyer>("LAWYER")

        // Populate the views with the lawyer's details
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
    }
}
