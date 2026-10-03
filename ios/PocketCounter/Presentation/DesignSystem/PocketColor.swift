import SwiftUI
import UIKit

/// Color tokens, mapped from `docs/ios26/glass.css` (lines 11-15).
///
/// Most of the prototype's tokens are Apple's semantic colors copied into CSS, so they map
/// back to the real ones. That is deliberate: semantic colors are what dark mode, Increased
/// Contrast, Reduce Transparency and the Liquid Glass blend are built on, and a literal hex
/// would break all four.
///
/// Only the purple tints and the hero gradient are custom. They are defined in OKLCH in the
/// spec, converted once, and stored in the asset catalog as Display P3 — `tint` in dark mode
/// falls outside sRGB. Do not hand-tweak them; regenerate from the OKLCH source if the spec
/// changes.
enum PocketColor {

    // MARK: Custom — asset catalog, derived from OKLCH

    /// `--tint`: oklch(0.55 0.2 285) / dark oklch(0.72 0.16 285)
    static let tint = Color("tint")
    /// `--tint-soft`: the tinted background behind a tint-colored label.
    static let tintSoft = Color("tintSoft")
    /// `--tint-ink`: tint darkened for text that must stay legible on `tintSoft`.
    static let tintInk = Color("tintInk")

    // MARK: Semantic — Apple's, not ours

    /// `--bg`
    static let background = Color(uiColor: .systemGroupedBackground)
    /// `--cell`: list rows, tiles, the month pill.
    static let cell = Color(uiColor: .secondarySystemGroupedBackground)
    /// `--label`
    static let label = Color.primary
    /// `--l2`
    static let labelSecondary = Color.secondary
    /// `--l3`: also the disabled state of a tinted control.
    static let labelTertiary = Color(uiColor: .tertiaryLabel)
    /// `--sep`
    static let separator = Color(uiColor: .separator)
    /// `--fill`
    static let fill = Color(uiColor: .systemFill)
    /// `--fill2`
    static let fillSecondary = Color(uiColor: .secondarySystemFill)

    /// `--green`: income.
    static let income = Color.green
    /// `--red`: expense.
    static let expense = Color.red
    /// `--orange`: needs attention — pending, needs review.
    static let warning = Color.orange

    // MARK: Hero

    /// The balance card is an opaque gradient, not glass: a translucent surface would make
    /// the balance unreadable over scrolling content.
    ///
    /// `screens.css:8` specifies a radial highlight from the top-right fading out at 60%,
    /// over a solid base — hence the elliptical gradient, whose radii are fractions of the
    /// frame, rather than a linear one.
    static var heroSurface: some View {
        ZStack {
            Color("heroBase")
            EllipticalGradient(
                colors: [Color("heroHighlight"), .clear],
                center: .topTrailing,
                startRadiusFraction: 0,
                endRadiusFraction: 0.6
            )
        }
    }

    static let heroShadow = Color("heroShadow")
    /// Content on the hero sits on a dark purple field in both appearances.
    static let onHero = Color.white

    /// The KPI dots on the hero, which sit on that fixed dark field and so are fixed too.
    enum HeroKPI {
        static let expense = Color(red: 1.0, green: 0.541, blue: 0.502)   // #ff8a80
        static let income = Color(red: 0.373, green: 0.878, blue: 0.541)  // #5fe08a
        static let warning = Color(red: 1.0, green: 0.753, blue: 0.302)   // #ffc04d
    }
}
