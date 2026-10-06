import Foundation

@testable import PocketCounter

struct FakeTagRepository: TagRepository {
    var tagsResult: Result<[Tag], LoadFailure> = .success([])
    var categoriesResult: Result<[TagContext], LoadFailure> = .success([])
    var rendezvous: Rendezvous?

    func tags() async throws(LoadFailure) -> [Tag] {
        await rendezvous?.arrive()
        return try tagsResult.get()
    }

    func categories() async throws(LoadFailure) -> [TagContext] {
        await rendezvous?.arrive()
        return try categoriesResult.get()
    }

    func tags(in category: ContextID) async throws(LoadFailure) -> [Tag] {
        try tagsResult.get()
    }

    func invalidateLookups() async {}
}
