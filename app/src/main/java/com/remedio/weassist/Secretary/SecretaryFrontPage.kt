package com.remedio.weassist.Secretary

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.R

class SecretaryFrontPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_secretary_front_page)



        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Set default fragment
        loadFragment(SecretaryDashboardFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
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
            .replace(R.id.flFragment, fragment)
            .commit()
    }
}
