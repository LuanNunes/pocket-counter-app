package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.IssuerCardStore
import com.resolveprogramming.pocketcounter.domain.notification.IssuerCardMatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [IssuerCardRepository]. Normalizes every key through [IssuerCardMatcher] on the
 * way in, so what is written matches what the matcher looks up.
 */
@Singleton
class LocalIssuerCardRepository @Inject constructor(
    private val store: IssuerCardStore,
) : IssuerCardRepository {

    override suspend fun getMap(): Map<String, String> = store.getMap()

    override suspend fun associate(issuerKey: String, cardId: String) {
        store.update { it + (IssuerCardMatcher.normalizeIssuer(issuerKey) to cardId) }
    }

    override suspend fun clear(issuerKey: String) {
        store.update { it - IssuerCardMatcher.normalizeIssuer(issuerKey) }
    }
}
