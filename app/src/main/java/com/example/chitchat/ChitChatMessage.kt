package com.example.chitchat

import java.util.Date

data class ChitChatMessage(
        val id: String,
        val name: String,
        val ip: String,
        val location: Array<Double?>,
        val date: Date,
        var likes: Int,
        var dislikes: Int,
        val message: String
)