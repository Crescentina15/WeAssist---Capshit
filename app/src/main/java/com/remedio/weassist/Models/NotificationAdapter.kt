package com.remedio.weassist.Models

import java.text.SimpleDateFormat
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.remedio.weassist.R

class NotificationAdapter(
    private val notifications: List<NotificationItem>,
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Define view types for different notification styles
    companion object {
        private const val VIEW_TYPE_NORMAL = 1
        private const val VIEW_TYPE_FORWARDED_CASE = 2
    }

    // Determine view type based on notification content
    override fun getItemViewType(position: Int): Int {
        val notification = notifications[position]
        return if (notification.type == "NEW_CASE_ASSIGNED" && notification.forwardingMessage != null) {
            VIEW_TYPE_FORWARDED_CASE
        } else {
            VIEW_TYPE_NORMAL
        }
    }

    // Create appropriate ViewHolder based on view type
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_FORWARDED_CASE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_forwarded_case_notification, parent, false)
                ForwardedCaseViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_notification, parent, false)
                NotificationViewHolder(view)
            }
        }
    }

    // Bind the ViewHolder with data from the notification
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val notification = notifications[position]

        when (holder) {
            is NotificationViewHolder -> holder.bind(notification)
            is ForwardedCaseViewHolder -> holder.bind(notification)
        }
    }

    override fun getItemCount() = notifications.size

    // Regular notification ViewHolder (your existing implementation)
    inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSenderName: TextView = view.findViewById(R.id.tvSenderName)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val unreadIndicator: View = view.findViewById(R.id.unreadIndicator)

        fun bind(notification: NotificationItem) {
            tvSenderName.text = notification.senderName
            tvMessage.text = notification.message

            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val dateString = dateFormat.format(java.util.Date(notification.timestamp))
            tvTimestamp.text = dateString

            // Set unread indicator visibility
            unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(notification)
            }
        }
    }

    // New ViewHolder for forwarded case notifications
    inner class ForwardedCaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSenderName: TextView = view.findViewById(R.id.tvSenderName)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvForwardingMessage: TextView = view.findViewById(R.id.tvForwardingMessage)
        val unreadIndicator: View = view.findViewById(R.id.unreadIndicator)

        fun bind(notification: NotificationItem) {
            tvSenderName.text = notification.senderName
            tvMessage.text = notification.message
            tvForwardingMessage.text = notification.forwardingMessage

            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val dateString = dateFormat.format(java.util.Date(notification.timestamp))
            tvTimestamp.text = dateString

            // Set unread indicator visibility
            unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(notification)
            }
        }
    }
}