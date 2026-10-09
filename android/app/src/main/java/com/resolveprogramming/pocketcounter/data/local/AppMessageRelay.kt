package com.resolveprogramming.pocketcounter.data.local

import com.resolveprogramming.pocketcounter.data.session.SessionScopedStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped relay for feedback whose own screen is about to disappear: a toast raised by a
 * screen that is being popped leaves composition with its toast host before it ever renders, so the
 * message is sent here instead and the screen the user lands on shows it.
 */
@Singleton
class AppMessageRelay @Inject constructor() : SessionScopedStore {

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun send(message: String) {
        _messages.tryEmit(message)
    }

    /** A message names what the previous user did, so nothing held may cross a session boundary. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun clearForSession() = _messages.resetReplayCache()
}
