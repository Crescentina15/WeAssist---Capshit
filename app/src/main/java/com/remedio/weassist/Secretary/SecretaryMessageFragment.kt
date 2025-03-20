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

    private fun forwardConversationToLawyer(conversation: Conversation) {
        // First, get the appointed lawyer ID if one exists
        database.child("conversations").child(conversation.conversationId)
            .child("appointedLawyerId").get()
            .addOnSuccessListener { snapshot ->
                val lawyerId = if (snapshot.exists() && snapshot.getValue(String::class.java) != null) {
                    // Use the appointed lawyer if one exists
                    snapshot.getValue(String::class.java)!!
                } else {
                    // Find any lawyer if no appointed lawyer exists
                    findAvailableLawyer()
                }

                if (lawyerId != null) {
                    // Create a new conversation between the lawyer and client
                    createNewLawyerClientConversation(lawyerId, conversation.clientId, conversation.conversationId)
                } else {
                    noLawyersFoundMessage()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to check for appointed lawyer: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Error checking appointment details: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun createNewLawyerClientConversation(lawyerId: String, clientId: String, originalConversationId: String) {
        // Generate the new conversation ID correctly using the existing pattern
        val newConversationId = generateConversationId(clientId, lawyerId)

        // Get the conversation reference
        val newConversationRef = database.child("conversations").child(newConversationId)

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
                    "handledByLawyer" to true
                )

                // Save the conversation data
                newConversationRef.setValue(conversationData)
                    .addOnSuccessListener {
                        // Add the welcome message
                        val welcomeMessage = mapOf(
                            "message" to "Hello, I'm Attorney $lawyerName. I've been assigned to provide you with legal assistance.",
                            "senderId" to lawyerId,
                            "receiverId" to clientId,
                            "timestamp" to ServerValue.TIMESTAMP
                        )

                        newConversationRef.child("messages").push().setValue(welcomeMessage)
                            .addOnSuccessListener {
                                // Add notification to the original conversation
                                addNotificationToOriginalConversation(originalConversationId, lawyerId, lawyerName)

                                Toast.makeText(
                                    requireContext(),
                                    "New conversation created with Attorney $lawyerName",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("SecretaryMessageFragment", "Failed to add welcome message: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryMessageFragment", "Failed to create new conversation: ${e.message}")
                        Toast.makeText(
                            requireContext(),
                            "Failed to create new conversation: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to get lawyer name: ${e.message}")
                // Call the fallback method if we can't get the lawyer's name
                createNewConversationWithGenericLawyer(lawyerId, clientId, originalConversationId)
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
            "handledByLawyer" to true
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
                            "New conversation created with a lawyer",
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
            "message" to "This case has been forwarded to Attorney $lawyerName. The client can now communicate with the attorney in a separate conversation.",
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

    private fun findAvailableLawyer(): String? {
        // This function would need to be implemented to query for a lawyer
        // For now, we'll use a placeholder implementation
        var lawyerId: String? = null

        // Query the database for lawyers
        database.child("Users").get()
            .addOnSuccessListener { usersSnapshot ->
                if (usersSnapshot.exists()) {
                    // Find lawyers by checking role field
                    val lawyers = usersSnapshot.children.filter { userSnapshot ->
                        val role = userSnapshot.child("role").getValue(String::class.java)
                        role == "lawyer"
                    }

                    if (lawyers.isNotEmpty()) {
                        // Use the first lawyer we find
                        lawyerId = lawyers.first().key
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to query for users: ${e.message}")
            }

        return lawyerId
    }

    private fun addTransitionMessages(conversationId: String, lawyerId: String, secretaryId: String) {
        // Get the conversation reference
        val conversationRef = database.child("conversations").child(conversationId)

        // Fetch the lawyer's name
        database.child("lawyers").child(lawyerId).get()
            .addOnSuccessListener { lawyerSnapshot ->
                val lawyerFirstName = lawyerSnapshot.child("name").getValue(String::class.java) ?: "The lawyer"

                val lawyerName = if (lawyerFirstName.isEmpty()) lawyerFirstName else "$lawyerFirstName"

                // Add a system message for adding the lawyer (not forwarding)
                val addingLawyerMessage = mapOf(
                    "message" to "Attorney $lawyerName has been added to this conversation to provide legal expertise.",
                    "senderId" to "system",
                    "timestamp" to ServerValue.TIMESTAMP
                )

                conversationRef.child("messages").push().setValue(addingLawyerMessage)
                    .addOnSuccessListener {
                        // Do NOT set secretaryActive to false
                        // Do NOT set handledByLawyer to true

                        // Add a welcome message from the lawyer
                        val welcomeMessage = mapOf(
                            "message" to "Hello, I'm Attorney $lawyerName. I've been brought in to provide legal assistance while you continue working with the secretary.",
                            "senderId" to lawyerId,
                            "timestamp" to (System.currentTimeMillis() + 1000)  // Adding 1 second to ensure proper ordering
                        )

                        conversationRef.child("messages").push().setValue(welcomeMessage)
                            .addOnSuccessListener {
                                // Set unread counter for lawyer
                                conversationRef.child("unreadMessages").child(lawyerId).setValue(1)
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            requireContext(),
                                            "Attorney $lawyerName has been added to the conversation",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        // Secretary remains an active participant
                                        // No need to remove or change the secretary's status
                                    }
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SecretaryMessageFragment", "Failed to add system messages: ${e.message}")
                        Toast.makeText(
                            requireContext(),
                            "Added lawyer, but failed to add notification messages",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                // Fallback if we can't get the lawyer's name
                val addingLawyerMessage = mapOf(
                    "message" to "A lawyer has been added to this conversation to provide legal expertise.",
                    "senderId" to "system",
                    "timestamp" to ServerValue.TIMESTAMP
                )

                conversationRef.child("messages").push().setValue(addingLawyerMessage)
                    .addOnSuccessListener {
                        // Do NOT set secretaryActive to false
                        // Do NOT set handledByLawyer to true

                        // Set unread counter for lawyer
                        conversationRef.child("unreadMessages").child(lawyerId).setValue(1)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    requireContext(),
                                    "A lawyer has been added to the conversation",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
            }
    }

    private fun noLawyersFoundMessage() {
        Log.e("SecretaryMessageFragment", "No lawyers found in the system")
        Toast.makeText(
            requireContext(),
            "No lawyers found in the system",
            Toast.LENGTH_SHORT
        ).show()
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