package com.homecam.te.network

import com.homecam.te.model.EventItem
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Polls /api/events every 5 seconds for a single camera device.
 * Compares with last poll time and reports new events via callback.
 */
class EventPoller(
    private val apiClient: ApiClient,
    private val onNewEvents: (List<EventItem>) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {

    private var job: Job? = null
    private var lastEventTime: Long = 0L
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Start polling. Pass the initial timestamp to compare against. */
    fun start(initialLastTime: Long = 0L) {
        if (!running.compareAndSet(false, true)) return
        lastEventTime = initialLastTime

        job = scope.launch {
            while (isActive && running.get()) {
                try {
                    val result = apiClient.getEvents()
                    result.onSuccess { events ->
                        val newItems = events
                            .filter { it.timestamp > lastEventTime }
                            .map { ev ->
                                EventItem(
                                    type = ev.eventType,
                                    time = ev.timestamp,
                                    label = ev.label,
                                    displayText = formatEventText(ev.eventType, ev.label)
                                )
                            }
                        if (newItems.isNotEmpty()) {
                            lastEventTime = newItems.maxOf { it.time }
                            onNewEvents(newItems)
                        }
                    }
                    result.onFailure { onError(it) }
                } catch (e: Exception) {
                    if (isActive) onError(e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        running.set(false)
        job?.cancel()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5000L

        fun formatEventText(type: String, label: String = ""): String {
            return when (type) {
                "enter" -> "有${label}进入了"
                "leave" -> "有${label}离开了"
                "cry" -> "检测到婴儿哭声"
                "sleep" -> "宝宝睡着了"
                "wake_up" -> "宝宝睡醒了"
                else -> type
            }
        }
    }
}
