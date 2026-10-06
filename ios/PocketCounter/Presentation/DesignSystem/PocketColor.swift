import SwiftUI
import UIKit

/// Color tokens, mapped from `docs/ios26/glass.css` (lines 11-15).
///
/// Most are Apple's semantic colors, which is what dark mode, Increased Contrast, Reduce
/// Transparency and the Liquid Glass blend are built on. Only the purple tints and the hero
/// are custom; they live in the asset catalog, converted once from the spec's OKLCH. Never
/// hand-tweak one — regenerate it from the OKLCH source.
enum PocketColor {

    static let tint = Color("tint")
    static let tintSoft = Color("tintSoft")
    static let tintInk = Color("tintInk")

    static let background = Color(uiColor: .systemGroupedBackground)
    /// List rows, tiles, the month pill.
    static let cell = Color(uiColor: .secondarySystemGroupedBackground)
    static let label = Color.primary
    static let labelSecondary = Color.secondary
    /// Also the disabled state of a tinted control.
    static let labelTertiary = Color(uiColor: .tertiaryLabel)
    static let separator = Color(uiColor: .separator)
    static let fill = Color(uiColor: .systemFill)
    static let fillSecondary = Color(uiColor: .secondarySystemFill)

    // MARK: Values

    /// `--green` and `--orange` fill dots and badges. Their `-ink` pair is the same hue
    /// darkened for *text*: `systemGreen` on `cell` is 2.22:1 in light mode, the ink 4.40:1.
    static let income = Color.green
    static let warning = Color.orange
    static let incomeInk = Color("incomeInk")
    static let warningInk = Color("warningInk")

    /// `.exp` is `--label`: an expense is the default case, so its value is not colored.
    static let expense = Color.primary

    /// `--red` darkened for **text**, the third ink pair. systemRed on `cell` measures
    /// 3.6:1 in light mode, which fails AA — the same trap `incomeInk` and `warningInk`
    /// exist for. Light is Apple's increased-contrast systemRed rather than a new hue.
    static let destructiveInk = Color("destructiveInk")

    /// `--red`, reserved for destructive actions — `.btn.dst`, `.mi.dst`, `.catc-del`.
    static let destructive = Color.red

    // MARK: Hero

    /// The balance card is an opaque gradient, not glass: a translucent surface would make
    /// the balance unreadable over scrolling content. `screens.css:8` specifies a radial
    /// highlight from the top-right fading out at 60% over a solid base, hence the
    /// elliptical gradient whose radii are fractions of the frame.
    static var heroSurface: some View {
        ZStack {
            heroBase
            EllipticalGradient(
                colors: [Color("heroHighlight"), .clear],
                center: .topTrailing,
                startRadiusFraction: 0,
                endRadiusFraction: 0.6
            )
        }
    }

    /// The gradient's solid base, flat. Also the ink for a label on a white fill over the
    /// hero, which is the only readable primary button there: 11.52:1.
    static let heroBase = Color("heroBase")

    static let heroShadow = Color("heroShadow")

    /// Content on a tint-filled control.
    static let onTint = Color.white

    /// The hero is a dark purple field in both appearances, so content over it is fixed.
    static let onHero = Color.white
    static let onHeroExpense = Color("onHeroExpense")
    static let onHeroIncome = Color("onHeroIncome")
    static let onHeroWarning = Color("onHeroWarning")

    // MARK: - Google sign-in

    // Google's dark variant: sRGB (not display-P3 like the rest of this file), fixed in both
    // appearances, never recoloured. The white variant would compete with the primary button.

    /// #E3E3E3 label. 14.47:1 on `googleButtonSurface`.
    static let googleButtonInk = Color("googleButtonInk")

    /// #131314 body, opaque. Never `.glassEffect` — Google fixes this colour.
    static let googleButtonSurface = Color("googleButtonSurface")

    /// #8E918F. Google's mandated 1pt border, 3.62:1 on `heroBase` — load-bearing, since the
    /// body alone is 1.61:1. Falls to 2.09:1 on `heroHighlight`, so keep the button low.
    static let googleButtonBorder = Color("googleButtonBorder")
}
