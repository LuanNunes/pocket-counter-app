import Foundation

@testable import PocketCounter

/// Builders for the backend's JSON; `nil` renders as `null`, and `.absent` omits the key.
enum WireFixtures {
    enum Field<Value> {
        case absent
        case value(Value?)
    }

    private static func quoted(_ text: String?) -> String {
        guard let text else { return "null" }
        return "\"\(text)\""
    }

    static func tag(
        id: String = "g1", name: String = "Mercado", kind: String = "EXPENSE",
        idCategory: String? = "c1", color: String? = nil, idRecurringTransaction: String? = nil
    ) -> String {
        """
        {"id":\(quoted(id)),"idUser":"u1","idCategory":\(quoted(idCategory)),"idTransaction":null,\
        "name":\(quoted(name)),"kind":\(quoted(kind)),"color":\(quoted(color)),"idRecurringTransaction":\(quoted(idRecurringTransaction))}
        """
    }

    static func category(id: String = "c1", name: String = "Casa", color: String? = nil, displayOrder: Int? = 1) -> String {
        """
        {"id":\(quoted(id)),"idUser":"u1","name":\(quoted(name)),"color":\(quoted(color)),\
        "displayOrder":\(displayOrder.map(String.init) ?? "null")}
        """
    }

    static func card(id: String = "k1", name: String = "Nubank", brand: String? = nil, closingDay: Int? = nil, color: String? = nil) -> String {
        """
        {"id":\(quoted(id)),"idUser":"u1","name":\(quoted(name)),"brand":\(quoted(brand)),\
        "closingDay":\(closingDay.map(String.init) ?? "null"),"color":\(quoted(color))}
        """
    }

    static func recurringTransaction(
        id: String? = "r1", name: String? = "Aluguel", type: String? = "EXPENSE"
    ) -> String {
        """
        {"id":\(quoted(id)),"idUser":"u1","name":\(quoted(name)),"transactionType":\(quoted(type))}
        """
    }

    static func transaction(
        id: String = "t1", type: String = "EXPENSE", name: String? = "Mercado", amount: String = "10.50",
        status: String = "PAID", ref: Int = 202610, displayOrder: Int = 0, method: String? = nil,
        cardId: String? = nil, isInvoice: Bool = false, idRecurringTransaction: String? = nil,
        dateDue: String? = nil, datePaid: String? = nil, tags: Field<[String]> = .absent
    ) -> String {
        var tagsField = ""
        if case .value(let tags) = tags {
            tagsField = ",\"tags\":" + (tags.map { "[" + $0.joined(separator: ",") + "]" } ?? "null")
        }
        return """
        {"id":\(quoted(id)),"idUser":"u1","transactionType":\(quoted(type)),"name":\(quoted(name)),\
        "description":null,"amount":\(amount),"statusPayment":\(quoted(status)),"refYearMonth":\(ref),\
        "displayOrder":\(displayOrder),"paymentMethod":\(quoted(method)),"cardId":\(quoted(cardId)),\
        "isInvoice":\(isInvoice),"idRecurringTransaction":\(quoted(idRecurringTransaction)),"currency":"BRL","dateDue":\(quoted(dateDue)),\
        "datePaid":\(quoted(datePaid))\(tagsField)}
        """
    }

    static func item(
        id: String = "i1", idTransaction: String = "t1", name: String = "Uber", amount: String = "20.00",
        datePurchase: String? = nil, originName: String? = nil, tags: Field<[String]> = .absent
    ) -> String {
        var tagsField = ""
        if case .value(let tags) = tags {
            tagsField = ",\"tags\":" + (tags.map { "[" + $0.joined(separator: ",") + "]" } ?? "null")
        }
        return """
        {"id":\(quoted(id)),"idTransaction":\(quoted(idTransaction)),"name":\(quoted(name)),"amount":\(amount),\
        "datePurchase":\(quoted(datePurchase)),"originName":\(quoted(originName))\(tagsField)}
        """
    }

    static func decode<T: Decodable>(_ type: T.Type = T.self, _ json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }
}
