import SwiftUI

/// Spacing, radii and control sizes, read from `docs/ios26/screens.css` and `glass.css`.
///
/// The prototype's `402×874` is its device frame, not a layout constraint — nothing here
/// assumes a screen size.
enum PocketMetrics {

    /// Horizontal inset shared by the hero, lists and tiles (`.list`, `.hero`: `margin 0 16px`).
    static let screenMargin: CGFloat = 16

    /// `.5px` separators and borders throughout the spec.
    static let hairline: CGFloat = 0.5

    // MARK: Lists

    /// `.list` — note this is far rounder than the native inset-grouped radius.
    static let listRadius: CGFloat = 26
    /// `.row { min-height: 52px }`
    static let rowMinHeight: CGFloat = 52
    static let rowPaddingH: CGFloat = 16
    static let rowPaddingV: CGFloat = 10
    static let rowSpacing: CGFloat = 12

    /// `.sec-h { padding: 22px 22px 8px }`
    static let sectionHeaderPadding = EdgeInsets(top: 22, leading: 22, bottom: 8, trailing: 22)

    // MARK: Hero

    static let heroRadius: CGFloat = 30
    static let heroPadding = EdgeInsets(top: 22, leading: 22, bottom: 10, trailing: 22)
    /// `.kd`
    static let heroKpiDot: CGFloat = 8
    static let heroKpiPaddingV: CGFloat = 10

    // MARK: Tiles

    /// `.tile`
    static let tileRadius: CGFloat = 24
    static let tilePadding: CGFloat = 14
    static let tileSpacing: CGFloat = 12

    // MARK: Controls

    /// `.gbtn`, `.gcap` — also the minimum comfortable touch target.
    static let controlSize: CGFloat = 44
    /// Always half `controlSize` — derived so the two cannot drift apart.
    static var controlRadius: CGFloat { controlSize / 2 }

    /// `.gcap { padding: 0 2px }` and `.gcap>button { min-width: 42px }`.
    static let capsulePadding: CGFloat = 2
    static let capsuleButtonMinWidth: CGFloat = 42

    /// `.badge`, `.mp-l em`
    static let badgePadding = EdgeInsets(top: 2, leading: 7, bottom: 2, trailing: 7)
    static let badgeRadius: CGFloat = 8

    /// `.mpill`
    static let monthPillHeight: CGFloat = 44
    static let monthPillRadius: CGFloat = 22
    static let monthPillButton: CGFloat = 40

    /// `.qa-home-b`
    static let quickAddHeight: CGFloat = 52
    static let quickAddRadius: CGFloat = 26
}
