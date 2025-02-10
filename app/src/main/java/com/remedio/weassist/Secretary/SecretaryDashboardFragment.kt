package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.remedio.weassist.AddbackgroundActivity
import com.remedio.weassist.LawyerslistActivity
import com.remedio.weassist.R

class SecretaryDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_secretarydashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the button by ID and set an OnClickListener
        val manageAvailabilityButton = view.findViewById<View>(R.id.manage_availability_button)
        manageAvailabilityButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyerslistActivity::class.java)
            startActivity(intent)
        }
        val addBackgroundButton = view.findViewById<View>(R.id.add_background_button)
        addBackgroundButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyerslistActivity::class.java)
            startActivity(intent)
        }
        val addBalanceButton = view.findViewById<View>(R.id.add_balance_button)
        addBalanceButton.setOnClickListener {
            val intent = Intent(requireContext(), LawyerslistActivity::class.java)
            startActivity(intent)
        }
    }
}
