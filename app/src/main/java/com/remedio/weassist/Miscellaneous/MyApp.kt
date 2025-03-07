package com.remedio.weassist.Miscellaneous


import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable Firebase persistence (only once globally)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
