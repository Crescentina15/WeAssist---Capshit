package com.remedio.weassist.MessageConversation

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.Clients.Message
import com.remedio.weassist.Clients.MessageAdapter
import com.remedio.weassist.R

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

    // Chat participant IDs
    private var clientId: String? = null
    private var secretaryId: String? = null
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var conversationId: String? = null

    // Current user type
    private var userType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        tvChatPartnerName = findViewById(R.id.name_secretary)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        backButton = findViewById(R.id.back_button)

        database = FirebaseDatabase.getInstance().reference
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Get all possible IDs from intent
        conversationId = intent.getStringExtra("CONVERSATION_ID")
        secretaryId = intent.getStringExtra("SECRETARY_ID")
        clientId = intent.getStringExtra("CLIENT_ID")
        userType = intent.getStringExtra("USER_TYPE")
        val appointmentId = intent.getStringExtra("APPOINTMENT_ID")

        Log.d("ChatActivity", "Received in intent - conversationId: $conversationId, secretaryId: $secretaryId, clientId: $clientId, appointmentId: $appointmentId, userType: $userType")

        // Set up RecyclerView
        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

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
                isSending = true
                sendMessage()
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

    private fun getLawyerName(lawyerId: String) {
        database.child("lawyers").child(lawyerId).get()
            .addOnSuccessListener { lawyerSnapshot ->
                if (lawyerSnapshot.exists()) {
                    val name = lawyerSnapshot.child("name").value?.toString() ?: ""
                    val imageUrl = lawyerSnapshot.child("profileImage").value?.toString() ?: ""

                    tvChatPartnerName.text = if (name.isNotEmpty()) name else "Lawyer"

                    // Update conversation in Firebase
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
            return
        }

        val receiverId = determineReceiverId() ?: run {
            Toast.makeText(this, "Recipient not identified", Toast.LENGTH_SHORT).show()
            return
        }

        val conversationId = conversationId ?: generateConversationId(currentUserId!!, receiverId)
        val messageId = database.child("conversations").child(conversationId).child("messages").push().key!!
        pendingMessageId = messageId

        val message = Message(
            senderId = currentUserId!!,
            receiverId = receiverId,
            message = messageText,
            timestamp = System.currentTimeMillis()
        )

        // Add to local list immediately for instant UI feedback
        messagesList.add(message)
        messagesAdapter.notifyItemInserted(messagesList.size - 1)
        rvChatMessages.scrollToPosition(messagesList.size - 1)
        etMessageInput.text.clear()

        // Send to Firebase
        database.child("conversations").child(conversationId).child("messages").child(messageId)
            .setValue(message)
            .addOnSuccessListener {
                pendingMessageId = null
                incrementUnreadCounter(conversationId, receiverId)
                createNotificationForRecipient(message)
            }
            .addOnFailureListener {
                // Remove from local list if failed
                messagesList.removeAll { it.timestamp == message.timestamp }
                messagesAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createConversationWithMessage(conversationId: String, message: Message, receiverId: String) {
        // Create conversation structure first
        val participantIds = mapOf(
            currentUserId!! to true,
            receiverId to true
        )

        val unreadMessages = mapOf(
            currentUserId!! to 0,
            receiverId to 1  // One unread message for recipient
        )

        // Determine if this is a lawyer conversation
        val isLawyerConversation = userType == "client" && secretaryId != null

        val conversationData = if (isLawyerConversation) {
            mapOf(
                "participantIds" to participantIds,
                "unreadMessages" to unreadMessages,
                "appointedLawyerId" to receiverId,
                "handledByLawyer" to true
            )
        } else {
            mapOf(
                "participantIds" to participantIds,
                "unreadMessages" to unreadMessages
            )
        }

        // Create the conversation and then add the message
        val conversationRef = database.child("conversations").child(conversationId)
        conversationRef.setValue(conversationData)
            .addOnSuccessListener {
                // Now add the message
                val chatRef = database.child("conversations").child(conversationId).child("messages").push()
                chatRef.setValue(message)
                    .addOnSuccessListener {
                        Log.d("ChatActivity", "Message sent successfully in new conversation")
                        etMessageInput.text.clear()
                        isSending = false // Reset flag

                        // Create notification for recipient
                        createNotificationForRecipient(message)

                        // Force refresh the messages list
                        listenForMessages()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "Failed to send message in new conversation: ${e.message}")
                        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                        isSending = false // Reset flag
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to create conversation: ${e.message}")
                Toast.makeText(this, "Failed to create conversation", Toast.LENGTH_SHORT).show()
                isSending = false // Reset flag
            }
    }

    private fun addMessageToExistingConversation(conversationId: String, message: Message) {
        val chatRef = database.child("conversations").child(conversationId).child("messages").push()
        Log.d("ChatActivity", "Push reference generated: ${chatRef.key}")

        chatRef.setValue(message)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Message sent successfully")
                etMessageInput.text.clear()
                isSending = false // Reset flag

                // Increment unread counter for recipient
                incrementUnreadCounter(conversationId, message.receiverId)

                // Create notification for recipient
                createNotificationForRecipient(message)

                // Force refresh the messages list
                listenForMessages()
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to send message: ${e.message}")
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                isSending = false // Reset flag
            }
    }

    private fun addMessageToConversation(conversationId: String, message: Message) {
        val chatRef = database.child("conversations").child(conversationId).child("messages").push()
        Log.d("ChatActivity", "Push reference generated: ${chatRef.key}")

        chatRef.setValue(message)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Message sent successfully")
                etMessageInput.text.clear()  // Clear the input field

                // Increment unread counter for recipient
                incrementUnreadCounter(conversationId, message.receiverId)

                // Create notification for recipient
                createNotificationForRecipient(message)

                // Force refresh the messages list
                listenForMessages()
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to send message: ${e.message}")
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
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
        return if (user1 < user2) {
            "conversation_${user1}_${user2}"
        } else {
            "conversation_${user2}_${user1}"
        }
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

        messagesRef.get().addOnSuccessListener { snapshot ->
            Log.d("ChatActivity", "Database check: ${if (snapshot.exists()) "Messages exist" else "No messages found"}")

            if (snapshot.exists()) {
                Log.d("ChatActivity", "Number of messages in database: ${snapshot.childrenCount}")

                createOnFirstMessage = false

                database.child("conversations").child(actualConversationId)
                    .child("unreadMessages")
                    .child(currentUserId!!)
                    .setValue(0)

                val firstMsg = snapshot.children.firstOrNull()
                if (firstMsg != null) {
                    val msgText = firstMsg.child("message").getValue(String::class.java)
                    val senderId = firstMsg.child("senderId").getValue(String::class.java)
                    Log.d("ChatActivity", "Sample message - Text: $msgText, Sender: $senderId")
                    Log.d("ChatActivity", "First message raw data: ${firstMsg.value}")
                }
            } else if (createOnFirstMessage) {
                Log.d("ChatActivity", "Conversation doesn't exist yet, waiting for first message")
                messagesList.clear()
                messagesAdapter.notifyDataSetChanged()
            } else {
                Log.d("ChatActivity", "No messages found in conversation: $actualConversationId")
            }
        }

        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newMessages = mutableListOf<Message>()

                Log.d("ChatActivity", "ValueEventListener fired, message count: ${snapshot.childrenCount}")

                if (!snapshot.exists()) {
                    Log.d("ChatActivity", "No messages found in conversation: $actualConversationId")
                    messagesList.clear()
                    messagesAdapter.notifyDataSetChanged()
                    return
                }

                for (messageSnapshot in snapshot.children) {
                    try {
                        // Skip the pending message we're currently sending
                        if (messageSnapshot.key == pendingMessageId) {
                            continue
                        }

                        val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                        val messageText = messageSnapshot.child("message").getValue(String::class.java) ?: ""
                        val timestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                        if (senderId == "system") {
                            val systemMessage = Message(
                                senderId = "system",
                                receiverId = "",
                                message = messageText,
                                timestamp = timestamp
                            )
                            newMessages.add(systemMessage)
                            Log.d("ChatActivity", "Added system message: $messageText")
                        } else if (!senderId.isNullOrEmpty() && messageText.isNotEmpty()) {
                            val receiverId = messageSnapshot.child("receiverId").getValue(String::class.java) ?: ""

                            // Fetch sender's image URL based on their user type
                            fetchSenderImageUrl(senderId) { imageUrl ->
                                val message = Message(
                                    senderId = senderId,
                                    receiverId = receiverId,
                                    message = messageText,
                                    timestamp = timestamp,
                                    senderName = null,
                                    senderImageUrl = imageUrl
                                )
                                newMessages.add(message)
                                Log.d("ChatActivity", "Added regular message: $messageText from $senderId with image: $imageUrl")

                                // Only update UI if this is the last message being processed
                                if (newMessages.size == snapshot.children.count { it.key != pendingMessageId }) {
                                    messagesList.clear()
                                    messagesList.addAll(newMessages.sortedBy { it.timestamp })
                                    messagesAdapter.notifyDataSetChanged()

                                    if (messagesList.isNotEmpty()) {
                                        rvChatMessages.post {
                                            rvChatMessages.scrollToPosition(messagesList.size - 1)
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.e("ChatActivity", "Invalid message data: senderId=$senderId, message=$messageText")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatActivity", "Error parsing message: ${e.message}")
                        e.printStackTrace()
                    }
                }

                // Handle messages without images (system messages)
                if (newMessages.isNotEmpty() && newMessages.any { it.senderImageUrl == null }) {
                    messagesList.clear()
                    messagesList.addAll(newMessages.sortedBy { it.timestamp })
                    messagesAdapter.notifyDataSetChanged()

                    if (messagesList.isNotEmpty()) {
                        rvChatMessages.post {
                            rvChatMessages.scrollToPosition(messagesList.size - 1)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error fetching messages: ${error.message}")
                Toast.makeText(applicationContext, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        })
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
                    database.child("lawyers").child(senderId).child("profileImage").get()
                        .addOnSuccessListener { snapshot ->
                            callback(snapshot.getValue(String::class.java))
                        }
                        .addOnFailureListener {
                            callback(null)
                        }
                }
                else -> callback(null)
            }
        } else {
            // For other users, we need to determine their type first
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
                        database.child("lawyers").child(senderId).child("profileImage").get()
                            .addOnSuccessListener { snapshot ->
                                callback(snapshot.getValue(String::class.java))
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

    // Updated checkForwardedStatus method combining your current implementation with the enhancements
    private fun checkForwardedStatus(conversationId: String) {
        // First check the forwarded flag at its specific location
        database.child("conversations").child(conversationId)
            .child("forwarded").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isForwarded = snapshot.getValue(Boolean::class.java) ?: false

                    if (isForwarded) {
                        // Disable messaging in forwarded conversations
                        disableMessaging() // Use the existing method without parameters

                        // Show a toast since we can't easily add UI elements without modifying layout
                        Toast.makeText(
                            applicationContext,
                            "This conversation has been forwarded to the lawyer.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // If not forwarded, check additional flags without using Toast
                        database.child("conversations").child(conversationId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(rootSnapshot: DataSnapshot) {
                                    // Check for the handledByLawyer flag
                                    val handledByLawyer = rootSnapshot.child("handledByLawyer").getValue(Boolean::class.java) ?: false
                                    val originalConversationId = rootSnapshot.child("originalConversationId").getValue(String::class.java)

                                    if (handledByLawyer && !originalConversationId.isNullOrEmpty()) {
                                        // Just log the status instead of UI changes that require layout modifications
                                        Log.d("ChatActivity", "This conversation was forwarded from secretary")
                                    }

                                    // Continue with the chat setup
                                    continueWithChatSetup()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("ChatActivity", "Error checking conversation details: ${error.message}")
                                    continueWithChatSetup()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error checking forwarded status: ${error.message}")
                    continueWithChatSetup()
                }
            })
    }

    // Helper method to continue with chat setup after checking forwarded status
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
}