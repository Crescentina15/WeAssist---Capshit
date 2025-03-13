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
        val view = inflater.inflate(R.layout.fragment_secretary_message, container, false)
        conversationsRecyclerView = view.findViewById(R.id.inbox_recycler_view)
        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // In SecretaryMessageFragment
        conversationsAdapter = ConversationAdapter(
            conversationList,
            { conversation -> openChatActivity(conversation.clientId) },
            currentUserId  // Pass current user ID (secretary)
        )
        conversationsRecyclerView.adapter = conversationsAdapter

        database = FirebaseDatabase.getInstance().reference

        checkAuthStatus()
        fetchConversations()
        setupSwipeToDelete()

        return view
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            private val deleteBackground = ColorDrawable(Color.RED)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // We don't want to support moving items
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val conversation = conversationList[position]

                // Remove from the local list first
                conversationList.removeAt(position)
                conversationsAdapter.notifyItemRemoved(position)

                // Delete the conversation from Firebase
                deleteConversation(conversation.conversationId)

                // Show undo option
                Snackbar.make(
                    conversationsRecyclerView,
                    "Conversation with ${conversation.clientName} removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    // Add the conversation back to the list
                    conversationList.add(position, conversation)
                    conversationsAdapter.notifyItemInserted(position)

                    // Restore in Firebase
                    restoreConversation(conversation.conversationId)
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

                // Draw the red delete background
                when {
                    dX < 0 -> { // Swiping to the left
                        val deleteIconMargin = (itemView.height - 24) / 2
                        val deleteIconTop = itemView.top + deleteIconMargin
                        val deleteIconBottom = itemView.bottom - deleteIconMargin
                        val deleteIconLeft = itemView.right - deleteIconMargin - 24
                        val deleteIconRight = itemView.right - deleteIconMargin

                        // Draw the background
                        deleteBackground.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                        )
                        deleteBackground.draw(c)

                        // You could also draw a delete icon here if you wanted
                        // context?.let {
                        //     val deleteIcon = ContextCompat.getDrawable(it, R.drawable.ic_delete)
                        //     deleteIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
                        //     deleteIcon?.draw(c)
                        // }
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        // Attach the swipe callback to the RecyclerView
        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(conversationsRecyclerView)
    }

    private fun deleteConversation(conversationId: String) {
        // Option 1: Completely delete the conversation
        // database.child("conversations").child(conversationId).removeValue()

        // Option 2: Hide the conversation from this user but don't delete it
        // This is often better as the other participant might still want to see it
        currentUserId?.let { userId ->
            val conversationRef = database.child("conversations").child(conversationId)
            conversationRef.child("hiddenFrom").child(userId).setValue(true)
                .addOnSuccessListener {
                    Log.d("SecretaryMessageFragment", "Conversation hidden successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("SecretaryMessageFragment", "Error hiding conversation: ${e.message}")
                }
        }
    }

    private fun restoreConversation(conversationId: String) {
        currentUserId?.let { userId ->
            val conversationRef = database.child("conversations").child(conversationId)
            conversationRef.child("hiddenFrom").child(userId).removeValue()
                .addOnSuccessListener {
                    Log.d("SecretaryMessageFragment", "Conversation restored successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("SecretaryMessageFragment", "Error restoring conversation: ${e.message}")
                }
        }
    }

    private fun checkAuthStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("FirebaseAuth", "User is NOT authenticated!")
        } else {
            Log.d("FirebaseAuth", "Authenticated as: ${user.uid}")
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

                        // Check if this conversation is hidden for the current user
                        if (conversationSnapshot.child("hiddenFrom")
                                .hasChild(currentUserId!!)) {
                            continue // Skip this conversation as it's hidden
                        }

                        val clientId = conversationSnapshot.child("participantIds")
                            .children.firstOrNull { it.key != currentUserId }?.key ?: continue

                        if (!conversationSnapshot.child("participantIds")
                                .hasChild(currentUserId!!)
                        ) {
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

                            // Only update the adapter once all conversations have been fetched
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
        // Make sure both these values are set correctly
        intent.putExtra("SECRETARY_ID", currentUserId) // Secretary's own ID
        intent.putExtra("CLIENT_ID", clientId) // Client's ID
        // You might want to log these values to verify they're correct
        Log.d(
            "SecretaryMessageFragment",
            "Opening chat with Secretary ID: $currentUserId, Client ID: $clientId"
        )
        startActivity(intent)
    }
}