import SwiftUI

/// Typography tokens, mapped from the prototype's hard-coded sizes.
///
/// The prototype sprinkles `font-size` per class because CSS gave it nothing better. Here
/// each one maps to a system text style so Dynamic Type works. A fixed size is used only
/// where the design depends on it — the hero balance — and even then it scales relative to
/// a text style rather than being frozen.
///
/// `letter-spacing` in the spec is in em; SwiftUI's `.tracking` is in points, so the
/// conversion is `tracking = size × em`.
///
/// The font family is the system one. `-apple-system` / `SF Pro` in the CSS *is*
/// `.system(...)` — never load a font file for it.
enum PocketFont {

    /// `.lt` — 34/700, -0.025em
    static let largeTitle = Font.system(.largeTitle, weight: .bold)
    static let largeTitleTracking: CGFloat = -0.85

    /// `.lt-sub` — 15/400
    static let largeTitleSubtitle = Font.system(.subheadline)

    /// `.hero-v` — 40/700, -0.03em. Fixed size, but relative to `.largeTitle` so it still
    /// responds to Dynamic Type.
    static let heroValue = Font.system(size: 40, weight: .bold)
    static let heroValueTracking: CGFloat = -1.2

    /// `.hero-k` — 14/500
    static let heroLabel = Font.system(.subheadline, weight: .medium)

    /// `.sec-h` — 20/700, -0.02em
    static let sectionTitle = Font.system(.title3, weight: .bold)
    static let sectionTitleTracking: CGFloat = -0.4

    /// `.sec-h small` — 15/400
    static let sectionSubtitle = Font.system(.subheadline)

    /// `.sec-h .lnk` — 15/500
    static let link = Font.system(.subheadline, weight: .medium)

    /// `.screen` base — 17, -0.01em. Also `.row .rk`.
    static let body = Font.system(.body)
    static let bodyTracking: CGFloat = -0.17

    /// `.row .rs` — 13
    static let rowSubtitle = Font.system(.footnote)

    /// `.mp-l` — 16
    static let monthPill = Font.system(.callout)

    /// `.tab` — 10.5/600, no tracking
    static let tabLabel = Font.system(.caption2, weight: .semibold)

    /// `.tl-k` — 13
    static let tileKey = Font.system(.footnote)
    /// `.tl-v` — 17/600
    static let tileValue = Font.system(.body, weight: .semibold)
}

extension View {
    /// Applies a token and its tracking together, so a caller cannot take one without the
    /// other. `.tnum` in the spec is `monospacedDigit`, for figures that must not jitter as
    /// they change.
    func pocketFont(_ font: Font, tracking: CGFloat = 0, tabularFigures: Bool = false) -> some View {
        self
            .font(tabularFigures ? font.monospacedDigit() : font)
            .tracking(tracking)
    }
}
