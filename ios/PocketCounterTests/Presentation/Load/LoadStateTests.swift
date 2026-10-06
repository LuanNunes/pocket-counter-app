import Testing

@testable import PocketCounter

@Suite("LoadState")
struct LoadStateTests {

    @Test("nothing yet is a first load")
    func firstLoad() {
        #expect(LoadState<[Int]>().phase == .firstLoad)
    }

    @Test("a commit is loaded")
    func loaded() {
        var state = LoadState<[Int]>()

        state.commit([1])

        #expect(state.phase == .loaded([1]))
    }

    @Test("a failure with nothing to show carries no value")
    func failed() {
        var state = LoadState<[Int]>()

        state.fail(.unreachable)

        #expect(state.phase == .failed(.unreachable))
        #expect(state.value == nil)
    }

    @Test("a failure that is a signal, not a message, is abandoned instead of recorded", arguments: [
        LoadFailure.sessionExpired, .abandoned,
    ])
    func signalsAreNotRecorded(failure: LoadFailure) {
        var state = LoadState<[Int]>()
        state.commit([1])
        state.beginLoading()

        state.fail(failure)

        #expect(state.failure == nil)
        #expect(!state.isLoading)
        #expect(state.phase == .loaded([1]))
    }

    @Test("a failure over a value keeps the value, stale")
    func stale() {
        var state = LoadState<[Int]>()
        state.commit([1])

        state.fail(.server)

        #expect(state.phase == .stale([1], .server))
    }

    @Test("an empty commit is loaded, never failed")
    func emptyIsLoaded() {
        var state = LoadState<[Int]>()

        state.commit([])

        #expect(state.phase == .loaded([]))
    }

    @Test("a commit clears a prior failure")
    func commitClearsFailure() {
        var state = LoadState<[Int]>()
        state.fail(.server)

        state.commit([2])

        #expect(state.failure == nil)
        #expect(state.phase == .loaded([2]))
    }

    @Test("a failure stops loading")
    func failStopsLoading() {
        var state = LoadState<[Int]>()
        state.beginLoading()

        state.fail(.server)

        #expect(!state.isLoading)
    }

    @Test("a commit stops loading")
    func commitStopsLoading() {
        var state = LoadState<[Int]>()
        state.beginLoading()

        state.commit([1])

        #expect(!state.isLoading)
    }

    @Test("abandoning stops loading and touches neither value nor failure")
    func abandon() {
        var state = LoadState<[Int]>()
        state.commit([1])
        state.fail(.server)
        state.beginLoading()
        let before = state.phase

        state.abandon()

        #expect(!state.isLoading)
        #expect(state.phase == before)
        #expect(state.value == [1])
        #expect(state.failure == .server)
    }

    @Test("loading never changes the phase", arguments: [
        LoadState<[Int]>(),
        { var s = LoadState<[Int]>(); s.commit([1]); return s }(),
        { var s = LoadState<[Int]>(); s.fail(.server); return s }(),
        { var s = LoadState<[Int]>(); s.commit([1]); s.fail(.server); return s }(),
    ])
    func loadingIsNotAPhase(state: LoadState<[Int]>) {
        var loading = state

        loading.beginLoading()

        #expect(loading.isLoading)
        #expect(loading.phase == state.phase)
    }
}
