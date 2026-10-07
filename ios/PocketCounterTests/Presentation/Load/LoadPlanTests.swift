import Testing

@testable import PocketCounter

@Suite("LoadPlan")
struct LoadPlanTests {

    @Test("each phase has one plan")
    func plans() {
        #expect(LoadPlan<Int>(.firstLoad) == .skeleton)
        #expect(LoadPlan<Int>(.failed(.unreachable)) == .blocking(.unreachable))
        #expect(LoadPlan<Int>(.loaded(1)) == .content(1, notice: nil))
    }

    @Test("stale carries the failure's notice over the data")
    func stale() {
        let notice = PocketNotice(
            kind: .error, title: "Algo deu errado", detail: "Mostrando os dados anteriores.")

        #expect(LoadPlan<Int>(.stale(1, .server)) == .content(1, notice: notice))
    }

    @Test("a stale phase whose failure is silent has no notice but is still stale")
    func silentStale() {
        let phase = LoadPhase<Int>.stale(1, .abandoned)
        #expect(LoadPlan(phase) == .content(1, notice: nil))
        #expect(phase.isStale)
        #expect(!LoadPhase<Int>.loaded(1).isStale)
    }
}
