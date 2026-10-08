import Foundation

/// Pairs each value with its source. A value with no source throws: an unbadged field is the one
/// thing the contract forbids. A source with no value is dropped.
enum SentenceReadingMapper {
    private static let entity = "SentenceReading"

    static func map(_ dto: TransactionRawResponseDTO) throws(MappingFailure) -> SentenceReading {
        let reading = dto.reading
        let source = dto.source
        return SentenceReading(
            type: try paired(try transactionType(reading.type), source.type, field: "type"),
            amount: try paired(reading.amount.map(Money.init), source.amount, field: "amount"),
            date: Sourced(value: try day(reading.date), source: try fieldSource(source.date, field: "source.date")),
            name: try paired(reading.name, source.name, field: "name"),
            paymentMethod: try paired(reading.paymentMethod.flatMap(PaymentMethod.init(wire:)), source.paymentMethod, field: "paymentMethod"),
            card: try card(dto.card, source: source.card),
            tag: dto.tag.idTag.flatMap { $0.isEmpty ? nil : TagID(rawValue: $0) },
            missing: try questions(dto.missing)
        )
    }

    private static func paired<Value: Hashable & Sendable>(
        _ value: Value?, _ source: String?, field: String
    ) throws(MappingFailure) -> Sourced<Value>? {
        guard let value else { return nil }
        return try sourced(value, source, field: field)
    }

    private static func sourced<Value: Hashable & Sendable>(
        _ value: Value, _ source: String?, field: String
    ) throws(MappingFailure) -> Sourced<Value> {
        guard let source else { throw .missingField(entity: entity, field: "source.\(field)") }
        return Sourced(value: value, source: try fieldSource(source, field: "source.\(field)"))
    }

    private static func fieldSource(_ wire: String, field: String) throws(MappingFailure) -> FieldSource {
        try decode(wire, field: field) { wire in
            switch wire {
            case "WRITTEN": .written
            case "INFERRED": .inferred
            default: nil
            }
        }
    }

    private static func transactionType(_ wire: String?) throws(MappingFailure) -> TransactionType? {
        guard let wire else { return nil }
        return try decode(wire, field: "reading.type", TransactionType.init(wire:))
    }

    private static func questions(_ wire: [String]) throws(MappingFailure) -> [MissingField] {
        var questions: [MissingField] = []
        for field in wire {
            questions.append(try decode(field, field: "missing", missingField))
        }
        return questions
    }

    private static func missingField(_ wire: String) -> MissingField? {
        switch wire {
        case "AMOUNT": .amount
        case "DESCRIPTION": .description
        case "CARD": .card
        case "TYPE": .type
        default: nil
        }
    }

    private static func card(_ dto: TransactionRawResponseDTO.Card, source: String?) throws(MappingFailure) -> CardReading {
        switch dto.status {
        case "RESOLVED":
            guard let resolved = dto.resolved else { throw .missingField(entity: entity, field: "card.resolved") }
            return .resolved(try sourced(try candidate(resolved), source, field: "card"))
        case "AMBIGUOUS":
            var candidates: [CardCandidate] = []
            for wire in dto.candidates { candidates.append(try candidate(wire)) }
            guard !candidates.isEmpty else { throw .missingField(entity: entity, field: "card.candidates") }
            return .ambiguous(candidates)
        case "UNRESOLVED":
            return .unresolved
        case "NOT_APPLICABLE":
            return .notApplicable
        default:
            throw .unknownEnum(entity: entity, field: "card.status", value: dto.status)
        }
    }

    private static func candidate(_ dto: TransactionRawResponseDTO.Candidate) throws(MappingFailure) -> CardCandidate {
        guard !dto.id.isEmpty else { throw .missingField(entity: entity, field: "card.candidates.id") }
        return CardCandidate(id: CardID(rawValue: dto.id), name: dto.name)
    }

    private static func day(_ text: String) throws(MappingFailure) -> CalendarDay {
        guard let day = try? CalendarDay(iso: text) else { throw .invalidDate(entity: entity, value: text) }
        return day
    }

    private static func decode<Value>(
        _ wire: String, field: String, _ transform: (String) -> Value?
    ) throws(MappingFailure) -> Value {
        guard let value = transform(wire) else { throw .unknownEnum(entity: entity, field: field, value: wire) }
        return value
    }
}
