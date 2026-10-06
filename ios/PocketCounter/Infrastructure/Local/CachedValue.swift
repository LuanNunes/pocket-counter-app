import Foundation

/// Single-flight: concurrent callers await one `Task`. A flag would not serialize across an
/// `await`, and the `Task` is unstructured so the first caller leaving cannot cancel it.
actor CachedValue<Value: Sendable> {
    static var defaultTTL: Duration { .seconds(300) }

    private let now: @Sendable () -> ContinuousClock.Instant
    private var cached: (value: Value, expires: ContinuousClock.Instant)?
    private var inFlight: Task<Result<Value, LoadFailure>, Never>?
    private var generation = 0

    init(now: @escaping @Sendable () -> ContinuousClock.Instant = { .now }) {
        self.now = now
    }

    /// No `await` may sit between reading and writing `inFlight`.
    func value(
        ttl: Duration = CachedValue.defaultTTL,
        load: @escaping @Sendable () async throws(LoadFailure) -> Value
    ) async throws(LoadFailure) -> Value {
        if let cached, now() < cached.expires { return cached.value }
        if let inFlight { return try await inFlight.value.get() }
        let started = generation
        let task = Task {
            let result = await Result(catching: load)
            if case .success(let loaded) = result, started == generation {
                cached = (loaded, now().advanced(by: ttl))
            }
            return result
        }
        inFlight = task
        defer { if inFlight == task { inFlight = nil } }
        return try await task.value.get()
    }

    /// A load already running finishes for its callers but is not kept.
    func invalidate() {
        generation += 1
        cached = nil
        inFlight = nil
    }
}
