package com.remedio.weassist.Clients

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.remedio.weassist.R
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messagesList: List<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault()) // Add this line

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
    }

    override fun getItemViewType(position: Int): Int {
        val message = messagesList[position]
        return when {
            message.senderId == "system" -> VIEW_TYPE_SYSTEM
            message.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            VIEW_TYPE_SYSTEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_system, parent, false)
                SystemMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messagesList[position]
        val date = Date(message.timestamp)
        val timeString = timeFormat.format(date)

        when (holder) {
            is SentMessageViewHolder -> {
                holder.bind(message)
                holder.tvTimestamp?.text = timeString
            }
            is ReceivedMessageViewHolder -> {
                holder.bind(message)
                holder.tvTimestamp?.text = timeString
            }
            is SystemMessageViewHolder -> holder.bind(message)
        }
    }

    private fun shouldShowDateHeader(position: Int): Boolean {
        if (position == 0) return true
        val currentDate = Date(messagesList[position].timestamp)
        val previousDate = Date(messagesList[position - 1].timestamp)
        return !isSameDay(currentDate, previousDate)
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemCount(): Int = messagesList.size

    // View holder for messages sent by the current user
    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val ivProfile: CircleImageView? = itemView.findViewById(R.id.profile_image)

        fun bind(message: Message) {
            tvMessage.text = message.message
            tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))

            // Load sender image if available and view exists
            ivProfile?.let { imageView ->
                if (!message.senderImageUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(message.senderImageUrl)
                        .placeholder(R.drawable.profile) // Default profile image
                        .error(R.drawable.profile) // Fallback image
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile)
                }
            }
        }
    }

    // View holder for messages received from other users
    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.message_text)
        private val tvSenderName: TextView? = itemView.findViewById(R.id.sender_name)
        val tvTimestamp: TextView? = itemView.findViewById(R.id.message_time)
        private val ivProfile: CircleImageView? = itemView.findViewById(R.id.profile_image)

        fun bind(message: Message) {
            tvMessage.text = message.message

            // Set timestamp if view exists
            tvTimestamp?.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(message.timestamp))

            // Set sender name if available and view exists
            tvSenderName?.let { nameView ->
                if (message.senderName != null) {
                    nameView.text = message.senderName
                    nameView.visibility = View.VISIBLE
                } else {
                    nameView.visibility = View.GONE
                }
            }

            // Load sender image if available and view exists
            ivProfile?.let { imageView ->
                if (!message.senderImageUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(message.senderImageUrl)
                        .placeholder(R.drawable.profile) // Default profile image
                        .error(R.drawable.profile) // Fallback image
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.profile)
                }
            }
        }
    }

    // View holder for system messages
    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.system_message_text)

        fun bind(message: Message) {
            tvMessage.text = message.message
        }
    }
}