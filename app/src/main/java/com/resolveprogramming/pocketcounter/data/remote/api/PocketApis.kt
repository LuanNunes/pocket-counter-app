package com.resolveprogramming.pocketcounter.data.remote.api

import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.AssistantAskResponseDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifiedRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyResponseDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CarryForwardRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.CarryForwardResultDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CategorizeRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.CategoryDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CategoryReorderDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CreateRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.CreditCardDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationDto
import com.resolveprogramming.pocketcounter.data.remote.dto.NotificationRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RecurringSeriesDto
import com.resolveprogramming.pocketcounter.data.remote.dto.RenameRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionItemDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionReorderRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

interface CreditCardApi {
    @GET("api/v1/credit-cards")
    suspend fun getCards(): List<CreditCardDto>

    @POST("api/v1/credit-cards")
    suspend fun create(@Body dto: CreditCardDto): String

    @PUT("api/v1/credit-cards/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: CreditCardDto): String

    @DELETE("api/v1/credit-cards/{id}")
    suspend fun delete(@Path("id") id: String)
}

interface InvoiceItemApi {
    @GET("api/v1/transactions/{invoiceId}/items")
    suspend fun getItems(@Path("invoiceId") invoiceId: String): List<TransactionItemDto>

    @POST("api/v1/transactions/{invoiceId}/items")
    suspend fun addItem(
        @Path("invoiceId") invoiceId: String,
        @Body dto: TransactionItemDto,
    ): String

    @PUT("api/v1/transactions/{invoiceId}/items/{itemId}")
    suspend fun updateItem(
        @Path("invoiceId") invoiceId: String,
        @Path("itemId") itemId: String,
        @Body dto: TransactionItemDto,
    ): String

    @DELETE("api/v1/transactions/{invoiceId}/items/{itemId}")
    suspend fun deleteItem(
        @Path("invoiceId") invoiceId: String,
        @Path("itemId") itemId: String,
    )
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

interface TagApi {
    @GET("api/v1/tags")
    suspend fun getTags(): List<TagDto>

    @GET("api/v1/tags/category/{idCategory}")
    suspend fun getTagsByCategory(@Path("idCategory") idCategory: String): List<TagDto>

    @POST("api/v1/tags")
    suspend fun addTag(@Body dto: TagDto): String

    @PUT("api/v1/tags/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: TagDto): String

    @DELETE("api/v1/tags/{id}")
    suspend fun delete(@Path("id") id: String)
}

interface CategoryApi {
    @GET("api/v1/categories")
    suspend fun getCategories(): List<CategoryDto>

    @POST("api/v1/categories")
    suspend fun addCategory(@Body dto: CategoryDto): String

    @PUT("api/v1/categories/{id}")
    suspend fun update(@Path("id") id: String, @Body dto: CategoryDto): String

    @DELETE("api/v1/categories/{id}")
    suspend fun delete(@Path("id") id: String)

    @PUT("api/v1/categories/reorder")
    suspend fun reorder(@Body body: CategoryReorderDto)
}

interface AssistantApi {
    @POST("api/v1/assistant/ask")
    suspend fun ask(@Body body: AssistantAskRequestDto): AssistantAskResponseDto
}

interface SeriesApi {
    @GET("api/v1/recurring-series")
    suspend fun getAll(): List<RecurringSeriesDto>

    @POST("api/v1/recurring-series")
    suspend fun create(@Body body: CreateRecurringSeriesRequest): RecurringSeriesDto

    @PATCH("api/v1/recurring-series/{id}")
    suspend fun rename(@Path("id") id: String, @Body body: RenameRecurringSeriesRequest): RecurringSeriesDto

    @DELETE("api/v1/recurring-series/{id}")
    suspend fun delete(@Path("id") id: String)

    @PUT("api/v1/recurring-series/{id}/tags")
    suspend fun setTags(@Path("id") id: String, @Body body: CategorizeRecurringSeriesRequest)

    @POST("api/v1/recurring-series/{seriesId}/transactions/{transactionId}")
    suspend fun linkTransaction(
        @Path("seriesId") seriesId: String,
        @Path("transactionId") transactionId: String,
        @Query("includePrevious") includePrevious: Boolean,
    )

    @DELETE("api/v1/recurring-series/{seriesId}/transactions/{transactionId}")
    suspend fun unlinkTransaction(
        @Path("seriesId") seriesId: String,
        @Path("transactionId") transactionId: String,
    )

    @POST("api/v1/months/{target}/carry-forward")
    suspend fun carryForward(
        @Path("target") targetRefYearMonth: Int,
        @Body body: CarryForwardRequest,
    ): CarryForwardResultDto
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
