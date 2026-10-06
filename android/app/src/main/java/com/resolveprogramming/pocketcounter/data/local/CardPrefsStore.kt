package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cardPrefsDataStore by preferencesDataStore(name = "card_prefs")

/** Persists Cartões UI preferences — currently just whether the fatura card tile is collapsed. */
@Singleton
class CardPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tileCollapsedKey = booleanPreferencesKey("fatura_tile_collapsed")

    val tileCollapsed: Flow<Boolean> =
        context.cardPrefsDataStore.data.map { it[tileCollapsedKey] ?: false }

    suspend fun setTileCollapsed(value: Boolean) {
        context.cardPrefsDataStore.edit { it[tileCollapsedKey] = value }
    }
}
