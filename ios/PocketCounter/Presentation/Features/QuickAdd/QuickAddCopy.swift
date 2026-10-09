enum QuickAddCopy {
    static let title = "Lançamento rápido"
    static let placeholder = "Ex.: paguei 250 em uma consulta do cachorro"
    static let send = "Lançar"
    static let confirm = "Confirmar"
    static let editSentence = "Editar a frase"
    static let another = "Lançar outro"
    static let done = "Concluir"
    static let saveAnyway = "Lançar mesmo assim"
    static let understood = "Entendi"
    static let skipCard = "Sem cartão específico"
    static let reviewNote = "Toque em um campo para corrigir antes de lançar."
    static let cardsUnavailable = "Não foi possível carregar os cartões."
    static let tagsUnavailable = "Não foi possível carregar as categorias."

    static let retry = "Tentar novamente"
    static let sentenceField = "Frase do lançamento"
    static let amountField = "Valor em reais"
    static let namePlaceholder = "Ex.: consulta do cachorro"
    static let amountPlaceholder = "0,00"
    static let currencyPrefix = "R$"
    static let expense = "Despesa"
    static let income = "Receita"
    static let close = "fechar"
    static let openHint = "Mostra as opções"
    static let closeHint = "Fecha as opções"
    static let tagNotLoaded = "categoria não carregada"

    static func noTags(for type: TransactionType) -> String {
        switch type {
        case .expense: "Nenhuma categoria de despesa."
        case .income: "Nenhuma categoria de receita."
        }
    }
    private static let cardPrefix = "Cartão "

    static func shortCardName(_ name: String) -> String {
        guard name.range(of: cardPrefix, options: [.anchored, .caseInsensitive]) != nil else { return name }
        return String(name.dropFirst(cardPrefix.count))
    }

    static func spokenCard(_ shortName: String) -> String { cardPrefix + shortName }

    static func badge(_ provenance: FieldProvenance) -> String {
        switch provenance {
        case .fromSentence: "da frase"
        case .assumed: "assumido"
        case .defined: "definido"
        }
    }

    static func spokenBadge(_ provenance: FieldProvenance) -> String {
        switch provenance {
        case .fromSentence: "da frase"
        case .assumed: "assumido"
        case .defined: "definido por você"
        }
    }

    static func kindName(_ type: TransactionType) -> String {
        switch type {
        case .expense: expense
        case .income: income
        }
    }

    static func receiptStatus(_ type: TransactionType) -> String {
        switch type {
        case .expense: "Despesa lançada · pendente"
        case .income: "Receita lançada · pendente"
        }
    }

    static func spokenReceiptStatus(_ type: TransactionType) -> String {
        switch type {
        case .expense: "Despesa lançada, pendente"
        case .income: "Receita lançada, pendente"
        }
    }

    static func question(_ field: MissingField) -> String {
        switch field {
        case .amount: "Qual foi o valor?"
        case .description: "O que foi?"
        case .card: "Em qual cartão?"
        case .type: "Foi despesa ou receita?"
        }
    }
}
