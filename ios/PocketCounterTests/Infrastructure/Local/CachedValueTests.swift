import Foundation
import Testing

@testable import PocketCounter

@Suite("CachedValue")
struct CachedValueTests {
    @Test("a second call within the TTL reuses the loaded value")
    func reusesWithinTTL() async throws {
        let cache = CachedValue<Int>()
        let loads = Counter()

        let first = try await cache.value { loads.increment() }
        let second = try await cache.value { loads.increment() }

        #expect(first == 1)
        #expect(second == 1)
        #expect(loads.count == 1)
    }

    @Test("a call at or after the TTL loads again")
    func reloadsAfterTTL() async throws {
        let clock = ManualClock()
        let cache = CachedValue<Int>(now: { clock.now })
        let loads = Counter()

        _ = try await cache.value(ttl: .seconds(300)) { loads.increment() }
        clock.advance(by: .seconds(299))
        let within = try await cache.value(ttl: .seconds(300)) { loads.increment() }
        clock.advance(by: .seconds(1))
        let expired = try await cache.value(ttl: .seconds(300)) { loads.increment() }

        #expect(within == 1)
        #expect(expired == 2)
    }

    @Test("two concurrent callers produce exactly one load")
    func singleFlight() async throws {
        let cache = CachedValue<Int>()
        let loads = Counter()
        let gate = Gate()
        let load: @Sendable () async -> Int = {
            let n = loads.increment()
            await gate.wait()
            return n
        }

        let first = Task { try await cache.value(load: load) }
        await gate.untilWaiting()
        let second = Task { try await cache.value(load: load) }
        for _ in 0..<50 { await Task.yield() }
        await gate.open()

        #expect(try await first.value == 1)
        #expect(try await second.value == 1)
        #expect(loads.count == 1)
    }

    @Test("invalidation forces the next call to load again")
    func invalidation() async throws {
        let cache = CachedValue<Int>()
        let loads = Counter()

        _ = try await cache.value { loads.increment() }
        await cache.invalidate()
        let reloaded = try await cache.value { loads.increment() }

        #expect(reloaded == 2)
    }

    @Test("a load that began before an invalidation does not repopulate the cache")
    func invalidationDuringLoad() async throws {
        let cache = CachedValue<Int>()
        let loads = Counter()
        let gate = Gate()

        let stale = Task {
            try await cache.value {
                let n = loads.increment()
                await gate.wait()
                return n
            }
        }
        await gate.untilWaiting()
        await cache.invalidate()
        await gate.open()
        _ = try await stale.value
        let fresh = try await cache.value { loads.increment() }

        #expect(fresh == 2)
    }

    @Test("a failed load is not cached and the next call retries")
    func failureIsNotCached() async throws {
        let cache = CachedValue<Int>()
        let loads = Counter()

        await #expect(throws: LoadFailure.server) {
            try await cache.value { () async throws(LoadFailure) -> Int in
                loads.increment()
                throw .server
            }
        }
        let retried = try await cache.value { loads.increment() }

        #expect(retried == 2)
    }

    @Test("the first caller cancelling does not cancel the load or poison the second caller")
    func cancellationDoesNotPoison() async throws {
        let cache = CachedValue<Int>()
        let gate = Gate()
        let load: @Sendable () async -> Int = {
            await gate.wait()
            return Task.isCancelled ? -1 : 42
        }

        let first = Task { try await cache.value(load: load) }
        await gate.untilWaiting()
        let second = Task { try await cache.value(load: load) }
        for _ in 0..<50 { await Task.yield() }
        first.cancel()
        await gate.open()

        #expect(try await second.value == 42)
    }
}
