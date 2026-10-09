import Foundation

enum RecurringTransactionMapper {
    static func map(_ dto: RecurringTransactionDTO) throws(MappingFailure) -> RecurringTransaction {
        guard let id = dto.id, !id.isEmpty else { throw .missingField(entity: "RecurringTransaction", field: "id") }
        guard let name = dto.name else { throw .missingField(entity: "RecurringTransaction", field: "name") }
        guard let wire = dto.transactionType else {
            throw .missingField(entity: "RecurringTransaction", field: "transactionType")
        }
        guard let type = TransactionType(wire: wire) else {
            throw .unknownEnum(entity: "RecurringTransaction", field: "transactionType", value: wire)
        }
        return RecurringTransaction(id: RecurringTransactionID(rawValue: id), name: name, type: type)
    }
}
