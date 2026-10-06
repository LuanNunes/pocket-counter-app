/// What a screen knows about one load. `.failed` carries no value, so a failure can never
/// render as zeros; an empty committed value is `.loaded`.
///
/// Not an input: the month, `isLoading` (an overlay on any phase) and the spinner's grace
/// period, which is time and belongs to the view.
struct LoadState<Value: Equatable>: Equatable {
    private(set) var value: Value?
    private(set) var isLoading = false
    private(set) var failure: LoadFailure?

    var phase: LoadPhase<Value> {
        switch (value, failure) {
        case (nil, nil): .firstLoad
        case (nil, let failure?): .failed(failure)
        case (let value?, nil): .loaded(value)
        case (let value?, let failure?): .stale(value, failure)
        }
    }

    mutating func beginLoading() {
        isLoading = true
    }

    mutating func commit(_ value: Value) {
        self.value = value
        failure = nil
        isLoading = false
    }

    /// A signal, not a message: `.sessionExpired` and `.abandoned` have nothing to render.
    mutating func fail(_ failure: LoadFailure) {
        switch failure {
        case .sessionExpired, .abandoned:
            abandon()
            return
        case .authenticationUnavailable, .unreachable, .notFound, .rejected, .server:
            break
        }
        self.failure = failure
        isLoading = false
    }

    mutating func abandon() {
        isLoading = false
    }
}

enum LoadPhase<Value: Equatable>: Equatable {
    case firstLoad
    case failed(LoadFailure)
    case loaded(Value)
    case stale(Value, LoadFailure)
}

extension LoadState: Sendable where Value: Sendable {}
extension LoadPhase: Sendable where Value: Sendable {}
