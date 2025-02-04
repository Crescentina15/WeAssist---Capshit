package com.remedio.weassist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var editProfileButton: LinearLayout
    private lateinit var securityButton: LinearLayout
    private lateinit var privacyButton: LinearLayout
    private lateinit var reportProblemButton: LinearLayout
    private lateinit var logoutButton: LinearLayout

    private val PREFS_NAME = "LoginPrefs"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Initialize UI elements
        usernameTextView = view.findViewById(R.id.headername)
        emailTextView = view.findViewById(R.id.headerprofile)
        editProfileButton = view.findViewById(R.id.edit_profile)
        securityButton = view.findViewById(R.id.security)
        privacyButton = view.findViewById(R.id.privacy)
        reportProblemButton = view.findViewById(R.id.report_problem)
        logoutButton = view.findViewById(R.id.log_out)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fetch user data after the fragment is attached
        val currentUser = auth.currentUser
        if (currentUser != null) {
            fetchUserData(currentUser.uid)
        } else {
            showToast("User not logged in")
        }

        // Set button click listeners safely
        editProfileButton.setOnClickListener { openActivity(EditProfileActivity::class.java) }
        securityButton.setOnClickListener { openActivity(SecurityActivity::class.java) }
        privacyButton.setOnClickListener { openActivity(PrivacyActivity::class.java) }
        reportProblemButton.setOnClickListener { openActivity(ReportActivity::class.java) }
        logoutButton.setOnClickListener { logoutUser() }
    }

    private fun fetchUserData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return // Prevent crash if fragment is detached

                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: "N/A"
                    val email = snapshot.child("email").getValue(String::class.java) ?: "N/A"

                    // Update UI
                    usernameTextView.text = firstName
                    emailTextView.text = email
                } else {
                    showToast("User data not found!")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    showToast("Database error: ${error.message}")
                }
            }
        })
    }

    private fun logoutUser() {
        if (!isAdded) return // Prevent crash if fragment is detached

        // Clear stored login credentials
        val sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        auth.signOut()
        showToast("You have been logged out")

        // Redirect to Login screen
        startActivity(Intent(requireActivity(), Login::class.java))
        requireActivity().finish()
    }

    private fun openActivity(activityClass: Class<*>) {
        if (isAdded) {
            startActivity(Intent(requireActivity(), activityClass))
        }
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
