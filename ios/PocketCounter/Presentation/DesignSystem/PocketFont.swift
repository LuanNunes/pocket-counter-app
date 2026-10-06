import SwiftUI

/// A font and the tracking that belongs to it. Pairing them structurally is the point: the
/// spec's `letter-spacing` is part of the token, not an optional extra at the call site.
///
/// `letter-spacing` is in em and `.tracking` is in points, so `tracking = size × em`.
struct PocketTextStyle {
    let font: Font
    let tracking: CGFloat

    init(_ font: Font, tracking: CGFloat = 0) {
        self.font = font
        self.tracking = tracking
    }
}

/// Typography tokens, mapped from the prototype's hard-coded sizes to system text styles so
/// Dynamic Type works.
///
/// The family is the system one: `-apple-system` / `SF Pro` in the CSS *is* `.system(...)` —
/// never load a font file for it.
enum PocketFont {

    /// `.lt` — 34/700, -0.025em
    static let largeTitle = PocketTextStyle(.system(.largeTitle, weight: .bold), tracking: -0.85)

    /// `.lt-sub`, `.sec-h small` — 15/400
    static let subtitle = PocketTextStyle(.system(.subheadline))

    /// `.hero-k` — 14/500
    static let heroLabel = PocketTextStyle(.system(.subheadline, weight: .medium))

    /// `.hero-kpis>div` — 15/400
    static let heroKpi = PocketTextStyle(.system(.subheadline))

    /// `.hero-kpis b` — 16/600
    static let heroKpiValue = PocketTextStyle(.system(.callout, weight: .semibold))

    /// `.sec-h` — 20/700, -0.02em
    static let sectionTitle = PocketTextStyle(.system(.title3, weight: .bold), tracking: -0.4)

    /// `.sec-h .lnk` — 15/500
    static let link = PocketTextStyle(.system(.subheadline, weight: .medium))

    /// `.screen` base — 17, -0.01em. Also `.row .rk`.
    static let body = PocketTextStyle(.system(.body), tracking: -0.17)

    /// `.row .rs`, `.tl-k` — 13. Small secondary text under a title or inside a tile.
    static let caption = PocketTextStyle(.system(.footnote))

    /// `.mp-l` — 16. The label inside a control, such as the month pill.
    static let controlLabel = PocketTextStyle(.system(.callout))

    /// `.mp-l` month run — 16/600. Equal to `heroKpiValue` by coincidence, so not aliased.
    static let controlLabelEmphasis = PocketTextStyle(.system(.callout, weight: .semibold))

    /// `.tab` — 10.5/600
    static let tabLabel = PocketTextStyle(.system(.caption2, weight: .semibold))

    /// `.tl-v` — 17/600. An emphasised value: a tile figure, a total.
    static let valueEmphasis = PocketTextStyle(.system(.body, weight: .semibold))

    /// `.badge` — 10.5/700, 0.04em. An uppercased, tracked eyebrow label. Apply
    /// `.textCase(.uppercase)` at the call site; the tracking is what the uppercase needs.
    static let eyebrow = PocketTextStyle(.system(.caption, weight: .bold), tracking: 0.48)

    /// The 28pt symbol that opens a full-screen notice. `.title` *is* 28pt, and scales.
    static let notice = PocketTextStyle(.system(.title, weight: .semibold))
}

extension View {
    /// `.tnum` in the spec is `monospacedDigit`, for figures that must not jitter as they
    /// change.
    func pocketFont(_ style: PocketTextStyle, tabularFigures: Bool = false) -> some View {
        self
            .font(tabularFigures ? style.font.monospacedDigit() : style.font)
            .tracking(style.tracking)
    }

    /// `.hero-v` — 40/700, -0.03em. The only fixed size in the design, so it is the only
    /// token that needs `@ScaledMetric` to follow Dynamic Type.
    func heroValueFont() -> some View {
        modifier(HeroValueFont())
    }
}

private struct HeroValueFont: ViewModifier {
    @ScaledMetric(relativeTo: .largeTitle) private var size: CGFloat = 40

    func body(content: Content) -> some View {
        content
            .font(.system(size: size, weight: .bold).monospacedDigit())
            .tracking(size * -0.03)
    }
}
