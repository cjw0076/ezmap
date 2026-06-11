package com.example.ez_capstone.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.server.models.LocationPoint
import com.example.ez_capstone.server.models.RouteRecommendResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class RouteState {
    object Idle : RouteState()
    object Loading : RouteState()
    data class Success(val response: RouteRecommendResponse) : RouteState()
    data class Error(val message: String) : RouteState()
}

class RouteViewModel : ViewModel() {

    private val repository = RouteRepository()

    private val _routeState = MutableStateFlow<RouteState>(RouteState.Idle)
    val routeState: StateFlow<RouteState> = _routeState

    var lastRouteId: Int = -1
        private set

    fun recommendRoute(query: String, origin: LocationPoint, destination: LocationPoint) {
        viewModelScope.launch {
            _routeState.value = RouteState.Loading
            try {
                val response = repository.recommendRoute(query, origin, destination)
                lastRouteId = response.route_id
                _routeState.value = RouteState.Success(response)
            } catch (e: Exception) {
                _routeState.value = RouteState.Error(e.message ?: "경로 추천 실패")
            }
        }
    }

    fun submitFeedback(rating: Int, comment: String?) {
        if (lastRouteId == -1) return
        viewModelScope.launch {
            runCatching { repository.submitFeedback(lastRouteId, rating, comment) }
        }
    }

    fun reset() { _routeState.value = RouteState.Idle }
}
