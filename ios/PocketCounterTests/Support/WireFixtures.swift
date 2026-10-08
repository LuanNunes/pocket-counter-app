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
        idCategory: String? = "c1", color: String? = nil, idSeries: String? = nil
    ) -> String {
        """
        {"id":\(quoted(id)),"idUser":"u1","idCategory":\(quoted(idCategory)),"idTransaction":null,\
        "name":\(quoted(name)),"kind":\(quoted(kind)),"color":\(quoted(color)),"idSeries":\(quoted(idSeries))}
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

    static func series(
        id: String? = "s1", name: String? = "Aluguel", type: String? = "EXPENSE", recurrenceDay: Int? = 5
    ) -> String {
        """
        {"id":\(quoted(id)),"idUser":"u1","name":\(quoted(name)),"transactionType":\(quoted(type)),\
        "recurrenceDay":\(recurrenceDay.map(String.init) ?? "null")}
        """
    }

    static func transaction(
        id: String = "t1", type: String = "EXPENSE", name: String? = "Mercado", amount: String = "10.50",
        status: String = "PAID", ref: Int = 202610, displayOrder: Int = 0, method: String? = nil,
        cardId: String? = nil, isInvoice: Bool = false, idSeries: String? = nil,
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
        "isInvoice":\(isInvoice),"idSeries":\(quoted(idSeries)),"currency":"BRL","dateDue":\(quoted(dateDue)),\
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

    /// Live `POST /transactions/raw` answers from api-dev, `referenceDate` 2026-10-07.
    enum Captured {
        /// `missing: []`; no `source.paymentMethod` key; `NOT_APPLICABLE`.
        static let supermarket = #"{"reading":{"type":"EXPENSE","amount":150.00,"date":"2026-10-07","name":"Supermercado","paymentMethod":null},"source":{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","name":"WRITTEN"},"card":{"status":"NOT_APPLICABLE","resolved":null,"candidates":[]},"tag":{"idTag":null,"idCategory":null},"missing":[]}"#

        /// `source.card` WRITTEN with `source.paymentMethod` INFERRED.
        static let gasolineOnNubank = #"{"reading":{"type":"EXPENSE","amount":320.00,"date":"2026-10-07","name":"Gasolina","paymentMethod":"CREDIT"},"source":{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","name":"WRITTEN","paymentMethod":"INFERRED","card":"WRITTEN"},"card":{"status":"RESOLVED","resolved":{"id":"d0000000-0000-0000-0000-000000000001","name":"NuBank"},"candidates":[]},"tag":{"idTag":null,"idCategory":null},"missing":[]}"#

        /// Two candidates; `name` null and `source.name` absent.
        static let ambiguousCredit = #"{"reading":{"type":"EXPENSE","amount":50.00,"date":"2026-10-07","name":null,"paymentMethod":"CREDIT"},"source":{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","paymentMethod":"WRITTEN"},"card":{"status":"AMBIGUOUS","resolved":null,"candidates":[{"id":"d0000000-0000-0000-0000-000000000001","name":"NuBank"},{"id":"d0000000-0000-0000-0000-000000000002","name":"Itaú"}]},"tag":{"idTag":null,"idCategory":null},"missing":["DESCRIPTION","CARD"]}"#

        /// `type` null; the date is resolved to yesterday and WRITTEN.
        static let yesterday = #"{"reading":{"type":null,"amount":68.00,"date":"2026-10-06","name":null,"paymentMethod":null},"source":{"amount":"WRITTEN","date":"WRITTEN"},"card":{"status":"NOT_APPLICABLE","resolved":null,"candidates":[]},"tag":{"idTag":null,"idCategory":null},"missing":["DESCRIPTION","TYPE"]}"#

        /// `amount` null and `source.amount` absent.
        static let noAmount = #"{"reading":{"type":"EXPENSE","amount":null,"date":"2026-10-07","name":"Mercado","paymentMethod":null},"source":{"type":"WRITTEN","date":"INFERRED","name":"WRITTEN"},"card":{"status":"NOT_APPLICABLE","resolved":null,"candidates":[]},"tag":{"idTag":null,"idCategory":null},"missing":["AMOUNT"]}"#

        static let blankText = #"{"code":"BAD_REQUEST","message":"Domain exception occurred","details":["The text is required"],"correlationId":"…","timestamp":"…"}"#

        static let missingReferenceDate = #"{"code":"BAD_REQUEST","message":"Domain exception occurred","details":["The reference date is required"],"correlationId":"…","timestamp":"…"}"#
    }

    static func rawResponse(
        reading: String = #"{"type":"EXPENSE","amount":150.00,"date":"2026-10-07","name":"Supermercado","paymentMethod":null}"#,
        source: String = #"{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","name":"WRITTEN"}"#,
        card: String = #"{"status":"NOT_APPLICABLE","resolved":null,"candidates":[]}"#,
        tag: String = #"{"idTag":null,"idCategory":null}"#,
        missing: String = "[]"
    ) -> String {
        #"{"reading":\#(reading),"source":\#(source),"card":\#(card),"tag":\#(tag),"missing":\#(missing)}"#
    }

    static func decode<T: Decodable>(_ type: T.Type = T.self, _ json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }
}
