package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMappersRuleTest {

    @Test
    fun toDto_suggest_sendsPatternActiveAndTag() {
        val dto = ClassificationRule.suggest("Padaria", "tag-1").toDto()

        assertEquals("Padaria", dto.pattern)
        assertEquals("tag-1", dto.idTag)
        assertEquals(true, dto.active)
    }

    @Test
    fun toDto_neverSendsIdCategory() {
        val body = Json.encodeToString(ClassificationRule.suggest("Padaria", "tag-1").toDto())

        assertFalse(body.contains("idCategory"))
    }

    @Test
    fun toDto_ignore_omitsTagAndMarksAction() {
        val dto = ClassificationRule.ignore("Spam").toDto()

        assertNull(dto.idTag)
        assertEquals("IGNORE", dto.action)
    }

    @Test
    fun toDto_ignoreWithStrayTag_doesNotSendIt() {
        val dto = ClassificationRule.ignore("Spam").copy(idTag = "t").toDto()

        assertNull(dto.idTag)
    }

    @Test
    fun toDto_suggest_leavesActionImplicit() {
        assertNull(ClassificationRule.suggest("Padaria", "t").toDto().action)
    }

    @Test
    fun toDomain_mapsFlatFields() {
        val rule = ClassificationRuleDto(
            id = "r1", pattern = "Padaria", idTag = "t1", idCategory = "c1",
            active = false, appliedCount = 4, action = "SUGGEST",
        ).toDomain()

        assertEquals("r1", rule.id)
        assertEquals("Padaria", rule.pattern)
        assertEquals("t1", rule.idTag)
        assertEquals(false, rule.active)
        assertEquals(4, rule.appliedCount)
        assertEquals(RuleAction.SUGGEST, rule.action)
    }

    @Test
    fun toDomain_ignoreAction_isCaseInsensitive() {
        assertEquals(RuleAction.IGNORE, ClassificationRuleDto(pattern = "x", action = "ignore").toDomain().action)
    }

    @Test
    fun dto_readsTheNewWireShape() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<ClassificationRuleDto>(
            """{"id":"r","pattern":"p","idTag":"t","idCategory":"c","active":true,"action":"SUGGEST",
               "appliedCount":2}""",
        )

        assertEquals("t", dto.idTag)
        assertEquals("c", dto.idCategory)
    }
}
