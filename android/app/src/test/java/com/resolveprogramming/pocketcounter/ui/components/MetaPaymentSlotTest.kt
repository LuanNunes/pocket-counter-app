package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric has no real font metrics — it measures any string as ~16px — so the drop-when-it-does
 * -not-fit behaviour cannot be asserted here and needs a device. What these cover is the structural
 * failure that shipped a crash: a `SubcomposeLayout` under `IntrinsicSize.Min`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetaPaymentSlotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `survives intrinsic measurement`() {
        compose.setContent {
            PocketTheme {
                Row(
                    modifier = Modifier.width(300.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MetaPaymentSlot(payment = "Crédito Nubank")
                }
            }
        }

        compose.onNodeWithText("· Crédito Nubank").assertIsDisplayed()
    }

    @Test
    fun `emits nothing for a blank payment`() {
        compose.setContent {
            PocketTheme {
                Row(modifier = Modifier.width(300.dp).height(IntrinsicSize.Min)) {
                    MetaPaymentSlot(payment = "")
                }
            }
        }

        compose.onNodeWithText("·", substring = true).assertDoesNotExist()
    }
}
