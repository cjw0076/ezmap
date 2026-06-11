package com.example.ez_capstone.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.server.models.ChatContext
import com.example.ez_capstone.server.models.ChatMessage
import com.example.ez_capstone.server.models.ChatMessageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiMessage(
    val role: String,       // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class ChatState {
    data object Idle : ChatState()
    data object Sending : ChatState()
    data class Error(val message: String) : ChatState()
}

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState

    private var conversationId: String? = null

    fun sendMessage(text: String, context: ChatContext? = null) {
        if (text.isBlank()) return

        // 사용자 메시지 즉시 추가
        val userMsg = ChatUiMessage(role = "user", content = text)
        _messages.value = _messages.value + userMsg

        _chatState.value = ChatState.Sending

        viewModelScope.launch {
            try {
                val request = ChatMessageRequest(
                    message = text,
                    conversation_id = conversationId,
                    context = context
                )
                val response = ApiClient.chatApi.sendMessage(request)

                conversationId = response.conversation_id

                val assistantMsg = ChatUiMessage(
                    role = "assistant",
                    content = response.displayText
                )
                _messages.value = _messages.value + assistantMsg

                _chatState.value = ChatState.Idle
            } catch (e: Exception) {
                _chatState.value = ChatState.Error(e.message ?: "메시지 전송에 실패했습니다")
            }
        }
    }

    fun loadHistory(convId: String) {
        conversationId = convId
        viewModelScope.launch {
            try {
                val response = ApiClient.chatApi.getChatHistory(convId)
                _messages.value = response.messages.map { msg ->
                    ChatUiMessage(role = msg.role, content = msg.content)
                }
            } catch (e: Exception) {
                _chatState.value = ChatState.Error("대화 기록을 불러올 수 없습니다")
            }
        }
    }

    fun startNewConversation() {
        conversationId = null
        _messages.value = emptyList()
        _chatState.value = ChatState.Idle
    }
}
