    package com.remedio.weassist

    data class Conversation(
        val conversationId: String = "",
        val secretaryId: String = "",
        val secretaryName: String = "",  // 🔹 Added this field
        val lastMessage: String = "",
        val unreadCount: Int = 0
    )

