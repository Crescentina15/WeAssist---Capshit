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
    private lateinit var profileSection: View
    private lateinit var profileIcon: ImageView
    private var currentProfileImageUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_dashboard)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.lawyerNav)
        lawyerNameTextView = findViewById(R.id.lawyer_name)
        profileSection = findViewById(R.id.profile_section)
        profileIcon = findViewById(R.id.profile_image)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        val imageButton: ImageButton = findViewById(R.id.notification_icon)
        imageButton.setOnClickListener {
            val intent = Intent(this, LawyerNotification::class.java)
            startActivity(intent)
        }

        loadLawyerData()

        if (intent.getBooleanExtra("SHOW_MESSAGE_FRAGMENT", false)) {
            val messageFragment = LawyerMessageFragment()
            val bundle = Bundle()
            intent.getStringExtra("CONVERSATION_ID")?.let {
                bundle.putString("CONVERSATION_ID", it)
            }
            intent.getStringExtra("CLIENT_ID")?.let {
                bundle.putString("CLIENT_ID", it)
            }
            messageFragment.arguments = bundle
            loadFragment(messageFragment)
            bottomNavigationView.selectedItemId = R.id.nav_message_lawyer
            profileSection.visibility = View.VISIBLE
        } else {
            loadFragment(LawyerAppointmentsFragment())
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_appointments_lawyer -> {
                    profileSection.visibility = View.VISIBLE
                    LawyerAppointmentsFragment()
                }
                R.id.nav_message_lawyer -> {
                    profileSection.visibility = View.VISIBLE
                    LawyerMessageFragment()
                }
                R.id.nav_history_lawyer -> {
                    profileSection.visibility = View.GONE
                    LawyerAppointmentHistory()
                }
                R.id.nav_profile_lawyer -> {
                    profileSection.visibility = View.GONE
                    LawyerProfileFragment()
                }
                else -> LawyerAppointmentsFragment()
            }
            loadFragment(selectedFragment)
            true
        }
    }

    fun loadLawyerData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            database.child(userId).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    var lawyerName = snapshot.child("name").value.toString()
                    currentProfileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                    if (!lawyerName.startsWith("Atty.")) {
                        lawyerName = "Atty. $lawyerName"
                    }

                    lawyerNameTextView.text = lawyerName

                    if (currentProfileImageUrl.isNotEmpty()) {
                        updateProfileIcon(currentProfileImageUrl)
                    }
                }
            }.addOnFailureListener {
                lawyerNameTextView.text = "Atty. Unknown Lawyer"
            }
        }
    }

    fun getCurrentProfileImageUrl(): String {
        return currentProfileImageUrl
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