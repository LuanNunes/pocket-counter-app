import Foundation

@testable import PocketCounter

/// Scripted responses for `HTTPSend`. Receives the fully built `URLRequest`.
final class FakeHTTP: @unchecked Sendable {
    enum Reply {
        case response(status: Int, body: Data)
        case failure(any Error)
    }

    private let lock = NSLock()
    private var replies: [Reply]
    private var routes: [String: Reply]
    private var recorded: [URLRequest] = []

    init(_ replies: Reply...) {
        self.replies = replies
        routes = [:]
    }

    /// Answers by request path, so concurrent requests need no agreed order.
    init(routes: [String: Reply]) {
        replies = []
        self.routes = routes
    }

    func reply(to path: String, with reply: Reply) {
        lock.withLock { routes[path] = reply }
    }

    func count(path: String) -> Int {
        requests.filter { $0.url?.path == path }.count
    }

    static func json(_ body: String, status: Int = 200) -> Reply {
        .response(status: status, body: Data(body.utf8))
    }

    static func empty(_ status: Int) -> Reply { .response(status: status, body: Data()) }

    var requests: [URLRequest] { lock.withLock { recorded } }
    var callCount: Int { requests.count }

    var send: HTTPSend {
        { [self] request in
            let reply = lock.withLock { () -> Reply in
                recorded.append(request)
                if let routed = routes[request.url?.path ?? ""] { return routed }
                if replies.isEmpty { return .response(status: 404, body: Data()) }
                return replies.count > 1 ? replies.removeFirst() : replies[0]
            }
            switch reply {
            case .failure(let error):
                throw error
            case .response(let status, let body):
                let url = request.url ?? URL(fileURLWithPath: "/")
                let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)
                return (body, response ?? HTTPURLResponse())
            }
        }
    }
}
