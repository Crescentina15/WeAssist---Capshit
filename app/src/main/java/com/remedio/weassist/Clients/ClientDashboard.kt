package com.remedio.weassist.Clients

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.remedio.weassist.R

class ClientDashboard : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_client_dashboard)

        // Initialize NavHostFragment programmatically if it's not already in the layout
        if (savedInstanceState == null) {
            val navHostFragment = NavHostFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_container, navHostFragment)
                .setPrimaryNavigationFragment(navHostFragment) // Important for navigation
                .commitNow() // Use commitNow instead of commit()
        }

        // Retrieve the NavController after the fragment is committed
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_container) as NavHostFragment?
        if (navHostFragment != null) {
            val navController = navHostFragment.navController

            // Set up the BottomNavigationView with the NavController
            val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            setupWithNavController(bottomNavigationView, navController)

            // Handling edge-to-edge and system bars insets
            ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main)
            ) { v: View, insets: WindowInsetsCompat ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        } else {
            // Handle the case where NavHostFragment is not found
            throw IllegalStateException("NavHostFragment not found in the layout.")
        }
    }
}
