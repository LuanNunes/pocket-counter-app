package com.resolveprogramming.pocketcounter.di

import com.resolveprogramming.pocketcounter.data.local.DataStorePaymentMethodDictionaryStore
import com.resolveprogramming.pocketcounter.data.local.DataStorePaymentMethodPrefsStore
import com.resolveprogramming.pocketcounter.data.local.PaymentMethodDictionaryStore
import com.resolveprogramming.pocketcounter.data.local.PaymentMethodPrefsStore
import com.resolveprogramming.pocketcounter.data.remote.CredentialManagerGoogleSignIn
import com.resolveprogramming.pocketcounter.data.remote.GoogleSignInClient
import com.resolveprogramming.pocketcounter.data.repository.AnalyticsRepository
import com.resolveprogramming.pocketcounter.data.repository.AssistantRepository
import com.resolveprogramming.pocketcounter.data.repository.CardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.LocalCardLast4Repository
import com.resolveprogramming.pocketcounter.data.repository.LocalPaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.LocalPaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodDictionaryRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitAnalyticsRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitCardRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitAssistantRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitNotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitTagRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitSeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitTransactionRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.platform.biometric.AndroidBiometricAuthenticator
import com.resolveprogramming.pocketcounter.platform.biometric.BiometricAuthenticator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds repository interfaces to their Retrofit-backed implementations. All data now
 * comes from the pocket-counter backend (API_BASE_URL); start it before exercising the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindNotificationRepository(
        impl: RetrofitNotificationRepository,
    ): NotificationRepository

    @Binds
    abstract fun bindTagRepository(
        impl: RetrofitTagRepository,
    ): TagRepository

    @Binds
    abstract fun bindTransactionRepository(
        impl: RetrofitTransactionRepository,
    ): TransactionRepository

    @Binds
    abstract fun bindCardRepository(
        impl: RetrofitCardRepository,
    ): CardRepository

    @Binds
    abstract fun bindAnalyticsRepository(
        impl: RetrofitAnalyticsRepository,
    ): AnalyticsRepository

    @Binds
    abstract fun bindClassificationRuleRepository(
        impl: RetrofitClassificationRuleRepository,
    ): ClassificationRuleRepository

    @Binds
    abstract fun bindSeriesRepository(
        impl: RetrofitSeriesRepository,
    ): SeriesRepository

    @Binds
    abstract fun bindAssistantRepository(
        impl: RetrofitAssistantRepository,
    ): AssistantRepository

    @Binds
    abstract fun bindGoogleSignInClient(
        impl: CredentialManagerGoogleSignIn,
    ): GoogleSignInClient

    @Binds
    abstract fun bindBiometricAuthenticator(
        impl: AndroidBiometricAuthenticator,
    ): BiometricAuthenticator

    @Binds
    abstract fun bindCardLast4Repository(
        impl: LocalCardLast4Repository,
    ): CardLast4Repository

    @Binds
    abstract fun bindPaymentMethodPrefsStore(
        impl: DataStorePaymentMethodPrefsStore,
    ): PaymentMethodPrefsStore

    @Binds
    abstract fun bindPaymentMethodPrefsRepository(
        impl: LocalPaymentMethodPrefsRepository,
    ): PaymentMethodPrefsRepository

    @Binds
    abstract fun bindPaymentMethodDictionaryStore(
        impl: DataStorePaymentMethodDictionaryStore,
    ): PaymentMethodDictionaryStore

    @Binds
    abstract fun bindPaymentMethodDictionaryRepository(
        impl: LocalPaymentMethodDictionaryRepository,
    ): PaymentMethodDictionaryRepository
}
