package com.remedio.weassist.Lawyer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.Secretary.SetAppointmentActivity
import com.remedio.weassist.databinding.ActivityLawyerBackgroundBinding

class LawyerBackgroundActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerBackgroundBinding
    private lateinit var databaseReference: DatabaseReference
    private var lawyerId: String? = null
    private var secretaryId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLawyerBackgroundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backArrow.setOnClickListener { finish() }

        lawyerId = intent.getStringExtra("LAWYER_ID")
        if (lawyerId.isNullOrEmpty()) {
            Toast.makeText(this, "Lawyer ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        retrieveLawyerData(lawyerId!!)

        binding.btnSetAppointment.setOnClickListener {
            val intent = Intent(this, SetAppointmentActivity::class.java)
            intent.putExtra("LAWYER_ID", lawyerId)
            intent.putExtra("SECRETARY_ID", secretaryId)
            startActivity(intent)
        }

        binding.btnMessage.setOnClickListener {
            // For debugging - remove in production
            Log.d("LawyerBackgroundActivity", "Message button clicked, secretaryId: $secretaryId")

            if (secretaryId.isNullOrEmpty()) {
                // If secretaryId hasn't been retrieved yet, check database directly
                checkAllPossibleSecretaryLocations()
            } else {
                startChatActivity()
            }
        }
    }

    private fun startChatActivity() {
        // For debugging - remove in production
        Log.d("LawyerBackgroundActivity", "Starting ChatActivity with secretaryId: $secretaryId")

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
                            Log.d("LawyerBackgroundActivity", "Found secretaryId as '$fieldName': $secretaryId")
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
                                Log.d("LawyerBackgroundActivity", "Found secretaryId in field '${child.key}': $secretaryId")
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
                            Log.d("LawyerBackgroundActivity", "Found secretaryId in admin as '$fieldName': $secretaryId")
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
                            Log.d("LawyerBackgroundActivity", "Found secretaryId in admin's secretaries list: $secretaryId")
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
                        Log.d("LawyerBackgroundActivity", "Using default secretary as fallback: $secretaryId")
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
                                Log.d("LawyerBackgroundActivity", "Found secretaryId as '$fieldName': $secretaryId")
                                break
                            }
                        }

                        // Retrieve `adminUID` from lawyer's details
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

    // Fetch law firm details using `adminUID`
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
                                Log.d("LawyerBackgroundActivity", "Found secretaryId in admin as '$fieldName': $secretaryId")
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