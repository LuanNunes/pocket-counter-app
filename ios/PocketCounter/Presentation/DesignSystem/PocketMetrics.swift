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

    /// `screens.css:51`, `.qa-dnm { margin-top: 2px }`.
    static let detailTitleSpacing: CGFloat = 2

    /// `tx.jsx:103`, the gap above the delete button.
    static let detailDeleteGap: CGFloat = 14

    /// An empty-state note, `screens.css:93`.
    static let emptyNotePadding = EdgeInsets(top: 40, leading: 20, bottom: 40, trailing: 20)

    // MARK: Transactions

    /// `.txr` — a minimum, never fixed: the row grows with Dynamic Type.
    static let txRowMinHeight: CGFloat = 64
    static let txRowPadding = EdgeInsets(top: 0, leading: 10, bottom: 0, trailing: 12)
    static let txRowSpacing: CGFloat = 10
    /// `.txr{--in:54px}`
    static let txHairlineInset: CGFloat = 54
    /// `.st`, inside a `controlSize` hit target.
    static let statusPillSize: CGFloat = 34
    /// `.mtag`
    static let tagChipHeight: CGFloat = 20
    static let tagChipPaddingH: CGFloat = 8
    static let tagChipSpacing: CGFloat = 5
    static let tagDot: CGFloat = 6
    static let rowMetaSpacing: CGFloat = 6
    /// `.txg-h`
    static let groupHeaderPadding = EdgeInsets(top: 18, leading: 22, bottom: 7, trailing: 22)
    static let groupHeaderSpacing: CGFloat = 7
    static let groupDot: CGFloat = 8
    static let groupCountPadding = EdgeInsets(top: 1, leading: 7, bottom: 1, trailing: 7)
    static let groupCountRadius: CGFloat = 9
    /// `.txsum`
    static let summaryPadding = EdgeInsets(top: 16, leading: 20, bottom: 2, trailing: 20)
    /// `.txfoot`
    static let footerPadding = EdgeInsets(top: 14, leading: 22, bottom: 0, trailing: 22)
    /// `.chip`
    static let modeChipHeight: CGFloat = 30
    static let modeChipPaddingH: CGFloat = 12
    static let modeChipSpacing: CGFloat = 6
    /// `.edit-hint`
    static let hintSpacing: CGFloat = 6
    static let hintPaddingH: CGFloat = 12
    static let hintPaddingV: CGFloat = 8
    static let hintRadius: CGFloat = 14

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
    static let tilesMarginV: CGFloat = 12
    static let tileIconBottomSpacing: CGFloat = 10
    static let tileTextSpacing: CGFloat = 2
    static let iconTileSize: CGFloat = 30
    static let iconTileRadius: CGFloat = 8

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
    /// `screens.css:2`
    static let monthPillInnerPadding: CGFloat = 4
    static let monthPillTopPadding: CGFloat = 4
    static let monthPillBottomPadding: CGFloat = 14
    /// `.mp-l` gap, `screens.css:5`
    static let monthPillLabelSpacing: CGFloat = 6

    /// `.qa-home-b`
    static let quickAddHeight: CGFloat = 52
    static let quickAddRadius: CGFloat = 26
    static let quickAddIconSize: CGFloat = 36
    static let quickAddRing: CGFloat = 1.5
    static let quickAddSpacing: CGFloat = 8
    static let quickAddInnerPadding: CGFloat = 8
    static let quickAddIconSpacing: CGFloat = 10
    static let quickAddBottomPadding: CGFloat = 14

    /// `.btn` — the full-width primary button. Equal to `quickAdd*` by coincidence, not by
    /// derivation, so the two are not aliased.
    static let primaryButtonHeight: CGFloat = 52
    static let primaryButtonRadius: CGFloat = 26

    // MARK: Quick add

    /// `.chips{gap:7px}`, `glass.css:148`
    static let chipGap: CGFloat = 7
    /// 30 + 2 × 7 = 44, the minimum touch target.
    static let chipHitPaddingV: CGFloat = 7
    /// `.qa-field`, `screens.css:32`
    static let entryRadius: CGFloat = 24
    static let entryPadding = EdgeInsets(top: 14, leading: 16, bottom: 14, trailing: 16)
    static let entryRing: CGFloat = 2
    /// `.qa-read`, `screens.css:41`
    static let readCardRadius: CGFloat = 22
    static let readCardPadding: CGFloat = 12
    static let readCardSpacing: CGFloat = 6
    /// `.qa-q` margin, `screens.css:44`
    static let questionPadding = EdgeInsets(top: 18, leading: 4, bottom: 10, trailing: 4)
    /// `.qa-amt`, `screens.css:45`
    static let amountFieldRadius: CGFloat = 22
    static let amountFieldPadding = EdgeInsets(top: 12, leading: 18, bottom: 12, trailing: 18)
    static let amountFieldSpacing: CGFloat = 8
    /// `.qa-orb`, `screens.css:48`
    static let receiptOrb: CGFloat = 58
    /// `.qa-done`, `screens.css:47`
    static let receiptPadding = EdgeInsets(top: 4, leading: 20, bottom: 6, trailing: 20)
    /// `.qa-damt` margin, `screens.css:50`
    static let receiptAmountGap: CGFloat = 10
    /// `.qa-rr.open` — the opacity of `tint` behind the open row.
    static let reviewOpenWash: Double = 0.04
    /// `.qa-opts`, `screens.css:61`
    static let reviewStripPadding = EdgeInsets(top: 0, leading: 16, bottom: 14, trailing: 16)
    /// `.foot-note`, `glass.css:92`
    static let footNotePadding = EdgeInsets(top: 8, leading: 32, bottom: 0, trailing: 32)
    /// `.qa-rv` flex gap and `margin-top`
    static let valueLineGap: CGFloat = 7
    static let valueLineVGap: CGFloat = 4
    /// The footer's two buttons.
    static let footerButtonGap: CGFloat = 10
    static let footerContainerSpacing: CGFloat = 8
    static let footerTopPadding: CGFloat = 8

    // MARK: Fields

    /// A field row is taller than the read-only `rowMinHeight`: it carries a caret and a
    /// keyboard, and 52 leaves a text field feeling cramped.
    static let fieldRowMinHeight: CGFloat = 56
    static let fieldRowPaddingV: CGFloat = 14
    /// The leading glyph of a field row.
    static let fieldIconSize: CGFloat = 20

    /// Vertical breathing room above a full-screen form, past the safe area.
    static let formContentInset: CGFloat = 48
}
