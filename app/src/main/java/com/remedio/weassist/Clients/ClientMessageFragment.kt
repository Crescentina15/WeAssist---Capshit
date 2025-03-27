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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize database here to ensure it's ready before any operations
        database = FirebaseDatabase.getInstance().reference
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_client_message, container, false)

        // Initialize views
        conversationsRecyclerView = view.findViewById(R.id.inbox_recycler_view)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        conversationsAdapter = ConversationAdapter(
            conversationList = conversationList,
            onItemClick = { conversation -> openChatActivity(conversation.secretaryId) },
            currentUserId = currentUserId,
            onLongClickListener = null
        )
        conversationsRecyclerView.adapter = conversationsAdapter

        setupSwipeToDelete()
        checkAuthStatus()
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
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val deletedConversation = conversationList[position]

                conversationList.removeAt(position)
                conversationsAdapter.notifyItemRemoved(position)
                updateEmptyState()
                removeConversation(deletedConversation.conversationId)

                Snackbar.make(
                    conversationsRecyclerView,
                    "Conversation with ${deletedConversation.secretaryName} removed",
                    Snackbar.LENGTH_LONG
                ).setAction("UNDO") {
                    conversationList.add(position, deletedConversation)
                    conversationsAdapter.notifyItemInserted(position)
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

                deleteBackground.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
                )
                deleteBackground.draw(c)

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

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(conversationsRecyclerView)
    }

    private fun removeConversation(conversationId: String) {
        currentUserId?.let { uid ->
            database.child("conversations").child(conversationId)
                .child("participantIds").child(uid)
                .setValue(false)
                .addOnFailureListener { e ->
                    Log.e("ClientMessageFragment", "Failed to hide conversation: ${e.message}")
                }

            database.child("conversations").child(conversationId)
                .child("unreadMessages").child(uid)
                .setValue(0)
        }
    }

    private fun restoreConversation(conversationId: String) {
        currentUserId?.let { uid ->
            database.child("conversations").child(conversationId)
                .child("participantIds").child(uid)
                .setValue(true)
                .addOnFailureListener { e ->
                    Log.e("ClientMessageFragment", "Failed to restore conversation: ${e.message}")
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

    private fun fetchConversationsDebug() {
        // Ensure database is initialized before using it
        if (!this::database.isInitialized) {
            database = FirebaseDatabase.getInstance().reference
        }

        currentUserId?.let { uid ->
            database.child("conversations").get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        for (conversationSnapshot in snapshot.children) {
                            val conversationId = conversationSnapshot.key
                            Log.d("FirebaseDB", "Conversation ID: $conversationId")

                            if (conversationSnapshot.child("participantIds").hasChild(uid)) {
                                Log.d("FirebaseDB", "User $uid is a participant in conversation: $conversationId")
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseDB", "Error: ${e.message}")
                }
        }
    }

    private fun fetchConversations() {
        currentUserId?.let { uid ->
            progressIndicator.visibility = View.VISIBLE
            conversationsRecyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.GONE

            database.child("conversations")
                .orderByChild("participantIds/$uid")
                .equalTo(true)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        conversationList.clear()

                        if (!snapshot.exists()) {
                            progressIndicator.visibility = View.GONE
                            updateEmptyState()
                            return
                        }

                        val tempConversationList = mutableListOf<Conversation>()
                        var fetchCount = 0

                        for (conversationSnapshot in snapshot.children) {
                            val conversationId = conversationSnapshot.key ?: continue

                            val otherParticipantId = conversationSnapshot.child("participantIds")
                                .children.firstOrNull { it.key != uid && it.getValue(Boolean::class.java) == true }?.key
                                ?: continue

                            val lastMessage = conversationSnapshot.child("messages").children.lastOrNull()
                                ?.child("message")?.getValue(String::class.java) ?: "No messages yet"

                            val unreadCount = conversationSnapshot.child("unreadMessages/$uid")
                                .getValue(Int::class.java) ?: 0

                            val isTransferred = conversationSnapshot.child("transferred")
                                .getValue(Boolean::class.java) ?: false

                            fetchCount++
                            fetchParticipantInfo(otherParticipantId) { participantName, imageUrl ->
                                val displayName = if (isTransferred ||
                                    conversationSnapshot.hasChild("lawyerId") &&
                                    conversationSnapshot.child("lawyerId").getValue(String::class.java) == otherParticipantId) {
                                    "Lawyer: $participantName"
                                } else {
                                    participantName
                                }

                                // For client view, we don't need client info in the conversation list
                                val conversation = Conversation(
                                    conversationId = conversationId,
                                    secretaryId = otherParticipantId,
                                    secretaryName = displayName,
                                    secretaryImageUrl = imageUrl ?: "",
                                    lastMessage = lastMessage,
                                    unreadCount = unreadCount,
                                    clientId = uid,
                                    clientName = "", // Not needed in client view
                                    clientImageUrl = "" // Not needed in client view
                                )

                                tempConversationList.add(conversation)
                                fetchCount--

                                if (fetchCount == 0) {
                                    progressIndicator.visibility = View.GONE
                                    conversationList.clear()
                                    conversationList.addAll(tempConversationList)
                                    conversationsAdapter.notifyDataSetChanged()
                                    updateEmptyState()
                                }
                            }
                        }

                        if (fetchCount == 0) {
                            progressIndicator.visibility = View.GONE
                            updateEmptyState()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        progressIndicator.visibility = View.GONE
                        updateEmptyState()
                        Log.e("ClientMessagesFragment", "Error fetching conversations: ${error.message}")
                    }
                })
        } ?: run {
            progressIndicator.visibility = View.GONE
            updateEmptyState()
        }
    }

    private fun fetchParticipantInfo(participantId: String, callback: (String, String?) -> Unit) {
        // First check if this is a secretary
        database.child("secretaries").child(participantId).get()
            .addOnSuccessListener { secretarySnapshot ->
                if (secretarySnapshot.exists()) {
                    val name = secretarySnapshot.child("name").getValue(String::class.java)
                        ?: "Unknown Secretary"
                    val imageUrl = secretarySnapshot.child("profilePicture").getValue(String::class.java)
                    callback(name, imageUrl)
                } else {
                    // If not a secretary, check if this is a lawyer
                    database.child("lawyers").child(participantId).get()
                        .addOnSuccessListener { lawyerSnapshot ->
                            if (lawyerSnapshot.exists()) {
                                val name = lawyerSnapshot.child("name").getValue(String::class.java)
                                    ?: "Unknown Lawyer"
                                // Get lawyer profile image URL
                                val imageUrl = lawyerSnapshot.child("profileImageUrl").getValue(String::class.java)
                                callback(name, imageUrl)
                            } else {
                                callback("Unknown Contact", null)
                            }
                        }
                        .addOnFailureListener {
                            callback("Unknown Contact", null)
                        }
                }
            }
            .addOnFailureListener {
                callback("Unknown Contact", null)
            }
    }

    private fun openChatActivity(secretaryId: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("SECRETARY_ID", secretaryId)
        startActivity(intent)
    }
}