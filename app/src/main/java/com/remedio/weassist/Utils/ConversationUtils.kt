package com.remedio.weassist.Utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

object ConversationUtils {
    /**
     * Standardized method to generate conversation IDs consistently throughout the app
     */
    fun generateConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) {
            "conversation_${userId1}_${userId2}"
        } else {
            "conversation_${userId2}_${userId1}"
        }
    }

    /**
     * Extracts the problem description from the first message in a conversation
     */
    fun extractProblemFromConversation(
        databaseRef: DatabaseReference,
        conversationId: String,
        callback: (String) -> Unit
    ) {
        databaseRef.child("conversations").child(conversationId)
            .child("messages").orderByChild("timestamp").limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(messagesSnapshot: DataSnapshot) {
                    var problemDescription = "No details available"

                    if (messagesSnapshot.exists() && messagesSnapshot.childrenCount > 0) {
                        val firstMessage = messagesSnapshot.children.first()
                        problemDescription = firstMessage.child("message").getValue(String::class.java)
                            ?: "No details available"
                    }

                    callback(problemDescription)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConversationUtils", "Failed to fetch original problem: ${error.message}")
                    callback("No details available")
                }
            })
    }

    /**
     * Marks a conversation as forwarded and closes it for the secretary
     */
    fun markConversationAsForwarded(
        databaseRef: DatabaseReference,
        conversationId: String,
        lawyerId: String,
        callback: (Boolean) -> Unit
    ) {
        val conversationRef = databaseRef.child("conversations").child(conversationId)

        val updates = mapOf(
            "forwarded" to true,
            "secretaryActive" to false,
            "forwardedToLawyerId" to lawyerId,
            "forwardedAt" to System.currentTimeMillis()
        )

        conversationRef.updateChildren(updates)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("ConversationUtils", "Failed to mark conversation as forwarded: ${e.message}")
                callback(false)
            }
    }

    /**
     * Checks if a conversation exists between two users
     */
    fun checkConversationExists(
        databaseRef: DatabaseReference,
        userId1: String,
        userId2: String,
        callback: (String?) -> Unit
    ) {
        val conversationId = generateConversationId(userId1, userId2)

        databaseRef.child("conversations").child(conversationId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        callback(conversationId)
                    } else {
                        callback(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConversationUtils", "Failed to check conversation: ${error.message}")
                    callback(null)
                }
            })
    }

    /**
     * Adds a system message to a conversation
     */
    fun addSystemMessage(
        databaseRef: DatabaseReference,
        conversationId: String,
        message: String,
        callback: (Boolean) -> Unit
    ) {
        val systemMessage = mapOf(
            "senderId" to "system",
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )

        databaseRef.child("conversations").child(conversationId)
            .child("messages").push().setValue(systemMessage)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e("ConversationUtils", "Failed to add system message: ${e.message}")
                callback(false)
            }
    }

    /**
     * Gets all conversations for a user
     */
    fun getUserConversations(
        databaseRef: DatabaseReference,
        userId: String,
        callback: (List<String>) -> Unit
    ) {
        databaseRef.child("conversations")
            .orderByChild("participantIds/$userId")
            .equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val conversationIds = mutableListOf<String>()

                    if (snapshot.exists()) {
                        for (conversationSnapshot in snapshot.children) {
                            conversationIds.add(conversationSnapshot.key ?: continue)
                        }
                    }

                    callback(conversationIds)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ConversationUtils", "Failed to get user conversations: ${error.message}")
                    callback(emptyList())
                }
            })
    }

    /**
     * Increments the unread message counter for a user in a conversation
     */
    fun incrementUnreadCounter(
        databaseRef: DatabaseReference,
        conversationId: String,
        userId: String
    ) {
        val unreadRef = databaseRef.child("conversations").child(conversationId)
            .child("unreadMessages").child(userId)

        unreadRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCount = snapshot.getValue(Int::class.java) ?: 0
                unreadRef.setValue(currentCount + 1)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ConversationUtils", "Error updating unread count: ${error.message}")
            }
        })
    }

    /**
     * Resets the unread message counter for a user in a conversation
     */
    fun resetUnreadCounter(
        databaseRef: DatabaseReference,
        conversationId: String,
        userId: String
    ) {
        databaseRef.child("conversations").child(conversationId)
            .child("unreadMessages").child(userId).setValue(0)
    }
}