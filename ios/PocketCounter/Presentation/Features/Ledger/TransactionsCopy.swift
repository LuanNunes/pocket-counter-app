import Foundation

enum TransactionsCopy {
    static func kindName(_ kind: TransactionType) -> String {
        kind == .income ? "Receitas" : "Despesas"
    }

    static func count(_ count: Int, kind: TransactionType) -> String {
        switch (kind, count) {
        case (.expense, 1): "1 despesa"
        case (.expense, _): "\(count) despesas"
        case (.income, 1): "1 receita"
        case (.income, _): "\(count) receitas"
        }
    }

    static func emptyMonth(_ kind: TransactionType) -> String {
        kind == .income ? "Nenhuma receita neste mês." : "Nenhuma despesa neste mês."
    }

    static let filteredOutTitle = "Nenhum resultado"
    static let filteredOutDetail = "Nenhum lançamento corresponde ao filtro."

    static func modeName(_ mode: LedgerGroupMode) -> String {
        switch mode {
        case .lista: "Lista"
        case .categoria: "Por categoria"
        case .tag: "Por tag"
        }
    }

    static let searchPrompt = "Buscar lançamentos"
    static let groupingLabel = "Agrupar por"
    static let onlyFixos = "Só fixos"

    static let total = "Total"
    static let uncategorised = "Sem categoria"
    static let untagged = "Sem tag"
    static let unresolvedGroup = "Nome indisponível"

    static func methodName(_ method: PaymentMethod) -> String {
        switch method {
        case .credit: "Crédito"
        case .debit: "Débito"
        case .pix: "Pix"
        case .cash: "Dinheiro"
        case .crypto: "Cripto"
        }
    }

    static func statusCaption(_ status: PaymentStatus) -> String {
        status == .paid ? "paga" : "pendente"
    }

    static func extraTags(_ count: Int) -> String { "+\(count)" }
}
