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

    static func statusToggleLabel(isPaid: Bool) -> String {
        isPaid ? "Paga — marcar como pendente" : "Pendente — marcar como paga"
    }

    static func statusMarked(_ status: PaymentStatus) -> String {
        status == .paid ? "Marcada como paga" : "Marcada como pendente"
    }

    static func remedyTitle(_ remedy: TransactionRowWrite.Remedy) -> String {
        switch remedy {
        case .retry: "Tentar novamente"
        case .refresh: "Atualizar"
        }
    }

    static let statusSaving = "Salvando"
    static let statusDeleting = "Excluindo"

    static let detailTitle = "Lançamento"
    static let detailDate = "Data"
    static let detailPayment = "Forma de Pagamento"
    static let detailCategory = "Categoria"
    static let detailPaid = "Paga"
    static let detailRepeats = "Repete todo mês"
    static let detailRepeatsHint = "Vira conta fixa"
    static let deleteAction = "Excluir lançamento"
    static let deleteConfirmTitle = "Excluir este lançamento?"
    static let deleteConfirmMessage = "Isso não pode ser desfeito."
    static let deleteConfirm = "Excluir"
    static let deleteCancel = "Cancelar"
    static let reorderAction = "Reordenar"
    static let reorderDone = "OK"
    static let moveUp = "Mover para cima"
    static let moveDown = "Mover para baixo"

    static func position(_ number: Int, of total: Int) -> String {
        "posição \(number) de \(total)"
    }

    static func reorderHint(_ mode: LedgerGroupMode) -> String {
        "Arraste para reordenar dentro do \(mode == .lista ? "dia" : "grupo")"
    }

    static let emptyValue = "—"

    static func detailValue(_ text: String?) -> String {
        guard let text, !text.isEmpty else { return emptyValue }
        return text
    }

    static func detailCategories(_ tags: [TransactionRowContent.TagChip]) -> String {
        detailValue(tags.map(\.name).joined(separator: ", "))
    }

    static func extraTags(_ count: Int) -> String { "+\(count)" }
}
