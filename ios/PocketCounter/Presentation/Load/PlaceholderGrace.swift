import SwiftUI

extension View {
    /// Raises `elapsed` once a first load outlasts the grace period, and lowers it when the load ends.
    /// Time belongs to the view: in a model it would make tests sleep.
    func placeholderGrace(isFirstLoad: Bool, elapsed: Binding<Bool>) -> some View {
        task(id: isFirstLoad) {
            guard isFirstLoad else {
                elapsed.wrappedValue = false
                return
            }
            do { try await Task.sleep(for: PocketMotion.indicatorGrace) } catch { return }
            withAnimation(PocketMotion.quick) { elapsed.wrappedValue = true }
        }
    }
}
