import Foundation

final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var n = 0

    var count: Int { lock.withLock { n } }

    @discardableResult
    func increment() -> Int { lock.withLock { n += 1; return n } }
}

final class ManualClock: @unchecked Sendable {
    private let lock = NSLock()
    private var current = ContinuousClock.now

    var now: ContinuousClock.Instant { lock.withLock { current } }

    func advance(by duration: Duration) { lock.withLock { current = current.advanced(by: duration) } }
}

/// Suspends `wait()` until `open()`; `untilWaiting()` returns once someone is suspended.
actor Gate {
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private var isOpen = false

    func wait() async {
        guard !isOpen else { return }
        await withCheckedContinuation { waiters.append($0) }
    }

    func untilWaiting() async {
        while waiters.isEmpty { await Task.yield() }
    }

    func open() {
        isOpen = true
        waiters.forEach { $0.resume() }
        waiters = []
    }
}

/// Each `arrive()` suspends until `release()`, so a test can ask how many callers got in first.
final class Rendezvous: Sendable {
    private let counter = Counter()
    private let gate = Gate()

    var arrivals: Int { counter.count }

    func arrive() async {
        counter.increment()
        await gate.wait()
    }

    /// Bounded: a sequential implementation fails on the count instead of hanging.
    func untilArrivals(_ expected: Int) async {
        for _ in 0..<10_000 where arrivals < expected { await Task.yield() }
    }

    func release() async { await gate.open() }
}
