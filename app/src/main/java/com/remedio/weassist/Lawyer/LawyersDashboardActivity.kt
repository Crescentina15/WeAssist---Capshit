package com.remedio.weassist.Lawyer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class LawyersDashboardActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var lawyerNameTextView: TextView
    private lateinit var profileSection: View // Reference to the profile header section

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_dashboard)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.lawyerNav)
        lawyerNameTextView = findViewById(R.id.lawyer_name) // Reference to TextView
        profileSection = findViewById(R.id.profile_section) // Reference to profile header

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        // Load lawyer's name
        loadLawyerData()

        // Set default fragment
        loadFragment(LawyerAppointmentsFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_appointments -> {
                    profileSection.visibility = View.VISIBLE // Show header for Appointments
                    LawyerAppointmentsFragment()
                }
                R.id.nav_profile -> {
                    profileSection.visibility = View.GONE // Hide header for Profile
                    LawyerProfileFragment()
                }
                else -> LawyerAppointmentsFragment()
            }
            loadFragment(selectedFragment)
            true
        }
    }

    private fun loadLawyerData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            database.child(userId).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    var lawyerName = snapshot.child("name").value.toString()

                    // Ensure "Atty." is always prefixed
                    if (!lawyerName.startsWith("Atty.")) {
                        lawyerName = "Atty. $lawyerName"
                    }

                    lawyerNameTextView.text = lawyerName
                }
            }.addOnFailureListener {
                lawyerNameTextView.text = "Atty. Unknown Lawyer"
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flFragment, fragment)
            .commit()
    }
}
