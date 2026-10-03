package com.resolveprogramming.pocketcounter.data.repository

/**
 * Local-only store that maps a credit-card id to its last-4 digits.
 *
 * The map is populated when the user assigns a "final NNNN" hint to a card
 * (either from the wizard's unknown-card prompt or from the add-card flow).
 * The backend never sees this data — it is purely a device-local convenience.
 *
 * Implementation (DataStore): provided by a later module binding. This interface
 * lives in the data layer so ViewModels can inject it through DI.
 */
interface CardLast4Repository {
    /** Returns the full cardId → last4 map. */
    suspend fun getMap(): Map<String, String>

    /** Associates [cardId] with [last4], replacing any prior value for that card. */
    suspend fun associate(cardId: String, last4: String)

    /** Removes the last-4 entry for [cardId] if one exists. */
    suspend fun clear(cardId: String)
}
