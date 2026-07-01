package com.resolveprogramming.pocketcounter.data.local

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped "the ledger changed" signal shared by the month-scoped screens that render the same
 * transactions from different ViewModels (Home and Transações). Home and Transações each keep their own
 * copy of the month's rows and only agree on the [ViewedMonthStore]; without a change signal, marking a
 * row paid on Transações leaves Home's KPIs (Pendente, saldo) stale until a cold start.
 *
 * Any ViewModel emits [signal] right after a successful ledger mutation (mark paid/pending, save, edit,
 * delete); every collector — including the one on the ViewModel that emitted — reloads the current month,
 * so the emitter needs no separate reload of its own.
 *
 * Replay is 0 so a screen never sees a stale event on construction. [BufferOverflow.DROP_OLDEST] keeps
 * [signal] non-suspending and, since every event is an identical "reload" tick, guarantees the latest
 * reload intent survives — back-to-back mutations coalesce into a reload rather than dropping one.
 */
@Singleton
class LedgerRefreshSignal @Inject constructor() {

    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun signal() {
        _events.tryEmit(Unit)
    }
}
