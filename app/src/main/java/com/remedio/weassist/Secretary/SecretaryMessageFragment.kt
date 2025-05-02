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
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
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
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator
    private val conversationList = mutableListOf<Conversation>()
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_secretary_message, container, false)
        conversationsRecyclerView = view.findViewById(R.id.inbox_recycler_view)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        conversationsAdapter = ConversationAdapter(
            conversationList,
            { conversation -> openChatActivity(conversation) },  // Pass the whole conversation object
            currentUserId  // Pass current user ID (secretary)
        )
        conversationsRecyclerView.adapter = conversationsAdapter

        // Set up swipe to delete functionality
        setupSwipeToDelete()

        database = FirebaseDatabase.getInstance().reference

        checkAuthStatus() // Check if the user is logged in

        fetchConversations()

        return view
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        conversationsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        progressIndicator.visibility = View.GONE
        conversationsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showConversations() {
        progressIndicator.visibility = View.GONE
        conversationsRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun updateUiState() {
        if (conversationList.isEmpty()) {
            showEmptyState()
        } else {
            showConversations()
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

                // Update UI state
                updateUiState()

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

                    // Update UI state
                    updateUiState()

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

    // Remove this method if not needed or fix its implementation
    // private fun fetchConversationsDebug() { ... }

    private fun fetchConversations() {
        if (currentUserId == null) return

        // Show loading state while fetching data
        showLoading()

        val conversationsRef = database.child("conversations")
        conversationsRef.orderByChild("participantIds/$currentUserId").equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    conversationList.clear()

                    if (!snapshot.exists()) {
                        Log.e("FirebaseDB", "No conversations found for user: $currentUserId")
                        conversationsAdapter.notifyDataSetChanged()
                        showEmptyState()
                        return
                    }

                    val tempConversationList = mutableListOf<Conversation>()
                    var fetchCount = 0

                    for (conversationSnapshot in snapshot.children) {
                        val conversationId = conversationSnapshot.key ?: continue

                        // Find the other participant (client) by looking at all participants
                        var clientId: String? = null
                        val participantsSnapshot = conversationSnapshot.child("participantIds")

                        for (participantSnapshot in participantsSnapshot.children) {
                            val participantId = participantSnapshot.key
                            val isActive = participantSnapshot.getValue(Boolean::class.java) ?: false

                            // If participant is active and not the current user, assume it's the client
                            if (participantId != currentUserId && isActive) {
                                clientId = participantId
                                break
                            }
                        }

                        // If no client ID found, try to find any participant that's not the current user
                        if (clientId == null) {
                            for (participantSnapshot in participantsSnapshot.children) {
                                val participantId = participantSnapshot.key
                                if (participantId != currentUserId) {
                                    clientId = participantId
                                    break
                                }
                            }
                        }

                        // Skip conversations without a client
                        if (clientId == null) {
                            Log.e("FirebaseDB", "No client found in conversation: $conversationId")
                            continue
                        }

                        val isForwarded = conversationSnapshot.child("forwarded").getValue(Boolean::class.java) ?: false
                        val secretaryActive = conversationSnapshot.child("secretaryActive").getValue(Boolean::class.java) ?: true

                        // Get last message
                        val messagesSnapshot = conversationSnapshot.child("messages")
                        var lastMessage = "No messages yet"
                        var latestTimestamp = 0L

                        for (messageSnapshot in messagesSnapshot.children) {
                            val messageTimestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            if (messageTimestamp > latestTimestamp) {
                                latestTimestamp = messageTimestamp
                                lastMessage = messageSnapshot.child("message").getValue(String::class.java) ?: lastMessage
                            }
                        }

                        val unreadCount = conversationSnapshot.child("unreadMessages/$currentUserId")
                            .getValue(Int::class.java) ?: 0

                        fetchCount++
                        // Fetch both client and secretary details
                        fetchConversationDetails(clientId, conversationId, lastMessage, unreadCount, isForwarded, !secretaryActive) { conversation ->
                            tempConversationList.add(conversation)
                            fetchCount--

                            // Only update the adapter once all details are fetched
                            if (fetchCount == 0) {
                                // Sort conversations by unread count (unread first) and then by client name
                                val sortedList = tempConversationList.sortedWith(
                                    compareByDescending<Conversation> { it.unreadCount > 0 }
                                        .thenBy { it.clientName }
                                )

                                conversationList.clear()
                                conversationList.addAll(sortedList)
                                conversationsAdapter.notifyDataSetChanged()
                                updateUiState()
                            }
                        }
                    }

                    // If no conversations to fetch, update UI immediately
                    if (fetchCount == 0) {
                        updateUiState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("SecretaryMessagesFragment", "Error fetching conversations: ${error.message}")
                    showEmptyState()
                }
            })
    }

    private fun fetchConversationDetails(
        clientId: String,
        conversationId: String,
        lastMessage: String,
        unreadCount: Int,
        isForwarded: Boolean,
        isInactive: Boolean,
        callback: (Conversation) -> Unit
    ) {
        // Add detailed logging
        Log.d("ClientNameDebug", "Fetching conversation details for clientId: $clientId")

        // First check Users node for client data
        database.child("Users").child(clientId).get()
            .addOnSuccessListener { clientSnapshot ->
                if (clientSnapshot.exists()) {
                    Log.d("ClientNameDebug", "Client data found in Users node")

                    // Get names directly from fields
                    val firstName = clientSnapshot.child("firstName").getValue(String::class.java) ?: ""
                    val lastName = clientSnapshot.child("lastName").getValue(String::class.java) ?: ""

                    Log.d("ClientNameDebug", "firstName: $firstName, lastName: $lastName")

                    // Combine for full name
                    val clientName = if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
                        "${firstName.trim()} ${lastName.trim()}".trim()
                    } else {
                        "Unknown Client"
                    }

                    Log.d("ClientNameDebug", "Resolved clientName: $clientName")

                    // Get profile image
                    val clientImageUrl = clientSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                    // Now get secretary details
                    val secretaryId = currentUserId ?: ""
                    database.child("secretaries").child(secretaryId).get()
                        .addOnSuccessListener { secretarySnapshot ->
                            val secretaryName = secretarySnapshot.child("name").getValue(String::class.java) ?: ""
                            val secretaryImageUrl = secretarySnapshot.child("profilePicture").getValue(String::class.java) ?: ""

                            // Create conversation object
                            val conversation = Conversation(
                                conversationId = conversationId,
                                secretaryId = secretaryId,
                                secretaryName = secretaryName,
                                secretaryImageUrl = secretaryImageUrl,
                                lastMessage = if (isForwarded) "[Forwarded to lawyer] $lastMessage" else lastMessage,
                                unreadCount = unreadCount,
                                clientId = clientId,
                                clientName = clientName,
                                clientImageUrl = clientImageUrl,
                                isForwarded = isForwarded,
                                isActive = !isInactive
                            )

                            Log.d("ClientNameDebug", "Created conversation with clientName: ${conversation.clientName}")

                            // Return the conversation object
                            callback(conversation)
                        }
                        .addOnFailureListener { e ->
                            Log.e("ClientNameDebug", "Error fetching secretary details: ${e.message}")

                            // Still create conversation with client info even if secretary info fails
                            val conversation = Conversation(
                                conversationId = conversationId,
                                secretaryId = secretaryId,
                                secretaryName = "",
                                secretaryImageUrl = "",
                                lastMessage = if (isForwarded) "[Forwarded to lawyer] $lastMessage" else lastMessage,
                                unreadCount = unreadCount,
                                clientId = clientId,
                                clientName = clientName,
                                clientImageUrl = clientImageUrl,
                                isForwarded = isForwarded,
                                isActive = !isInactive
                            )

                            callback(conversation)
                        }
                } else {
                    Log.e("ClientNameDebug", "Client data not found in Users node, checking other sources")

                    // If not found in Users node, try cached data in conversation
                    database.child("conversations").child(conversationId).get()
                        .addOnSuccessListener { conversationSnapshot ->
                            // Try to get client name from conversation metadata
                            val clientName = conversationSnapshot.child("clientName").getValue(String::class.java)
                                ?: "Unknown Client"
                            val clientImageUrl = conversationSnapshot.child("clientImageUrl").getValue(String::class.java) ?: ""

                            val secretaryId = currentUserId ?: ""
                            val secretaryName = conversationSnapshot.child("secretaryName").getValue(String::class.java) ?: ""
                            val secretaryImageUrl = conversationSnapshot.child("secretaryImageUrl").getValue(String::class.java) ?: ""

                            val conversation = Conversation(
                                conversationId = conversationId,
                                secretaryId = secretaryId,
                                secretaryName = secretaryName,
                                secretaryImageUrl = secretaryImageUrl,
                                lastMessage = if (isForwarded) "[Forwarded to lawyer] $lastMessage" else lastMessage,
                                unreadCount = unreadCount,
                                clientId = clientId,
                                clientName = clientName,
                                clientImageUrl = clientImageUrl,
                                isForwarded = isForwarded,
                                isActive = !isInactive
                            )

                            callback(conversation)
                        }
                        .addOnFailureListener { e ->
                            Log.e("ClientNameDebug", "Error fetching conversation data: ${e.message}")

                            // Create conversation with unknown client as fallback
                            val secretaryId = currentUserId ?: ""
                            val conversation = Conversation(
                                conversationId = conversationId,
                                secretaryId = secretaryId,
                                secretaryName = "",
                                secretaryImageUrl = "",
                                lastMessage = if (isForwarded) "[Forwarded to lawyer] $lastMessage" else lastMessage,
                                unreadCount = unreadCount,
                                clientId = clientId,
                                clientName = "Unknown Client",
                                clientImageUrl = "",
                                isForwarded = isForwarded,
                                isActive = !isInactive
                            )

                            callback(conversation)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ClientNameDebug", "Error fetching client data: ${e.message}")

                // Create conversation with unknown client on error
                val secretaryId = currentUserId ?: ""
                val conversation = Conversation(
                    conversationId = conversationId,
                    secretaryId = secretaryId,
                    secretaryName = "",
                    secretaryImageUrl = "",
                    lastMessage = if (isForwarded) "[Forwarded to lawyer] $lastMessage" else lastMessage,
                    unreadCount = unreadCount,
                    clientId = clientId,
                    clientName = "Unknown Client",
                    clientImageUrl = "",
                    isForwarded = isForwarded,
                    isActive = !isInactive
                )

                callback(conversation)
            }
    }

    private fun openChatActivity(conversation: Conversation) {
        // Log debug info
        Log.d("SecretaryMessageFragment", "Opening chat with client: ${conversation.clientId}")
        Log.d("SecretaryMessageFragment", "Using conversation ID: ${conversation.conversationId}")

        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("CLIENT_ID", conversation.clientId)
        intent.putExtra("CONVERSATION_ID", conversation.conversationId)
        intent.putExtra("USER_TYPE", "secretary")
        startActivity(intent)
    }
}