package com.resolveprogramming.pocketcounter.data.session

/**
 * In-memory state that belongs to the signed-in user. Implementors are bound `@IntoSet` in
 * `DataModule`, and `AuthRepository` clears the whole set on login, logout and account deletion —
 * registering is the default so a new cache cannot leak the previous account's data.
 */
interface SessionScopedStore {

    fun clearForSession()
}
