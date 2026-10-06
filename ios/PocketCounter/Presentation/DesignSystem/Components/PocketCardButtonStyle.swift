import SwiftUI

/// Pressed state for a tappable card: a fill overlay, no scale.
struct PocketCardButtonStyle: ButtonStyle {
    let radius: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .overlay {
                PocketColor.fill
                    .opacity(configuration.isPressed ? 1 : 0)
                    .clipShape(.rect(cornerRadius: radius))
                    .allowsHitTesting(false)
            }
    }
}
