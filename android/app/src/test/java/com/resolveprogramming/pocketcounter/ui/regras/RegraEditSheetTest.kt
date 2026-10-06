package com.resolveprogramming.pocketcounter.ui.regras

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor is the only way to repair a mistagged rule, so what it refuses to save
 * and where it puts the 409 are the assertions that matter. Robolectric does not route taps into the
 * sheet's own window, so the CTA is driven through its semantics action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RegraEditSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctxFood = TagContext("ctx-food", "Alimentação", 0xFF_AA_00_00L)
    private val tagDelivery = Tag("tag-1", "Delivery", TransactionType.EXPENSE, idContext = "ctx-food")
    private val tagSalary = Tag("tag-9", "Salário", TransactionType.INCOME)

    private val suggestRule = ClassificationRule(
        id = "rule-1",
        pattern = "Ifood",
        idTag = "tag-1",
        active = true,
        appliedCount = 3,
        action = RuleAction.SUGGEST,
    )

    private var savedPattern: String? = null
    private var savedIdTag: String? = null
    private var savedActive: Boolean? = null
    private var clearedErrors = 0

    private fun render(
        rule: ClassificationRule = suggestRule,
        saving: Boolean = false,
        errorMessage: String? = null,
    ) {
        compose.setContent {
            PocketTheme {
                RegraEditSheet(
                    rule = rule,
                    tags = listOf(tagDelivery, tagSalary),
                    contexts = listOf(ctxFood),
                    saving = saving,
                    errorMessage = errorMessage,
                    onClearError = { clearedErrors++ },
                    onSave = { pattern, idTag, active ->
                        savedPattern = pattern
                        savedIdTag = idTag
                        savedActive = active
                    },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `the pattern field is seeded from the rule`() {
        render()

        compose.onNodeWithText("Editar regra").assertIsDisplayed()
        compose.onNodeWithText("Ifood").assertIsDisplayed()
    }

    @Test
    fun `saving reports the trimmed pattern, the tag and a non-null active`() {
        render(rule = suggestRule.copy(active = null))

        compose.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Ifood", savedPattern)
        assertEquals("tag-1", savedIdTag)
        assertEquals(true, savedActive)
    }

    @Test
    fun `a pattern with no letter or digit disables the CTA`() {
        render()

        compose.onNodeWithText("Ifood").performTextClearance()
        compose.onNodeWithText("parte do texto da notificação").performTextInput("---")

        compose.onNodeWithText("Salvar").assertIsNotEnabled()
    }

    @Test
    fun `the pattern field caps at the length the server accepts`() {
        render()

        compose.onNodeWithText("Ifood").performTextClearance()
        compose.onNodeWithText("parte do texto da notificação")
            .performTextInput("x".repeat(ClassificationRule.MAX_PATTERN_LENGTH + 100))
        compose.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(ClassificationRule.MAX_PATTERN_LENGTH, savedPattern?.length)
    }

    @Test
    fun `a saving sheet refuses a second tap`() {
        render(saving = true)

        compose.onNodeWithText("Salvar").assertIsNotEnabled()
    }

    @Test
    fun `the error note renders above the tag picker`() {
        render(errorMessage = DUPLICATE_PATTERN_ERROR)

        val note = compose.onNodeWithText(DUPLICATE_PATTERN_ERROR).getUnclippedBoundsInRoot()
        val tagLabel = compose.onNodeWithText("TAG").getUnclippedBoundsInRoot()

        compose.onNodeWithText(DUPLICATE_PATTERN_ERROR).assertIsDisplayed()
        assertTrue("note top ${note.top} should precede the tag picker at ${tagLabel.top}", note.top < tagLabel.top)
    }

    @Test
    fun `typing in the pattern field clears the error`() {
        render(errorMessage = DUPLICATE_PATTERN_ERROR)

        compose.onNodeWithText("Ifood").performTextInput("!")

        assertEquals(1, clearedErrors)
    }

    @Test
    fun `an IGNORE rule hides the tag picker and saves no tag`() {
        render(rule = suggestRule.copy(idTag = null, action = RuleAction.IGNORE))

        compose.onNodeWithText("TAG").assertDoesNotExist()
        compose.onNodeWithText("Salvar").assertIsEnabled()
        compose.onNodeWithText("Salvar").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(null, savedIdTag)
    }
}
