import Testing

@testable import PocketCounter

@Suite("HexColor")
struct HexColorTests {

    @Test("it parses colours into opaque or explicit ARGB", arguments: [
        ("#112233", 0xFF112233),
        ("112233", 0xFF112233),
        ("  #AbCdEf \n", 0xFFABCDEF),
        ("#80112233", 0x80112233),
        ("000000", 0xFF000000),
    ] as [(String, UInt32)])
    func parses(text: String, expected: UInt32) {
        #expect(HexColor.argb(text) == expected)
    }

    @Test("absence and garbage both answer nil", arguments: [
        nil, "", "#", "#12345", "#1234567", "#123456789", "#GGGGGG", "red", "##112233", "#11 223", "#+12233",
    ] as [String?])
    func rejects(text: String?) {
        #expect(HexColor.argb(text) == nil)
    }
}
