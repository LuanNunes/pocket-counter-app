import SwiftUI

/// The face of a chip: the interactive ones in the review's strip and the static ones in the
/// "Entendi" card.
struct QuickAddChipLabel: View {
    enum Look {
        case plain
        case selected
        case income
    }

    enum Leading: Equatable {
        case none
        case dot(UInt32?)
        case symbol(String)
    }

    let text: String
    var look = Look.plain
    var leading = Leading.none

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        HStack(spacing: PocketMetrics.modeChipSpacing) {
            if look == .selected {
                Image(systemName: "checkmark")
                    .font(.footnote.bold())
                    .accessibilityHidden(true)
            }
            leadingView
            Text(verbatim: text)
                .pocketFont(PocketFont.link)
                .lineLimit(typeSize.isAccessibilitySize ? 3 : 1)
                .truncationMode(.tail)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, PocketMetrics.modeChipPaddingH)
        .frame(minHeight: PocketMetrics.modeChipHeight)
        .background(fill, in: .rect(cornerRadius: PocketMetrics.modeChipHeight / 2, style: .continuous))
    }

    @ViewBuilder
    private var leadingView: some View {
        switch leading {
        case .none:
            EmptyView()
        case .dot(let color):
            LedgerDot(argb: color, size: PocketMetrics.groupDot)
        case .symbol(let name):
            Image(systemName: name)
                .font(.footnote)
                .accessibilityHidden(true)
        }
    }

    private var ink: Color {
        switch look {
        case .plain: PocketColor.label
        case .selected: PocketColor.tintInk
        case .income: PocketColor.incomeInk
        }
    }

    private var fill: Color {
        switch look {
        case .plain: PocketColor.fill
        case .selected: PocketColor.tintSoft
        case .income: PocketColor.incomeSoft
        }
    }
}

/// A chip that applies its change. The face is 30pt; the target is 44.
struct QuickAddChipButton: View {
    let chip: QuickAddChip
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            QuickAddChipLabel(text: chip.label, look: chip.isOn ? .selected : .plain, leading: leading)
                .padding(.vertical, PocketMetrics.chipHitPaddingV)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spokenLabel)
        .accessibilityAddTraits(chip.isOn ? [.isButton, .isSelected] : .isButton)
    }

    private var isCard: Bool {
        guard case .card = chip.change else { return false }
        return true
    }

    private var leading: QuickAddChipLabel.Leading {
        guard !isCard else { return .symbol("creditcard") }
        guard case .tag = chip.change else { return .none }
        return .dot(chip.color)
    }

    private var spokenLabel: String {
        isCard ? QuickAddCopy.cardPrefix + chip.label : chip.label
    }
}
