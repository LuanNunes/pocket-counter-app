package com.resolveprogramming.pocketcounter.di

import com.resolveprogramming.pocketcounter.BuildConfig
import com.resolveprogramming.pocketcounter.data.remote.AuthInterceptor
import com.resolveprogramming.pocketcounter.data.remote.TokenAuthenticator
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.api.CategoryApi
import com.resolveprogramming.pocketcounter.data.remote.api.CreditCardApi
import com.resolveprogramming.pocketcounter.data.remote.api.InvoiceItemApi
import com.resolveprogramming.pocketcounter.data.remote.api.NotificationApi
import com.resolveprogramming.pocketcounter.data.remote.api.SeriesApi
import com.resolveprogramming.pocketcounter.data.remote.api.TagApi
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY.takeIf { BuildConfig.DEBUG }
                    ?: HttpLoggingInterceptor.Level.NONE
            },
        )
        .authenticator(tokenAuthenticator)
        // TokenAuthenticator refreshes on THIS client. When a burst of requests all 401 at once
        // (expired token), each holds a per-host slot while blocking on /auth/refresh — which then
        // can't get a slot and deadlocks, hanging the app on the loading spinner. The default cap is
        // 5; the Home cold-load alone fans out ~6 concurrent calls, so give the refresh ample headroom.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideTransactionApi(retrofit: Retrofit): TransactionApi =
        retrofit.create(TransactionApi::class.java)

    @Provides
    @Singleton
    fun provideTagApi(retrofit: Retrofit): TagApi =
        retrofit.create(TagApi::class.java)

    @Provides
    @Singleton
    fun provideCategoryApi(retrofit: Retrofit): CategoryApi =
        retrofit.create(CategoryApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi =
        retrofit.create(NotificationApi::class.java)

    @Provides
    @Singleton
    fun provideCreditCardApi(retrofit: Retrofit): CreditCardApi =
        retrofit.create(CreditCardApi::class.java)

    @Provides
    @Singleton
    fun provideClassificationRuleApi(retrofit: Retrofit): ClassificationRuleApi =
        retrofit.create(ClassificationRuleApi::class.java)

    @Provides
    @Singleton
    fun provideInvoiceItemApi(retrofit: Retrofit): InvoiceItemApi =
        retrofit.create(InvoiceItemApi::class.java)

    @Provides
    @Singleton
    fun provideSeriesApi(retrofit: Retrofit): SeriesApi =
        retrofit.create(SeriesApi::class.java)

    @Provides
    @Singleton
    fun provideAssistantApi(retrofit: Retrofit): com.resolveprogramming.pocketcounter.data.remote.api.AssistantApi =
        retrofit.create(com.resolveprogramming.pocketcounter.data.remote.api.AssistantApi::class.java)
}
