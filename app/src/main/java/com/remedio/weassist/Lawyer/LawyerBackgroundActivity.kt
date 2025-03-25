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

        // Update to the message button click listener in LawyerBackgroundActivity
        binding.btnMessage.setOnClickListener {
            Log.d(TAG, "Message button clicked")

            // Check if there's an accepted appointment with this lawyer
            val appointmentsRef = FirebaseDatabase.getInstance().getReference("accepted_appointment")

            appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Look for an accepted appointment with this lawyer
                        var foundAppointment = false
                        var appointmentLawyerId: String? = null

                        for (appointmentSnapshot in snapshot.children) {
                            val appointmentData = appointmentSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})

                            val clientId = appointmentData?.get("clientId")?.toString()
                            appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()
                            val status = appointmentData?.get("status")?.toString()

                            // Check if this is an accepted appointment for the current client and lawyer
                            if (clientId == currentUserId && appointmentLawyerId == lawyerId &&
                                (status == "accepted" || status == "Accepted")) {
                                foundAppointment = true
                                break
                            }
                        }

                        if (foundAppointment && appointmentLawyerId != null) {
                            // If appointment is accepted, open chat with the lawyer directly
                            // But don't create a conversation yet
                            startChatWithLawyerNoConversation(appointmentLawyerId)
                        } else {
                            // If no accepted appointment found, check regular appointments
                            checkRegularAppointmentsForChat()
                        }
                    } else {
                        // If no accepted_appointment node, check regular appointments
                        checkRegularAppointmentsForChat()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to check accepted appointments", error.toException())
                    // Fall back to secretary messaging
                    if (secretaryId.isNullOrEmpty()) {
                        checkAllPossibleSecretaryLocations()
                    } else {
                        startChatActivity()
                    }
                }
            })
        }

    }

    private fun startChatWithLawyerNoConversation(lawyerId: String) {
        Log.d(TAG, "Starting chat with lawyer: $lawyerId (no conversation created yet)")

        // Generate the conversation ID between client and lawyer
        val conversationId = if (currentUserId!! < lawyerId) {
            "conversation_${currentUserId}_$lawyerId"
        } else {
            "conversation_${lawyerId}_$currentUserId"
        }

        // Check if a conversation already exists
        val conversationRef = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId)

        conversationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Start the chat activity regardless of whether the conversation exists
                val intent = Intent(this@LawyerBackgroundActivity, ChatActivity::class.java).apply {
                    putExtra("CONVERSATION_ID", conversationId)
                    putExtra("SECRETARY_ID", lawyerId) // Using SECRETARY_ID field for lawyer ID
                    putExtra("USER_TYPE", "client")

                    // Add a flag to indicate we're starting a new chat - conversation should be created only on first message
                    putExtra("CREATE_ON_FIRST_MESSAGE", !snapshot.exists())
                }
                startActivity(intent)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check if conversation exists", error.toException())
                // Still continue to chat activity but with the flag to create on first message
                val intent = Intent(this@LawyerBackgroundActivity, ChatActivity::class.java).apply {
                    putExtra("CONVERSATION_ID", conversationId)
                    putExtra("SECRETARY_ID", lawyerId) // Using SECRETARY_ID field for lawyer ID
                    putExtra("USER_TYPE", "client")
                    putExtra("CREATE_ON_FIRST_MESSAGE", true)
                }
                startActivity(intent)
            }
        })
    }

    // Add this new method to check regular appointments for the chat
    private fun checkRegularAppointmentsForChat() {
        val appointmentsRef = FirebaseDatabase.getInstance().getReference("appointments")

        appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Look for an accepted appointment with this lawyer
                    var foundAppointment = false
                    var appointmentLawyerId: String? = null

                    for (appointmentSnapshot in snapshot.children) {
                        val appointmentData = appointmentSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})

                        val clientId = appointmentData?.get("clientId")?.toString()
                        appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()
                        val status = appointmentData?.get("status")?.toString()

                        // Check if this is an accepted appointment for the current client and lawyer
                        if (clientId == currentUserId && appointmentLawyerId == lawyerId &&
                            (status == "accepted" || status == "Accepted")) {
                            foundAppointment = true
                            break
                        }
                    }

                    if (foundAppointment && appointmentLawyerId != null) {
                        // If appointment is accepted, open chat with lawyer but don't create conversation
                        startChatWithLawyerNoConversation(appointmentLawyerId)
                    } else {
                        // If no accepted appointment found in regular appointments either, use secretary
                        if (secretaryId.isNullOrEmpty()) {
                            checkAllPossibleSecretaryLocations()
                        } else {
                            startChatActivity()
                        }
                    }
                } else {
                    // If no appointments found, fall back to messaging the secretary
                    if (secretaryId.isNullOrEmpty()) {
                        checkAllPossibleSecretaryLocations()
                    } else {
                        startChatActivity()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check regular appointments", error.toException())
                // Fall back to secretary messaging
                if (secretaryId.isNullOrEmpty()) {
                    checkAllPossibleSecretaryLocations()
                } else {
                    startChatActivity()
                }
            }
        })
    }

    // Add this new method to start a chat with the lawyer
    private fun startChatWithLawyer(lawyerId: String) {
        Log.d(TAG, "Starting chat with lawyer: $lawyerId")

        // Generate the conversation ID between client and lawyer
        val conversationId = if (currentUserId!! < lawyerId) {
            "conversation_${currentUserId}_$lawyerId"
        } else {
            "conversation_${lawyerId}_$currentUserId"
        }

        // Check if a conversation already exists
        val conversationRef = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId)

        conversationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Conversation exists, start the chat activity with this conversation
                    val intent = Intent(this@LawyerBackgroundActivity, ChatActivity::class.java).apply {
                        putExtra("CONVERSATION_ID", conversationId)
                        putExtra("SECRETARY_ID", lawyerId) // Using SECRETARY_ID field for lawyer ID
                        putExtra("USER_TYPE", "client")
                    }
                    startActivity(intent)
                } else {
                    // Conversation doesn't exist yet, create it with a welcome message
                    createNewConversationWithLawyer(lawyerId, conversationId)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check if conversation exists", error.toException())
                // Fall back to creating a new conversation
                createNewConversationWithLawyer(lawyerId, conversationId)
            }
        })
    }

    // Add this new method to create a new conversation with the lawyer
    private fun createNewConversationWithLawyer(lawyerId: String, conversationId: String) {
        // Get lawyer information for the conversation setup - no welcome message
        val lawyerRef = FirebaseDatabase.getInstance().getReference("lawyers").child(lawyerId)

        lawyerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Set up the conversation data WITHOUT the welcome message
                val participantIds = mapOf(
                    currentUserId!! to true,
                    lawyerId to true
                )

                val unreadMessages = mapOf(
                    currentUserId!! to 0, // No unread messages initially
                    lawyerId to 0         // No unread messages initially
                )

                val conversationData = mapOf(
                    "participantIds" to participantIds,
                    "unreadMessages" to unreadMessages,
                    "appointedLawyerId" to lawyerId,
                    "handledByLawyer" to true
                )

                // Create the conversation without a welcome message
                val conversationRef = FirebaseDatabase.getInstance().getReference("conversations").child(conversationId)
                conversationRef.setValue(conversationData)
                    .addOnSuccessListener {
                        // Just open the chat without adding a welcome message
                        val intent = Intent(this@LawyerBackgroundActivity, ChatActivity::class.java).apply {
                            putExtra("CONVERSATION_ID", conversationId)
                            putExtra("SECRETARY_ID", lawyerId) // Using SECRETARY_ID field for lawyer ID
                            putExtra("USER_TYPE", "client")
                        }
                        startActivity(intent)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create conversation", e)
                        // Fall back to secretary messaging
                        if (secretaryId.isNullOrEmpty()) {
                            checkAllPossibleSecretaryLocations()
                        } else {
                            startChatActivity()
                        }
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to get lawyer information", error.toException())
                // Fall back to secretary messaging
                if (secretaryId.isNullOrEmpty()) {
                    checkAllPossibleSecretaryLocations()
                } else {
                    startChatActivity()
                }
            }
        })
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
            binding.btnSetAppointment.visibility = View.VISIBLE
            binding.btnMessage.visibility = View.GONE
            return
        }

        // First check the accepted_appointment node
        checkAcceptedAppointments()
    }

    private fun checkAcceptedAppointments() {
        // Check the accepted_appointment node first, since this explicitly contains accepted appointments
        val acceptedApptRef = FirebaseDatabase.getInstance().getReference("accepted_appointment")

        acceptedApptRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasAcceptedAppointment = false
                Log.d(TAG, "Checking accepted_appointment node")

                if (snapshot.exists()) {
                    // Iterate through all entries in the accepted_appointment node
                    for (appointmentSnapshot in snapshot.children) {
                        val appointmentData = appointmentSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})

                        val clientId = appointmentData?.get("clientId")?.toString()
                        val appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()

                        // Check if this appointment matches our current user and lawyer
                        if (clientId == currentUserId && appointmentLawyerId == lawyerId) {
                            hasAcceptedAppointment = true
                            Log.d(TAG, "Found matching appointment in accepted_appointment node!")
                            break
                        }
                    }
                }

                if (hasAcceptedAppointment) {
                    updateButtonsVisibility(true)
                } else {
                    // If nothing found in accepted_appointment, check regular appointments
                    checkRegularAppointments()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check accepted_appointment", error.toException())
                // Fall back to regular appointments check
                checkRegularAppointments()
            }
        })
    }

    private fun checkRegularAppointments() {
        // Check the appointments node
        val appointmentsRef = FirebaseDatabase.getInstance().getReference("appointments")

        appointmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasAcceptedAppointment = false
                Log.d(TAG, "Checking appointments node")

                if (snapshot.exists()) {
                    // Iterate through all entries in the appointments node
                    for (appointmentSnapshot in snapshot.children) {
                        val appointmentData = appointmentSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})

                        val clientId = appointmentData?.get("clientId")?.toString()
                        val appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()
                        val status = appointmentData?.get("status")?.toString()

                        // Check if this appointment matches and is accepted
                        if (clientId == currentUserId &&
                            appointmentLawyerId == lawyerId &&
                            status == "accepted") {
                            hasAcceptedAppointment = true
                            Log.d(TAG, "Found matching ACCEPTED appointment in appointments node!")
                            break
                        }
                    }
                }

                if (hasAcceptedAppointment) {
                    updateButtonsVisibility(true)
                } else {
                    // Check individual appointment nodes with IDs
                    checkIndividualAppointmentNodes()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check appointments", error.toException())
                checkIndividualAppointmentNodes()
            }
        })
    }

    private fun checkIndividualAppointmentNodes() {
        // Get the root reference to check top-level nodes that might be appointment IDs
        val rootRef = FirebaseDatabase.getInstance().getReference()

        rootRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var hasAcceptedAppointment = false

                // Look for node names that start with "OL" which appear to be your appointment IDs
                for (nodeSnapshot in snapshot.children) {
                    val nodeName = nodeSnapshot.key

                    // If it looks like an appointment ID node
                    if (nodeName?.startsWith("OL") == true) {
                        val appointmentData = nodeSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})

                        val clientId = appointmentData?.get("clientId")?.toString()
                        val appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()
                        val status = appointmentData?.get("status")?.toString()

                        // Check if this appointment matches and is accepted
                        if (clientId == currentUserId &&
                            appointmentLawyerId == lawyerId &&
                            (status == "accepted" || nodeName.contains("accepted", ignoreCase = true))) {
                            hasAcceptedAppointment = true
                            Log.d(TAG, "Found matching ACCEPTED appointment in node: $nodeName")
                            break
                        }
                    }
                }

                // Final update after checking all possible locations
                updateButtonsVisibility(hasAcceptedAppointment)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check root nodes", error.toException())
                // Default to showing Set Appointment button
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