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
    private val onError: (Throwable) -> Unit = {}
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
                                val (cleanType, extra) = parseEventType(ev.eventType)
                                EventItem(
                                    type = cleanType,
                                    time = ev.timestamp,
                                    label = ev.label,
                                    displayText = formatEventText(cleanType, ev.label, extra)
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

        private fun parseEventType(rawType: String): Pair<String, String> {
            val parts = rawType.split(":", limit = 2)
            return parts.first() to parts.getOrElse(1) { "" }
        }

        fun formatEventText(type: String, label: String = "", extra: String = ""): String {
            return when (type) {
                "enter" -> "有${label}进入了"
                "leave" -> "有${label}离开了"
                "cry" -> "婴儿哭声"
                "sleep" -> "宝宝睡着了"
                "wake_up" -> "宝宝睡醒了"
                "fall" -> "检测到有人摔倒"
                "get_up" -> "有人站起来了"
                "phone" -> if (label.isNotEmpty()) "有人在玩手机（${label}）" else "有人在玩手机"
                else -> type
            }
        }
    }
}
