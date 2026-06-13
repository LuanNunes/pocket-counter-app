package com.resolveprogramming.pocketcounter.data.remote.api

import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskResponseDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifiedRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyResponseDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ContextDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ContextReorderDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.PaymentSourceDto
import com.resolveprogramming.pocketcounter.data.remote.dto.SourceDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionReorderRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface TransactionApi {
    @GET("api/v1/transactions/incomes/{refYearMonth}")
    suspend fun getIncomes(@Path("refYearMonth") refYearMonth: Int): List<TransactionDto>

    @GET("api/v1/transactions/expenses/{refYearMonth}")
    suspend fun getExpenses(@Path("refYearMonth") refYearMonth: Int): List<TransactionDto>

    /** Mixed incomes + expenses across an inclusive refYearMonth range (e.g. 202601..202612). */
    @GET("api/v1/transactions/range/{fromRef}/{toRef}")
    suspend fun getRange(@Path("fromRef") fromRef: Int, @Path("toRef") toRef: Int): List<TransactionDto>

    @POST("api/v1/transactions/incomes")
    suspend fun addIncome(@Body dto: TransactionDto): String

    @POST("api/v1/transactions/expenses")
    suspend fun addExpense(@Body dto: TransactionDto): String

    @PUT("api/v1/transactions/{id}/paid")
    suspend fun markPaid(@Path("id") id: String)

    @PUT("api/v1/transactions/{id}/pending")
    suspend fun markPending(@Path("id") id: String)

    @PUT("api/v1/transactions/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: TransactionDto): String

    @DELETE("api/v1/transactions/{id}")
    suspend fun delete(@Path("id") id: String)

    @PUT("api/v1/transactions/reorder")
    suspend fun reorder(@Body body: TransactionReorderRequest)
}

interface ClassificationRuleApi {
    @POST("api/v1/classification-rules")
    suspend fun create(@Body dto: ClassificationRuleDto): String

    @GET("api/v1/classification-rules")
    suspend fun getAll(): List<ClassificationRuleDto>

    @PUT("api/v1/classification-rules/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: ClassificationRuleDto): String

    @DELETE("api/v1/classification-rules/{id}")
    suspend fun delete(@Path("id") id: String)
}

interface SourceApi {
    @GET("api/v1/sources")
    suspend fun getSources(): List<SourceDto>

    @POST("api/v1/sources")
    suspend fun addSource(@Body dto: SourceDto): String

    @PUT("api/v1/sources/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: SourceDto): String

    @DELETE("api/v1/sources/{id}")
    suspend fun delete(@Path("id") id: String)
}

interface PaymentSourceApi {
    @GET("api/v1/payment-sources")
    suspend fun getPaymentSources(): List<PaymentSourceDto>

    @POST("api/v1/payment-sources")
    suspend fun create(@Body dto: PaymentSourceDto): String

    @PUT("api/v1/payment-sources/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: PaymentSourceDto): String

    @DELETE("api/v1/payment-sources/{id}")
    suspend fun delete(@Path("id") id: String)
}

interface TagApi {
    @GET("api/v1/tags")
    suspend fun getTags(): List<TagDto>

    @GET("api/v1/tags/context/{idContext}")
    suspend fun getTagsByContext(@Path("idContext") idContext: String): List<TagDto>

    @POST("api/v1/tags")
    suspend fun addTag(@Body dto: TagDto): String

    @PUT("api/v1/tags/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: TagDto): String

    @DELETE("api/v1/tags/{id}")
    suspend fun delete(@Path("id") id: String)
}

interface ContextApi {
    @GET("api/v1/contexts")
    suspend fun getContexts(): List<ContextDto>

    @POST("api/v1/contexts")
    suspend fun addContext(@Body dto: ContextDto): String

    @PUT("api/v1/contexts/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: ContextDto): String

    @DELETE("api/v1/contexts/{id}")
    suspend fun delete(@Path("id") id: String)

    @PUT("api/v1/contexts/reorder")
    suspend fun reorder(@Body body: ContextReorderDto)
}

interface AssistantApi {
    @POST("api/v1/assistant/ask")
    suspend fun ask(@Body body: AssistantAskRequestDto): AssistantAskResponseDto
}

interface NotificationApi {
    @GET("api/v1/notifications")
    suspend fun getNotifications(): List<NotificationDto>

    @POST("api/v1/notifications")
    suspend fun add(@Body body: NotificationRequestDto): NotificationDto

    @GET("api/v1/notifications/pending")
    suspend fun getPending(): List<NotificationDto>

    @POST("api/v1/notifications/{id}/ignore")
    suspend fun ignore(@Path("id") id: String)

    @POST("api/v1/notifications/classify")
    suspend fun classify(@Body body: ClassifyRequestDto): ClassifyResponseDto

    @POST("api/v1/notifications/{id}/classified")
    suspend fun markClassified(@Path("id") id: String, @Body body: ClassifiedRequestDto)
}
