import SwiftUI

/// The grouped card the design uses for every list — `.list` in `screens.css`.
///
/// Not `List`: the native inset-grouped style rounds at roughly 10pt and the design asks for
/// 26, and these sections are short enough that a `LazyVStack` inside the screen's scroll
/// view is simpler than fighting `List` configuration.
struct PocketListSection<Content: View>: View {
    var header: String?
    var accessory: (title: String, action: () -> Void)?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let header {
                HStack(alignment: .firstTextBaseline) {
                    Text(header)
                        .pocketFont(PocketFont.sectionTitle, tracking: PocketFont.sectionTitleTracking)

                    Spacer(minLength: 8)

                    if let accessory {
                        Button(accessory.title, action: accessory.action)
                            .font(PocketFont.link)
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

/// A row inside a `PocketListSection` — `.row`.
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
                    .pocketFont(PocketFont.body, tracking: PocketFont.bodyTracking)

                if let subtitle {
                    Text(subtitle)
                        .font(PocketFont.rowSubtitle)
                        .foregroundStyle(PocketColor.labelSecondary)
                }
            }

            Spacer(minLength: 8)

            trailing
        }
        .padding(.horizontal, PocketMetrics.rowPaddingH)
        .padding(.vertical, PocketMetrics.rowPaddingV)
        .frame(minHeight: PocketMetrics.rowMinHeight)
    }
}

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
            .frame(height: 0.5)
            .padding(.leading, inset)
    }
}

#Preview("List section") {
    ScrollView {
        PocketListSection(header: "Transações", accessory: ("Ver tudo", {})) {
            PocketRow(title: "Mercado", subtitle: "Cartão · crédito") {
                Text(PocketFormat.currency(Decimal(string: "-184.90")!))
                    .pocketFont(PocketFont.body, tabularFigures: true)
                    .foregroundStyle(PocketColor.expense)
            }
            PocketRowSeparator()
            PocketRow(title: "Salário", subtitle: "Pix") {
                Text(PocketFormat.currency(Decimal(string: "7200")!))
                    .pocketFont(PocketFont.body, tabularFigures: true)
                    .foregroundStyle(PocketColor.income)
            }
        }
    }
    .background(PocketColor.background)
}
