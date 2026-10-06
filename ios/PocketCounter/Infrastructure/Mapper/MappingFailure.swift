import Foundation

/// Infrastructure; never leaves `Infrastructure/`.
enum MappingFailure: Error, Equatable, Sendable {
    case missingField(entity: String, field: String)
    case unknownEnum(entity: String, field: String, value: String)
    case invalidDate(entity: String, value: String)
    case invalidRef(Int)
}
