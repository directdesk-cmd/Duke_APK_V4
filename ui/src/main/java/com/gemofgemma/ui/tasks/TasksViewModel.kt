package com.gemofgemma.ui.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemofgemma.core.postbox.PostboxClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Duke addition (change 3): the Queue Manager console's phone face.
 * Reads the queue through the postbox, and every management action -
 * drag re-sort, hold, release - is carried back over the postbox as a
 * Queue Manager action file, receipted by the Queue Manager into
 * Kevin's record with the phone named as the actor.
 */
data class QmJob(
    val id: String,
    val title: String,
    val kind: String,
    val state: String,
    val priority: Int
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val client = PostboxClient(context)

    private val _next = MutableStateFlow<List<QmJob>>(emptyList())
    val next: StateFlow<List<QmJob>> = _next.asStateFlow()

    private val _running = MutableStateFlow<List<QmJob>>(emptyList())
    val running: StateFlow<List<QmJob>> = _running.asStateFlow()

    private val _done = MutableStateFlow<List<QmJob>>(emptyList())
    val done: StateFlow<List<QmJob>> = _done.asStateFlow()

    private val _status = MutableStateFlow("Not loaded yet - tap Refresh.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun refresh() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = "Asking Puppy for the queue..."
            runCatching { client.readQueueStateJson() }
                .onSuccess { parse(it) }
                .onFailure {
                    _status.value = "Could not read the queue: ${it.message}"
                }
            _busy.value = false
        }
    }

    private fun parse(stateJson: String) {
        val root = JSONObject(stateJson)
        val jobs: JSONArray = root.optJSONArray("queue") ?: JSONArray()
        val nextList = mutableListOf<QmJob>()
        val runningList = mutableListOf<QmJob>()
        val doneList = mutableListOf<QmJob>()
        for (i in 0 until jobs.length()) {
            val item = jobs.optJSONObject(i) ?: continue
            val job = QmJob(
                id = item.optString("id"),
                title = item.optString("title"),
                kind = item.optString("kind"),
                state = item.optString("state"),
                priority = item.optInt("priority", 100)
            )
            when (job.state) {
                "running" -> runningList.add(job)
                "complete", "cut" -> doneList.add(job)
                else -> nextList.add(job) // ready, held
            }
        }
        _next.value = nextList
        _running.value = runningList
        _done.value = doneList
        _status.value = "Queue read from Puppy: ${nextList.size} next, " +
            "${runningList.size} being processed, ${doneList.size} completed."
    }

    fun hold(job: QmJob) = act(
        JSONObject().put("action", "hold").put("id", job.id).put("actor", "wolf_app"),
        "Holding ${job.id}..."
    )

    fun release(job: QmJob) = act(
        JSONObject().put("action", "release").put("id", job.id).put("actor", "wolf_app"),
        "Releasing ${job.id}..."
    )

    fun move(job: QmJob, position: Int) = act(
        JSONObject().put("action", "reprioritize").put("id", job.id)
            .put("position", position).put("actor", "wolf_app"),
        "Re-sorting ${job.id} to position ${position + 1}..."
    )

    private fun act(action: JSONObject, label: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = label
            runCatching { client.sendQueueAction(action) }
                .onSuccess {
                    _status.value = "Sent - the Queue Manager receipts it into Kevin's record."
                    _busy.value = false
                    refresh()
                }
                .onFailure {
                    _status.value = "Action failed: ${it.message}"
                    _busy.value = false
                }
        }
    }
}
