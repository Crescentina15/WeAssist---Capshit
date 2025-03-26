package com.remedio.weassist.Clients

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


class ClientMessageFragment : Fragment() {
    private lateinit var database: DatabaseReference
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var conversationsAdapter: ConversationAdapter
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var progressIndicator: CircularProgressIndicator

    private val conversationList = mutableListOf<Conversation>()
    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fetchConversationsDebug()
        val view = inflater.inflate(R.layout.fragment_client_message, container, false)

        // Initialize views
        conversationsRecyclerView = view.findViewById(R.id.inbox_recycler_view)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // In ClientMessageFragment
        conversationsAdapter = ConversationAdapter(
            conversationList,
            { conversation -> openChatActivity(conversation.secretaryId) },
            currentUserId  // Pass current user ID (client)
        )
        conversationsRecyclerView.adapter = conversationsAdapter

        // Set up swipe to delete functionality
        setupSwipeToDelete()

        database = FirebaseDatabase.getInstance().reference

        checkAuthStatus() // 🔹 Check if the user is logged in

        fetchConversations()

        return view
    }

    private fun updateEmptyState() {
        if (conversationList.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            conversationsRecyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            conversationsRecyclerView.visibility = View.VISIBLE
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

                // Update empty state
                updateEmptyState()

                // Remove from Firebase
                removeConversation(deletedConversation.conversationId)

                // Show undo snackbar
                Snackbar.make(
                    conversationsRecyclerView,
                    "Conversation with ${deletedConversation.secretaryName} removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Restore the conversation if user clicks undo
                    conversationList.add(position, deletedConversation)
                    conversationsAdapter.notifyItemInserted(position)

                    // Update empty state
                    updateEmptyState()

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
                Log.d("ClientMessageFragment", "Conversation hidden successfully")
            }
            .addOnFailureListener { e ->
                Log.e("ClientMessageFragment", "Failed to hide conversation: ${e.message}")
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
                Log.d("ClientMessageFragment", "Conversation restored successfully")
            }
            .addOnFailureListener { e ->
                Log.e("ClientMessageFragment", "Failed to restore conversation: ${e.message}")
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

        // Show loading indicator and hide other views
        progressIndicator.visibility = View.VISIBLE
        conversationsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE

        val conversationsRef = database.child("conversations")
        conversationsRef.orderByChild("participantIds/$currentUserId").equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    conversationList.clear()

                    if (!snapshot.exists()) {
                        Log.e("FirebaseDB", "No conversations found for user: $currentUserId")
                        progressIndicator.visibility = View.GONE
                        updateEmptyState()
                        return
                    }

                    val tempConversationList = mutableListOf<Conversation>()
                    var fetchCount = 0

                    for (conversationSnapshot in snapshot.children) {
                        val conversationId = conversationSnapshot.key ?: continue

                        // Find the other participant ID (could be secretary or lawyer)
                        val otherParticipantId = conversationSnapshot.child("participantIds")
                            .children.firstOrNull { it.key != currentUserId &&
                                    it.getValue(Boolean::class.java) == true }?.key
                            ?: continue

                        if (!conversationSnapshot.child("participantIds").hasChild(currentUserId!!)) {
                            Log.e("FirebaseDB", "User $currentUserId is NOT a participant in $conversationId")
                            continue
                        }

                        val lastMessage = conversationSnapshot.child("messages").children.lastOrNull()
                            ?.child("message")?.getValue(String::class.java) ?: "No messages yet"

                        val unreadCount = conversationSnapshot.child("unreadMessages/$currentUserId")
                            .getValue(Int::class.java) ?: 0

                        // Check if this conversation was transferred from a secretary to a lawyer
                        val isTransferred = conversationSnapshot.child("transferred")
                            .getValue(Boolean::class.java) ?: false

                        fetchCount++
                        fetchParticipantName(otherParticipantId) { participantName ->
                            // Determine if this is a conversation with a lawyer (using the transferred flag)
                            val isLawyer = isTransferred ||
                                    conversationSnapshot.hasChild("lawyerId") &&
                                    conversationSnapshot.child("lawyerId").getValue(String::class.java) == otherParticipantId

                            // Create a descriptive name that shows if it's a lawyer
                            val displayName = if (isLawyer) {
                                "Lawyer: $participantName"
                            } else {
                                participantName
                            }

                            val conversation = Conversation(
                                conversationId = conversationId,
                                clientId = currentUserId ?: "",
                                secretaryId = otherParticipantId, // This could be a secretary or lawyer ID
                                clientName = "", // Empty for client-side view
                                secretaryName = displayName, // This could be secretary or lawyer name
                                lastMessage = lastMessage,
                                unreadCount = unreadCount
                            )

                            tempConversationList.add(conversation)
                            fetchCount--

                            // Only update the adapter once all names are fetched
                            if (fetchCount == 0) {
                                progressIndicator.visibility = View.GONE
                                conversationList.clear()
                                conversationList.addAll(tempConversationList)
                                conversationsAdapter.notifyDataSetChanged()

                                // Update empty state after data is loaded
                                updateEmptyState()
                            }
                        }
                    }

                    // If no conversations to fetch, update UI immediately
                    if (fetchCount == 0) {
                        progressIndicator.visibility = View.GONE
                        updateEmptyState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ClientMessagesFragment", "Error fetching conversations: ${error.message}")
                    progressIndicator.visibility = View.GONE
                    updateEmptyState()
                }
            })
    }

    private fun fetchLawyerName(lawyerId: String, callback: (String) -> Unit) {
        val lawyersRef = database.child("lawyers").child(lawyerId).child("name")
        lawyersRef.get().addOnSuccessListener { snapshot ->
            val lawyerName = snapshot.value?.toString() ?: "Unknown Lawyer"
            callback(lawyerName)
        }.addOnFailureListener {
            callback("Unknown Lawyer")
        }
    }

    // Also, let's create a combined method to fetch either secretary or lawyer name
    private fun fetchParticipantName(participantId: String, callback: (String) -> Unit) {
        // First check if this is a secretary
        database.child("secretaries").child(participantId).child("name")
            .get().addOnSuccessListener { secretarySnapshot ->
                if (secretarySnapshot.exists()) {
                    val name = secretarySnapshot.value?.toString() ?: "Unknown Secretary"
                    callback(name)
                } else {
                    // If not a secretary, check if this is a lawyer
                    database.child("lawyers").child(participantId).child("name")
                        .get().addOnSuccessListener { lawyerSnapshot ->
                            if (lawyerSnapshot.exists()) {
                                val name = lawyerSnapshot.value?.toString() ?: "Unknown Lawyer"
                                callback(name)
                            } else {
                                callback("Unknown Contact")
                            }
                        }.addOnFailureListener {
                            callback("Unknown Contact")
                        }
                }
            }.addOnFailureListener {
                // If query fails, try lawyer directly
                fetchLawyerName(participantId, callback)
            }
    }

    private fun openChatActivity(secretaryId: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("SECRETARY_ID", secretaryId)
        startActivity(intent)
    }
}