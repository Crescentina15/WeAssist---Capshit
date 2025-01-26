package com.remedio.weassist;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ClientDashboard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_client_dashboard);

        // Initialize NavHostFragment programmatically if it's not already in the layout
        if (savedInstanceState == null) {
            NavHostFragment navHostFragment = new NavHostFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_container, navHostFragment)
                    .setPrimaryNavigationFragment(navHostFragment)  // Important for navigation
                    .commitNow();  // Use commitNow instead of commit()
        }

        // Retrieve the NavController after the fragment is committed
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_container);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            // Set up the BottomNavigationView with the NavController
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
            NavigationUI.setupWithNavController(bottomNavigationView, navController);

            // Handling edge-to-edge and system bars insets
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        } else {
            // Handle the case where NavHostFragment is not found
            throw new IllegalStateException("NavHostFragment not found in the layout.");
        }
    }
}
