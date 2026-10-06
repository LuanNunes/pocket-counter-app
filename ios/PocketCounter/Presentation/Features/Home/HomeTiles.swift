import SwiftUI

struct HomeTiles: View {
    let summary: HomeSummary
    let onAction: (HomeAction) -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize {
                VStack(spacing: PocketMetrics.tileSpacing) { tiles }
            } else {
                HStack(spacing: PocketMetrics.tileSpacing) { tiles }
            }
        }
        .padding(.horizontal, PocketMetrics.screenMargin)
    }

    @ViewBuilder
    private var tiles: some View {
        NavigationLink(value: HomeRoute.report) {
            tile(symbol: "chart.bar.fill", fill: PocketColor.tint, glyph: PocketColor.onTint,
                 key: "Relatório", value: "Para onde foi")
        }
        .buttonStyle(PocketCardButtonStyle(radius: PocketMetrics.tileRadius))

        Button { onAction(.showCards) } label: {
            tile(symbol: "creditcard.fill", fill: PocketColor.fill, glyph: PocketColor.labelSecondary,
                 key: "Faturas · \(HomeCopy.cardCount(summary.openInvoices.cardCount))", value: invoiceTotal)
                .accessibilityLabel("Faturas, \(HomeCopy.cardCountLabel(summary.openInvoices.cardCount))")
                .accessibilityValue(summary.openInvoices.total == nil ? "Total indisponível" : invoiceTotal)
        }
        .buttonStyle(PocketCardButtonStyle(radius: PocketMetrics.tileRadius))
    }

    private var invoiceTotal: String {
        summary.openInvoices.total.map { PocketFormat.currency($0.amount, signed: false) } ?? HomeCopy.unknownFigure
    }

    private func tile(symbol: String, fill: Color, glyph: Color, key: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: PocketMetrics.tileIconBottomSpacing) {
            Image(systemName: symbol)
                .pocketFont(PocketFont.controlLabel)
                .foregroundStyle(glyph)
                .frame(width: PocketMetrics.iconTileSize, height: PocketMetrics.iconTileSize)
                .background(fill, in: .rect(cornerRadius: PocketMetrics.iconTileRadius))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: PocketMetrics.tileTextSpacing) {
                Text(key)
                    .pocketFont(PocketFont.caption)
                    .foregroundStyle(PocketColor.labelSecondary)
                    .multilineTextAlignment(.leading)

                Text(value)
                    .pocketFont(PocketFont.valueEmphasis, tabularFigures: true)
                    .foregroundStyle(PocketColor.label)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .padding(PocketMetrics.tilePadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(PocketColor.cell, in: .rect(cornerRadius: PocketMetrics.tileRadius))
        .contentShape(.rect(cornerRadius: PocketMetrics.tileRadius))
        .accessibilityElement(children: .combine)
    }
}
