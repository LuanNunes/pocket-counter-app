import Foundation

enum TagMapper {
    // `kind` is trusted only because this is fed by /tags, never by a transaction's embedded tags.
    static func tag(_ dto: TagDTO) throws(MappingFailure) -> Tag {
        guard !dto.id.isEmpty else { throw .missingField(entity: "Tag", field: "id") }
        guard let kind = TransactionType(wire: dto.kind) else {
            throw .unknownEnum(entity: "Tag", field: "kind", value: dto.kind)
        }
        return Tag(
            id: TagID(rawValue: dto.id),
            name: dto.name,
            kind: kind,
            contextId: dto.idCategory.map { ContextID(rawValue: $0) },
            color: HexColor.argb(dto.color)
        )
    }

    static func context(_ dto: CategoryDTO) throws(MappingFailure) -> TagContext {
        guard !dto.id.isEmpty else { throw .missingField(entity: "Category", field: "id") }
        return TagContext(id: ContextID(rawValue: dto.id), name: dto.name, color: HexColor.argb(dto.color))
    }
}
