package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toHistoryItem
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Date precedence when mapping a persisted transaction to a ledger row.
 *
 * The backend gained `datePurchase`, but [RemoteMappers.toHistoryItem] deliberately keeps reading
 * `dateDue`. `HistoryItem.date` is not display-only — `setTags` derives the month partition from it
 * to locate the row, and the edit sheet seeds the form from it, which is written back as `dateDue`.
 * With a single date on `HistoryItem`, preferring `datePurchase` would send a cross-month row's tag
 * edit to the wrong month and rewrite its due date.
 */
class RemoteMappersDateTest {

    private fun expense(
        dateDue: String? = null,
        datePurchase: String? = null,
        datePaid: String? = null,
    ) = TransactionDto(
        id = "tx-1",
        transactionType = "EXPENSE",
        amount = BigDecimal("10.00"),
        dateDue = dateDue,
        datePurchase = datePurchase,
        datePaid = datePaid,
    )

    @Test
    fun `dateDue wins over datePurchase so the row stays in its own month partition`() {
        val item = expense(dateDue = "2026-07-10", datePurchase = "2026-06-23").toHistoryItem()

        assertEquals(LocalDate.of(2026, 7, 10), item.date)
    }

    @Test
    fun `datePurchase alone does not become the row date`() {
        // A row with only datePurchase set falls through to datePaid/today rather than adopting it —
        // adopting it is what would desynchronise the row from its refYearMonth partition.
        val item = expense(datePurchase = "2026-06-23", datePaid = "2026-07-02").toHistoryItem()

        assertEquals(LocalDate.of(2026, 7, 2), item.date)
    }

    @Test
    fun `datePaid is the last resort before today`() {
        val item = expense(datePaid = "2026-07-02").toHistoryItem()

        assertEquals(LocalDate.of(2026, 7, 2), item.date)
    }

    @Test
    fun `a paid row still groups by its due date, not the day it was paid`() {
        // Marking a row paid stamps datePaid with ~today; that must never move the row's day group.
        val item = expense(dateDue = "2026-06-23", datePaid = "2026-07-30").toHistoryItem()

        assertEquals(LocalDate.of(2026, 6, 23), item.date)
    }
}
