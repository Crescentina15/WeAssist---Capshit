package com.remedio.weassist.Clients

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.R

class ClientFrontPage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_frontpage_client)



        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Set default fragment
        loadFragment(ClientHomeFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> ClientHomeFragment()
                R.id.nav_appointments -> ClientAppointmentsFragment()
                R.id.nav_message -> ClientMessageFragment()
                R.id.nav_profile -> ClientProfileFragment()
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
