package com.remedio.weassist.MessageConversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.database.FirebaseDatabase
import com.remedio.weassist.R

class ConversationAdapter(
    private val conversationList: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit,
    private val currentUserId: String? = null,
    private val onLongClickListener: ((View, Int) -> Boolean)? = null
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.secretary_name)
        val lastMessageTextView: TextView = view.findViewById(R.id.last_message)
        val unreadCountTextView: TextView = view.findViewById(R.id.unread_count)
        val timestampTextView: TextView = view.findViewById(R.id.timestamp)
        val profileImageView: ShapeableImageView = view.findViewById(R.id.profile_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversationList[position]

        // Determine if current user is a secretary/lawyer (by checking if they're conversing with client)
        val isStaffView = currentUserId != null && conversation.clientId != currentUserId

        // Display the appropriate name and image based on user role
        if (isStaffView) {
            // Staff (secretary/lawyer) viewing client conversation
            holder.nameTextView.text = conversation.clientName
            loadProfileImage(holder.profileImageView, conversation.clientImageUrl)
        } else {
            // Client viewing staff conversation
            holder.nameTextView.text = conversation.secretaryName
            loadProfileImage(holder.profileImageView, conversation.secretaryImageUrl)
        }

        holder.lastMessageTextView.text = conversation.lastMessage
        holder.unreadCountTextView.text = conversation.unreadCount.toString()
        holder.unreadCountTextView.visibility = if (conversation.unreadCount > 0) View.VISIBLE else View.GONE

        // You can set timestamp if available in your Conversation model
        // holder.timestampTextView.text = formatTimestamp(conversation.timestamp)

        // Check for forwarded conversation status
        checkIfConversationIsActive(conversation.conversationId) { isActive, isHandledByLawyer ->
            if (!isActive && isStaffView) {
                // This conversation has been forwarded and staff is inactive
                holder.itemView.alpha = 0.5f
                holder.lastMessageTextView.text = "[Forwarded to lawyer] " + holder.lastMessageTextView.text
            } else {
                holder.itemView.alpha = 1.0f
            }

            // Set click listener
            holder.itemView.setOnClickListener {
                if (!isActive && isStaffView) {
                    val context = holder.itemView.context
                    Toast.makeText(
                        context,
                        "This conversation has been forwarded to a lawyer and is now read-only.",
                        Toast.LENGTH_SHORT
                    ).show()
                    onItemClick(conversation)
                } else {
                    onItemClick(conversation)
                }
            }
        }

        // Only add long press listener if it's provided AND this is a staff view
        if (onLongClickListener != null && isStaffView) {
            checkIfConversationIsActive(conversation.conversationId) { isActive, _ ->
                if (isActive) {
                    holder.itemView.setOnLongClickListener { view ->
                        onLongClickListener.invoke(view, position)
                    }
                } else {
                    // No long press for forwarded conversations
                    holder.itemView.setOnLongClickListener(null)
                }
            }
        }
    }

    private fun loadProfileImage(imageView: ShapeableImageView, imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(imageUrl)
                .placeholder(R.drawable.profile) // Your default profile drawable
                .error(R.drawable.profile) // Fallback if error loading
                .circleCrop() // Matches your circular ShapeableImageView
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.profile)
        }
    }

    private fun checkIfConversationIsActive(conversationId: String, callback: (Boolean, Boolean) -> Unit) {
        val database = FirebaseDatabase.getInstance().reference
        database.child("conversations").child(conversationId).get()
            .addOnSuccessListener { snapshot ->
                val isActive = !(snapshot.child("secretaryActive").exists() &&
                        snapshot.child("secretaryActive").getValue(Boolean::class.java) == false)

                val isHandledByLawyer = snapshot.child("handledByLawyer").exists() &&
                        snapshot.child("handledByLawyer").getValue(Boolean::class.java) == true

                callback(isActive, isHandledByLawyer)
            }
            .addOnFailureListener {
                callback(true, false)
            }
    }

    override fun getItemCount(): Int = conversationList.size
}