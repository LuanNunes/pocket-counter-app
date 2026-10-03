package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.CardLast4Store
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [CardLast4Repository]. Persists the `cardId → last4` map across process
 * restarts via [CardLast4Store]. The map never leaves the device.
 */
@Singleton
class LocalCardLast4Repository @Inject constructor(
    private val store: CardLast4Store,
) : CardLast4Repository {

    override suspend fun getMap(): Map<String, String> = store.getMap()

    override suspend fun associate(cardId: String, last4: String) {
        store.update { it + (cardId to last4) }
    }

    override suspend fun clear(cardId: String) {
        store.update { it - cardId }
    }
}
