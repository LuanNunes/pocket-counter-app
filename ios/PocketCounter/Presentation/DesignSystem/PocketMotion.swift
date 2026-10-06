import SwiftUI

/// Animation curves and timings shared across screens.
enum PocketMotion {
    /// The gate and screen transitions.
    static let standard = Animation.timingCurve(0.32, 0.72, 0, 1, duration: 0.42)
    static let quick = Animation.easeOut(duration: 0.2)
    /// Stands in for `standard` under Reduce Motion.
    static let reduced = Animation.easeOut(duration: 0.15)
    /// How long a load runs before its spinner may appear.
    static let indicatorGrace = Duration.milliseconds(600)
}
