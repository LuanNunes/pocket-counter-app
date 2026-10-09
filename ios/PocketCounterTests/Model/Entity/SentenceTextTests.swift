import Testing

@testable import PocketCounter

@Suite("SentenceText")
struct SentenceTextTests {
    @Test("a blank sentence is refused as blank", arguments: ["", " ", "  \n\t "])
    func blank(_ raw: String) {
        #expect(throws: SentenceTextError.blank) { try SentenceText(raw) }
    }

    @Test("the surrounding whitespace is trimmed")
    func trimmed() throws {
        #expect(try SentenceText("  gastei 150 no mercado \n").value == "gastei 150 no mercado")
    }

    @Test("500 units are accepted and 501 are refused with the limit")
    func limit() throws {
        _ = try SentenceText(String(repeating: "a", count: 500))
        #expect(throws: SentenceTextError.tooLong(maximum: 500)) { try SentenceText(String(repeating: "a", count: 501)) }
    }

    @Test("the limit counts UTF-16 units, as the server does, not characters")
    func utf16Boundary() throws {
        _ = try SentenceText(String(repeating: "😀", count: 250))
        #expect(throws: SentenceTextError.tooLong(maximum: 500)) { try SentenceText(String(repeating: "😀", count: 251)) }
        #expect(throws: SentenceTextError.tooLong(maximum: 500)) { try SentenceText(String(repeating: "a", count: 499) + "😀") }
    }

    @Test("the limit applies to the trimmed text")
    func limitAfterTrim() throws {
        _ = try SentenceText(" " + String(repeating: "a", count: 500) + " ")
    }

    @Test("each reason has user-facing text")
    func messages() {
        #expect(SentenceTextError.blank.message == "Escreva o lançamento")
        #expect(SentenceTextError.tooLong(maximum: 500).message == "O texto é longo demais (máximo de 500 caracteres)")
    }
}
