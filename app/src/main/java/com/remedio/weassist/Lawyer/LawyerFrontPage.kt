package com.remedio.weassist.Lawyer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.R

class LawyerFrontPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyer_front_page)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.lawyerNav)

        // Set default fragment to Lawyer Home
        loadFragment(LawyerAppointmentsFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {

                R.id.nav_appointments -> LawyerAppointmentsFragment()
                R.id.nav_profile -> LawyerProfileFragment()
                else -> LawyerAppointmentsFragment()
            }
            loadFragment(selectedFragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flFragment, fragment)
            .commit()
    }
}
