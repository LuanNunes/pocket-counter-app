package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDto
import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.errorMessage
import com.resolveprogramming.pocketcounter.data.remote.isConflict
import com.resolveprogramming.pocketcounter.data.remote.isUnprocessable
import com.resolveprogramming.pocketcounter.data.remote.withoutHttpException
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitClassificationRuleRepository @Inject constructor(
    private val api: ClassificationRuleApi,
    private val json: Json,
) : ClassificationRuleRepository {

    override suspend fun getAll(): Result<List<ClassificationRule>> = translated {
        api.getAll().map { it.toDomain() }
    }

    override suspend fun create(rule: ClassificationRule): Result<RuleWriteOutcome> = write(rule) {
        api.create(rule.toDto())
    }

    override suspend fun update(rule: ClassificationRule): Result<RuleWriteOutcome> = write(rule) {
        val id = rule.id ?: error("Cannot update a rule without an id")
        api.update(id, rule.toDto())
    }

    override suspend fun delete(id: String): Result<Unit> = translated {
        api.delete(id)
    }

    private suspend fun write(rule: ClassificationRule, call: suspend () -> Any): Result<RuleWriteOutcome> {
        // Tag kind is unknown here; callers that know it check it themselves.
        rule.writeBlocker(null)?.let { return Result.failure(IllegalArgumentException("Rule refused: $it")) }
        return try {
            call()
            Result.success(RuleWriteOutcome.Saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e.isConflict()) return Result.success(RuleWriteOutcome.Duplicate)
            if (e.isUnprocessable()) return Result.success(RuleWriteOutcome.Rejected(e.errorMessage(json)))
            Result.failure(e.withoutHttpException())
        }
    }

    /** [runCatching] plus the HTTP translation, so no `HttpException` rides out in a failure. */
    private suspend fun <T> translated(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.withoutHttpException())
    }
}
