package com.remedio.weassist.Models

data class NotificationItem(
    val id: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val type: String,
    val isRead: Boolean,
    val conversationId: String?
)
