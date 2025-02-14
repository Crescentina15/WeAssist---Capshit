package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.AppointmentsFragment
import com.remedio.weassist.Lawyer.LawyersListActivity
import com.remedio.weassist.MessageFragment
import com.remedio.weassist.R


class SecretaryDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secretary_dashboard)

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Load the default fragment when the activity starts
        if (savedInstanceState == null) {
            loadFragment(SecretaryDashboardFragment())
        }


        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> SecretaryDashboardFragment()
                R.id.nav_appointments -> AppointmentsFragment()
                R.id.nav_message -> MessageFragment()
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
