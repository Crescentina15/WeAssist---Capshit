package com.remedio.weassist.Lawyer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.AppointmentsFragment
import com.remedio.weassist.Clients.ClientHomeFragment
import com.remedio.weassist.MessageFragment
import com.remedio.weassist.Profile.ProfileFragment
import com.remedio.weassist.R

class LawyersDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lawyers_dashboard)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.lawyerNav)

        // Set default fragment
        loadFragment(AppointmentsFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {

                R.id.nav_appointments -> AppointmentsFragment()


                else -> ClientHomeFragment()
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
