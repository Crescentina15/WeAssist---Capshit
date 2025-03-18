package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.R
import com.remedio.weassist.Secretary.SetAppointmentActivity
import com.remedio.weassist.databinding.ActivityLawyerBackgroundBinding

class LawyerBackgroundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerBackgroundBinding
    private lateinit var databaseReference: DatabaseReference
    private var lawyerId: String? = null
    private var secretaryId: String? = null
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val TAG = "LawyerBackgroundActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLawyerBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize profile image view
        val lawyerProfileImage: ImageView = binding.profileImage // Ensure this matches the XML ID

        binding.backArrow.setOnClickListener { finish() }

        lawyerId = intent.getStringExtra("LAWYER_ID")
        if (lawyerId.isNullOrEmpty()) {
            Toast.makeText(this, "Lawyer ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Debug log to verify IDs
        Log.d(TAG, "Current User ID: $currentUserId, Lawyer ID: $lawyerId")

        retrieveLawyerData(lawyerId!!)

        // Initially hide buttons until we determine status
        binding.btnMessage.visibility = View.GONE
        binding.btnSetAppointment.visibility = View.GONE

        binding.btnSetAppointment.setOnClickListener {
            val intent = Intent(this, SetAppointmentActivity::class.java)
            intent.putExtra("LAWYER_ID", lawyerId)
            intent.putExtra("SECRETARY_ID", secretaryId)
            startActivity(intent)
        }

        binding.btnMessage.setOnClickListener {
            Log.d(TAG, "Message button clicked, secretaryId: $secretaryId")

            if (secretaryId.isNullOrEmpty()) {
                checkAllPossibleSecretaryLocations()
            } else {
                startChatActivity()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always check appointment status when this screen comes into view
        Log.d(TAG, "onResume: Checking appointment status")
        checkAppointmentStatus()
    }

    private fun checkAppointmentStatus() {
        if (currentUserId == null || lawyerId == null) {
            Log.e(TAG, "Missing user ID or lawyer ID")
            // Default to showing Set Appointment if we can't check
            binding.btnSetAppointment.visibility = View.VISIBLE
            binding.btnMessage.visibility = View.GONE
            return
        }

        // Try multiple database paths where appointments might be stored
        checkAppointmentsInPrimaryLocation()
    }

    private fun checkAppointmentsInPrimaryLocation() {
        // Primary location check - "appointments" node
        val appointmentsRef = FirebaseDatabase.getInstance().getReference("appointments")

        // Debug log to show we're checking
        Log.d(TAG, "Checking appointments for user: $currentUserId and lawyer: $lawyerId")

        // Query for appointments where both client ID and lawyer ID match
        appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasAppointment = false
                Log.d(TAG, "Found ${snapshot.childrenCount} appointment records in database")

                if (snapshot.exists()) {
                    for (appointmentSnapshot in snapshot.children) {
                        // Use GenericTypeIndicator instead of HashMap.class
                        val appointmentData = appointmentSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})
                        Log.d(TAG, "Checking appointment: $appointmentData")

                        val clientId = appointmentData?.get("clientId")?.toString()
                        val appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()

                        if (clientId == currentUserId && appointmentLawyerId == lawyerId) {
                            hasAppointment = true
                            Log.d(TAG, "Found matching appointment! Appointment data: $appointmentData")
                            break
                        }
                    }
                }

                if (hasAppointment) {
                    updateButtonsVisibility(true)
                } else {
                    // If no appointment found in primary location, check secondary location
                    checkAppointmentsInSecondaryLocation()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check appointment status", error.toException())
                // Fall back to secondary check
                checkAppointmentsInSecondaryLocation()
            }
        })
    }

    private fun checkAppointmentsInSecondaryLocation() {
        // Alternative location - "lawyer_appointments" node
        val altApptRef = FirebaseDatabase.getInstance().getReference("lawyer_appointments")
            .child(lawyerId!!)

        Log.d(TAG, "Checking alternative appointments location")

        altApptRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasAppointment = false

                if (snapshot.exists()) {
                    for (clientSnapshot in snapshot.children) {
                        if (clientSnapshot.key == currentUserId) {
                            hasAppointment = true
                            Log.d(TAG, "Found appointment in secondary location!")
                            break
                        }
                    }
                }

                // Final update to UI based on all checks
                updateButtonsVisibility(hasAppointment)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check secondary appointment location", error.toException())
                // Default to showing Set Appointment
                updateButtonsVisibility(false)
            }
        })
    }

    private fun updateButtonsVisibility(hasAppointment: Boolean) {
        runOnUiThread {
            if (hasAppointment) {
                Log.d(TAG, "Appointment found - showing Message button")
                binding.btnSetAppointment.visibility = View.GONE
                binding.btnMessage.visibility = View.VISIBLE
            } else {
                Log.d(TAG, "No appointment found - showing Set Appointment button")
                binding.btnSetAppointment.visibility = View.VISIBLE
                binding.btnMessage.visibility = View.GONE
            }
        }
    }

    private fun startChatActivity() {
        Log.d(TAG, "Starting ChatActivity with secretaryId: $secretaryId")

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("LAWYER_ID", lawyerId)
            putExtra("SECRETARY_ID", secretaryId)
        }
        startActivity(intent)
    }

    private fun checkAllPossibleSecretaryLocations() {
        // Try all these locations in parallel and use the first valid result
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId!!)
        lawyerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Try different possible field names
                    val possibleFieldNames = listOf(
                        "secretaryId", "secretary_id", "secretary",
                        "secretaryUID", "secretaryUid", "secretary_uid"
                    )

                    for (fieldName in possibleFieldNames) {
                        val potentialId = snapshot.child(fieldName).getValue(String::class.java)
                        if (!potentialId.isNullOrEmpty()) {
                            secretaryId = potentialId
                            Log.d(TAG, "Found secretaryId as '$fieldName': $secretaryId")
                            startChatActivity()
                            return
                        }
                    }

                    // If we get here, we didn't find the secretary in the direct fields
                    // Let's look for any field that might contain "secretary" in its name
                    for (child in snapshot.children) {
                        if (child.key?.contains("secretary", ignoreCase = true) == true) {
                            val value = child.getValue(String::class.java)
                            if (!value.isNullOrEmpty()) {
                                secretaryId = value
                                Log.d(TAG, "Found secretaryId in field '${child.key}': $secretaryId")
                                startChatActivity()
                                return
                            }
                        }
                    }

                    // As a fallback, check if we can find the admin and then check there
                    val adminUID = snapshot.child("adminUID").getValue(String::class.java)
                    if (!adminUID.isNullOrEmpty()) {
                        checkAdminForSecretary(adminUID)
                    } else {
                        // Last resort - use a default secretary ID if one exists
                        useDefaultSecretary()
                    }
                } else {
                    Toast.makeText(applicationContext, "Lawyer data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to retrieve secretary information", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun checkAdminForSecretary(adminUID: String) {
        val adminRef = FirebaseDatabase.getInstance().getReference("law_firm_admin").child(adminUID)
        adminRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Try different possible field names
                    val possibleFieldNames = listOf(
                        "secretaryId", "secretary_id", "secretary",
                        "secretaryUID", "secretaryUid", "secretary_uid",
                        "defaultSecretaryId", "default_secretary"
                    )

                    for (fieldName in possibleFieldNames) {
                        val potentialId = snapshot.child(fieldName).getValue(String::class.java)
                        if (!potentialId.isNullOrEmpty()) {
                            secretaryId = potentialId
                            Log.d(TAG, "Found secretaryId in admin as '$fieldName': $secretaryId")
                            startChatActivity()
                            return
                        }
                    }

                    // Check for a "secretaries" node that might contain a list of secretaries
                    val secretariesNode = snapshot.child("secretaries")
                    if (secretariesNode.exists() && secretariesNode.childrenCount > 0) {
                        // Just take the first secretary in the list
                        val firstSecretaryKey = secretariesNode.children.first().key
                        if (!firstSecretaryKey.isNullOrEmpty()) {
                            secretaryId = firstSecretaryKey
                            Log.d(TAG, "Found secretaryId in admin's secretaries list: $secretaryId")
                            startChatActivity()
                            return
                        }
                    }

                    // Last resort - use a default secretary ID if one exists
                    useDefaultSecretary()
                } else {
                    useDefaultSecretary()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to retrieve admin information", Toast.LENGTH_SHORT).show()
                useDefaultSecretary()
            }
        })
    }

    private fun useDefaultSecretary() {
        // Query for any secretary in the system as a last resort
        // This is just a fallback to get the chat working
        val secretariesRef = FirebaseDatabase.getInstance().getReference("secretaries")
        secretariesRef.limitToFirst(1).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    val firstSecretaryKey = snapshot.children.first().key
                    if (!firstSecretaryKey.isNullOrEmpty()) {
                        secretaryId = firstSecretaryKey
                        Log.d(TAG, "Using default secretary as fallback: $secretaryId")
                        startChatActivity()
                    } else {
                        Toast.makeText(applicationContext, "No secretary found in the system", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "No secretaries available", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to find any secretary", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun retrieveLawyerData(lawyerId: String) {
        databaseReference = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val lawyer = snapshot.getValue(Lawyer::class.java)
                    lawyer?.let {
                        binding.lawyerName.text = it.name
                        binding.lawyerSpecialization.text = "Specialization: ${it.specialization}"
                        binding.lawyerBio.text = "Bio: ${it.bio}"
                        binding.lawyerExperience.text = "Experience: ${it.experience} years"
                        binding.lawyerLawSchool.text = "Law School: ${it.lawSchool}"
                        binding.lawyerGraduationYear.text = "Graduation Year: ${it.graduationYear}"
                        binding.lawyerCertifications.text = "Certifications: ${it.certifications}"
                        binding.lawyerJurisdiction.text = "Jurisdiction: ${it.jurisdiction}"
                        binding.lawyerRate.text = "Professional Rate: ${it.rate}"

                        // Load lawyer profile image
                        loadLawyerProfileImage(it.profileImageUrl)

                        // Try to get secretary ID while we're fetching lawyer data
                        // Check for several possible field names
                        val possibleFieldNames = listOf(
                            "secretaryId", "secretary_id", "secretary",
                            "secretaryUID", "secretaryUid", "secretary_uid"
                        )

                        for (fieldName in possibleFieldNames) {
                            val potentialId = snapshot.child(fieldName).getValue(String::class.java)
                            if (!potentialId.isNullOrEmpty()) {
                                secretaryId = potentialId
                                Log.d(TAG, "Found secretaryId as '$fieldName': $secretaryId")
                                break
                            }
                        }

                        // Retrieve adminUID from lawyer's details
                        val adminUID = snapshot.child("adminUID").getValue(String::class.java)

                        if (!adminUID.isNullOrEmpty()) {
                            Log.d("LawFirmDetails", "Fetching law firm details for adminUID: $adminUID")
                            retrieveLawFirmFromAdmin(adminUID)
                        } else {
                            Log.e("LawFirmDetails", "No adminUID found for lawyer: $lawyerId")
                            binding.lawyerFirm.text = "Law Firm: Not Available"
                            binding.lawyerLocation.text = "Location: Not Available"
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "Lawyer data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load lawyer data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // New function to load lawyer profile image
    private fun loadLawyerProfileImage(profileImageUrl: String?) {
        if (!profileImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(profileImageUrl)
                .placeholder(R.drawable.profile) // Placeholder image while loading
                .error(R.drawable.profile) // Error image if loading fails
                .into(binding.profileImage)

            Log.d(TAG, "Loading lawyer profile image: $profileImageUrl")
        } else {
            // Set default profile image if no URL available
            binding.profileImage.setImageResource(R.drawable.profile)
            Log.d(TAG, "No profile image URL, using default")
        }
    }

    // Fetch law firm details using adminUID
    private fun retrieveLawFirmFromAdmin(adminUID: String) {
        val firmRef = FirebaseDatabase.getInstance().getReference("law_firm_admin").child(adminUID)
        firmRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firmName = snapshot.child("lawFirm").getValue(String::class.java) ?: "Unknown"
                    val officeAddress = snapshot.child("officeAddress").getValue(String::class.java) ?: "Unknown"

                    binding.lawyerFirm.text = "Law Firm: $firmName"
                    binding.lawyerLocation.text = "Location: $officeAddress"

                    Log.d("LawFirmDetails", "Law Firm: $firmName")
                    Log.d("LawFirmDetails", "Office Address: $officeAddress")

                    // If we still don't have a secretaryId, try to get it from the admin
                    if (secretaryId.isNullOrEmpty()) {
                        // Try different possible field names
                        val possibleFieldNames = listOf(
                            "secretaryId", "secretary_id", "secretary",
                            "secretaryUID", "secretaryUid", "secretary_uid",
                            "defaultSecretaryId", "default_secretary"
                        )

                        for (fieldName in possibleFieldNames) {
                            val potentialId = snapshot.child(fieldName).getValue(String::class.java)
                            if (!potentialId.isNullOrEmpty()) {
                                secretaryId = potentialId
                                Log.d(TAG, "Found secretaryId in admin as '$fieldName': $secretaryId")
                                break
                            }
                        }
                    }
                } else {
                    binding.lawyerFirm.text = "Law Firm: Not Available"
                    binding.lawyerLocation.text = "Location: Not Available"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(applicationContext, "Failed to load firm details", Toast.LENGTH_SHORT).show()
            }
        })
    }
}