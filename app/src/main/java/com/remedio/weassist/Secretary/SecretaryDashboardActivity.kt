package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.R

class SecretaryDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_dashboard)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Check if we should open the appointment fragment from a notification
        val openAppointmentFragment = intent.getBooleanExtra("OPEN_APPOINTMENT_FRAGMENT", false)
        val appointmentId = intent.getStringExtra("APPOINTMENT_ID")

        if (savedInstanceState == null) {
            if (openAppointmentFragment) {
                // Open appointment fragment and pass the appointment ID
                val appointmentFragment = SecretaryAppointmentFragment().apply {
                    arguments = Bundle().apply {
                        putString("APPOINTMENT_ID", appointmentId)
                    }
                }
                loadFragment(appointmentFragment)
                // Update the bottom navigation to show the appointment tab as selected
                bottomNavigationView.selectedItemId = R.id.nav_appointments
            } else {
                // Load the default fragment when the activity starts
                loadFragment(SecretaryDashboardFragment())
            }
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> SecretaryDashboardFragment()
                R.id.nav_appointments -> {
                    // Check if we have an appointment ID to pass
                    if (openAppointmentFragment && appointmentId != null) {
                        SecretaryAppointmentFragment().apply {
                            arguments = Bundle().apply {
                                putString("APPOINTMENT_ID", appointmentId)
                            }
                        }
                    } else {
                        SecretaryAppointmentFragment()
                    }
                }
                R.id.nav_message -> SecretaryMessageFragment()
                R.id.nav_profile -> SecretaryProfileFragment()
                else -> SecretaryDashboardFragment()
            }
            loadFragment(selectedFragment)
            true
        }

        // Clear the appointment ID flag after first use
        if (openAppointmentFragment) {
            intent.removeExtra("OPEN_APPOINTMENT_FRAGMENT")
            intent.removeExtra("APPOINTMENT_ID")
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container1, fragment)
            .commit()
    }

    // Method to directly navigate to appointment details from outside components
    fun showAppointmentDetails(appointmentId: String) {
        val appointmentFragment = SecretaryAppointmentFragment().apply {
            arguments = Bundle().apply {
                putString("APPOINTMENT_ID", appointmentId)
            }
        }
        loadFragment(appointmentFragment)
        findViewById<BottomNavigationView>(R.id.bottomNavigationView).selectedItemId = R.id.nav_appointments
    }
}