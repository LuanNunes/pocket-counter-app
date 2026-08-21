package com.resolveprogramming.pocketcounter.data.repository

/**
 * How many confirmed transactions each notification source has produced on this device. Read by the
 * "Ignorar" dialog so blocking a source that already pays off names what is being given up.
 */
interface ProductiveSourceRepository {
    suspend fun countFor(app: String): Int
    suspend fun record(app: String)
}
