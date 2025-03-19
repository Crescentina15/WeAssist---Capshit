package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class LawyersDashboardActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var lawyerNameTextView: TextView
    private lateinit var profileSection: View // Reference to the profile header section
    private lateinit var profileIcon: ImageView // Profile icon in the header

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_dashboard)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.lawyerNav)
        lawyerNameTextView = findViewById(R.id.lawyer_name) // Reference to TextView
        profileSection = findViewById(R.id.profile_section) // Reference to profile header
        profileIcon = findViewById(R.id.profile_icon) // Reference to profile image icon

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        val imageButton: ImageButton = findViewById(R.id.notification_icon)
        imageButton.setOnClickListener {
            val intent = Intent(this, LawyerNotification::class.java)
            startActivity(intent)
        }

        // Load lawyer's data including name and profile image
        loadLawyerData()

        // Set default fragment
        loadFragment(LawyerAppointmentsFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_appointments_lawyer -> {
                    profileSection.visibility = View.VISIBLE // Show header for Appointments
                    LawyerAppointmentsFragment()
                }
                R.id.nav_message_lawyer -> {
                    profileSection.visibility = View.VISIBLE // Show header for Messages
                    LawyerMessageFragment()
                }
                R.id.nav_history_lawyer -> {
                    profileSection.visibility = View.GONE // Hide header for History
                    LawyerAppointmentHistory()
                }
                R.id.nav_profile_lawyer -> {
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
                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                    // Ensure "Atty." is always prefixed
                    if (!lawyerName.startsWith("Atty.")) {
                        lawyerName = "Atty. $lawyerName"
                    }

                    lawyerNameTextView.text = lawyerName

                    // Load profile image into the profile icon
                    if (profileImageUrl.isNotEmpty()) {
                        updateProfileIcon(profileImageUrl)
                    }
                }
            }.addOnFailureListener {
                lawyerNameTextView.text = "Atty. Unknown Lawyer"
            }
        }
    }

    fun updateProfileIcon(profileImageUrl: String) {
        Glide.with(this).load(profileImageUrl).into(profileIcon)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flFragment, fragment)
            .commit()
    }
}
