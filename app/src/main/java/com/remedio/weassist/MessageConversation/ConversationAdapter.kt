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
        val isStaffView = currentUserId != null && conversation.clientId != currentUserId
        val isLawyer = conversation.secretaryName.startsWith("Lawyer:") ||
                conversation.secretaryId.startsWith("lawyer_") // Adjust based on your ID pattern

        if (isStaffView) {
            holder.nameTextView.text = conversation.clientName
            loadProfileImage(holder.profileImageView, conversation.clientImageUrl)
        } else {
            holder.nameTextView.text = conversation.secretaryName
            loadProfileImage(holder.profileImageView, conversation.secretaryImageUrl, isLawyer)
        }

        // Rest of your binding code...
        holder.lastMessageTextView.text = conversation.lastMessage
        holder.unreadCountTextView.text = conversation.unreadCount.toString()
        holder.unreadCountTextView.visibility = if (conversation.unreadCount > 0) View.VISIBLE else View.GONE

        // Apply visual treatment for forwarded/inactive conversations
        if (conversation.isForwarded || !conversation.isActive) {
            // This conversation has been forwarded
            holder.itemView.alpha = 0.6f

            // If not already showing forwarded message prefix, show it
            if (!holder.lastMessageTextView.text.toString().startsWith("[Forwarded to lawyer]")) {
                holder.lastMessageTextView.text = "[Forwarded to lawyer] " + holder.lastMessageTextView.text
            }
        } else {
            holder.itemView.alpha = 1.0f
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            if (conversation.isForwarded || !conversation.isActive) {
                // For forwarded conversations, show a toast explaining the status
                val context = holder.itemView.context
                Toast.makeText(
                    context,
                    "This conversation has been forwarded to a lawyer and is now read-only.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Always invoke the click handler - the ChatActivity will handle the read-only state
            onItemClick(conversation)
        }
    }

    private fun loadProfileImage(imageView: ShapeableImageView, imageUrl: String?, isLawyer: Boolean = false) {
        val placeholder = if (isLawyer) {
            R.drawable.baseline_circle_24
        } else {
            R.drawable.baseline_circle_24
        }

        when {
            !imageUrl.isNullOrEmpty() -> {
                Glide.with(imageView.context)
                    .load(imageUrl)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(imageView)
            }
            isLawyer -> {
                // Load default lawyer image if no URL provided
                Glide.with(imageView.context)
                    .load(R.drawable.baseline_circle_24)
                    .circleCrop()
                    .into(imageView)
            }
            else -> {
                // Load default secretary image
                imageView.setImageResource(R.drawable.baseline_circle_24)
            }
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