package com.resolveprogramming.pocketcounter.di

import com.resolveprogramming.pocketcounter.data.repository.AnalyticsRepository
import com.resolveprogramming.pocketcounter.data.repository.AssistantRepository
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.ClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitAnalyticsRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitCardRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitAssistantRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitClassificationRuleRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitNotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitPaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitTagRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitSeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.RetrofitTransactionRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
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
    abstract fun bindPaymentSourceRepository(
        impl: RetrofitPaymentSourceRepository,
    ): PaymentSourceRepository

    @Binds
    abstract fun bindSourceRepository(
        impl: RetrofitSourceRepository,
    ): SourceRepository

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
}
