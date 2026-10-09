import Foundation

/// One line of the review: what it says, and what it offers when opened.
struct QuickAddReviewRow: Identifiable, Equatable {
    enum Field: String, Identifiable {
        case date, paymentMethod, tag
        var id: String { rawValue }
    }

    let field: Field
    let label: String
    let value: String
    /// `nil` when the field has no value yet; the row then reads "definir" and dims its value.
    let provenance: FieldProvenance?
    let isWeak: Bool
    let unavailable: String?

    var id: String { field.id }
    var action: String { isWeak ? "definir" : "alterar" }
}

/// One chip: what it says, whether it is on, and what it changes.
struct QuickAddChip: Identifiable, Equatable {
    enum Change: Equatable {
        case date(CalendarDay)
        case paymentMethod(PaymentMethod?)
        case card(CardCandidate)
        case tag(TagID?)
    }

    let id: String
    let label: String
    let isOn: Bool
    /// A tag's colour dot; `nil` on every other row.
    let color: UInt32?
    let change: Change
}

enum QuickAddReviewRows {
    private static let methods: [(method: PaymentMethod, label: String)] = [
        (.pix, "Pix"), (.debit, "Débito"), (.cash, "Dinheiro"),
    ]

    static func rows(from draft: ReadingDraft, lookups: LookupSet, today: CalendarDay) -> [QuickAddReviewRow] {
        [
            dateRow(draft, today: today),
            paymentMethodRow(draft, lookups: lookups),
            tagRow(draft, lookups: lookups),
        ]
    }

    static func chips(
        for field: QuickAddReviewRow.Field, from draft: ReadingDraft, lookups: LookupSet, today: CalendarDay
    ) -> [QuickAddChip] {
        switch field {
        case .date: dateChips(draft, today: today)
        case .paymentMethod: paymentMethodChips(draft, lookups: lookups)
        case .tag: tagChips(draft, lookups: lookups)
        }
    }

    private static func dateRow(_ draft: ReadingDraft, today: CalendarDay) -> QuickAddReviewRow {
        let day = draft.date.value
        let short = PocketFormat.shortDay(day)
        let value =
            day == today ? "\(short) · hoje"
            : day == today.adding(days: -1) ? "\(short) · ontem"
            : short
        return QuickAddReviewRow(
            field: .date, label: "Data", value: value, provenance: draft.date.provenance,
            isWeak: false, unavailable: nil
        )
    }

    private static func paymentMethodRow(_ draft: ReadingDraft, lookups: LookupSet) -> QuickAddReviewRow {
        let unavailable = lookups.failed.contains(.cards) ? QuickAddCopy.cardsUnavailable : nil
        let label = "Forma de pagamento"
        if let card = draft.card {
            return QuickAddReviewRow(
                field: .paymentMethod, label: label, value: card.value.name,
                provenance: card.provenance, isWeak: false, unavailable: unavailable
            )
        }
        guard let method = draft.paymentMethod else {
            return QuickAddReviewRow(
                field: .paymentMethod, label: label, value: "não informada",
                provenance: nil, isWeak: true, unavailable: unavailable
            )
        }
        return QuickAddReviewRow(
            field: .paymentMethod, label: label, value: methodName(method.value),
            provenance: method.provenance, isWeak: false, unavailable: unavailable
        )
    }

    private static func tagRow(_ draft: ReadingDraft, lookups: LookupSet) -> QuickAddReviewRow {
        let unavailable = lookups.failed.contains(.tags) ? QuickAddCopy.tagsUnavailable : nil
        guard let tag = draft.tag else {
            return QuickAddReviewRow(
                field: .tag, label: "Categoria", value: "sem categoria",
                provenance: nil, isWeak: true, unavailable: unavailable
            )
        }
        let name = lookups.tags.first { $0.id == tag.value }?.name ?? "sem categoria"
        return QuickAddReviewRow(
            field: .tag, label: "Categoria", value: name,
            provenance: tag.provenance, isWeak: false, unavailable: unavailable
        )
    }

    private static func dateChips(_ draft: ReadingDraft, today: CalendarDay) -> [QuickAddChip] {
        let options = [("Hoje", 0), ("Ontem", -1), ("Anteontem", -2)]
        return options.compactMap { label, offset in
            guard let day = today.adding(days: offset) else { return nil }
            return QuickAddChip(
                id: "date-\(offset)", label: label, isOn: draft.date.value == day,
                color: nil, change: .date(day)
            )
        }
    }

    private static func paymentMethodChips(_ draft: ReadingDraft, lookups: LookupSet) -> [QuickAddChip] {
        let methodChips = methods.map { entry in
            QuickAddChip(
                id: "method-\(entry.method.rawValue)", label: entry.label,
                isOn: draft.card == nil && draft.paymentMethod?.value == entry.method,
                color: nil, change: .paymentMethod(entry.method)
            )
        }
        guard !lookups.failed.contains(.cards) else { return methodChips }

        let cardChips = lookups.cards.map { card in
            QuickAddChip(
                id: "card-\(card.id.rawValue)", label: card.name,
                isOn: draft.card?.value.id == card.id,
                color: card.color, change: .card(CardCandidate(id: card.id, name: card.name))
            )
        }
        return methodChips + cardChips
    }

    private static func tagChips(_ draft: ReadingDraft, lookups: LookupSet) -> [QuickAddChip] {
        guard !lookups.failed.contains(.tags) else { return [] }

        return lookups.tags
            .filter { $0.kind == draft.type?.value }
            .map { tag in
                QuickAddChip(
                    id: "tag-\(tag.id.rawValue)", label: tag.name, isOn: draft.tag?.value == tag.id,
                    color: tag.color, change: .tag(tag.id)
                )
            }
    }

    private static func methodName(_ method: PaymentMethod) -> String {
        switch method {
        case .credit: "Crédito"
        case .debit: "Débito"
        case .pix: "Pix"
        case .cash: "Dinheiro"
        case .crypto: "Cripto"
        }
    }
}
