import SwiftUI

/// The grouped card the design uses for every list — `.list` in `glass.css`.
///
/// Not `List`: the native inset-grouped style rounds at roughly 10pt where the spec asks for
/// 26, and these sections are short enough to sit in the screen's own scroll view.
struct PocketListSection<Content: View>: View {
    var header: String?
    var linkTitle: String?
    var linkAction: (() -> Void)?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let header {
                HStack(alignment: .firstTextBaseline) {
                    Text(header)
                        .pocketFont(PocketFont.sectionTitle)

                    Spacer(minLength: 8)

                    if let linkTitle, let linkAction {
                        Button(linkTitle, action: linkAction)
                            .pocketFont(PocketFont.link)
                            .foregroundStyle(PocketColor.tint)
                    }
                }
                .padding(PocketMetrics.sectionHeaderPadding)
            }

            VStack(spacing: 0) {
                content
            }
            .background(PocketColor.cell, in: .rect(cornerRadius: PocketMetrics.listRadius))
            .padding(.horizontal, PocketMetrics.screenMargin)
        }
    }
}

/// A `PocketListSection` over a collection, placing the hairlines itself so a caller cannot
/// forget one. The spec expresses it as an adjacency rule (`glass.css`, `.row + .row::before`),
/// which makes it the container's job rather than the caller's.
struct PocketList<Item: Identifiable, Row: View>: View {
    var header: String?
    var linkTitle: String?
    var linkAction: (() -> Void)?
    var items: [Item]
    @ViewBuilder var row: (Item) -> Row

    var body: some View {
        PocketListSection(header: header, linkTitle: linkTitle, linkAction: linkAction) {
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                if index > 0 {
                    PocketRowSeparator()
                }

                row(item)
            }
        }
    }
}

/// A row inside a `PocketListSection` — `.row`.
///
/// Reads as a single VoiceOver element: three separate stops per row would make a screen
/// that is nothing but rows exhausting to navigate.
struct PocketRow<Leading: View, Trailing: View>: View {
    var title: String
    var subtitle: String?
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: PocketMetrics.rowSpacing) {
            leading

            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .pocketFont(PocketFont.body)

                if let subtitle {
                    Text(subtitle)
                        .pocketFont(PocketFont.caption)
                        .foregroundStyle(PocketColor.labelSecondary)
                }
            }

            Spacer(minLength: 8)

            trailing
        }
        .padding(.horizontal, PocketMetrics.rowPaddingH)
        .padding(.vertical, PocketMetrics.rowPaddingV)
        .frame(minHeight: PocketMetrics.rowMinHeight)
        .accessibilityElement(children: .combine)
    }
}

/// Only the trailing shorthand exists: a matching `leading:`-only initialiser makes every
/// trailing-closure call ambiguous, since both would be a single closure in last position.
/// A row with a leading view spells out both.
extension PocketRow where Leading == EmptyView {
    init(title: String, subtitle: String? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, subtitle: subtitle, leading: { EmptyView() }, trailing: trailing)
    }
}

extension PocketRow where Leading == EmptyView, Trailing == EmptyView {
    init(title: String, subtitle: String? = nil) {
        self.init(title: title, subtitle: subtitle, leading: { EmptyView() }, trailing: { EmptyView() })
    }
}

/// Hairline between rows, inset past the row's leading padding the way iOS does it.
struct PocketRowSeparator: View {
    var inset: CGFloat = PocketMetrics.rowPaddingH

    var body: some View {
        Rectangle()
            .fill(PocketColor.separator)
            .frame(height: PocketMetrics.hairline)
            .padding(.leading, inset)
    }
}

/// An amount in a row's trailing position: tabular so digits do not jitter, and colored by
/// what it is — `.inc` is the income ink, `.wrn` the warning ink, `.exp` the default label.
struct PocketAmount: View {
    enum Kind { case income, expense, pending }

    var value: Decimal
    var kind: Kind

    var body: some View {
        Text(PocketFormat.currency(value))
            .pocketFont(PocketFont.body, tabularFigures: true)
            .foregroundStyle(color)
            .accessibilityValue(PocketFormat.spokenCurrency(value))
    }

    private var color: Color {
        switch kind {
        case .income: PocketColor.incomeInk
        case .pending: PocketColor.warningInk
        case .expense: PocketColor.expense
        }
    }
}

#Preview("List section") {
    ScrollView {
        PocketListSection(header: "Transações", linkTitle: "Ver tudo", linkAction: {}) {
            PocketRow(title: "Mercado", subtitle: "Cartão · crédito") {
                PocketAmount(value: PreviewMoney.groceries, kind: .expense)
            }
            PocketRowSeparator()
            PocketRow(title: "Salário", subtitle: "Pix") {
                PocketAmount(value: PreviewMoney.salary, kind: .income)
            }
        }
    }
    .background(PocketColor.background)
}
