import SwiftUI

/// `.dot`: a tag or category colour, or a neutral when it has none. No palette is invented here.
struct LedgerDot: View {
    let argb: UInt32?
    let size: CGFloat

    var body: some View {
        Circle()
            .fill(argb.map(Color.init(argb:)) ?? PocketColor.labelTertiary)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
