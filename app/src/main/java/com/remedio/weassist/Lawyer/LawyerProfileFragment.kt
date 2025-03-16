package com.remedio.weassist.Lawyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.LoginAndRegister.Login
import com.remedio.weassist.Miscellaneous.PrivacyActivity
import com.remedio.weassist.Miscellaneous.SecurityActivity
import com.remedio.weassist.R

class LawyerProfileFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var usernameTextView: TextView
    private lateinit var editProfileButton: LinearLayout
    private lateinit var securityButton: LinearLayout
    private lateinit var privacyButton: LinearLayout
    private lateinit var logoutButton: LinearLayout
    private lateinit var lawyerProfileImage: ImageView
    private val PREFS_NAME = "LoginPrefs"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_lawyer_profile, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        // Initialize UI elements
        usernameTextView = view.findViewById(R.id.lawyer_name)
        lawyerProfileImage = view.findViewById(R.id.profile_image)
        editProfileButton = view.findViewById(R.id.lawyer_edit_profile)
        securityButton = view.findViewById(R.id.lawyer_security)
        privacyButton = view.findViewById(R.id.lawyer_privacy)
        logoutButton = view.findViewById(R.id.lawyer_log_out)

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
        editProfileButton.setOnClickListener { openActivity(LawyerEditProfileActivity::class.java) }
        securityButton.setOnClickListener { openActivity(SecurityActivity::class.java) }
        privacyButton.setOnClickListener { openActivity(PrivacyActivity::class.java) }
        logoutButton.setOnClickListener { logoutUser() }
    }

    override fun onResume() {
        super.onResume()
        auth.currentUser?.uid?.let { fetchUserData(it) }
    }

    private fun fetchUserData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return // Prevent crash if fragment is detached

                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "N/A"
                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                    // Update UI
                    usernameTextView.text = name

                    // Load profile image
                    if (profileImageUrl.isNotEmpty()) {
                        Glide.with(requireContext()).load(profileImageUrl).into(lawyerProfileImage)
                    }
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
