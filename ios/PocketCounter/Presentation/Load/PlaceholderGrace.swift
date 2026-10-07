import SwiftUI

extension View {
    /// Raises `elapsed` once an activity outlasts the grace period, and lowers it when it ends.
    /// Time belongs to the view: in a model it would make tests sleep.
    func graced(isActive: Bool, elapsed: Binding<Bool>) -> some View {
        task(id: isActive) {
            guard isActive else {
                elapsed.wrappedValue = false
                return
            }
            do { try await Task.sleep(for: PocketMotion.indicatorGrace) } catch { return }
            withAnimation(PocketMotion.quick) { elapsed.wrappedValue = true }
        }
    }
}
