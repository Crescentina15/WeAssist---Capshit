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

        // Get the new flag for creating conversation on first message
        createOnFirstMessage = intent.getBooleanExtra("CREATE_ON_FIRST_MESSAGE", false)
        Log.d("ChatActivity", "Create conversation on first message: $createOnFirstMessage")

        Log.d("ChatActivity", "Received in intent - conversationId: $conversationId, secretaryId: $secretaryId, clientId: $clientId, appointmentId: $appointmentId, userType: $userType")

        // Set up RecyclerView
        messagesAdapter = MessageAdapter(messagesList)
        rvChatMessages.layoutManager = LinearLayoutManager(this)
        rvChatMessages.adapter = messagesAdapter

        // If we have an appointment ID but no conversation ID, try to find it
        if (conversationId == null && appointmentId != null && currentUserId != null) {
            fetchConversationIdFromAppointment(appointmentId)
        } else {
            // Determine current user type and set up chat
            if (currentUserId != null) {
                // If user type is already set via intent, skip determination
                if (userType != null) {
                    if (clientId == null && conversationId != null) {
                        extractClientIdFromConversation(conversationId!!)
                    } else {
                        setupChatPartner()
                    }
                } else {
                    determineCurrentUserType()
                }
            } else {
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnSendMessage.setOnClickListener {
            sendMessage()
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
        // Check if this is a new conversation that we're waiting to create on first message
        if (createOnFirstMessage) {
            Log.d("ChatActivity", "Skipping participant extraction for new conversation that will be created on first message")
            setupChatPartner()
            return
        }

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
                                        }
                                    }
                            }
                        }
                    } else {
                        Log.e("ChatActivity", "No participants found in conversation")

                        // If we're expecting to create the conversation on first message, don't show error
                        if (!createOnFirstMessage) {
                            Toast.makeText(applicationContext, "Conversation data not found", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            setupChatPartner()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching conversation participants: ${error.message}")
                    if (!createOnFirstMessage) {
                        Toast.makeText(applicationContext, "Failed to load conversation data", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        setupChatPartner()
                    }
                }
            })
    }

    private fun extractSecretaryIdFromConversation(conversationId: String) {
        // Check if this is a new conversation that we're waiting to create on first message
        if (createOnFirstMessage) {
            Log.d("ChatActivity", "Skipping secretary extraction for new conversation that will be created on first message")
            setupChatPartner()
            return
        }

        database.child("conversations").child(conversationId).child("participantIds")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (participantSnapshot in snapshot.children) {
                            val participantId = participantSnapshot.key
                            if (participantId != currentUserId) {
                                // First check if this is a secretary
                                database.child("secretaries").child(participantId!!).get()
                                    .addOnSuccessListener { secretarySnapshot ->
                                        if (secretarySnapshot.exists()) {
                                            secretaryId = participantId
                                            Log.d("ChatActivity", "Extracted secretaryId: $secretaryId from conversation")
                                            setupChatPartner()
                                        } else {
                                            // If not a secretary, check if this is a lawyer
                                            database.child("lawyers").child(participantId).get()
                                                .addOnSuccessListener { lawyerSnapshot ->
                                                    if (lawyerSnapshot.exists()) {
                                                        secretaryId = participantId  // Reuse secretaryId for lawyers too
                                                        Log.d("ChatActivity", "Extracted lawyerId (as secretaryId): $secretaryId from conversation")
                                                        setupChatPartner()
                                                    }
                                                }
                                        }
                                    }
                            }
                        }
                    } else {
                        Log.e("ChatActivity", "No participants found in conversation")

                        // If we're expecting to create the conversation on first message, don't show error
                        if (!createOnFirstMessage) {
                            Toast.makeText(applicationContext, "Conversation data not found", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            setupChatPartner()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatActivity", "Error fetching conversation participants: ${error.message}")
                    if (!createOnFirstMessage) {
                        Toast.makeText(applicationContext, "Failed to load conversation data", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        setupChatPartner()
                    }
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
                    tvChatPartnerName.text = fullName
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
                    tvChatPartnerName.text = secretaryName
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

    private fun getLawyerName(lawyerId: String) {
        database.child("lawyers").child(lawyerId).get()
            .addOnSuccessListener { lawyerSnapshot ->
                if (lawyerSnapshot.exists()) {
                    val name = lawyerSnapshot.child("name").value?.toString() ?: ""
                    tvChatPartnerName.text = if (name.isNotEmpty()) name else "Lawyer"
                    listenForMessages()
                } else {
                    Log.e("ChatActivity", "Lawyer not found!")

                    // If we're expecting to create conversation on first message, don't show error
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

                // If we're expecting to create conversation on first message, don't show error
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
        Log.d("ChatActivity", "Attempting to send message: $messageText")

        if (messageText.isNotEmpty() && currentUserId != null) {
            val receiverId = determineReceiverId()
            if (receiverId == null) {
                Log.e("ChatActivity", "Could not determine receiver ID")
                Toast.makeText(this, "Unable to send message: recipient not identified", Toast.LENGTH_SHORT).show()
                return
            }

            val conversationId = generateConversationId(currentUserId!!, receiverId)
            Log.d("ChatActivity", "Sending to conversation: $conversationId")

            val message = Message(
                senderId = currentUserId!!,
                receiverId = receiverId,
                message = messageText,
                timestamp = System.currentTimeMillis()
            )

            // If this is the first message and we need to create the conversation
            if (createOnFirstMessage) {
                Log.d("ChatActivity", "Creating new conversation on first message")

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
                val isLawyerConversation = userType == "client" && intent.getStringExtra("USER_TYPE") == "client"

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
                        addMessageToConversation(conversationId, message)

                        // Reset flag since conversation is now created
                        createOnFirstMessage = false
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "Failed to create conversation: ${e.message}")
                        Toast.makeText(this, "Failed to create conversation", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Conversation already exists, just add the message
                addMessageToConversation(conversationId, message)
            }
        } else {
            Log.e("ChatActivity", "Message cannot be sent! messageText=${messageText}, currentUserId=$currentUserId")
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            }
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

        // If we have a conversation ID from intent, use it directly
        val actualConversationId = conversationId ?: generateConversationId(currentUserId!!, receiverId)
        Log.d("ChatActivity", "Listening for messages with conversationId: $actualConversationId")

        // Listen for messages
        val messagesRef = database.child("conversations").child(actualConversationId).child("messages")

        // First, check if messages exist in the database
        messagesRef.get().addOnSuccessListener { snapshot ->
            Log.d("ChatActivity", "Database check: ${if (snapshot.exists()) "Messages exist" else "No messages found"}")

            if (snapshot.exists()) {
                Log.d("ChatActivity", "Number of messages in database: ${snapshot.childrenCount}")

                // Conversation exists, reset createOnFirstMessage flag
                createOnFirstMessage = false

                // Reset unread messages counter for this user
                database.child("conversations").child(actualConversationId)
                    .child("unreadMessages")
                    .child(currentUserId!!)
                    .setValue(0)

                // Print sample of first message to debug
                val firstMsg = snapshot.children.firstOrNull()
                if (firstMsg != null) {
                    val msgText = firstMsg.child("message").getValue(String::class.java)
                    val senderId = firstMsg.child("senderId").getValue(String::class.java)
                    Log.d("ChatActivity", "Sample message - Text: $msgText, Sender: $senderId")
                    Log.d("ChatActivity", "First message raw data: ${firstMsg.value}")
                }
            } else if (createOnFirstMessage) {
                // Conversation doesn't exist yet, but that's expected since createOnFirstMessage is true
                Log.d("ChatActivity", "Conversation doesn't exist yet, waiting for first message")

                // Clear messages list and update UI for empty state
                messagesList.clear()
                messagesAdapter.notifyDataSetChanged()
            } else {
                Log.d("ChatActivity", "No messages found in conversation: $actualConversationId")
            }
        }

        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messagesList.clear()

                Log.d("ChatActivity", "ValueEventListener fired, message count: ${snapshot.childrenCount}")

                if (!snapshot.exists()) {
                    Log.d("ChatActivity", "No messages found in conversation: $actualConversationId")

                    // Ensure adapter refreshes even when empty
                    messagesAdapter.notifyDataSetChanged()
                    return
                }

                // Print all message data for debugging
                for (child in snapshot.children) {
                    Log.d("ChatActivity", "Message data: ${child.value}")
                }

                for (messageSnapshot in snapshot.children) {
                    try {
                        // Log the current message being processed
                        Log.d("ChatActivity", "Processing message: ${messageSnapshot.value}")

                        // Check if this is a system message first
                        val senderId = messageSnapshot.child("senderId").getValue(String::class.java)
                        val messageText = messageSnapshot.child("message").getValue(String::class.java) ?: ""
                        val timestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                        if (senderId == "system") {
                            // This is a system message
                            val systemMessage = Message(
                                senderId = "system",
                                receiverId = "", // System messages don't have a specific receiver
                                message = messageText,
                                timestamp = timestamp
                            )
                            messagesList.add(systemMessage)
                            Log.d("ChatActivity", "Added system message: $messageText")
                        } else if (!senderId.isNullOrEmpty() && messageText.isNotEmpty()) {
                            // This is a regular message
                            val receiverId = messageSnapshot.child("receiverId").getValue(String::class.java) ?: ""

                            // Create message with sender name (if current user, leave null)
                            val message = Message(
                                senderId = senderId,
                                receiverId = receiverId,
                                message = messageText,
                                timestamp = timestamp,
                                senderName = null // Will be handled by adapter based on senderId
                            )

                            messagesList.add(message)
                            Log.d("ChatActivity", "Added regular message: $messageText from $senderId")
                        } else {
                            Log.e("ChatActivity", "Invalid message data: senderId=$senderId, message=$messageText")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatActivity", "Error parsing message: ${e.message}")
                        e.printStackTrace()
                    }
                }

                // Sort messages by timestamp
                messagesList.sortBy { it.timestamp }

                // Log the final list of messages
                Log.d("ChatActivity", "Final message list size: ${messagesList.size}")
                for (msg in messagesList) {
                    Log.d("ChatActivity", "Final list - Message: ${msg.message}, Sender: ${msg.senderId}")
                }

                // Update the UI
                messagesAdapter.notifyDataSetChanged()

                if (messagesList.isNotEmpty()) {
                    // Scroll to the bottom to see latest messages
                    rvChatMessages.post {
                        rvChatMessages.scrollToPosition(messagesList.size - 1)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Error fetching messages: ${error.message}")
                Toast.makeText(applicationContext, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        })
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