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
    private lateinit var logoutButton: LinearLayout
    private lateinit var lawyerProfileImage: ImageView
    private var profileSection: View? = null
    private val PREFS_NAME = "LoginPrefs"
    private var isNavigatingToAnotherActivity = false
    private var valueEventListener: ValueEventListener? = null
    private var imageUrlListener: ValueEventListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is LawyersDashboardActivity) {
            profileSection = context.findViewById(R.id.profile_section)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_lawyer_profile, container, false)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("lawyers")

        usernameTextView = view.findViewById(R.id.lawyer_name)
        lawyerProfileImage = view.findViewById(R.id.profile_image)
        editProfileButton = view.findViewById(R.id.lawyer_edit_profile)
        logoutButton = view.findViewById(R.id.lawyer_log_out)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            setupProfileUpdates(currentUser.uid)
            setupImageUrlListener(currentUser.uid)
        } else {
            showToast("User not logged in")
        }

        editProfileButton.setOnClickListener { openActivity(LawyerEditProfileActivity::class.java) }
        logoutButton.setOnClickListener { logoutUser() }
    }

    private fun setupProfileUpdates(userId: String) {
        valueEventListener?.let {
            database.child(userId).removeEventListener(it)
        }

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "N/A"
                    usernameTextView.text = name

                    // Also check for image URL in case it's updated along with other profile data
                    val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    if (!imageUrl.isNullOrEmpty()) {
                        loadProfileImage(imageUrl)
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
        }

        valueEventListener?.let {
            database.child(userId).addValueEventListener(it)
        }
    }

    private fun setupImageUrlListener(userId: String) {
        imageUrlListener?.let {
            database.child(userId).child("profileImageUrl").removeEventListener(it)
        }

        imageUrlListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                val profileImageUrl = snapshot.getValue(String::class.java) ?: ""

                // Get the most recent image from activity if available
                val activity = activity as? LawyersDashboardActivity
                val currentImageUrl = activity?.getCurrentProfileImageUrl() ?: ""

                // Use the activity's image URL if available, otherwise use the one from snapshot
                val imageToLoad = if (currentImageUrl.isNotEmpty()) currentImageUrl else profileImageUrl

                if (imageToLoad.isNotEmpty()) {
                    loadProfileImage(imageToLoad)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    showToast("Image update error: ${error.message}")
                }
            }
        }

        imageUrlListener?.let {
            database.child(userId).child("profileImageUrl").addValueEventListener(it)
        }
    }

    private fun loadProfileImage(imageUrl: String) {
        Glide.with(requireContext())
            .load(imageUrl)
            .circleCrop()
            .into(lawyerProfileImage)
    }

    override fun onResume() {
        super.onResume()
        profileSection?.visibility = View.GONE
        isNavigatingToAnotherActivity = false

        // Refresh the profile image when coming back from edit activity
        auth.currentUser?.uid?.let { userId ->
            database.child(userId).child("profileImageUrl")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val imageUrl = snapshot.getValue(String::class.java)
                        if (!imageUrl.isNullOrEmpty()) {
                            loadProfileImage(imageUrl)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isNavigatingToAnotherActivity) {
            profileSection?.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        auth.currentUser?.uid?.let { userId ->
            valueEventListener?.let { listener ->
                database.child(userId).removeEventListener(listener)
            }
            imageUrlListener?.let { listener ->
                database.child(userId).child("profileImageUrl").removeEventListener(listener)
            }
        }
    }

    private fun logoutUser() {
        if (!isAdded) return

        val sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        auth.signOut()
        showToast("You have been logged out")

        openActivity(Login::class.java)
        requireActivity().finish()
    }

    private fun openActivity(activityClass: Class<*>) {
        if (isAdded) {
            isNavigatingToAnotherActivity = true
            startActivity(Intent(requireActivity(), activityClass))
        }
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}