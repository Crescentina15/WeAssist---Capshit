package com.remedio.weassist.Secretary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        return view
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
