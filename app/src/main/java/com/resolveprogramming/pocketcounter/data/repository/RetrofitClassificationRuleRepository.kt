package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDto
import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitClassificationRuleRepository @Inject constructor(
    private val api: ClassificationRuleApi,
) : ClassificationRuleRepository {

    override suspend fun getAll(): Result<List<ClassificationRule>> = runCatching {
        api.getAll().map { it.toDomain() }
    }

    override suspend fun create(rule: ClassificationRule): Result<Unit> = runCatching {
        api.create(rule.toDto())
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        api.delete(id)
    }
}
