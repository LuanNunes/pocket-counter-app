import Foundation

protocol TagRepository: LookupCaching {
    func tags() async throws(LoadFailure) -> [Tag]
    func categories() async throws(LoadFailure) -> [TagContext]
    func tags(in category: ContextID) async throws(LoadFailure) -> [Tag]
}
