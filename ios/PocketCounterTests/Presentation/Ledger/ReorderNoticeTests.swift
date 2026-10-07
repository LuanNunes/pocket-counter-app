import Testing

@testable import PocketCounter

@Suite("ReorderNotice")
struct ReorderNoticeTests {
    @Test("a failure shows the reorder message")
    func match() {
        let notice = ReorderNotice.message(for: .unreachable)

        #expect(notice == PocketNotice(kind: .offline, title: "Não foi possível reordenar", detail: "Sem conexão com o servidor."))
    }

    @Test("no failure shows nothing")
    func none() {
        #expect(ReorderNotice.message(for: nil) == nil)
    }

    /// The gate renders an expired session; a notice over the list would be a second account of it.
    @Test("an expired session shows nothing")
    func sessionExpired() {
        #expect(ReorderNotice.message(for: .sessionExpired) == nil)
    }
}
