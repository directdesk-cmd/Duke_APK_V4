package com.gemofgemma.ui.postbox

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemofgemma.core.postbox.PostboxClient
import com.gemofgemma.core.postbox.PostboxMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Duke addition (change 2): the seat messaging face of the postbox
 * client. Messages are addressed to a seat's own mailbox - Duke and
 * Kevin are destinations; the mailbox itself does the routing.
 */
@HiltViewModel
class PostboxViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val client = PostboxClient(context)

    private val _inbox = MutableStateFlow<List<PostboxMessage>>(emptyList())
    val inbox: StateFlow<List<PostboxMessage>> = _inbox.asStateFlow()

    private val _status = MutableStateFlow("The mailbox secret is set in Settings.")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun send(seat: String, text: String) {
        if (_busy.value || text.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = "Sending to $seat..."
            runCatching { client.sendSeatMessage(seat, text) }
                .onSuccess { _status.value = "Sent to $seat through the postbox." }
                .onFailure { _status.value = "Send failed: ${it.message}" }
            _busy.value = false
        }
    }

    fun refreshInbox() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _status.value = "Pulling this phone's mailbox..."
            runCatching { client.pullInbox() }
                .onSuccess {
                    _inbox.value = it
                    _status.value = "Mailbox holds ${it.size} message(s)."
                }
                .onFailure { _status.value = "Pull failed: ${it.message}" }
            _busy.value = false
        }
    }

    fun clearInbox() {
        if (_busy.value || _inbox.value.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { client.ack(_inbox.value.map { it.id }) }
                .onSuccess {
                    _inbox.value = emptyList()
                    _status.value = "Mailbox cleared (acknowledged)."
                }
                .onFailure { _status.value = "Clear failed: ${it.message}" }
            _busy.value = false
        }
    }
}
