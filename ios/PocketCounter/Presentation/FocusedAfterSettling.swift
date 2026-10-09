import SwiftUI

extension View {
    /// Focusing at once makes the keyboard and the sheet animate together.
    func focusedAfterSettling(_ focus: FocusState<Bool>.Binding, when isEnabled: Bool = true) -> some View {
        task(id: isEnabled) {
            guard isEnabled else { return }
            do { try await Task.sleep(for: .milliseconds(420)) } catch { return }
            focus.wrappedValue = true
        }
    }
}
