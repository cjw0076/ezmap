package com.example.ez_capstone.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.agent.models.PlaceResult
import com.example.ez_capstone.api.KakaoLocalApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val kakaoLocalApi: KakaoLocalApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<PlaceResult>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val results: StateFlow<List<PlaceResult>> = _results
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { q ->
                    _isLoading.value = true
                    try {
                        _results.value = kakaoLocalApi.searchKeyword(q, size = 10)
                    } catch (_: Exception) {
                        _results.value = emptyList()
                    } finally {
                        _isLoading.value = false
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        if (query.isBlank()) _results.value = emptyList()
    }
}
