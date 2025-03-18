package com.remedio.weassist.Lawyer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.remedio.weassist.R

class LawyerMessageFragment : Fragment() {

    private var profileSection: View? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Access the profile section from the activity
        if (context is LawyersDashboardActivity) {
            profileSection = context.findViewById(R.id.profile_section)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_lawyer_message, container, false)
    }

    override fun onResume() {
        super.onResume()
        profileSection?.visibility = View.GONE // Hide profile section
    }

    override fun onPause() {
        super.onPause()
        profileSection?.visibility = View.VISIBLE // Show profile section when leaving
    }
}
