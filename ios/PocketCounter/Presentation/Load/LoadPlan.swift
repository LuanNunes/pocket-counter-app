/// What a `LoadPhase` puts on screen. Shared by `LoadRegion` and `LoadRegionRows`, which differ
/// only in how they lay it out.
enum LoadPlan<Value: Equatable>: Equatable {
    case skeleton
    case blocking(LoadFailure)
    /// `notice` rides above data that is still on screen; `nil` when the failure has nothing to say.
    case content(Value, notice: PocketNotice?)

    init(_ phase: LoadPhase<Value>) {
        switch phase {
        case .firstLoad: self = .skeleton
        case .failed(let failure): self = .blocking(failure)
        case .loaded(let value): self = .content(value, notice: nil)
        case .stale(let value, let failure): self = .content(value, notice: LoadFailureMessage.notice(for: failure))
        }
    }
}

extension LoadPhase {
    var isStale: Bool {
        guard case .stale = self else { return false }
        return true
    }
}
