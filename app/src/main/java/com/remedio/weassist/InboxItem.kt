package com.remedio.weassist

data class InboxItem(
    val secretaryId: String,
    val secretaryName: String,  // Ensure this matches the adapter reference
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int
)


