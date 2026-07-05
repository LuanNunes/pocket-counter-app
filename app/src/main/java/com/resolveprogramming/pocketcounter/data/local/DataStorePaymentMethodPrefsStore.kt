package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.paymentMethodPrefsDataStore by preferencesDataStore(name = "payment_method_prefs")

@Singleton
class DataStorePaymentMethodPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PaymentMethodPrefsStore {

    private val enabledKey = stringSetPreferencesKey("enabled_payment_methods")

    override val enabledRaw: Flow<Set<String>?> = context.paymentMethodPrefsDataStore.data
        .map { prefs -> prefs[enabledKey] }

    override suspend fun setEnabledRaw(names: Set<String>) {
        context.paymentMethodPrefsDataStore.edit { prefs -> prefs[enabledKey] = names }
    }
}
