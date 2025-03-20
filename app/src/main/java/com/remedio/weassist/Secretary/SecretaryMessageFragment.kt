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
        // First check if this conversation already has an appointedLawyerId
        database.child("conversations").child(conversation.conversationId)
            .child("appointedLawyerId").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.getValue(String::class.java) != null) {
                    // This conversation already has an appointed lawyer, just update their visibility
                    val lawyerId = snapshot.getValue(String::class.java)!!
                    Log.d("SecretaryMessageFragment", "Found appointed lawyer ID: $lawyerId for conversation")

                    // Simply update the lawyer's participant status to true (making it visible to them)
                    database.child("conversations").child(conversation.conversationId)
                        .child("participantIds").child(lawyerId).setValue(true)
                        .addOnSuccessListener {
                            Log.d("SecretaryMessageFragment", "Successfully made conversation visible to appointed lawyer")

                            // Add a system message indicating the conversation was forwarded
                            val systemMessageRef = database.child("conversations").child(conversation.conversationId)
                                .child("messages").push()

                            val systemMessage = mapOf(
                                "message" to "This conversation was forwarded to the lawyer by a secretary",
                                "senderId" to "system",
                                "timestamp" to ServerValue.TIMESTAMP
                            )

                            systemMessageRef.setValue(systemMessage)
                                .addOnSuccessListener {
                                    // Set unread counter for lawyer
                                    database.child("conversations").child(conversation.conversationId)
                                        .child("unreadMessages").child(lawyerId).setValue(1)
                                        .addOnSuccessListener {
                                            Toast.makeText(
                                                requireContext(),
                                                "Conversation forwarded to appointed lawyer",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("SecretaryMessageFragment", "Failed to add system message: ${e.message}")
                                    Toast.makeText(
                                        requireContext(),
                                        "Error adding notification message: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("SecretaryMessageFragment", "Failed to update lawyer visibility: ${e.message}")
                            Toast.makeText(
                                requireContext(),
                                "Failed to forward to lawyer: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    // No appointed lawyer found, use the original approach of finding any lawyer
                    Log.d("SecretaryMessageFragment", "No appointed lawyer found for this conversation, searching for any lawyer")

                    // Instead of querying by role, we'll get all users and filter client-side
                    database.child("Users").get()
                        .addOnSuccessListener { usersSnapshot ->
                            if (usersSnapshot.exists()) {
                                // Find lawyers by checking role field client-side
                                val lawyers = usersSnapshot.children.filter { userSnapshot ->
                                    val role = userSnapshot.child("role").getValue(String::class.java)
                                    role == "lawyer"
                                }

                                if (lawyers.isNotEmpty()) {
                                    // Use the first lawyer we find
                                    val lawyerId = lawyers.first().key

                                    if (lawyerId != null) {
                                        Log.d("SecretaryMessageFragment", "Found lawyer ID: $lawyerId from users list")

                                        // Check if lawyer already exists in participantIds but is set to false
                                        database.child("conversations").child(conversation.conversationId)
                                            .child("participantIds").child(lawyerId).get()
                                            .addOnSuccessListener { participantSnapshot ->
                                                if (participantSnapshot.exists()) {
                                                    // Lawyer already in participants, just update visibility
                                                    database.child("conversations").child(conversation.conversationId)
                                                        .child("participantIds").child(lawyerId).setValue(true)
                                                        .addOnSuccessListener {
                                                            // Add system message and set unread counter
                                                            addForwardingSystemMessage(conversation.conversationId, lawyerId)
                                                        }
                                                } else {
                                                    // Add lawyer to participants and set appointedLawyerId
                                                    val updates = hashMapOf<String, Any>(
                                                        "participantIds/$lawyerId" to true,
                                                        "appointedLawyerId" to lawyerId,
                                                        "unreadMessages/$lawyerId" to 1
                                                    )

                                                    database.child("conversations").child(conversation.conversationId)
                                                        .updateChildren(updates)
                                                        .addOnSuccessListener {
                                                            // Add system message
                                                            addForwardingSystemMessage(conversation.conversationId, lawyerId)
                                                        }
                                                        .addOnFailureListener { e ->
                                                            Log.e("SecretaryMessageFragment", "Failed to add lawyer to conversation: ${e.message}")
                                                            Toast.makeText(
                                                                requireContext(),
                                                                "Failed to forward conversation: ${e.message}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                }
                                            }
                                    } else {
                                        noLawyersFoundMessage()
                                    }
                                } else {
                                    noLawyersFoundMessage()
                                }
                            } else {
                                noLawyersFoundMessage()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("SecretaryMessageFragment", "Failed to query for users: ${e.message}")
                            Toast.makeText(
                                requireContext(),
                                "Error finding lawyers: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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

    private fun addForwardingSystemMessage(conversationId: String, lawyerId: String) {
        val systemMessageRef = database.child("conversations").child(conversationId)
            .child("messages").push()

        val systemMessage = mapOf(
            "message" to "This conversation was forwarded to the lawyer by a secretary",
            "senderId" to "system",
            "timestamp" to ServerValue.TIMESTAMP
        )

        systemMessageRef.setValue(systemMessage)
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "Conversation forwarded to lawyer successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Log.e("SecretaryMessageFragment", "Failed to add system message: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Forwarded, but failed to add notification message",
                    Toast.LENGTH_SHORT
                ).show()
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