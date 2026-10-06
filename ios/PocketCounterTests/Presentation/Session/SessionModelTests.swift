import Testing

@testable import PocketCounter

@MainActor
@Suite("SessionModel")
struct SessionModelTests {

    private let user = AuthenticatedUser.fixture

    private func credentials() throws -> LoginCredentials {
        try LoginCredentials(email: "ana@b.com", password: "secret")
    }

    private func signedIn() async throws -> (SessionModel, FakeSessionRepository) {
        let fake = FakeSessionRepository(signIn: .success(user))
        let model = SessionModel(repository: fake)
        try await model.signIn(credentials())
        return (model, fake)
    }

    @Test("registering signs the user in, moving the gate exactly as a sign-in does")
    func registerMovesTheGate() async throws {
        let fake = FakeSessionRepository(restores: [.signedOut], signIn: .success(user))
        let model = SessionModel(repository: fake)

        try await model.register(Registration(name: "Ana", email: "ana@b.com", password: "segredo12"))

        #expect(model.state.gate == .signedIn(user))
    }

    @Test("a failed registration rethrows and leaves the gate alone")
    func registerFailureLeavesTheGate() async throws {
        let fake = FakeSessionRepository(restores: [.signedOut], signIn: .failure(.emailAlreadyRegistered))
        let model = SessionModel(repository: fake)
        await model.resolve()

        let input = try Registration(name: "Ana", email: "ana@b.com", password: "segredo12")
        await #expect(throws: AuthenticationFailure.emailAlreadyRegistered) {
            try await model.register(input)
        }
        #expect(model.state.gate == .signedOut)
    }

    @Test("a new model has not resolved anything")
    func initial() {
        let model = SessionModel(repository: FakeSessionRepository())

        #expect(model.state == SessionState())
        #expect(model.state.gate == .resolving)
    }

    @Test("resolve maps every session status to its gate", arguments: [
        (SessionStatus.signedOut, SessionState.Gate.signedOut),
        (.signedIn(.fixture), .signedIn(.fixture)),
        (.undetermined, .undetermined),
    ])
    func resolveMaps(status: SessionStatus, gate: SessionState.Gate) async {
        let model = SessionModel(repository: FakeSessionRepository(restores: [status]))

        await model.resolve()

        #expect(model.state.gate == gate)
        #expect(!model.state.isResolving)
    }

    @Test("a second resolve while one is in flight does not ask the repository again")
    func resolveReentry() async {
        let fake = FakeSessionRepository(restores: [.signedOut])
        await fake.hold()
        let model = SessionModel(repository: fake)

        let first = Task { await model.resolve() }
        await fake.untilRestoreIsSuspended()
        #expect(model.state.isResolving)
        await model.resolve()
        await fake.release()
        await first.value

        #expect(await fake.restoreCount == 1)
        #expect(!model.state.isResolving)
    }

    @Test("a retry from undetermined keeps that gate while in flight, then commits the answer")
    func retryFromUndetermined() async {
        let fake = FakeSessionRepository(restores: [.undetermined, .signedIn(.fixture)])
        let model = SessionModel(repository: fake)
        await model.resolve()
        await fake.hold()

        let retry = Task { await model.resolve() }
        await fake.untilRestoreIsSuspended()
        #expect(model.state.gate == .undetermined)
        #expect(model.state.isResolving)
        await fake.release()
        await retry.value

        #expect(model.state.gate == .signedIn(.fixture))
        #expect(!model.state.isResolving)
    }

    @Test("failed resolves are counted, the escape is offered from the second, and a conclusive answer resets them")
    func failedAttempts() async {
        let fake = FakeSessionRepository(restores: [.undetermined, .undetermined, .signedOut])
        let model = SessionModel(repository: fake)

        await model.resolve()
        #expect(model.state.failedResolveAttempts == 1)
        #expect(!model.state.offersEscape)
        await model.resolve()
        #expect(model.state.failedResolveAttempts == 2)
        #expect(model.state.offersEscape)
        await model.resolve()

        #expect(model.state.failedResolveAttempts == 0)
        #expect(!model.state.offersEscape)
    }

    @Test("a restore that answers after the user chose the password path is dropped")
    func staleRestoreIsDropped() async {
        let fake = FakeSessionRepository(restores: [.undetermined, .undetermined, .signedIn(.fixture)])
        let model = SessionModel(repository: fake)
        await model.resolve()
        await model.resolve()
        await fake.hold()

        let retry = Task { await model.resolve() }
        await fake.untilRestoreIsSuspended()
        model.preferPassword()
        await fake.release()
        await retry.value

        #expect(model.state.gate == .undetermined)
        #expect(model.state.failedResolveAttempts == 2)
        #expect(!model.state.isResolving)
    }

    @Test("a sign-in that lands clears the password preference")
    func signInClearsThePreference() async throws {
        let fake = FakeSessionRepository(signIn: .success(user))
        let model = SessionModel(repository: fake)
        model.preferPassword()

        try await model.signIn(credentials())

        #expect(model.state.gate == .signedIn(user))
        #expect(!model.state.prefersPassword)
    }

    @Test("a registration that lands clears the password preference")
    func registerClearsThePreference() async throws {
        let fake = FakeSessionRepository(signIn: .success(user))
        let model = SessionModel(repository: fake)
        model.preferPassword()

        try await model.register(Registration(name: "Ana", email: "ana@b.com", password: "segredo12"))

        #expect(!model.state.prefersPassword)
    }

    @Test("cancelling the escape returns to the splash, keeping the failed attempts, and resolving works again")
    func cancelPasswordEscape() async {
        let fake = FakeSessionRepository(restores: [.undetermined, .undetermined, .signedIn(.fixture)])
        let model = SessionModel(repository: fake)
        await model.resolve()
        await model.resolve()
        model.preferPassword()

        model.cancelPasswordEscape()

        #expect(!model.state.prefersPassword)
        #expect(model.state.gate == .undetermined)
        #expect(model.state.offersEscape)
        await model.resolve()
        #expect(model.state.gate == .signedIn(.fixture))
    }

    @Test("a successful sign-in moves the gate to the returned user")
    func signInSuccess() async throws {
        let (model, fake) = try await signedIn()

        #expect(model.state.gate == .signedIn(user))
        #expect(await fake.signInCount == 1)
    }

    @Test("a failed sign-in rethrows the failure and leaves the gate alone")
    func signInFailure() async throws {
        let fake = FakeSessionRepository(restores: [.signedOut], signIn: .failure(.invalidCredentials))
        let model = SessionModel(repository: fake)
        await model.resolve()
        let input = try credentials()

        await #expect(throws: AuthenticationFailure.invalidCredentials) {
            try await model.signIn(input)
        }

        #expect(model.state.gate == .signedOut)
    }

    @Test("signing out ends the session")
    func signOutSuccess() async throws {
        let (model, fake) = try await signedIn()

        await model.signOut()

        #expect(model.state.gate == .signedOut)
        #expect(!model.state.signOutFailed)
        #expect(await fake.signOutCount == 1)
    }

    @Test("a sign-out that fails keeps the user signed in and says so")
    func signOutFailure() async throws {
        let fake = FakeSessionRepository(signIn: .success(user), signOutFailures: [.server])
        let model = SessionModel(repository: fake)
        try await model.signIn(credentials())

        await model.signOut()

        #expect(model.state.gate == .signedIn(user))
        #expect(model.state.signOutFailed)
    }

    @Test("an abandoned sign-out neither moves the gate nor reports a failure")
    func signOutAbandoned() async throws {
        let fake = FakeSessionRepository(signIn: .success(user), signOutFailures: [.abandoned])
        let model = SessionModel(repository: fake)
        try await model.signIn(credentials())

        await model.signOut()

        #expect(model.state.gate == .signedIn(user))
        #expect(!model.state.signOutFailed)
    }

    @Test("a sign-out that succeeds after a failed one clears the failure")
    func signOutFailureCleared() async throws {
        let fake = FakeSessionRepository(signIn: .success(user), signOutFailures: [.server])
        let model = SessionModel(repository: fake)
        try await model.signIn(credentials())
        await model.signOut()
        #expect(model.state.signOutFailed)

        await model.signOut()

        #expect(model.state.gate == .signedOut)
        #expect(!model.state.signOutFailed)
    }

    @Test("sessionEnded signs a signed-in session out")
    func sessionEndedFromSignedIn() async throws {
        let (model, _) = try await signedIn()

        model.sessionEnded()

        #expect(model.state.gate == .signedOut)
    }

    @Test("sessionEnded changes nothing from any other gate", arguments: [
        SessionStatus.signedOut, .undetermined,
    ])
    func sessionEndedElsewhere(status: SessionStatus) async {
        let model = SessionModel(repository: FakeSessionRepository(restores: [status]))
        await model.resolve()
        let before = model.state

        model.sessionEnded()

        #expect(model.state == before)
    }

    @Test("sessionEnded is a no-op while still resolving")
    func sessionEndedWhileResolving() {
        let model = SessionModel(repository: FakeSessionRepository())

        model.sessionEnded()

        #expect(model.state.gate == .resolving)
    }
}
