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

        // Check if we should open the appointment tab from a notification
        val openAppointmentTab = intent.getBooleanExtra("OPEN_APPOINTMENT_TAB", false)

        if (savedInstanceState == null) {
            if (openAppointmentTab) {
                // Open appointment fragment
                loadFragment(SecretaryAppointmentFragment())
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
                R.id.nav_appointments -> SecretaryAppointmentFragment()
                R.id.nav_message -> SecretaryMessageFragment()
                R.id.nav_profile -> SecretaryProfileFragment()
                else -> SecretaryDashboardFragment()
            }
            loadFragment(selectedFragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container1, fragment)
            .commit()
    }
}