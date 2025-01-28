package com.remedio.weassist

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Safely find the security LinearLayout by its ID
        val securityOption: LinearLayout? = view.findViewById(R.id.security)
        val reportOption: LinearLayout? = view.findViewById(R.id.report_problem)
        val privacyOption: LinearLayout? = view.findViewById(R.id.privacy)
        val editprofileOption: LinearLayout? = view.findViewById(R.id.edit_profile)
        // Set OnClickListener for the security option
        securityOption?.setOnClickListener {
            // Navigate to SecurityActivity
            try {
                val intent = Intent(requireContext(), SecurityActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Optional: Show an error message
                // Toast.makeText(requireContext(), "Failed to navigate to Security Settings", Toast.LENGTH_SHORT).show()
            }
        }

        reportOption?.setOnClickListener {

            try {
                val intent = Intent(requireContext(), ReportActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        editprofileOption?.setOnClickListener {

            try {
                val intent = Intent(requireContext(), EditProfileActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        privacyOption?.setOnClickListener {
            // Navigate to SecurityActivity
            try {
                val intent = Intent(requireContext(), PrivacyActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Optional: Show an error message
                // Toast.makeText(requireContext(), "Failed to navigate to Security Settings", Toast.LENGTH_SHORT).show()
            }
        }

        // Find the logout option
        val logOutOption: LinearLayout? = view.findViewById(R.id.log_out)

        // Handle logout logic
        logOutOption?.setOnClickListener {
            try {
                // Clear user session data
                val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val editor = sharedPreferences.edit()
                editor.clear() // Clear all stored data
                editor.apply()

                // Display a logout confirmation message
                Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

                // Navigate to the login screen
                val intent = Intent(requireContext(), Login::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear the activity stack
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to log out", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
