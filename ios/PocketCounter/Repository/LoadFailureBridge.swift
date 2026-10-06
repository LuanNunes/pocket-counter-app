import Foundation

extension AuthenticatedAPIClient {
    func load<R>(_ endpoint: Endpoint<R>) async throws(LoadFailure) -> R {
        do {
            return try await send(endpoint)
        } catch {
            throw LoadFailure(error)
        }
    }
}

extension Array {
    func mappedOrFailing<T>(_ transform: (Element) throws(MappingFailure) -> T) throws(LoadFailure) -> [T] {
        var mapped: [T] = []
        for element in self {
            do {
                mapped.append(try transform(element))
            } catch {
                throw LoadFailure(error)
            }
        }
        return mapped
    }
}
