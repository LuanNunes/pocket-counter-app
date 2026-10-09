enum QuickAddCopy {
    static let title = "Lançamento rápido"
    static let placeholder = "Ex.: paguei 250 em uma consulta do cachorro"
    static let homeField = "O que você gastou ou recebeu?"
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

    static func question(_ field: MissingField) -> String {
        switch field {
        case .amount: "Qual foi o valor?"
        case .description: "O que foi?"
        case .card: "Em qual cartão?"
        case .type: "Foi despesa ou receita?"
        }
    }
}
