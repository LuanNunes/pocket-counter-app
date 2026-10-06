package com.resolveprogramming.pocketcounter.ui.regras

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The badge and the outcome line are the row's only signal that a rule is not doing anything,
 * so they are asserted on rendered text rather than on state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RegraCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctxFood = TagContext("ctx-food", "Alimentação", 0xFF_AA_00_00L)
    private val tagDelivery = Tag("tag-1", "Delivery", TransactionType.EXPENSE, idContext = "ctx-food")

    private val baseRule = ClassificationRule(
        id = "rule-1",
        pattern = "Ifood",
        idTag = "tag-1",
        active = true,
        appliedCount = 3,
        action = RuleAction.SUGGEST,
    )

    private fun render(
        rule: ClassificationRule = baseRule,
        tags: List<Tag> = listOf(tagDelivery),
    ) {
        compose.setContent {
            PocketTheme {
                RegraCard(
                    rule = rule,
                    tagsById = tags.associateBy { it.id },
                    contextsById = mapOf(ctxFood.id to ctxFood),
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
    }

    @Test
    fun `an applied rule shows its pattern, tag and count`() {
        render()

        compose.onNodeWithText("Ifood").assertIsDisplayed()
        compose.onNodeWithText("Delivery").assertIsDisplayed()
        compose.onNodeWithText("aplicada 3×").assertIsDisplayed()
    }

    @Test
    fun `an inactive rule gets the inativa badge`() {
        render(rule = baseRule.copy(active = false))

        compose.onNodeWithText("INATIVA").assertIsDisplayed()
    }

    @Test
    fun `a tag id absent from the catalog renders the removed-tag fallback`() {
        render(tags = emptyList())

        compose.onNodeWithText("tag removida").assertIsDisplayed()
    }

    @Test
    fun `an IGNORE rule keeps its outcome label`() {
        render(rule = baseRule.copy(idTag = null, action = RuleAction.IGNORE, appliedCount = 0))

        compose.onNodeWithText("→ ignorar").assertIsDisplayed()
    }
}
