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
        // Add debug function call similar to ClientMessageFragment
        fetchConversationsDebug()

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
                        val clientId = conversationSnapshot.child("participantIds")
                            .children.firstOrNull { it.key != currentUserId }?.key ?: continue

                        if (!conversationSnapshot.child("participantIds").hasChild(currentUserId!!)) {
                            Log.e("FirebaseDB", "User $currentUserId is NOT a participant in $conversationId")
                            continue
                        }

                        // CHANGE: Don't skip forwarded conversations, but check the flag to mark them as inactive
                        val isForwarded = conversationSnapshot.child("forwarded").getValue(Boolean::class.java) ?: false
                        val secretaryActive = conversationSnapshot.child("secretaryActive").getValue(Boolean::class.java) ?: true

                        val lastMessage = conversationSnapshot.child("messages").children.lastOrNull()
                            ?.child("message")?.getValue(String::class.java) ?: "No messages yet"

                        val unreadCount = conversationSnapshot.child("unreadMessages/$currentUserId")
                            .getValue(Int::class.java) ?: 0

                        fetchCount++
                        // Fetch both client and secretary details
                        fetchConversationDetails(clientId, conversationId, lastMessage, unreadCount, isForwarded, !secretaryActive) { conversation ->
                            tempConversationList.add(conversation)
                            fetchCount--

                            // Only update the adapter once all details are fetched
                            if (fetchCount == 0) {
                                conversationList.clear()
                                conversationList.addAll(tempConversationList)
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

// 3. Update the fetchConversationDetails method to pass the forwarded status:

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

        // Direct reference to Users node
        database.child("Users").child(clientId).get()
            .addOnSuccessListener { clientSnapshot ->
                if (clientSnapshot.exists()) {
                    Log.d("ClientNameDebug", "Client data found in Users node: ${clientSnapshot.value}")

                    // Get names directly from fields
                    val firstName = clientSnapshot.child("firstName").getValue(String::class.java)
                    val lastName = clientSnapshot.child("lastName").getValue(String::class.java)

                    Log.d("ClientNameDebug", "firstName: $firstName, lastName: $lastName")

                    // Combine for full name
                    val clientName = "${firstName ?: ""} ${lastName ?: ""}".trim()
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
                                clientName = clientName.ifEmpty { "Unknown Client" },
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
                                clientName = clientName.ifEmpty { "Unknown Client" },
                                clientImageUrl = clientImageUrl,
                                isForwarded = isForwarded,
                                isActive = !isInactive
                            )

                            callback(conversation)
                        }
                } else {
                    Log.e("ClientNameDebug", "Client data not found in Users node for ID: $clientId")

                    // Create conversation with unknown client
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

    // Change this method to accept a Conversation object instead of just the clientId
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