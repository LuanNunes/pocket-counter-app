import Foundation

enum RecurringSeriesMapper {
    static func map(_ dto: RecurringSeriesDTO) throws(MappingFailure) -> RecurringSeries {
        guard let id = dto.id, !id.isEmpty else { throw .missingField(entity: "RecurringSeries", field: "id") }
        guard let name = dto.name else { throw .missingField(entity: "RecurringSeries", field: "name") }
        guard let wire = dto.transactionType else {
            throw .missingField(entity: "RecurringSeries", field: "transactionType")
        }
        guard let type = TransactionType(wire: wire) else {
            throw .unknownEnum(entity: "RecurringSeries", field: "transactionType", value: wire)
        }
        return RecurringSeries(id: SeriesID(rawValue: id), name: name, type: type, recurrenceDay: dto.recurrenceDay)
    }
}
