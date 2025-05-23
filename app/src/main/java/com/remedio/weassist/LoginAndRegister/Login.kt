package com.remedio.weassist.LoginAndRegister

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Clients.ClientFrontPage
import com.remedio.weassist.Lawyer.LawyersDashboardActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.SecretaryDashboardActivity


class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var rememberMeCheckbox: CheckBox

    private val PREFS_NAME = "LoginPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signUpText = findViewById<TextView>(R.id.signUpText)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Load saved credentials if Remember Me was checked
        loadLoginDetails()

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        signUpText.setOnClickListener {
            startActivity(Intent(this, RegisterSelection::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        // Check if the account is active
                        checkAccountStatus(user.uid) { isActive ->
                            if (isActive) {
                                if (rememberMeCheckbox.isChecked) {
                                    saveLoginDetails(email, password)
                                } else {
                                    clearLoginDetails()
                                }

                                // Check if password needs to be changed
                                checkPasswordChangeRequired(user.uid, password) { changeRequired ->
                                    if (changeRequired) {
                                        // Redirect to change password screen
                                        val intent = Intent(this, ChangePasswordActivity::class.java)
                                        intent.putExtra("FIRST_LOGIN", true)
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        // Normal login flow
                                        fetchAndRedirectUser(user.uid)
                                    }
                                }
                            } else {
                                // Account is disabled
                                Toast.makeText(this, "This account has been disabled. Please contact the administrator.", Toast.LENGTH_LONG).show()
                                auth.signOut()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Add this new function to check if the account is active
    private fun checkAccountStatus(uid: String, callback: (Boolean) -> Unit) {
        // Check if user is a secretary
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries").child(uid)
        secretaryRef.child("active").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val isActive = snapshot.getValue(Boolean::class.java) ?: true
                    callback(isActive)
                } else {
                    // If not a secretary or no active field, assume account is active
                    callback(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // In case of error, allow login (fail open)
                callback(true)
            }
        })
    }

    private fun isTemporaryPassword(password: String): Boolean {
        // Check if the password follows the temporary password pattern
        return password.startsWith("Temp") && password.endsWith("!")
    }

    private fun checkPasswordChangeRequired(uid: String, password: String, callback: (Boolean) -> Unit) {
        // First check if the password itself looks like a temporary password
        if (isTemporaryPassword(password)) {
            callback(true)
            return
        }

        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(uid)
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries").child(uid)

        // First check if user is a lawyer
        lawyerRef.get().addOnSuccessListener { lawyerSnapshot ->
            if (lawyerSnapshot.exists()) {
                // Check if passwordChanged flag exists
                val passwordChanged = lawyerSnapshot.child("passwordChanged").getValue(Boolean::class.java) ?: false
                callback(!passwordChanged)
            } else {
                // If not a lawyer, check if secretary
                secretaryRef.get().addOnSuccessListener { secretarySnapshot ->
                    if (secretarySnapshot.exists()) {
                        val passwordChanged = secretarySnapshot.child("passwordChanged").getValue(Boolean::class.java) ?: false
                        callback(!passwordChanged)
                    } else {
                        // Not a lawyer or secretary, no need to change password
                        callback(false)
                    }
                }.addOnFailureListener {
                    // In case of error, don't force password change
                    callback(false)
                }
            }
        }.addOnFailureListener {
            // In case of error, don't force password change
            callback(false)
        }
    }

    private fun fetchAndRedirectUser(uid: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(uid)
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(uid)
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries").child(uid)

        userRef.get().addOnSuccessListener { userSnapshot ->
            if (userSnapshot.exists()) {
                val role = userSnapshot.child("role").value.toString()
                saveUserRole(role)

                val intent = when (role) {
                    "lawyer" -> {
                        lawyerRef.get().addOnSuccessListener { lawyerSnapshot ->
                            if (lawyerSnapshot.exists()) {
                                val name = lawyerSnapshot.child("name").value.toString()
                                saveLawyerName("Atty. $name") // Add "Atty." prefix
                            }
                        }
                        Intent(this, LawyersDashboardActivity::class.java)
                    }
                    "secretary" -> Intent(this, SecretaryDashboardActivity::class.java) // Redirect to secretary dashboard
                    else -> Intent(this, ClientFrontPage::class.java)
                }

                startActivity(intent)
                finish()
            } else {
                // If not found in Users, check if the user is a lawyer
                lawyerRef.get().addOnSuccessListener { lawyerSnapshot ->
                    if (lawyerSnapshot.exists()) {
                        val name = lawyerSnapshot.child("name").value.toString()
                        saveUserRole("lawyer")
                        saveLawyerName("Atty. $name")
                        startActivity(Intent(this, LawyersDashboardActivity::class.java))
                        finish()
                    } else {
                        // Check if the user is a secretary
                        secretaryRef.get().addOnSuccessListener { secretarySnapshot ->
                            if (secretarySnapshot.exists()) {
                                saveUserRole("secretary")
                                startActivity(Intent(this, SecretaryDashboardActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show()
                                auth.signOut()
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveLawyerName(name: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("lawyer_name", name)
        editor.apply()
    }

    private fun saveUserRole(role: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("role", role)
        editor.apply()
    }

    private fun saveLoginDetails(email: String, password: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("email", email)
        editor.putString("password", password)
        editor.putBoolean("rememberMe", true)
        editor.apply()
    }

    private fun loadLoginDetails() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedEmail = sharedPreferences.getString("email", "")
        val savedPassword = sharedPreferences.getString("password", "")
        val isRemembered = sharedPreferences.getBoolean("rememberMe", false)

        if (isRemembered) {
            emailInput.setText(savedEmail)
            passwordInput.setText(savedPassword)
            rememberMeCheckbox.isChecked = true
        }
    }

    private fun clearLoginDetails() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("email")
        editor.remove("password")
        editor.putBoolean("rememberMe", false)
        editor.apply()
    }
}