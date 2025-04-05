package com.remedio.weassist.LoginAndRegister

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.Clients.ClientFrontPage
import com.remedio.weassist.Lawyer.LawyersDashboardActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.SecretaryDashboardActivity

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var currentPasswordInput: EditText
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var changePasswordButton: Button
    private val PREFS_NAME = "LoginPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            // If no user is logged in, go back to login
            Toast.makeText(this, "Authentication error, please log in again", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        currentPasswordInput = findViewById(R.id.currentPasswordInput)
        newPasswordInput = findViewById(R.id.newPasswordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        changePasswordButton = findViewById(R.id.changePasswordButton)

        // If this is first login, explain why password change is required
        val isFirstLogin = intent.getBooleanExtra("FIRST_LOGIN", false)
        if (isFirstLogin) {
            Toast.makeText(this, "For security, you must change your temporary password before continuing",
                Toast.LENGTH_LONG).show()
        }

        changePasswordButton.setOnClickListener {
            val currentPassword = currentPasswordInput.text.toString().trim()
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if new password is the same as old password
            if (newPassword == currentPassword) {
                Toast.makeText(this, "New password must be different from current password",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Re-authenticate and change password
            val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
            user.reauthenticate(credential)
                .addOnCompleteListener { reAuthTask ->
                    if (reAuthTask.isSuccessful) {
                        user.updatePassword(newPassword)
                            .addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    // Update password changed flag in database
                                    updatePasswordChangedFlag(user.uid)

                                    Toast.makeText(this, "Password updated successfully",
                                        Toast.LENGTH_SHORT).show()

                                    // After password change, proceed to appropriate screen
                                    fetchAndRedirectUser(user.uid)
                                } else {
                                    Toast.makeText(this, "Failed to update password: ${updateTask.exception?.message}",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        Toast.makeText(this, "Authentication failed: Current password is incorrect",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun updatePasswordChangedFlag(uid: String) {
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(uid)
        val secretaryRef = FirebaseDatabase.getInstance().getReference("secretaries").child(uid)

        lawyerRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                lawyerRef.child("passwordChanged").setValue(true)
            } else {
                secretaryRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        secretaryRef.child("passwordChanged").setValue(true)
                    }
                }
            }
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
}