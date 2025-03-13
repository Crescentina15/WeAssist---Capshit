package com.remedio.weassist.Models


data class NotificationItem(
    val id: String,
    val senderId: String,
    val senderName: String = "Unknown",
    val message: String,
    val timestamp: Long,
    val type: String,
    val isRead: Boolean = false,
    val conversationId: String? = null,
    val appointmentId: String? = null
)
