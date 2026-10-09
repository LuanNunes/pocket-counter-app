/// What the sheet's views ask of the model. One closure carries them all.
enum QuickAddAction {
    case type(String)
    case send
    case editSentence
    case answerAmount(Money)
    case answerName(String)
    case answerType(TransactionType)
    case answerCard(CardCandidate)
    case skipCard
    case correct(QuickAddChip.Change)
    case save
    case saveAnyway
    case startAnother
    case finish
}
