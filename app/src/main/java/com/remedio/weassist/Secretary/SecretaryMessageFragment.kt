package com.remedio.weassist.Secretary

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.remedio.weassist.MessageConversation.ChatActivity
import com.remedio.weassist.MessageConversation.Conversation
import com.remedio.weassist.MessageConversation.ConversationAdapter
import com.remedio.weassist.Models.Appointment
import com.remedio.weassist.R

class SecretaryMessageFragment : Fragment() {
    private lateinit var database: DatabaseReference
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var conversationsAdapter: ConversationAdapter
    private val conversationList = mutableListOf<Conversation>()
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Add debug function call similar to ClientMessageFragment
        fetchConversationsDebug()

        val view = inflater.inflate(R.layout.fragment_secretary_message, container, false)
        conversationsRecyclerView = view.findViewById(R.id.inbox_recycler_view)
        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // In SecretaryMessageFragment
        conversationsAdapter = ConversationAdapter(
            conversationList,
            { conversation -> openChatActivity(conversation.clientId) },
            currentUserId,  // Pass current user ID (secretary)
            { view, position -> showConversationOptions(view, position) }  // Long press handler
        )
        conversationsRecyclerView.adapter = conversationsAdapter

        // Set up swipe to delete functionality
        setupSwipeToDelete()

        database = FirebaseDatabase.getInstance().reference

        checkAuthStatus() // Check if the user is logged in

        fetchConversations()

        return view
    }

    private fun showConversationOptions(view: View, position: Int): Boolean {
        val conversation = conversationList[position]
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.conversation_options_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_forward_to_lawyer -> {
                    forwardConversationToLawyer(conversation)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
        return true
    }

    // Update to forwardConversationToLawyer method in SecretaryMessageFragment
    private fun forwardConversationToLawyer(conversation: Conversation) {
        // First, check the accepted_appointment node
        val acceptedApptRef = FirebaseDatabase.getInstance().getReference("accepted_appointment")

        acceptedApptRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var validAppointment: Appointment? = null

                if (snapshot.exists()) {
                    // Iterate through all entries in the accepted_appointment node
                    for (appointmentSnapshot in snapshot.children) {
                        val appointmentData = appointmentSnapshot.getValue(object : GenericTypeIndicator<HashMap<String, Any>>() {})

                        val clientId = appointmentData?.get("clientId")?.toString()
                        val appointmentLawyerId = appointmentData?.get("lawyerId")?.toString()
                        val status = appointmentData?.get("status")?.toString()
                        val date = appointmentData?.get("date")?.toString() ?: ""

                        // Check if this appointment matches our current client
                        if (clientId == conversation.clientId &&
                            (status == "Accepted" || status == "accepted" || status == "Forwarded")) {
                            // Create an appointment object to pass to proceedWithConversationForwarding
                            val appointment = Appointment()
                            appointment.clientId = clientId ?: ""
                            appointment.lawyerId = appointmentLawyerId ?: ""
                            appointment.status = status ?: ""
                            appointment.date = date
                            appointment.appointmentId = appointmentSnapshot.key ?: ""

                            validAppointment = appointment
                            break
                        }
                    }
                }

                if (validAppointment != null) {
                    proceedWithConversationForwarding(conversation, validAppointment!!)
                } else {
                    // If nothing found in accepted_appointment, check regular appointments
                    checkMainAppointmentsNode(conversation)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryMessageFragment", "Failed to check accepted_appointment", error.toException())
                // Fall back to regular appointments check
                checkMainAppointmentsNode(conversation)
            }
        })
    }

    private fun checkMainAppointmentsNode(conversation: Conversation) {
        database.child("appointments")
            .orderByChild("clientId").equalTo(conversation.clientId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Find the most recent accepted or forwarded appointment
                    val validAppointment = snapshot.children
                        .mapNotNull {
                            val appointment = it.getValue(Appointment::class.java)
                            appointment?.apply {
                                appointmentId = it.key ?: ""
                            }
                        }
                        .filter { it.status in listOf("Accepted", "Forwarded") }
                        .maxByOrNull {
                            // Parse the date to determine recency
                            try {
                                // Assuming date is in format "MM/dd/yyyy"
                                val dateParts = it.date.split("/")
                                "${dateParts[2]}${dateParts[0].padStart(2, '0')}${dateParts[1].padStart(2, '0')}".toInt()
                            } catch (e: Exception) {
                                0 // Default to earliest if date parsing fails
                            }
                        }

                    if (validAppointment == null) {
                        // No valid appointment found
                        Toast.makeText(
                            requireContext(),
                            "Cannot forward conversation to lawyer appointment have not been accepted",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    // If a valid appointment exists, proceed with forwarding
                    proceedWithConversationForwarding(conversation, validAppointment)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryMessageFragment", "Error checking appointments: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Error checking appointment status",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun proceedWithConversationForwarding(conversation: Conversation, appointment: Appointment) {
        // First, get the appointed lawyer ID from the appointment
        val lawyerId = appointment.lawyerId

        if (lawyerId.isNullOrEmpty()) {
            // Instead of Toast, use a Snackbar which is less intrusive
            Snackbar.make(
                requireView(),
                "No lawyer found for forwarding",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // Mark the conversation as forwarded
        database.child("conversations").child(conversation.conversationId)
            .child("forwarded").setValue(true)
            .addOnSuccessListener {
                // Create a new conversation between the lawyer and client
                createNewLawyerClientConversation(lawyerId, conversation.clientId, conversation.conversationId)

                // Send notification to the client
                createClientForwardingNotification(conversation.clientId, conversation.conversationId)

                // Send notification to the lawyer
                createLawyerForwardingNotification(lawyerId, conversation.clientId, conversation.conversationId)
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to mark conversation as forwarded: ${e.message}")
                // Still create the conversation even if marking as forwarded fails
                createNewLawyerClientConversation(lawyerId, conversation.clientId, conversation.conversationId)

                // Still send notifications even if marking as forwarded fails
                createClientForwardingNotification(conversation.clientId, conversation.conversationId)
                createLawyerForwardingNotification(lawyerId, conversation.clientId, conversation.conversationId)
            }
    }

    /**
     * Creates a notification for the client about their case being forwarded to a lawyer
     * @param clientId The ID of the client to notify
     * @param conversationId The ID of the original conversation
     */
    private fun createClientForwardingNotification(clientId: String, conversationId: String) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Get the secretary's name first
        database.child("secretaries").child(currentSecretaryId).get()
            .addOnSuccessListener { snapshot ->
                val secretaryName = snapshot.child("name").getValue(String::class.java) ?: "Secretary"

                // Create notification data - matching the structure in ClientNotificationActivity
                val notificationData = hashMapOf<String, Any>(
                    "senderId" to currentSecretaryId, // Secretary as sender
                    "senderName" to "Secretary $secretaryName", // Include secretary name
                    "message" to "Your case has been forwarded to a lawyer who will assist you further.",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to "CONVERSATION_FORWARDED",
                    "isRead" to false,
                    "conversationId" to conversationId
                )

                // Generate a unique notification ID
                val notificationId = database.child("notifications").child(clientId).push().key ?: return@addOnSuccessListener

                // Save notification to database
                database.child("notifications").child(clientId).child(notificationId).setValue(notificationData)
                    .addOnSuccessListener {
                        Log.d("SecretaryMessageFragment", "Notification created for client: $clientId")

                        // Update unread notification count in the client's user record
                        updateClientUnreadNotificationCount(clientId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryMessageFragment", "Failed to create notification for client: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to fetch secretary name: ${e.message}")

                // Fallback if we can't get the secretary name
                val notificationData = hashMapOf<String, Any>(
                    "senderId" to currentSecretaryId,
                    "senderName" to "Secretary", // Generic fallback
                    "message" to "Your case has been forwarded to a lawyer who will assist you further.",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to "CONVERSATION_FORWARDED",
                    "isRead" to false,
                    "conversationId" to conversationId
                )

                val notificationId = database.child("notifications").child(clientId).push().key ?: return@addOnFailureListener

                database.child("notifications").child(clientId).child(notificationId).setValue(notificationData)
                    .addOnSuccessListener {
                        Log.d("SecretaryMessageFragment", "Notification created for client: $clientId")
                        updateClientUnreadNotificationCount(clientId)
                    }
                    .addOnFailureListener { e2 ->
                        Log.e("SecretaryMessageFragment", "Failed to create notification for client: ${e2.message}")
                    }
            }
    }

    /**
     * Creates a notification for the lawyer about a new case being forwarded to them
     * @param lawyerId The ID of the lawyer to notify
     * @param clientId The ID of the client whose case is being forwarded
     * @param conversationId The ID of the original conversation
     */
    private fun createLawyerForwardingNotification(lawyerId: String, clientId: String, conversationId: String) {
        val currentSecretaryId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // First get the secretary's name
        database.child("secretaries").child(currentSecretaryId).get()
            .addOnSuccessListener { secretarySnapshot ->
                val secretaryName = secretarySnapshot.child("name").getValue(String::class.java) ?: "Secretary"

                // Then get the client's name to include in the notification
                database.child("Users").child(clientId).get()
                    .addOnSuccessListener { clientSnapshot ->
                        val firstName = clientSnapshot.child("firstName").getValue(String::class.java) ?: "A client"
                        val lastName = clientSnapshot.child("lastName").getValue(String::class.java) ?: ""
                        val clientName = if (lastName.isEmpty()) firstName else "$firstName $lastName"

                        // Then create the notification
                        val newConversationId = generateConversationId(clientId, lawyerId)

                        // Create notification data - matching the structure in LawyerNotification
                        val notificationData = hashMapOf<String, Any>(
                            "senderId" to currentSecretaryId, // Secretary as sender
                            "senderName" to "Secretary $secretaryName", // Include actual secretary name
                            "message" to "A new case from $clientName has been forwarded to you.",
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "NEW_CASE_ASSIGNED",
                            "isRead" to false,
                            "conversationId" to newConversationId
                        )

                        // Generate a unique notification ID
                        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return@addOnSuccessListener

                        // Save notification to database
                        database.child("notifications").child(lawyerId).child(notificationId).setValue(notificationData)
                            .addOnSuccessListener {
                                Log.d("SecretaryMessageFragment", "Notification created for lawyer: $lawyerId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("SecretaryMessageFragment", "Failed to create notification for lawyer: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryMessageFragment", "Failed to fetch client name: ${e.message}")

                        // Create a notification with just the secretary name if we can't get the client's name
                        val newConversationId = generateConversationId(clientId, lawyerId)

                        val notificationData = hashMapOf<String, Any>(
                            "senderId" to currentSecretaryId,
                            "senderName" to "Secretary $secretaryName",
                            "message" to "A new case has been forwarded to you.",
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "NEW_CASE_ASSIGNED",
                            "isRead" to false,
                            "conversationId" to newConversationId
                        )

                        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return@addOnFailureListener
                        database.child("notifications").child(lawyerId).child(notificationId).setValue(notificationData)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to fetch secretary name: ${e.message}")

                // Fallback if we can't get the secretary's name - proceed with client name lookup
                database.child("Users").child(clientId).get()
                    .addOnSuccessListener { clientSnapshot ->
                        val firstName = clientSnapshot.child("firstName").getValue(String::class.java) ?: "A client"
                        val lastName = clientSnapshot.child("lastName").getValue(String::class.java) ?: ""
                        val clientName = if (lastName.isEmpty()) firstName else "$firstName $lastName"

                        val newConversationId = generateConversationId(clientId, lawyerId)

                        val notificationData = hashMapOf<String, Any>(
                            "senderId" to currentSecretaryId,
                            "senderName" to "Secretary", // Generic fallback
                            "message" to "A new case from $clientName has been forwarded to you.",
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "NEW_CASE_ASSIGNED",
                            "isRead" to false,
                            "conversationId" to newConversationId
                        )

                        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return@addOnSuccessListener
                        database.child("notifications").child(lawyerId).child(notificationId).setValue(notificationData)
                    }
                    .addOnFailureListener { clientError ->
                        // Final fallback if both secretary and client name lookups fail
                        val newConversationId = generateConversationId(clientId, lawyerId)

                        val notificationData = hashMapOf<String, Any>(
                            "senderId" to currentSecretaryId,
                            "senderName" to "Secretary",
                            "message" to "A new case has been forwarded to you.",
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "NEW_CASE_ASSIGNED",
                            "isRead" to false,
                            "conversationId" to newConversationId
                        )

                        val notificationId = database.child("notifications").child(lawyerId).push().key ?: return@addOnFailureListener
                        database.child("notifications").child(lawyerId).child(notificationId).setValue(notificationData)
                    }
            }
    }

    /**
     * Updates the unread notification count for a client
     * @param clientId The ID of the client to update
     */
    private fun updateClientUnreadNotificationCount(clientId: String) {
        // Query to count unread notifications
        database.child("notifications").child(clientId)
            .orderByChild("isRead").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val unreadCount = snapshot.childrenCount.toInt()

                    // Update the client's unread notification count
                    database.child("Users").child(clientId)
                        .child("unreadNotifications").setValue(unreadCount)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryMessageFragment", "Failed to count unread notifications: ${error.message}")
                }
            })
    }


    // Update the createNewLawyerClientConversation method
    private fun createNewLawyerClientConversation(lawyerId: String, clientId: String, originalConversationId: String) {
        // Generate the new conversation ID correctly using the existing pattern
        val newConversationId = generateConversationId(clientId, lawyerId)

        // Get the conversation reference
        val newConversationRef = database.child("conversations").child(newConversationId)

        // First check if this conversation already exists
        newConversationRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Conversation already exists, just add the forwarding data
                    updateExistingConversation(snapshot, lawyerId, clientId, originalConversationId)
                } else {
                    // Create new conversation
                    createBrandNewConversation(lawyerId, clientId, originalConversationId, newConversationId)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SecretaryMessageFragment", "Failed to check if conversation exists: ${error.message}")
                // Proceed with creating new conversation
                createBrandNewConversation(lawyerId, clientId, originalConversationId, newConversationId)
            }
        })
    }

    private fun createBrandNewConversation(lawyerId: String, clientId: String, originalConversationId: String, newConversationId: String) {
        // Get the lawyer's name
        database.child("lawyers").child(lawyerId).get()
            .addOnSuccessListener { lawyerSnapshot ->
                val lawyerFirstName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "The lawyer"
                val lawyerName = if (lawyerFirstName.isEmpty()) "The lawyer" else lawyerFirstName

                // Set up conversation data
                val participantIds = hashMapOf(
                    lawyerId to true,
                    clientId to true
                )

                val unreadMessages = hashMapOf(
                    clientId to 1,  // Client has 1 unread message
                    lawyerId to 0   // Lawyer has 0 unread messages (since they're sending the first one)
                )

                // Create conversation data
                val conversationData = hashMapOf(
                    "participantIds" to participantIds,
                    "unreadMessages" to unreadMessages,
                    "appointedLawyerId" to lawyerId,
                    "originalConversationId" to originalConversationId,  // Reference to the original conversation
                    "handledByLawyer" to true,
                    "forwardedFromSecretary" to true  // Add flag indicating this was forwarded from secretary
                )

                // Save the conversation data
                val newConversationRef = database.child("conversations").child(newConversationId)
                newConversationRef.setValue(conversationData)
                    .addOnSuccessListener {
                        // Add the welcome message
                        val welcomeMessage = mapOf(
                            "message" to "Hello, I'm Atty. $lawyerName. I've been assigned to provide you with legal assistance.",
                            "senderId" to lawyerId,
                            "receiverId" to clientId,
                            "timestamp" to ServerValue.TIMESTAMP
                        )

                        newConversationRef.child("messages").push().setValue(welcomeMessage)
                            .addOnSuccessListener {
                                // Add notification to the original conversation
                                addNotificationToOriginalConversation(originalConversationId, lawyerId, lawyerName)

                                // Use Snackbar instead of Toast
                                Snackbar.make(
                                    requireView(),
                                    "Conversation forwarded to lawyer",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("SecretaryMessageFragment", "Failed to add welcome message: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryMessageFragment", "Failed to create new conversation: ${e.message}")
                        // Use Snackbar for error message
                        Snackbar.make(
                            requireView(),
                            "Failed to create new conversation",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to get lawyer name: ${e.message}")
                // Call the fallback method if we can't get the lawyer's name
                createNewConversationWithGenericLawyer(lawyerId, clientId, originalConversationId)
            }
    }

    // Add this method to update an existing conversation when forwarding
    private fun updateExistingConversation(snapshot: DataSnapshot, lawyerId: String, clientId: String, originalConversationId: String) {
        val conversationId = snapshot.key ?: return
        val conversationRef = database.child("conversations").child(conversationId)

        // Check if this conversation was forwarded before
        val wasForwardedBefore = snapshot.child("forwardedFromSecretary").getValue(Boolean::class.java) ?: false

        // Update conversation with forwarding info
        val updates = mapOf(
            "originalConversationId" to originalConversationId,
            "handledByLawyer" to true,
            "forwardedFromSecretary" to true
        )

        conversationRef.updateChildren(updates)
            .addOnSuccessListener {
                // Only add welcome message if this conversation wasn't previously forwarded
                if (!wasForwardedBefore) {
                    // Get the lawyer's name
                    database.child("lawyers").child(lawyerId).get()
                        .addOnSuccessListener { lawyerSnapshot ->
                            val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "Your lawyer"

                            // Add the welcome message
                            val welcomeMessage = mapOf(
                                "message" to "Hello, I'm Atty. $lawyerName. I've been assigned to provide you with legal assistance.",
                                "senderId" to lawyerId,
                                "receiverId" to clientId,
                                "timestamp" to ServerValue.TIMESTAMP
                            )

                            conversationRef.child("messages").push().setValue(welcomeMessage)
                                .addOnSuccessListener {
                                    // Update unread count for client
                                    conversationRef.child("unreadMessages").child(clientId).addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            val currentCount = snapshot.getValue(Int::class.java) ?: 0
                                            conversationRef.child("unreadMessages").child(clientId).setValue(currentCount + 1)
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e("SecretaryMessageFragment", "Failed to update unread count: ${error.message}")
                                        }
                                    })

                                    // Add notification to original conversation
                                    addNotificationToOriginalConversation(originalConversationId, lawyerId, lawyerName)
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("SecretaryMessageFragment", "Failed to get lawyer name: ${e.message}")
                            // Add generic welcome message as fallback
                            val welcomeMessage = mapOf(
                                "message" to "Hello, I'm your assigned attorney. I've been assigned to provide you with legal assistance.",
                                "senderId" to lawyerId,
                                "receiverId" to clientId,
                                "timestamp" to ServerValue.TIMESTAMP
                            )

                            conversationRef.child("messages").push().setValue(welcomeMessage)
                            addNotificationToOriginalConversation(originalConversationId, lawyerId, "a lawyer")
                        }
                } else {
                    // Conversation was previously forwarded, just add notification to original conversation
                    database.child("lawyers").child(lawyerId).get()
                        .addOnSuccessListener { lawyerSnapshot ->
                            val lawyerName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "a lawyer"
                            addNotificationToOriginalConversation(originalConversationId, lawyerId, lawyerName)
                        }
                        .addOnFailureListener { e ->
                            Log.e("SecretaryMessageFragment", "Failed to get lawyer name: ${e.message}")
                            addNotificationToOriginalConversation(originalConversationId, lawyerId, "a lawyer")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to update conversation: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to update conversation data",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Add this new helper function
    private fun generateConversationId(user1: String, user2: String): String {
        return if (user1 < user2) {
            "conversation_${user1}_${user2}"
        } else {
            "conversation_${user2}_${user1}"
        }
    }

    // Add this new fallback method
    private fun createNewConversationWithGenericLawyer(lawyerId: String, clientId: String, originalConversationId: String) {
        val newConversationId = generateConversationId(clientId, lawyerId)

        // Set up conversation data
        val conversationData = hashMapOf(
            "participantIds" to hashMapOf(
                lawyerId to true,
                clientId to true
            ),
            "unreadMessages" to hashMapOf(
                clientId to 1,
                lawyerId to 0
            ),
            "appointedLawyerId" to lawyerId,
            "originalConversationId" to originalConversationId,
            "handledByLawyer" to true,
            "forwardedFromSecretary" to true  // Add flag indicating this was forwarded from secretary
        )

        // First create the conversation
        database.child("conversations").child(newConversationId)
            .setValue(conversationData)
            .addOnSuccessListener {
                // Then add the welcome message
                val welcomeMessage = mapOf(
                    "message" to "Hello, I'm your assigned attorney. I've been assigned to provide you with legal assistance.",
                    "senderId" to lawyerId,
                    "receiverId" to clientId,
                    "timestamp" to ServerValue.TIMESTAMP
                )

                database.child("conversations").child(newConversationId)
                    .child("messages").push().setValue(welcomeMessage)
                    .addOnSuccessListener {
                        addNotificationToOriginalConversation(originalConversationId, lawyerId, "a lawyer")

                        Toast.makeText(
                            requireContext(),
                            "",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to create new conversation: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to create new conversation",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun addNotificationToOriginalConversation(originalConversationId: String, lawyerId: String, lawyerName: String) {
        // Get the reference to the original conversation
        val originalConversationRef = database.child("conversations").child(originalConversationId)

        // Add a system message indicating that a lawyer has been assigned
        val notificationMessage = mapOf(
            "message" to "This case has been forwarded to Atty. $lawyerName. The client can now communicate with the attorney in a separate conversation.",
            "senderId" to "system",
            "timestamp" to ServerValue.TIMESTAMP
        )

        originalConversationRef.child("messages").push().setValue(notificationMessage)
            .addOnSuccessListener {
                Log.d("SecretaryMessageFragment", "Notification added to original conversation")
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to add notification: ${e.message}")
            }
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)
        val deleteBackground = ColorDrawable(Color.RED)

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // We don't support moving items
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedConversation = conversationList[position]

                // Remove from local list
                conversationList.removeAt(position)
                conversationsAdapter.notifyItemRemoved(position)

                // Remove from Firebase
                removeConversation(deletedConversation.conversationId)

                // Show undo snackbar
                Snackbar.make(
                    conversationsRecyclerView,
                    "Conversation with ${deletedConversation.clientName} removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Restore the conversation if user clicks undo
                    conversationList.add(position, deletedConversation)
                    conversationsAdapter.notifyItemInserted(position)
                    restoreConversation(deletedConversation.conversationId)
                }.show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2

                // Draw the red delete background
                deleteBackground.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                deleteBackground.draw(c)

                // Draw the delete icon
                deleteIcon.setBounds(
                    itemView.right - iconMargin - deleteIcon.intrinsicWidth,
                    itemView.top + iconMargin,
                    itemView.right - iconMargin,
                    itemView.bottom - iconMargin
                )
                deleteIcon.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        // Attach the touch helper to the recycler view
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(conversationsRecyclerView)
    }

    private fun removeConversation(conversationId: String) {
        if (currentUserId == null) return

        // Option 1: Hide the conversation from the user's view without deleting all data
        database.child("conversations").child(conversationId)
            .child("participantIds").child(currentUserId!!)
            .setValue(false)
            .addOnSuccessListener {
                Log.d("SecretaryMessageFragment", "Conversation hidden successfully")
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to hide conversation: ${e.message}")
            }

        // Option 2 (Alternative): Remove unread count for this user
        database.child("conversations").child(conversationId)
            .child("unreadMessages").child(currentUserId!!)
            .setValue(0)
    }

    private fun restoreConversation(conversationId: String) {
        if (currentUserId == null) return

        // Restore participant status to visible
        database.child("conversations").child(conversationId)
            .child("participantIds").child(currentUserId!!)
            .setValue(true)
            .addOnSuccessListener {
                Log.d("SecretaryMessageFragment", "Conversation restored successfully")
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to restore conversation: ${e.message}")
            }
    }

    private fun checkAuthStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("FirebaseAuth", "User is NOT authenticated!")
        } else {
            Log.d("FirebaseAuth", "Authenticated as: ${user.uid}")
        }
        Log.d("AuthCheck", "Current User ID: $currentUserId")
    }

    private fun fetchConversationsDebug() {
        val database = FirebaseDatabase.getInstance().reference
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId == null) {
            Log.e("FirebaseDB", "Current User ID is NULL!")
            return
        }

        database.child("conversations").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Log.d("FirebaseDB", "Fetched Data: ${snapshot.value}")
                    for (conversationSnapshot in snapshot.children) {
                        val conversationId = conversationSnapshot.key
                        Log.d("FirebaseDB", "Conversation ID: $conversationId")

                        val participants = conversationSnapshot.child("participantIds").children
                        for (participant in participants) {
                            Log.d("FirebaseDB", "Participant ID: ${participant.key}")
                        }

                        // Check if the current user is a participant
                        if (conversationSnapshot.child("participantIds").hasChild(currentUserId)) {
                            Log.d("FirebaseDB", "User $currentUserId is a participant in conversation: $conversationId")
                        } else {
                            Log.e("FirebaseDB", "User $currentUserId is NOT a participant in conversation: $conversationId")
                        }
                    }
                } else {
                    Log.e("FirebaseDB", "No conversations found in the database.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseDB", "Error: ${e.message}")
            }
    }

    private fun fetchConversations() {
        if (currentUserId == null) return

        val conversationsRef = database.child("conversations")
        conversationsRef.orderByChild("participantIds/$currentUserId").equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    conversationList.clear()

                    if (!snapshot.exists()) {
                        Log.e("FirebaseDB", "No conversations found for user: $currentUserId")
                        conversationsAdapter.notifyDataSetChanged()
                        return
                    }

                    val tempConversationList = mutableListOf<Conversation>()
                    var fetchCount = 0

                    for (conversationSnapshot in snapshot.children) {
                        val conversationId = conversationSnapshot.key ?: continue
                        val clientId = conversationSnapshot.child("participantIds")
                            .children.firstOrNull { it.key != currentUserId }?.key ?: continue

                        if (!conversationSnapshot.child("participantIds").hasChild(currentUserId!!)) {
                            Log.e("FirebaseDB", "User $currentUserId is NOT a participant in $conversationId")
                            continue
                        }

                        val lastMessage = conversationSnapshot.child("messages").children.lastOrNull()
                            ?.child("message")?.getValue(String::class.java) ?: "No messages yet"

                        val unreadCount = conversationSnapshot.child("unreadMessages/$currentUserId")
                            .getValue(Int::class.java) ?: 0

                        fetchCount++
                        fetchClientName(clientId) { clientName ->
                            val conversation = Conversation(
                                conversationId = conversationId,
                                clientId = clientId,
                                secretaryId = currentUserId ?: "",
                                clientName = clientName,
                                secretaryName = "", // Empty for secretary view
                                lastMessage = lastMessage,
                                unreadCount = unreadCount
                            )

                            tempConversationList.add(conversation)
                            fetchCount--

                            // Only update the adapter once all client names are fetched
                            if (fetchCount == 0) {
                                conversationList.clear()
                                conversationList.addAll(tempConversationList)
                                conversationsAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryMessagesFragment", "Error fetching conversations: ${error.message}")
                }
            })
    }

    private fun fetchClientName(clientId: String, callback: (String) -> Unit) {
        val clientsRef = database.child("Users").child(clientId)

        clientsRef.get().addOnSuccessListener { snapshot ->
            val firstName = snapshot.child("firstName").value?.toString() ?: "Unknown"
            val lastName = snapshot.child("lastName").value?.toString() ?: ""
            val fullName = "$firstName $lastName".trim()
            callback(fullName)
        }.addOnFailureListener {
            callback("Unknown")
        }
    }

    private fun openChatActivity(clientId: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("CLIENT_ID", clientId)
        startActivity(intent)
    }
}