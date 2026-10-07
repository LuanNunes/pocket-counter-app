import SwiftUI

/// The sheet over a transaction row: the amount, its facts, and the three things to do to it.
/// Takes no model; the writes live in the ledger, so dismissing mid-flight is safe.
struct TransactionDetailView: View {
    let detail: TransactionDetail
    let onToggleStatus: () -> Void
    let onToggleFixo: () -> Void
    let onDelete: () -> Void
    let onRefresh: () -> Void
    let onClose: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var isConfirming = false
    @State private var showsSpinner = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    header
                    facts
                    deleteSection
                }
                .padding(.bottom, PocketMetrics.screenMargin)
            }
            .background(PocketColor.background)
            .navigationTitle(TransactionsCopy.detailTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .close, action: onClose)
                }
            }
        }
        .graced(isActive: detail.isBusy, elapsed: $showsSpinner)
        .presentationDetents(dynamicTypeSize.isAccessibilitySize ? [.large] : [.medium, .large])
        .presentationDragIndicator(.visible)
        .confirmationDialog(
            TransactionsCopy.deleteConfirmTitle, isPresented: $isConfirming, titleVisibility: .visible
        ) {
            Button(TransactionsCopy.deleteConfirm, role: .destructive, action: onDelete)
            Button(TransactionsCopy.deleteCancel, role: .cancel) {}
        } message: {
            Text(TransactionsCopy.deleteConfirmMessage)
        }
    }

    private var header: some View {
        VStack(spacing: PocketMetrics.detailTitleSpacing) {
            PocketAmount(
                value: detail.amount, kind: detail.kind == .income ? .income : .expense,
                style: .detailAmount, showsDirection: true
            )
            Text(detail.title)
                .pocketFont(PocketFont.valueEmphasis)
                .lineLimit(3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, PocketMetrics.screenMargin)
        .padding(.vertical, PocketMetrics.rowPaddingV)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var facts: some View {
        PocketListSection {
            valueRow(TransactionsCopy.detailDate, detail.dateLabel)
            PocketRowSeparator()
            valueRow(TransactionsCopy.detailPayment, TransactionsCopy.detailValue(detail.payLabel))
            PocketRowSeparator()
            valueRow(TransactionsCopy.detailCategory, TransactionsCopy.detailCategories(detail.tags))
            PocketRowSeparator()
            switchRow(
                TransactionsCopy.detailPaid, isOn: detail.isPaid, isBusy: detail.statusWrite.isBusy,
                action: onToggleStatus)
            notice(detail.statusWrite, retry: onToggleStatus)
            PocketRowSeparator()
            switchRow(
                TransactionsCopy.detailRepeats, subtitle: TransactionsCopy.detailRepeatsHint, isOn: detail.isFixo,
                isBusy: detail.fixoWrite.isBusy, action: onToggleFixo)
            notice(detail.fixoWrite, retry: onToggleFixo)
        }
    }

    private var deleteSection: some View {
        VStack(spacing: 0) {
            PocketPrimaryButton(
                TransactionsCopy.deleteAction, role: .destructive, systemImage: "trash",
                isLoading: detail.deleteWrite.isBusy && showsSpinner
            ) { isConfirming = true }
                .disabled(detail.isBusy)
                .padding(.horizontal, PocketMetrics.screenMargin)
                .padding(.top, PocketMetrics.detailDeleteGap)
            notice(detail.deleteWrite, retry: onDelete)
                .background(PocketColor.cell, in: .rect(cornerRadius: PocketMetrics.listRadius))
                .padding(.horizontal, PocketMetrics.screenMargin)
        }
    }

    private func valueRow(_ title: String, _ value: String) -> some View {
        PocketRow(title: title) {
            Text(value)
                .pocketFont(PocketFont.body)
                .foregroundStyle(PocketColor.labelSecondary)
                .multilineTextAlignment(.trailing)
        }
    }

    private func switchRow(
        _ title: String, subtitle: String? = nil, isOn: Bool, isBusy: Bool, action: @escaping () -> Void
    ) -> some View {
        HStack(spacing: PocketMetrics.rowSpacing) {
            Toggle(isOn: Binding(get: { isOn }, set: { _ in action() })) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).pocketFont(PocketFont.body)
                    if let subtitle {
                        Text(subtitle)
                            .pocketFont(PocketFont.caption)
                            .foregroundStyle(PocketColor.labelSecondary)
                    }
                }
            }
            .disabled(detail.isBusy)
            if isBusy && showsSpinner {
                ProgressView().controlSize(.small)
            }
        }
        .padding(.horizontal, PocketMetrics.rowPaddingH)
        .padding(.vertical, PocketMetrics.rowPaddingV)
        .frame(minHeight: PocketMetrics.rowMinHeight)
    }

    @ViewBuilder
    private func notice(_ write: WriteIndicator, retry: @escaping () -> Void) -> some View {
        if let notice = write.notice {
            PocketInlineMessage(
                kind: notice.kind, text: notice.title, secondary: notice.detail,
                action: write.remedy.map { remedy in
                    .init(title: TransactionsCopy.remedyTitle(remedy)) {
                        switch remedy {
                        case .retry: retry()
                        case .refresh: onRefresh()
                        }
                    }
                },
                surface: .onCell
            )
            .padding(.horizontal, PocketMetrics.rowPaddingH)
            .padding(.bottom, PocketMetrics.rowPaddingV)
        }
    }
}

#if DEBUG
private enum DetailPreview {
    static func detail(
        fixo: Bool = false, bare: Bool = false, status: WriteIndicator = .none, fixoWrite: WriteIndicator = .none,
        deleteWrite: WriteIndicator = .none
    ) -> TransactionDetail {
        let item = TransactionsPreview.items[bare ? 3 : 0]
        let lookups = TransactionsPreview.lookups()
        return TransactionDetail(
            item: item, title: item.displayTitle(), dateLabel: PocketFormat.dayLabel(item.date), kind: item.type,
            amount: item.amount.amount, isPaid: item.statusPayment == .paid, isFixo: fixo,
            payLabel: TransactionRowContent.payLabel(item, lookups: lookups),
            tags: item.effectiveTagIds(inheriting: []).map { TransactionRowContent.chip(for: $0, lookups: lookups) },
            statusWrite: status, fixoWrite: fixoWrite, deleteWrite: deleteWrite)
    }

    static let busy = WriteIndicator(isBusy: true, notice: nil, remedy: nil)

    static func failed(_ target: RowIntent) -> WriteIndicator {
        .of(RowIntentWrite(ref: TransactionsPreview.ref, phase: .failed(.unreachable), attempted: target))
    }

    static let failedStatus = WriteIndicator.of(
        PaymentStatusWrite(ref: TransactionsPreview.ref, phase: .failed(.unreachable)), subject: .saving)

    @MainActor static func sheet(_ detail: TransactionDetail) -> some View {
        Color.clear.sheet(isPresented: .constant(true)) {
            TransactionDetailView(
                detail: detail, onToggleStatus: {}, onToggleFixo: {}, onDelete: {},
                onRefresh: {}, onClose: {})
        }
    }
}

#Preview("Simples") { DetailPreview.sheet(DetailPreview.detail()) }
#Preview("Fixo") { DetailPreview.sheet(DetailPreview.detail(fixo: true)) }
#Preview("Sem tag nem forma") { DetailPreview.sheet(DetailPreview.detail(bare: true)) }
#Preview("Gravando status") { DetailPreview.sheet(DetailPreview.detail(status: DetailPreview.busy)) }
#Preview("Gravando fixo") { DetailPreview.sheet(DetailPreview.detail(fixo: true, fixoWrite: DetailPreview.busy)) }
#Preview("Excluindo") {
    DetailPreview.sheet(DetailPreview.detail(deleteWrite: DetailPreview.busy))
}
#Preview("Falha no status") {
    DetailPreview.sheet(DetailPreview.detail(status: DetailPreview.failedStatus))
}
#Preview("Falha no fixo") {
    DetailPreview.sheet(DetailPreview.detail(fixoWrite: DetailPreview.failed(.fixo(true))))
}
#Preview("Falha ao excluir") {
    DetailPreview.sheet(DetailPreview.detail(deleteWrite: DetailPreview.failed(.deletion)))
}
#Preview("AX5") {
    DetailPreview.sheet(DetailPreview.detail(fixo: true)).environment(\.dynamicTypeSize, .accessibility5)
}
#endif
