package com.remedio.weassist.MessageConversation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Clients.MessageAdapter
import com.remedio.weassist.R
import java.util.UUID

class ChatActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvChatPartnerName: TextView
    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: ImageButton
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var messagesAdapter: MessageAdapter
    private lateinit var backButton: ImageButton
    private val messagesList = mutableListOf<Message>()
    private var createOnFirstMessage = false
    private var isSending = false // Add this with other class variables
    private var pendingMessageId: String? = null

    private var lastSentMessage: String? = null // Track the last sent message content
    private var lastSentTimestamp: Long = 0 // Track when the last message was sent
    private val receivedMessageIds = mutableSetOf<String>()

    // Chat participant IDs
    private var clientId: String? = null
    private var secretaryId: String? = null
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var conversationId: String? = null
    private var lawyerId: String? = null  // Add this line

    private lateinit var profileImageView: ImageView

    // Current user type
    private var userType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val fileAttachmentButton: ImageButton = findViewById(R.id.file_attachment_button)

        tvChatPartnerName = findViewById(R.id.name_secretary)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        backButton = findViewById(R.id.back_button)
        profileImageView = findViewById(R.id.profile_image)

        database = FirebaseDatabase.getInstance().reference
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Get all possible IDs from intent
        conversationId = intent.getStringExtra("CONVERSATION_ID")
        secretaryId = intent.getStringExtra("SECRETARY_ID")
        clientId = intent.getStringExtra("CLIENT_ID")
        lawyerId = intent.getStringExtra("LAWYER_ID")  // Add this line
        userType = intent.getStringExtra("USER_TYPE")
        val appointmentId = intent.getStringExtra("APPOINTMENT_ID")

        Log.d("ChatActivity", "Received in intent - conversationId: $conversationId, secretaryId: $secretaryId, clientId: $clientId, appointmentId: $appointmentId, userType: $userType")

        // Set up RecyclerView
        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

        fileAttachmentButton.setOnClickListener {
            // Generate a dynamic request code using UUID
            val dynamicRequestCode = UUID.randomUUID().toString()

            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "*/*" // Allow all file types to be picked
            startActivityForResult(intent, dynamicRequestCode.hashCode())
        }

        // If we have a conversation ID, prioritize loading that conversation
        if (conversationId != null) {
            // Check if the conversation exists and load chat partner info
            database.child("conversations").child(conversationId!!).get()
                .addOnSuccessListener { snapshot ->
                    // Even if the conversation doesn't exist, proceed with setup
                    // Always determine chat partner
                    if (userType == "client") {
                        // Client is messaging lawyer or secretary
                        if (secretaryId != null) {
                            // First check if this ID is a lawyer
                            database.child("lawyers").child(secretaryId!!).get()
                                .addOnSuccessListener { lawyerSnapshot ->
                                    if (lawyerSnapshot.exists()) {
                                        getLawyerName(secretaryId!!)
                                    } else {
                                        // If not a lawyer, check if this is a secretary
                                        database.child("secretaries").child(secretaryId!!).get()
                                            .addOnSuccessListener { secretarySnapshot ->
                                                if (secretarySnapshot.exists()) {
                                                    getSecretaryName(secretaryId!!)
                                                } else {
                                                    // Default to "Lawyer" if neither found
                                                    tvChatPartnerName.text = "Lawyer"
                                                    listenForMessages()
                                                }
                                            }
                                    }
                                }
                        } else {
                            // Try to determine secretaryId/lawyerId from conversation
                            extractSecretaryIdFromConversation(conversationId!!)
                        }
                    } else {
                        // Lawyer or secretary is messaging client
                        if (clientId != null) {
                            getClientName(clientId!!)
                        } else {
                            // Try to determine clientId from conversation
                            extractClientIdFromConversation(conversationId!!)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Error checking conversation: ${e.message}")
                    // Continue with setup even on error
                    setupChatPartner()
                }
        } else if (appointmentId != null && currentUserId != null) {
            fetchConversationIdFromAppointment(appointmentId)
        } else {
            // Determine current user type and set up chat
            if (currentUserId != null) {
                if (userType != null) {
                    setupChatPartner()
                } else {
                    determineCurrentUserType()
                }
            } else {
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Replace your current btnSendMessage.setOnClickListener with this:

        btnSendMessage.setOnClickListener {
            if (!isSending) {
                val messageText = etMessageInput.text.toString().trim()
                // Prevent sending duplicate messages in quick succession
                if (messageText.isNotEmpty() &&
                    (messageText != lastSentMessage || System.currentTimeMillis() - lastSentTimestamp > 2000)) {
                    isSending = true
                    btnSendMessage.isEnabled = false
                    sendMessage()
                }
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun fetchConversationIdFromAppointment(appointmentId: String) {
        // Find the notification that contains this appointment ID
        if (currentUserId == null) return

        val notificationsRef = database.child("notifications").child(currentUserId!!)
        notificationsRef.orderByChild("appointmentId").equalTo(appointmentId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (notificationSnapshot in snapshot.children) {
                            val convId = notificationSnapshot.child("conversationId").getValue(String::class.java)
                            if (convId != null) {
                                conversationId = convId
                                Log.d("ChatActivity", "Found conversationId: $conversationId from appointmentId")

                                // Check if the conversation is forwarded
                                checkForwardedStatus(conversationId!!)

                                // Now determine user type before extracting participant info
                                if (userType == null) {
                                    determineCurrentUserType()
                                } else {
                                    if (clientId == null) {
                                        extractClientIdFromConversation(conversationId!!)
                                    } else {
                                        setupChatPartner()
                                    }
                                }
                                break
                            }
                        }
                    } else {
                        // If no notification with this appointment ID, just continue with normal flow
                        if (userType == null) {
                            determineCurrentUserType()
                        } else {
                            setupChatPartner()
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching notifications: ${error.message}")
                    // Continue with normal flow
                    if (userType == null) {
                        determineCurrentUserType()
                    } else {
                        setupChatPartner()
                    }
                }
            })
    }

    private fun determineCurrentUserType() {
        if (currentUserId == null) {
            Log.e("ChatActivity", "No current user ID found!")
            finish()
            return
        }

        // Check if user type was explicitly set via intent
        if (userType != null) {
            Log.d("ChatActivity", "User type from intent: $userType")
            // If we have a conversation ID but no client ID, try to extract it
            if (clientId == null && conversationId != null) {
                extractClientIdFromConversation(conversationId!!)
            } else {
                setupChatPartner()
            }
            return
        }

        // Check if current user is a secretary
        database.child("secretaries").child(currentUserId!!).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    userType = "secretary"
                    Log.d("ChatActivity", "Current user is a secretary")

                    // If we have a conversation ID but no client ID, try to extract it
                    if (clientId == null && conversationId != null) {
                        extractClientIdFromConversation(conversationId!!)
                    } else {
                        setupChatPartner()
                    }
                    return@addOnSuccessListener
                }

                // Check if current user is a lawyer
                database.child("lawyers").child(currentUserId!!).get()
                    .addOnSuccessListener { lawyerSnapshot ->
                        if (lawyerSnapshot.exists()) {
                            userType = "lawyer"
                            Log.d("ChatActivity", "Current user is a lawyer")

                            // If we have a conversation ID but no client ID, try to extract it
                            if (clientId == null && conversationId != null) {
                                extractClientIdFromConversation(conversationId!!)
                            } else {
                                setupChatPartner()
                            }
                            return@addOnSuccessListener
                        }

                        // If not secretary or lawyer, assume client
                        userType = "client"
                        Log.d("ChatActivity", "Current user is a client")

                        // If we have a conversation ID but no secretary ID, try to extract it
                        if (secretaryId == null && conversationId != null) {
                            extractSecretaryIdFromConversation(conversationId!!)
                        } else {
                            setupChatPartner()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "Error checking lawyer status: ${e.message}")
                        // Fall back to client if can't determine
                        userType = "client"
                        setupChatPartner()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error determining user type: ${e.message}")
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun extractClientIdFromConversation(conversationId: String) {
        database.child("conversations").child(conversationId).child("participantIds")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (participantSnapshot in snapshot.children) {
                            val participantId = participantSnapshot.key
                            if (participantId != currentUserId) {
                                // Verify this is a client
                                database.child("Users").child(participantId!!).get()
                                    .addOnSuccessListener { userSnapshot ->
                                        if (userSnapshot.exists()) {
                                            clientId = participantId
                                            Log.d("ChatActivity", "Extracted clientId: $clientId from conversation")
                                            setupChatPartner()
                                        } else {
                                            // If not found in Users, just use the ID anyway
                                            clientId = participantId
                                            Log.d("ChatActivity", "Using participantId as clientId: $clientId")
                                            getClientName(clientId!!)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        // On failure, just use the ID
                                        clientId = participantId
                                        Log.d("ChatActivity", "Error finding client, using ID: $clientId")
                                        tvChatPartnerName.text = "Client"
                                        listenForMessages()
                                    }
                                break
                            }
                        }
                    } else {
                        Log.e("ChatActivity", "No participants found in conversation")
                        // Since we're showing existing conversations, we need a fallback
                        // Just use a default name and proceed
                        tvChatPartnerName.text = "Client"
                        listenForMessages()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching conversation participants: ${error.message}")
                    // Since we're showing existing conversations, provide fallback
                    tvChatPartnerName.text = "Client"
                    listenForMessages()
                }
            })
    }


    private fun extractSecretaryIdFromConversation(conversationId: String) {
        // First check if the conversation has an appointedLawyerId field
        database.child("conversations").child(conversationId).child("appointedLawyerId").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val lawyerId = snapshot.getValue(String::class.java)
                    if (!lawyerId.isNullOrEmpty()) {
                        secretaryId = lawyerId
                        Log.d("ChatActivity", "Found appointedLawyerId: $secretaryId")
                        getLawyerName(secretaryId!!)
                        return@addOnSuccessListener
                    }
                }

                // If no appointedLawyerId, check participants
                checkParticipantsForSecretaryOrLawyer(conversationId)
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error checking appointedLawyerId: ${e.message}")
                checkParticipantsForSecretaryOrLawyer(conversationId)
            }
    }

    private fun checkParticipantsForSecretaryOrLawyer(conversationId: String) {
        database.child("conversations").child(conversationId).child("participantIds")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (participantSnapshot in snapshot.children) {
                            val participantId = participantSnapshot.key
                            if (participantId != currentUserId) {
                                // Check if this is a lawyer
                                database.child("lawyers").child(participantId!!).get()
                                    .addOnSuccessListener { lawyerSnapshot ->
                                        if (lawyerSnapshot.exists()) {
                                            secretaryId = participantId
                                            Log.d("ChatActivity", "Found lawyer in participants: $secretaryId")
                                            getLawyerName(secretaryId!!)
                                        } else {
                                            // If not a lawyer, check if it's a secretary
                                            database.child("secretaries").child(participantId).get()
                                                .addOnSuccessListener { secretarySnapshot ->
                                                    if (secretarySnapshot.exists()) {
                                                        secretaryId = participantId
                                                        Log.d("ChatActivity", "Found secretary in participants: $secretaryId")
                                                        getSecretaryName(secretaryId!!)
                                                    } else {
                                                        // Just use the ID anyway
                                                        secretaryId = participantId
                                                        Log.d("ChatActivity", "Using participantId: $secretaryId")
                                                        tvChatPartnerName.text = "Lawyer"
                                                        listenForMessages()
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    // On error, just use the ID
                                                    secretaryId = participantId
                                                    Log.d("ChatActivity", "Error finding secretary, using ID: $secretaryId")
                                                    tvChatPartnerName.text = "Lawyer"
                                                    listenForMessages()
                                                }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        // On error, just use the ID
                                        secretaryId = participantId
                                        Log.d("ChatActivity", "Error finding lawyer, using ID: $secretaryId")
                                        tvChatPartnerName.text = "Lawyer"
                                        listenForMessages()
                                    }
                                break
                            }
                        }
                    } else {
                        Log.e("ChatActivity", "No participants found in conversation")
                        // Since we're showing existing conversations, provide fallback
                        tvChatPartnerName.text = "Lawyer"
                        listenForMessages()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching conversation participants: ${error.message}")
                    // Since we're showing existing conversations, provide fallback
                    tvChatPartnerName.text = "Lawyer"
                    listenForMessages()
                }
            })
    }

    // Update to the setupChatPartner method in ChatActivity
    private fun setupChatPartner() {
        if (userType == null) {
            // User type not determined yet, wait for determineCurrentUserType to complete
            return
        }

        when (userType) {
            "client" -> {
                // Client chatting with secretary or lawyer
                if (secretaryId != null) {
                    // First check if this ID is a secretary
                    database.child("secretaries").child(secretaryId!!).get()
                        .addOnSuccessListener { secretarySnapshot ->
                            if (secretarySnapshot.exists()) {
                                getSecretaryName(secretaryId!!)
                            } else {
                                // If not a secretary, check if this is a lawyer
                                database.child("lawyers").child(secretaryId!!).get()
                                    .addOnSuccessListener { lawyerSnapshot ->
                                        if (lawyerSnapshot.exists()) {
                                            getLawyerName(secretaryId!!)
                                        } else {
                                            Log.e("ChatActivity", "ID is neither secretary nor lawyer!")

                                            // If we're in create-on-first-message mode, this is expected
                                            if (createOnFirstMessage) {
                                                // We know this is supposed to be a lawyer, so just use the ID
                                                getLawyerName(secretaryId!!)
                                            } else {
                                                Toast.makeText(this, "Chat partner information not available", Toast.LENGTH_SHORT).show()
                                                finish()
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("ChatActivity", "Error checking lawyer: ${e.message}")

                                        // If we're in create-on-first-message mode, continue anyway
                                        if (createOnFirstMessage) {
                                            tvChatPartnerName.text = "Lawyer"
                                            listenForMessages()
                                        } else {
                                            Toast.makeText(this, "Failed to load chat partner information", Toast.LENGTH_SHORT).show()
                                            finish()
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatActivity", "Error checking secretary: ${e.message}")

                            // If we're in create-on-first-message mode, continue anyway
                            if (createOnFirstMessage) {
                                getLawyerName(secretaryId!!)
                            } else {
                                Toast.makeText(this, "Failed to load chat partner information", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                } else {
                    // If we have a conversation ID, try to determine who the client is chatting with
                    if (conversationId != null) {
                        determineConversationPartner()
                    } else {
                        Log.e("ChatActivity", "No secretary/lawyer ID or conversation ID provided!")
                        Toast.makeText(this, "Chat partner information not available", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            "secretary", "lawyer" -> {
                // Secretary or lawyer chatting with client
                if (clientId != null) {
                    getClientName(clientId!!)
                } else if (conversationId != null) {
                    // Try to extract client ID from conversation
                    extractClientIdFromConversation(conversationId!!)
                } else {
                    Log.e("ChatActivity", "No client ID or conversation ID provided!")
                    Toast.makeText(this, "Client information not available", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // Add this new method to determine the conversation partner from a conversation ID
    private fun determineConversationPartner() {
        val conversationRef = database.child("conversations").child(conversationId!!)

        conversationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Check if this is a conversation with a lawyer
                    val handledByLawyer = snapshot.child("handledByLawyer").getValue(Boolean::class.java) ?: false
                    val appointedLawyerId = snapshot.child("appointedLawyerId").getValue(String::class.java)

                    if (handledByLawyer && !appointedLawyerId.isNullOrEmpty()) {
                        // This is a conversation with a lawyer
                        secretaryId = appointedLawyerId
                        getLawyerName(appointedLawyerId)
                    } else {
                        // Check participants to find who the client is talking to
                        val participants = snapshot.child("participantIds").children
                        for (participant in participants) {
                            val participantId = participant.key
                            if (participantId != currentUserId) {
                                // Determine if this participant is a secretary or lawyer
                                checkParticipantType(participantId!!)
                                return@onDataChange
                            }
                        }
                    }
                } else {
                    Log.e("ChatActivity", "Conversation not found: $conversationId")
                    Toast.makeText(applicationContext, "Conversation data not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error loading conversation: ${error.message}")
                Toast.makeText(applicationContext, "Failed to load conversation", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }

    // Add this new helper method to check the type of a participant
    private fun checkParticipantType(participantId: String) {
        // First check if this is a secretary
        database.child("secretaries").child(participantId).get()
            .addOnSuccessListener { secretarySnapshot ->
                if (secretarySnapshot.exists()) {
                    // This is a secretary
                    secretaryId = participantId
                    getSecretaryName(participantId)
                } else {
                    // Check if this is a lawyer
                    database.child("lawyers").child(participantId).get()
                        .addOnSuccessListener { lawyerSnapshot ->
                            if (lawyerSnapshot.exists()) {
                                // This is a lawyer
                                secretaryId = participantId  // Reuse secretaryId field for the lawyer
                                getLawyerName(participantId)
                            } else {
                                // Not a secretary or lawyer, must be another client or unknown
                                Log.e("ChatActivity", "Participant is neither secretary nor lawyer: $participantId")
                                Toast.makeText(applicationContext, "Chat partner information not available", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatActivity", "Failed to check if participant is a lawyer: ${e.message}")
                            Toast.makeText(applicationContext, "Failed to load chat partner information", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to check if participant is a secretary: ${e.message}")
                Toast.makeText(applicationContext, "Failed to load chat partner information", Toast.LENGTH_SHORT).show()
                finish()
            }
    }


    private fun getClientName(clientId: String) {
        database.child("Users").child(clientId).get()
            .addOnSuccessListener { clientSnapshot ->
                if (clientSnapshot.exists()) {
                    val firstName = clientSnapshot.child("firstName").value?.toString() ?: "Unknown"
                    val lastName = clientSnapshot.child("lastName").value?.toString() ?: ""
                    val fullName = "$firstName $lastName".trim()
                    val imageUrl = clientSnapshot.child("profileImageUrl").value?.toString() ?: ""

                    tvChatPartnerName.text = fullName

                    // Load profile image
                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .circleCrop()
                            .into(profileImageView)
                    }

                    // Update conversation in Firebase
                    conversationId?.let { convId ->
                        val updates = hashMapOf<String, Any>(
                            "clientName" to fullName,
                            "clientImageUrl" to imageUrl
                        )
                        database.child("conversations").child(convId).updateChildren(updates)
                    }

                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Client not found!")
                    Toast.makeText(this, "Client information not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error fetching client data: ${e.message}")
                Toast.makeText(this, "Failed to load client information", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun getSecretaryName(secretaryId: String) {
        database.child("secretaries").child(secretaryId).get()
            .addOnSuccessListener { secretarySnapshot ->
                if (secretarySnapshot.exists()) {
                    val secretaryName = secretarySnapshot.child("name").value?.toString() ?: "Unknown"
                    val imageUrl = secretarySnapshot.child("profilePicture").value?.toString() ?: ""

                    tvChatPartnerName.text = secretaryName

                    // Load profile image
                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .circleCrop()
                            .into(profileImageView)
                    }

                    // Update conversation in Firebase
                    conversationId?.let { convId ->
                        val updates = hashMapOf<String, Any>(
                            "secretaryName" to secretaryName,
                            "secretaryImageUrl" to imageUrl
                        )
                        database.child("conversations").child(convId).updateChildren(updates)
                    }

                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Secretary not found!")
                    Toast.makeText(this, "Secretary information not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error fetching secretary data: ${e.message}")
                Toast.makeText(this, "Failed to load secretary information", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateConversationWithClientInfo(clientId: String, clientName: String, clientImageUrl: String) {
        // If you're maintaining a local list of conversations, update it here
        // Otherwise, this would update the conversation in Firebase

        // Example if you have a local list:
        /*
        conversationList.find { it.clientId == clientId }?.let { conversation ->
            conversation.clientName = clientName
            conversation.clientImageUrl = clientImageUrl
            notifyDataSetChanged()
        }
        */

        // For Firebase update:
        val updates = hashMapOf<String, Any>(
            "clientName" to clientName,
            "clientImageUrl" to clientImageUrl
        )

        database.child("conversations").child(conversationId ?: return).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Updated conversation with client info")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to update conversation with client info", e)
            }
    }

    private fun updateConversationWithSecretaryInfo(secretaryId: String, secretaryName: String, secretaryImageUrl: String) {
        // Similar to above but for secretary info

        val updates = hashMapOf<String, Any>(
            "secretaryName" to secretaryName,
            "secretaryImageUrl" to secretaryImageUrl
        )

        database.child("conversations").child(conversationId ?: return).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Updated conversation with secretary info")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to update conversation with secretary info", e)
            }
    }

    // Update the getLawyerName function to prioritize profileImageUrl
    private fun getLawyerName(lawyerId: String) {
        database.child("lawyers").child(lawyerId).get()
            .addOnSuccessListener { lawyerSnapshot ->
                if (lawyerSnapshot.exists()) {
                    val name = lawyerSnapshot.child("name").value?.toString() ?: ""
                    // Prioritize profileImageUrl, fall back to profileImage if needed
                    val imageUrl = lawyerSnapshot.child("profileImageUrl").value?.toString() ?:
                    lawyerSnapshot.child("profileImage").value?.toString() ?: ""

                    tvChatPartnerName.text = if (name.isNotEmpty()) name else "Lawyer"

                    // Load profile image
                    if (imageUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .circleCrop()
                            .into(profileImageView)
                    }

                    // Update conversation in Firebase with the proper image URL
                    conversationId?.let { convId ->
                        val updates = hashMapOf<String, Any>(
                            "secretaryName" to if (name.isNotEmpty()) name else "Lawyer",
                            "secretaryImageUrl" to imageUrl
                        )
                        database.child("conversations").child(convId).updateChildren(updates)
                    }

                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Lawyer not found!")
                    if (createOnFirstMessage) {
                        tvChatPartnerName.text = "Lawyer"
                        listenForMessages()
                    } else {
                        Toast.makeText(this, "Lawyer information not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Error fetching lawyer data: ${e.message}")
                if (createOnFirstMessage) {
                    tvChatPartnerName.text = "Lawyer"
                    listenForMessages()
                } else {
                    Toast.makeText(this, "Failed to load lawyer information", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
    }


    private fun sendMessage() {
        val messageText = etMessageInput.text.toString().trim()
        if (messageText.isEmpty() || currentUserId == null) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            resetSendButton()
            return
        }

        val receiverId = determineReceiverId() ?: run {
            Toast.makeText(this, "Recipient not identified", Toast.LENGTH_SHORT).show()
            resetSendButton()
            return
        }

        // Update last sent message tracking
        lastSentMessage = messageText
        lastSentTimestamp = System.currentTimeMillis()

        // First get the sender's profile image URL
        getCurrentUserImageUrl { imageUrl ->
            val conversationId = conversationId ?: generateConversationId(currentUserId!!, receiverId)
            val messageId = database.child("conversations").child(conversationId).child("messages").push().key!!

            val message = Message(
                senderId = currentUserId!!,
                receiverId = receiverId,
                message = messageText,
                timestamp = lastSentTimestamp,
                senderImageUrl = imageUrl
            )

            // Add to local list immediately for instant UI feedback
            runOnUiThread {
                messagesList.add(message)
                messagesAdapter.notifyItemInserted(messagesList.size - 1)
                rvChatMessages.scrollToPosition(messagesList.size - 1)
                etMessageInput.text.clear()
            }

            // Send to Firebase
            database.child("conversations").child(conversationId).child("messages").child(messageId)
                .setValue(message)
                .addOnSuccessListener {
                    incrementUnreadCounter(conversationId, receiverId)
                    createNotificationForRecipient(message)
                    resetSendButton()
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Failed to send message", e)
                    // Remove from local list if failed
                    runOnUiThread {
                        messagesList.removeAll { it.timestamp == message.timestamp && it.message == message.message }
                        messagesAdapter.notifyDataSetChanged()
                        Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                    resetSendButton()
                }
        }
    }

    private fun resetSendButton() {
        isSending = false
        btnSendMessage.isEnabled = true
    }

    private fun getCurrentUserImageUrl(callback: (String?) -> Unit) {
        when (userType) {
            "client" -> {
                database.child("Users").child(currentUserId!!).child("profileImageUrl").get()
                    .addOnSuccessListener { snapshot ->
                        callback(snapshot.getValue(String::class.java))
                    }
                    .addOnFailureListener {
                        callback(null)
                    }
            }
            "secretary" -> {
                database.child("secretaries").child(currentUserId!!).child("profilePicture").get()
                    .addOnSuccessListener { snapshot ->
                        callback(snapshot.getValue(String::class.java))
                    }
                    .addOnFailureListener {
                        callback(null)
                    }
            }
            "lawyer" -> {
                database.child("lawyers").child(currentUserId!!).get()
                    .addOnSuccessListener { snapshot ->
                        // Prioritize profileImageUrl, fall back to profileImage
                        val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                            ?: snapshot.child("profileImage").getValue(String::class.java)
                        callback(imageUrl)
                    }
                    .addOnFailureListener {
                        callback(null)
                    }
            }
            else -> callback(null)
        }
    }


    private fun determineReceiverId(): String? {
        return when (userType) {
            "client" -> secretaryId
            "secretary", "lawyer" -> clientId
            else -> null
        }
    }

    private fun createNotificationForRecipient(message: Message) {
        val recipientId = message.receiverId
        val convId = generateConversationId(message.senderId, message.receiverId)

        // First get the sender name for the notification
        getSenderName { senderName ->
            // Create notification with ID
            val notificationRef = database.child("notifications").child(recipientId).push()
            val notificationId = notificationRef.key ?: return@getSenderName

            val notification = hashMapOf(
                "id" to notificationId,  // Match the format in SetAppointmentActivity
                "senderId" to message.senderId,
                "senderName" to senderName,
                "message" to message.message,
                "timestamp" to message.timestamp,
                "type" to "message",
                "isRead" to false,
                "conversationId" to convId
            )

            // Add notification to recipient's notifications list
            notificationRef.setValue(notification)
                .addOnSuccessListener {
                    Log.d("ChatActivity", "Notification created for recipient: $recipientId")
                    incrementUnreadCounter(convId, recipientId)
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Failed to create notification: ${e.message}")
                }
        }
    }

    // Add method to get the sender name for notifications
    private fun getSenderName(callback: (String) -> Unit) {
        if (currentUserId == null) {
            callback("Unknown")
            return
        }

        when (userType) {
            "client" -> {
                database.child("Users").child(currentUserId!!).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                            val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                            val fullName = "$firstName $lastName".trim()
                            callback(fullName)
                        } else {
                            callback("Client")
                        }
                    }
                    .addOnFailureListener {
                        callback("Client")
                    }
            }
            "secretary" -> {
                database.child("secretaries").child(currentUserId!!).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val name = snapshot.child("name").getValue(String::class.java) ?: "Secretary"
                            callback(name)
                        } else {
                            callback("Secretary")
                        }
                    }
                    .addOnFailureListener {
                        callback("Secretary")
                    }
            }
            "lawyer" -> {
                database.child("lawyers").child(currentUserId!!).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                            val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                            val fullName = "$firstName $lastName".trim()
                            callback(if (fullName.isNotEmpty()) fullName else "Lawyer")
                        } else {
                            callback("Lawyer")
                        }
                    }
                    .addOnFailureListener {
                        callback("Lawyer")
                    }
            }
            else -> callback("Unknown")
        }
    }

    private fun incrementUnreadCounter(conversationId: String, userId: String) {
        val unreadRef = database.child("conversations").child(conversationId)
            .child("unreadMessages").child(userId)

        unreadRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                unreadRef.setValue(currentCount + 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error updating unread count: ${error.message}")
            }
        })
    }

    private fun generateConversationId(user1: String, user2: String): String {
        return com.remedio.weassist.Utils.ConversationUtils.generateConversationId(user1, user2)
    }

    private fun listenForMessages() {
        if (currentUserId == null) {
            Log.e("ChatActivity", "Current user ID is null!")
            return
        }

        val receiverId = determineReceiverId()
        if (receiverId == null) {
            Log.e("ChatActivity", "Could not determine receiver ID for messages")
            return
        }

        val actualConversationId = conversationId ?: generateConversationId(currentUserId!!, receiverId)
        Log.d("ChatActivity", "Listening for messages with conversationId: $actualConversationId")

        val messagesRef = database.child("conversations").child(actualConversationId).child("messages")

        // First load existing messages
        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempMessages = mutableListOf<Message>()
                Log.d("ChatActivity", "Loaded ${snapshot.childrenCount} messages")

                for (messageSnapshot in snapshot.children) {
                    try {
                        val messageId = messageSnapshot.key ?: continue

                        // Skip if we've already processed this message
                        if (messageId in receivedMessageIds) continue
                        receivedMessageIds.add(messageId)

                        val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                        val receiverId = messageSnapshot.child("receiverId").getValue(String::class.java)
                        val messageText = messageSnapshot.child("message").getValue(String::class.java) ?: ""
                        val timestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                        if (senderId == "system") {
                            tempMessages.add(Message(
                                senderId = "system",
                                receiverId = "",
                                message = messageText,
                                timestamp = timestamp
                            ))
                        } else if (!senderId.isNullOrEmpty() && messageText.isNotEmpty()) {
                            fetchSenderDetails(senderId) { senderName, imageUrl ->
                                val message = Message(
                                    senderId = senderId,
                                    receiverId = receiverId ?: "",
                                    message = messageText,
                                    timestamp = timestamp,
                                    senderName = senderName,
                                    senderImageUrl = imageUrl
                                )

                                synchronized(tempMessages) {
                                    tempMessages.add(message)

                                    if (tempMessages.size.toLong() == snapshot.childrenCount) {
                                        updateMessagesList(tempMessages.sortedBy { it.timestamp })
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatActivity", "Error parsing message: ${e.message}")
                    }
                }

                if (snapshot.childrenCount == 0L) {
                    updateMessagesList(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error loading messages: ${error.message}")
            }
        })

        // Set up real-time listener for new messages
        messagesRef.orderByChild("timestamp").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val messageId = snapshot.key ?: return

                // Skip if we've already processed this message
                if (messageId in receivedMessageIds) return
                receivedMessageIds.add(messageId)

                try {
                    val senderId = snapshot.child("senderId").getValue(String::class.java)
                    val receiverId = snapshot.child("receiverId").getValue(String::class.java)
                    val messageText = snapshot.child("message").getValue(String::class.java) ?: ""
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    // Skip if we already have a message with this exact timestamp and content
                    if (messagesList.any { it.timestamp == timestamp && it.message == messageText }) {
                        return
                    }

                    if (senderId == "system") {
                        val systemMessage = Message(
                            senderId = "system",
                            receiverId = "",
                            message = messageText,
                            timestamp = timestamp
                        )
                        addMessageInOrder(systemMessage)
                    } else if (!senderId.isNullOrEmpty() && messageText.isNotEmpty()) {
                        fetchSenderDetails(senderId) { senderName, imageUrl ->
                            val message = Message(
                                senderId = senderId,
                                receiverId = receiverId ?: "",
                                message = messageText,
                                timestamp = timestamp,
                                senderName = senderName,
                                senderImageUrl = imageUrl
                            )
                            addMessageInOrder(message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error parsing new message: ${e.message}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Handle message updates if needed
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Handle message removal if needed
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not used
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Message listener cancelled: ${error.message}")
            }
        })
    }
    private fun updateMessagesList(newMessages: List<Message>) {
        runOnUiThread {
            messagesList.clear()
            messagesList.addAll(newMessages)
            messagesAdapter.notifyDataSetChanged()

            if (messagesList.isNotEmpty()) {
                rvChatMessages.scrollToPosition(messagesList.size - 1)
            }
        }
    }


    private fun setupRealTimeMessageListener(messagesRef: DatabaseReference) {
        // Keep track of messages we've already processed
        val processedMessageIds = mutableSetOf<String>()

        messagesRef.orderByChild("timestamp").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Get message ID and skip if already processed
                val messageId = snapshot.key ?: ""
                if (messageId in processedMessageIds || pendingMessageId == messageId) {
                    return
                }
                processedMessageIds.add(messageId)

                try {
                    val senderId = snapshot.child("senderId").getValue(String::class.java)
                    val receiverId = snapshot.child("receiverId").getValue(String::class.java)
                    val messageText = snapshot.child("message").getValue(String::class.java) ?: ""
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    // Skip if we already have a message with this exact timestamp
                    if (messagesList.any { it.timestamp == timestamp }) {
                        return
                    }

                    if (senderId == "system") {
                        // System message
                        val systemMessage = Message(
                            senderId = "system",
                            receiverId = "",
                            message = messageText,
                            timestamp = timestamp
                        )

                        runOnUiThread {
                            addMessageInOrder(systemMessage)
                        }
                    } else if (!senderId.isNullOrEmpty() && messageText.isNotEmpty()) {
                        // Regular message
                        fetchSenderDetails(senderId) { senderName, imageUrl ->
                            val message = Message(
                                senderId = senderId,
                                receiverId = receiverId ?: "",
                                message = messageText,
                                timestamp = timestamp,
                                senderName = senderName,
                                senderImageUrl = imageUrl
                            )

                            runOnUiThread {
                                addMessageInOrder(message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error parsing new message: ${e.message}")
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Message content changed (rare, but possible)
                // Implementation omitted for brevity
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Message deleted (rare, but possible)
                // Implementation omitted for brevity
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not used
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Message listener cancelled: ${error.message}")
            }
        })
    }

    // Sender details cache for better performance
    private val senderDetailsCache = mutableMapOf<String, Pair<String?, String?>>()

    private fun fetchSenderDetails(senderId: String, callback: (String?, String?) -> Unit) {
        // Check cache first
        senderDetailsCache[senderId]?.let {
            callback(it.first, it.second)
            return
        }

        // For system messages
        if (senderId == "system") {
            callback("System", null)
            return
        }

        // Check if this is a conversation with a lawyer handling a forwarded case
        conversationId?.let { convId ->
            database.child("conversations").child(convId).get().addOnSuccessListener { snapshot ->
                val handledByLawyer = snapshot.child("handledByLawyer").getValue(Boolean::class.java) ?: false
                val forwardedFromSecretary = snapshot.child("forwardedFromSecretary").getValue(Boolean::class.java) ?: false
                val appointedLawyerId = snapshot.child("appointedLawyerId").getValue(String::class.java)

                // Check if this is a forwarded conversation and the sender matches the appointed lawyer
                if (handledByLawyer && forwardedFromSecretary && senderId == appointedLawyerId) {
                    // This is definitely a lawyer in a forwarded conversation
                    fetchLawyerDetails(senderId) { name, imageUrl ->
                        senderDetailsCache[senderId] = Pair(name, imageUrl)
                        callback(name, imageUrl)
                    }
                    return@addOnSuccessListener
                }

                // Continue with normal sender checks
                proceedWithNormalSenderChecks(senderId, callback)
            }.addOnFailureListener {
                // On error, proceed with normal checks
                proceedWithNormalSenderChecks(senderId, callback)
            }
        } ?: proceedWithNormalSenderChecks(senderId, callback)
    }

    private fun proceedWithNormalSenderChecks(senderId: String, callback: (String?, String?) -> Unit) {
        when {
            senderId == currentUserId -> {
                // Current user
                when (userType) {
                    "client" -> fetchClientDetails(senderId) { name, imageUrl ->
                        senderDetailsCache[senderId] = Pair(name, imageUrl)
                        callback(name, imageUrl)
                    }
                    "secretary" -> fetchSecretaryDetails(senderId) { name, imageUrl ->
                        senderDetailsCache[senderId] = Pair(name, imageUrl)
                        callback(name, imageUrl)
                    }
                    "lawyer" -> fetchLawyerDetails(senderId) { name, imageUrl ->
                        senderDetailsCache[senderId] = Pair(name, imageUrl)
                        callback(name, imageUrl)
                    }
                    else -> callback(null, null)
                }
            }
            senderId == secretaryId -> {
                // Check if this is actually a lawyer ID (for forwarded conversations)
                database.child("lawyers").child(senderId).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        fetchLawyerDetails(senderId) { name, imageUrl ->
                            senderDetailsCache[senderId] = Pair(name, imageUrl)
                            callback(name, imageUrl)
                        }
                    } else {
                        fetchSecretaryDetails(senderId) { name, imageUrl ->
                            senderDetailsCache[senderId] = Pair(name, imageUrl)
                            callback(name, imageUrl)
                        }
                    }
                }.addOnFailureListener {
                    // On error, assume it's a secretary
                    fetchSecretaryDetails(senderId) { name, imageUrl ->
                        senderDetailsCache[senderId] = Pair(name, imageUrl)
                        callback(name, imageUrl)
                    }
                }
            }
            senderId == lawyerId -> {
                // Lawyer
                fetchLawyerDetails(senderId) { name, imageUrl ->
                    senderDetailsCache[senderId] = Pair(name, imageUrl)
                    callback(name, imageUrl)
                }
            }
            senderId == clientId -> {
                // Client
                fetchClientDetails(senderId) { name, imageUrl ->
                    senderDetailsCache[senderId] = Pair(name, imageUrl)
                    callback(name, imageUrl)
                }
            }
            else -> {
                // Unknown sender, try all types
                tryAllUserTypes(senderId) { name, imageUrl ->
                    senderDetailsCache[senderId] = Pair(name, imageUrl)
                    callback(name, imageUrl)
                }
            }
        }
    }

    private fun fetchClientDetails(userId: String, callback: (String?, String?) -> Unit) {
        database.child("Users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                    val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                    val name = "$firstName $lastName".trim()
                    val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    callback(name, imageUrl)
                } else {
                    callback("Client", null)
                }
            }
            .addOnFailureListener {
                callback("Client", null)
            }
    }

    private fun fetchSecretaryDetails(userId: String, callback: (String?, String?) -> Unit) {
        database.child("secretaries").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "Secretary"
                    val imageUrl = snapshot.child("profilePicture").getValue(String::class.java)
                    callback(name, imageUrl)
                } else {
                    callback("Secretary", null)
                }
            }
            .addOnFailureListener {
                callback("Secretary", null)
            }
    }

    private fun fetchLawyerDetails(userId: String, callback: (String?, String?) -> Unit) {
        database.child("lawyers").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // First try the "name" field
                    var name = snapshot.child("name").getValue(String::class.java)

                    // If name is null or empty, try constructing from firstName and lastName
                    if (name.isNullOrEmpty()) {
                        val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                        name = "$firstName $lastName".trim()
                    }

                    // Don't add any prefix to lawyer names
                    if (name.isNullOrEmpty()) {
                        name = "Lawyer"
                    }

                    // Try multiple image fields to handle inconsistencies
                    val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                        ?: snapshot.child("profileImage").getValue(String::class.java)

                    callback(name, imageUrl)
                } else {
                    callback("Lawyer", null)
                }
            }
            .addOnFailureListener {
                callback("Lawyer", null)
            }
    }

    private fun tryAllUserTypes(userId: String, callback: (String?, String?) -> Unit) {
        // Try client first
        fetchClientDetails(userId) { clientName, clientImageUrl ->
            if (clientName != "Client" || clientImageUrl != null) {
                // Found valid client details
                callback(clientName, clientImageUrl)
            } else {
                // Try secretary
                fetchSecretaryDetails(userId) { secretaryName, secretaryImageUrl ->
                    if (secretaryName != "Secretary" || secretaryImageUrl != null) {
                        // Found valid secretary details
                        callback(secretaryName, secretaryImageUrl)
                    } else {
                        // Try lawyer
                        fetchLawyerDetails(userId) { lawyerName, lawyerImageUrl ->
                            callback(lawyerName, lawyerImageUrl)
                        }
                    }
                }
            }
        }
    }

    private fun addMessageInOrder(message: Message) {
        runOnUiThread {
            // Find correct position by timestamp
            val position = messagesList.binarySearch { it.timestamp.compareTo(message.timestamp) }
            val insertPosition = if (position < 0) -(position + 1) else position

            // Only add if not already present
            if (insertPosition >= messagesList.size || messagesList[insertPosition].timestamp != message.timestamp ||
                messagesList[insertPosition].message != message.message) {

                messagesList.add(insertPosition, message)
                messagesAdapter.notifyItemInserted(insertPosition)

                // Scroll to bottom if inserting at the end
                if (insertPosition == messagesList.size - 1) {
                    rvChatMessages.scrollToPosition(messagesList.size - 1)
                }
            }
        }
    }

    private fun fetchSenderImageUrl(senderId: String, callback: (String?) -> Unit) {
        if (senderId == currentUserId) {
            // For current user, we can get the image from our own profile
            when (userType) {
                "client" -> {
                    database.child("Users").child(senderId).child("profileImageUrl").get()
                        .addOnSuccessListener { snapshot ->
                            callback(snapshot.getValue(String::class.java))
                        }
                        .addOnFailureListener {
                            callback(null)
                        }
                }
                "secretary" -> {
                    database.child("secretaries").child(senderId).child("profilePicture").get()
                        .addOnSuccessListener { snapshot ->
                            callback(snapshot.getValue(String::class.java))
                        }
                        .addOnFailureListener {
                            callback(null)
                        }
                }
                "lawyer" -> {
                    database.child("lawyers").child(senderId).get()
                        .addOnSuccessListener { snapshot ->
                            // Prioritize profileImageUrl, fall back to profileImage
                            val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                                ?: snapshot.child("profileImage").getValue(String::class.java)
                            callback(imageUrl)
                        }
                        .addOnFailureListener {
                            callback(null)
                        }
                }
                else -> callback(null)
            }
        } else {
            // For other users, determine their type first
            determineUserType(senderId) { userType ->
                when (userType) {
                    "client" -> {
                        database.child("Users").child(senderId).child("profileImageUrl").get()
                            .addOnSuccessListener { snapshot ->
                                callback(snapshot.getValue(String::class.java))
                            }
                            .addOnFailureListener {
                                callback(null)
                            }
                    }
                    "secretary" -> {
                        database.child("secretaries").child(senderId).child("profilePicture").get()
                            .addOnSuccessListener { snapshot ->
                                callback(snapshot.getValue(String::class.java))
                            }
                            .addOnFailureListener {
                                callback(null)
                            }
                    }
                    "lawyer" -> {
                        database.child("lawyers").child(senderId).get()
                            .addOnSuccessListener { snapshot ->
                                // Prioritize profileImageUrl, fall back to profileImage
                                val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                                    ?: snapshot.child("profileImage").getValue(String::class.java)
                                callback(imageUrl)
                            }
                            .addOnFailureListener {
                                callback(null)
                            }
                    }
                    else -> callback(null)
                }
            }
        }
    }

    // Add this helper function to determine a user's type
    private fun determineUserType(userId: String, callback: (String?) -> Unit) {
        // Check if secretary
        database.child("secretaries").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    callback("secretary")
                    return@addOnSuccessListener
                }

                // Check if lawyer
                database.child("lawyers").child(userId).get()
                    .addOnSuccessListener { lawyerSnapshot ->
                        if (lawyerSnapshot.exists()) {
                            callback("lawyer")
                        } else {
                            // Assume client if neither
                            callback("client")
                        }
                    }
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun checkForwardedStatus(conversationId: String) {
        database.child("conversations").child(conversationId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Check multiple flags that might indicate forwarding
                    val isForwarded = snapshot.child("forwarded").getValue(Boolean::class.java) ?: false
                    val forwardedFromSecretary = snapshot.child("forwardedFromSecretary").getValue(Boolean::class.java) ?: false
                    val handledByLawyer = snapshot.child("handledByLawyer").getValue(Boolean::class.java) ?: false
                    val secretaryActive = snapshot.child("secretaryActive").getValue(Boolean::class.java) ?: true

                    if ((isForwarded || !secretaryActive) && userType == "secretary") {
                        // This conversation has been forwarded, disable input for secretary
                        disableChatInput("This conversation has been forwarded to a lawyer.")

                        // Show a toast to explain
                        Toast.makeText(
                            this@ChatActivity,
                            "This conversation has been forwarded to the lawyer and is now read-only.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (isForwarded && userType == "client" && !forwardedFromSecretary) {
                        // Client in original secretary conversation - also read-only
                        disableChatInput("Please use the lawyer conversation instead.")

                        Toast.makeText(
                            this@ChatActivity,
                            "This conversation has been forwarded to a lawyer. Please check your messages for the new conversation.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (forwardedFromSecretary && handledByLawyer) {
                        // This is a lawyer-client conversation that was forwarded
                        if (userType == "lawyer") {
                            // Lawyer view - normal messaging
                            enableChatInput()
                        } else if (userType == "client") {
                            // Client view in lawyer conversation - normal messaging
                            enableChatInput()
                        }
                    } else {
                        // Normal conversation
                        enableChatInput()
                    }

                    // Continue with the chat setup
                    continueWithChatSetup()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error checking forwarding status: ${error.message}")
                    // Continue anyway, enabling input
                    enableChatInput()
                    continueWithChatSetup()
                }
            })
    }

    // Helper methods for enabling/disabling chat input
    private fun disableChatInput(message: String) {
        etMessageInput.isEnabled = false
        btnSendMessage.isEnabled = false
        etMessageInput.hint = message

        // Set a gray background to indicate disabled state
        etMessageInput.setBackgroundResource(android.R.color.darker_gray)
    }

    private fun enableChatInput() {
        etMessageInput.isEnabled = true
        btnSendMessage.isEnabled = true
        etMessageInput.hint = "Type a message..."

        // Restore normal background
        etMessageInput.setBackgroundResource(android.R.color.white)
    }

    private fun continueWithChatSetup() {
        if (clientId == null && conversationId != null) {
            extractClientIdFromConversation(conversationId!!)
        } else if (secretaryId == null && conversationId != null) {
            extractSecretaryIdFromConversation(conversationId!!)
        } else {
            setupChatPartner()
        }
    }

    // Update the existing disableMessaging method to accept a message parameter with default value
    private fun disableMessaging(message: String = "This conversation has been forwarded to the lawyer.") {
        // Disable the input field and send button
        etMessageInput.isEnabled = false
        btnSendMessage.isEnabled = false

        // Change hint text to indicate messaging is disabled
        etMessageInput.hint = message

        // Optional: Add a visual indicator that the conversation is read-only
        etMessageInput.setBackgroundResource(R.drawable.bg_input_disabled) // Create this drawable
    }

    // This is where you handle the result of the file picker
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // You can dynamically check for the request code and process it accordingly
        if (resultCode == RESULT_OK && requestCode != 0) { // Check if the requestCode is valid (non-zero)
            val fileUri: Uri? = data?.data
            fileUri?.let {
                // Handle the selected file URI
                // For example, you can upload it to Firebase or show it in your UI
                Toast.makeText(this, "File Selected: $fileUri", Toast.LENGTH_SHORT).show()
            }
        }
    }
}