package com.example.ez_capstone.resilience

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 실시간 네트워크 상태 모니터.
 *
 * 멀티네트워크(WiFi+셀룰러 동시) 환경 버그 수정:
 * 기존엔 onLost가 '잃은 한 네트워크' 때문에 _isOnline=false로 전체를 오프라인 처리했다.
 * WiFi·LTE가 동시에 떠 있다가 한쪽이 전환/해제되면 onLost가 불리는데, 다른 네트워크가
 * 멀쩡해도 오프라인으로 떨어지고 onAvailable은 '새 네트워크 등장' 때만 불려 복구가 안 됐다.
 * → 인터넷이 정상인데도 에이전트가 "인터넷 연결이 필요합니다"로 막히던 원인.
 *
 * 수정: INTERNET 가능 네트워크 '집합'을 추적해 하나라도 살아있으면 온라인.
 * 콜백 시마다 OS 기본 네트워크(activeNetwork)도 함께 재확인(소스 오브 트루스).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // INTERNET capability를 가진 현재 가용 네트워크 집합 (콜백은 바인더 스레드 → thread-safe set)
    private val available = ConcurrentHashMap.newKeySet<Network>()
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        recompute()  // 초기 상태 = activeNetwork 기준 (콜백 등록 전 공백 메움)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.add(network)
                recompute()
            }

            override fun onLost(network: Network) {
                available.remove(network)
                recompute()  // 다른 네트워크가 남아있으면 온라인 유지
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) available.add(network)
                else available.remove(network)
                recompute()
            }
        })
    }

    /** 가용 INTERNET 네트워크가 하나라도 있거나, OS 기본 네트워크가 INTERNET이면 온라인. */
    private fun recompute() {
        val activeOk = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val online = activeOk || available.isNotEmpty()
        if (_isOnline.value != online) {
            Log.d(TAG, "online=$online (active=$activeOk, available=${available.size})")
            _isOnline.value = online
        }
    }
}
