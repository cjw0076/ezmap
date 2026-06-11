package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.mcp.client.McpServerConfig
import com.example.ez_capstone.mcp.client.McpToolGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MCP 서버 설정 화면 상태. McpToolGateway를 감싸 등록/삭제/새로고침을 노출.
 */
@HiltViewModel
class McpViewModel @Inject constructor(
    private val gateway: McpToolGateway
) : ViewModel() {

    data class ServerRow(
        val config: McpServerConfig,
        val toolCount: Int,
        val toolNames: List<String>
    )

    data class UiState(
        val servers: List<ServerRow> = emptyList(),
        val busy: Boolean = false,
        val message: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    init {
        reload()
        // 화면 진입 시 백그라운드 새로고침(도구 수·연결 상태 최신화).
        if (gateway.hasServers()) viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            runCatching { gateway.refreshAll() }
            reload()
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun reload() {
        _ui.update {
            it.copy(servers = gateway.listServers().map { c ->
                ServerRow(c, gateway.toolCount(c.id), gateway.toolNames(c.id))
            })
        }
    }

    fun addServer(name: String, url: String, token: String) {
        val n = name.trim(); val u = url.trim()
        if (n.isBlank() || u.isBlank()) {
            _ui.update { it.copy(message = "이름과 URL을 입력하세요") }; return
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            _ui.update { it.copy(message = "URL은 http(s)://로 시작해야 합니다") }; return
        }
        val id = n.lowercase().replace(Regex("[^a-z0-9]"), "").take(16)
            .ifBlank { "srv${gateway.listServers().size}" }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            val cfg = McpServerConfig(id = id, name = n, url = u, authToken = token.trim(), enabled = false)
            val r = gateway.addServer(cfg)
            reload()
            _ui.update {
                it.copy(
                    busy = false,
                    message = r.fold(
                        { "'$n' 저장됨 — 기본 비활성화 상태입니다. 검토 후 활성화하세요." },
                        { e -> "저장 실패: ${e.message}" }
                    )
                )
            }
        }
    }

    fun removeServer(id: String) {
        gateway.removeServer(id)
        reload()
        _ui.update { it.copy(message = "삭제됨") }
    }

    /** 활성/비활성 — 비활성 서버 도구는 에이전트 도구 집합에서 빠진다(풀은 유지). */
    fun toggleServer(id: String, enabled: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            gateway.setEnabled(id, enabled)
            reload()
            _ui.update { it.copy(busy = false, message = if (enabled) "활성화됨" else "비활성화됨") }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            runCatching { gateway.refreshAll() }
            reload()
            _ui.update { it.copy(busy = false, message = "새로고침 완료") }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}
