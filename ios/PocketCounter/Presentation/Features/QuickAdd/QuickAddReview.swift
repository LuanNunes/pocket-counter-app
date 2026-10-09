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
    /// What VoiceOver reads: "07/10" would be read as digits.
    let spokenValue: String
    /// The category's dot; `nil` on every other row.
    let color: UInt32?
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

/// One fact the server already read, shown above the question.
struct QuickAddUnderstood: Identifiable, Equatable {
    let id: String
    let text: String
    let spoken: String
    let symbol: String?
    let isIncome: Bool
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

    static func understood(from draft: ReadingDraft) -> [QuickAddUnderstood] {
        let amount = draft.amount.map { field in
            QuickAddUnderstood(
                id: "amount", text: PocketFormat.currency(field.value.amount, signed: false),
                spoken: PocketFormat.spokenCurrency(field.value.amount), symbol: nil,
                isIncome: draft.type?.value == .income
            )
        }
        let type = draft.type.map { field in
            let name = QuickAddCopy.kindName(field.value)
            return QuickAddUnderstood(id: "type", text: name, spoken: name.lowercased(), symbol: nil, isIncome: false)
        }
        let name = draft.name.map {
            QuickAddUnderstood(id: "name", text: $0.value, spoken: $0.value, symbol: nil, isIncome: false)
        }
        let date = QuickAddUnderstood(
            id: "date", text: PocketFormat.shortDay(draft.date.value),
            spoken: PocketFormat.dayLabel(draft.date.value), symbol: "calendar", isIncome: false
        )
        let method = (draft.card?.value.name ?? draft.paymentMethod.map { methodName($0.value) }).map {
            QuickAddUnderstood(id: "method", text: $0, spoken: $0, symbol: "creditcard", isIncome: false)
        }
        return [amount, type, name, date, method].compactMap { $0 }
    }

    private static func dateRow(_ draft: ReadingDraft, today: CalendarDay) -> QuickAddReviewRow {
        let day = draft.date.value
        let short = PocketFormat.shortDay(day)
        let named =
            day == today ? "hoje"
            : day == today.adding(days: -1) ? "ontem"
            : nil
        let spoken = PocketFormat.dayLabel(day)
        return QuickAddReviewRow(
            field: .date, label: "Data",
            value: named.map { "\(short) · \($0)" } ?? short,
            spokenValue: named.map { "\(spoken), \($0)" } ?? spoken,
            color: nil, provenance: draft.date.provenance, isWeak: false, unavailable: nil
        )
    }

    private static func paymentMethodRow(_ draft: ReadingDraft, lookups: LookupSet) -> QuickAddReviewRow {
        let unavailable = lookups.failed.contains(.cards) ? QuickAddCopy.cardsUnavailable : nil
        let label = "Forma de Pagamento"
        if let card = draft.card {
            return QuickAddReviewRow(
                field: .paymentMethod, label: label, value: card.value.name,
                spokenValue: card.value.name, color: nil, provenance: card.provenance, isWeak: false, unavailable: unavailable
            )
        }
        guard let method = draft.paymentMethod else {
            return QuickAddReviewRow(
                field: .paymentMethod, label: label, value: "não informada",
                spokenValue: "não informada", color: nil, provenance: nil, isWeak: true, unavailable: unavailable
            )
        }
        return QuickAddReviewRow(
            field: .paymentMethod, label: label, value: methodName(method.value),
            spokenValue: methodName(method.value), color: nil, provenance: method.provenance, isWeak: false, unavailable: unavailable
        )
    }

    private static func tagRow(_ draft: ReadingDraft, lookups: LookupSet) -> QuickAddReviewRow {
        let unavailable = lookups.failed.contains(.tags) ? QuickAddCopy.tagsUnavailable : nil
        guard let tag = draft.tag else {
            return QuickAddReviewRow(
                field: .tag, label: "Categoria", value: "sem categoria",
                spokenValue: "sem categoria", color: nil, provenance: nil, isWeak: true, unavailable: unavailable
            )
        }
        let found = lookups.tags.first { $0.id == tag.value }
        let name = found?.name ?? "sem categoria"
        return QuickAddReviewRow(
            field: .tag, label: "Categoria", value: name, spokenValue: name, color: found?.color,
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
                id: "card-\(card.id.rawValue)", label: card.name.replacingOccurrences(of: "^Cartão ", with: "", options: .regularExpression),
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
