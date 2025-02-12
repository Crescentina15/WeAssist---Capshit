package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.remedio.weassist.AddAvailabilityActivity
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.R

class SecretaryDashboardFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_dashboard, container, false)

        // Find ImageButton and set click listener
        val manageButton = view.findViewById<ImageButton>(R.id.manage_availability_button)
        manageButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyersListActivity::class.java)
            startActivity(intent)
        }

        val addBackgroundButton = view.findViewById<ImageButton>(R.id.add_background_button)
        addBackgroundButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyersListActivity::class.java)
            startActivity(intent)
        }

        val addBalanceButton = view.findViewById<ImageButton>(R.id.add_balance_button)
        addBalanceButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyersListActivity::class.java)
            startActivity(intent)
        }

        return view
    }

}
