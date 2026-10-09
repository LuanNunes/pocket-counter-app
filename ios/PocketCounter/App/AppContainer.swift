import Foundation

/// The composition root.
///
/// Exactly one must exist: a second `TokenRefresher` would not coalesce with the first, and the
/// duplicate refresh 401s and clears the session. `init` is private for that reason.
@MainActor
struct AppContainer {
    private let tokens: KeychainTokenStore
    private let client: APIClient

    /// For repositories that call bearer-authenticated endpoints.
    let authenticatedClient: AuthenticatedAPIClient

    // Stored, never computed: the lookup repositories own a cache, and a fresh instance per access would have none.
    let transactionRepository: any TransactionRepository
    let tagRepository: any TagRepository
    let creditCardRepository: any CreditCardRepository

    static func make(configuration: AppConfiguration) -> AppContainer {
        AppContainer(configuration: configuration, keychain: .live, send: URLSessionHTTPSend.live())
    }

    /// Builds an isolated container over fakes. Production code uses `make(configuration:)`.
    static func forTesting(
        configuration: AppConfiguration,
        keychain: KeychainAccess,
        send: @escaping HTTPSend
    ) -> AppContainer {
        AppContainer(configuration: configuration, keychain: keychain, send: send)
    }

    private init(
        configuration: AppConfiguration,
        keychain: KeychainAccess,
        send: @escaping HTTPSend
    ) {
        let tokens = KeychainTokenStore(scope: configuration.environment.rawValue, access: keychain)
        let client = APIClient(baseURL: configuration.baseURL, send: send)
        let refresher = TokenRefresher(client: client, tokens: tokens)
        self.tokens = tokens
        self.client = client
        let authenticated = AuthenticatedAPIClient(client: client, tokens: tokens, refresher: refresher)
        self.authenticatedClient = authenticated
        transactionRepository = APITransactionRepository(client: authenticated)
        tagRepository = APITagRepository(client: authenticated)
        creditCardRepository = APICreditCardRepository(client: authenticated)
    }

    var loadLedger: LoadLedger {
        LoadLedger(transactions: transactionRepository, tags: tagRepository, cards: creditCardRepository)
    }

    var loadMonth: LoadMonthAction {
        let ledger = loadLedger // read outside the closure: the property is MainActor-isolated, and rebuilt per access
        return { ref throws(LoadFailure) in try await ledger.month(ref) }
    }

    var setPaymentStatus: SetPaymentStatusAction {
        let transactions = transactionRepository
        return { id, status throws(WriteFailure) in try await transactions.setPaymentStatus(status, on: id) }
    }

    var toggleFixo: ToggleFixoAction {
        let toggle = ToggleFixo(recurring: recurringTransactionRepository)
        return { item throws(WriteFailure) in try await toggle.toggle(item) }
    }

    var deleteTransaction: DeleteTransactionAction {
        let transactions = transactionRepository
        return { id throws(WriteFailure) in try await transactions.delete(id) }
    }

    var reorderTransactions: ReorderTransactionsAction {
        let transactions = transactionRepository
        return { ids throws(WriteFailure) in try await transactions.reorder(ids) }
    }

    /// Hands the session model the verb it needs, not the repositories.
    var endSession: SessionEndAction {
        let caches: [any LookupCaching] = [tagRepository, creditCardRepository]
        return { for cache in caches { await cache.invalidateLookups() } }
    }

    // Computed: it holds no cache, so the built-once rule does not apply.
    private var recurringTransactionRepository: any RecurringTransactionRepository { APIRecurringTransactionRepository(client: authenticatedClient) }

    var sessionRepository: any SessionRepository { APISessionRepository(client: client, tokens: tokens) }
}
