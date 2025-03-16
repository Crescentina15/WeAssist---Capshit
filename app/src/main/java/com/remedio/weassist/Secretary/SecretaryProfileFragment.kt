package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.R

class SecretaryProfileFragment : Fragment(R.layout.fragment_secretary_profile) {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var nameTextView: TextView? = null
    private var profilePicture: ImageView? = null
    private var editProfileButton: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("secretaries")

        // ✅ Use `view.findViewById()`
        nameTextView = view.findViewById(R.id.secretary_name)
        profilePicture = view.findViewById(R.id.profile_picture)
        editProfileButton = view.findViewById(R.id.secretary_edit_profile)

        auth.currentUser?.let { fetchSecretaryData(it.uid) }

        editProfileButton?.setOnClickListener {
            startActivity(Intent(requireContext(), EditSecretaryProfileActivity::class.java))
        }
    }

    private fun fetchSecretaryData(userId: String) {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // ✅ Check if Fragment is still attached
                if (!isAdded || view == null) return

                // ✅ Check if `nameTextView` and `profilePicture` are still available
                nameTextView?.text = snapshot.child("name").getValue(String::class.java) ?: "N/A"

                val profilePicUrl = snapshot.child("profilePicture").getValue(String::class.java)
                if (!profilePicUrl.isNullOrEmpty()) {
                    profilePicture?.let { imageView ->
                        Glide.with(requireContext()).load(profilePicUrl).into(imageView)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Database error: ${error.message}")
            }
        })
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
