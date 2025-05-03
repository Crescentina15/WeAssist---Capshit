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

        // Reset visual indicators for forwarded conversations
        if (!conversation.isForwarded && conversation.isActive) {
            holder.itemView.alpha = 1.0f
            holder.lastMessageTextView.text = conversation.lastMessage
        } else {
            holder.itemView.alpha = 0.6f
            holder.lastMessageTextView.text = "[Forwarded to lawyer] " + conversation.lastMessage
        }

        holder.itemView.setOnClickListener {
            if (conversation.isForwarded) {
                Toast.makeText(
                    holder.itemView.context,
                    "This conversation was forwarded to a lawyer and is read-only.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onItemClick(conversation)
            }
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