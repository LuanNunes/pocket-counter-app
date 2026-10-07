import SwiftUI

/// The scroll, background and large title every tab root and pushed screen shares.
///
/// Owns no padding (components carry their own margins), no `NavigationStack` and no load state.
/// Eager on purpose: a lazy stack defers children, which breaks the skeleton crossfade. A long
/// list makes its own rows lazy.
/// The tab bar is hidden by the destination, never here: Cartões is a tab and uses this too.
struct Screen<Content: View, Bar: ToolbarContent>: View {
    let title: String
    var subtitle: String?
    var onRefresh: (@Sendable () async -> Void)?
    @ToolbarContentBuilder var toolbar: () -> Bar
    @ViewBuilder var content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                content()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(PocketColor.background)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.large)
        .scrollDismissesKeyboard(.interactively)
        .toolbar { toolbar() }
        .subtitled(subtitle)
        .refreshable(onRefresh)
    }
}

extension Screen where Bar == ToolbarItemGroup<EmptyView> {
    init(
        title: String,
        subtitle: String? = nil,
        onRefresh: (@Sendable () async -> Void)? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.init(
            title: title, subtitle: subtitle, onRefresh: onRefresh,
            toolbar: { return ToolbarItemGroup { EmptyView() } }, content: content
        )
    }
}

private extension View {
    @ViewBuilder
    func subtitled(_ subtitle: String?) -> some View {
        if let subtitle {
            navigationSubtitle(subtitle)
        } else {
            self
        }
    }

    /// Conditional rather than a no-op: an empty `.refreshable` still shows the pull affordance.
    @ViewBuilder
    func refreshable(_ action: (@Sendable () async -> Void)?) -> some View {
        if let action {
            refreshable(action: action)
        } else {
            self
        }
    }
}
