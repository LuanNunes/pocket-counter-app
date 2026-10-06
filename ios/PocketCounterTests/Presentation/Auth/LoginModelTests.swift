import Testing

@testable import PocketCounter

/// Stands in for `SessionModel.signIn`: records calls, fails as scripted, and can hold a call open.
@MainActor
private final class SignInRecorder {
    private(set) var calls: [LoginCredentials] = []
    var failures: [AuthenticationFailure] = []
    private var holding = false
    private var waiter: CheckedContinuation<Void, Never>?

    func hold() { holding = true }

    func untilSuspended() async {
        while waiter == nil { await Task.yield() }
    }

    func release() {
        holding = false
        waiter?.resume()
        waiter = nil
    }

    var action: SignInAction {
        { [self] credentials throws(AuthenticationFailure) in
            calls.append(credentials)
            if holding { await withCheckedContinuation { waiter = $0 } }
            guard !failures.isEmpty else { return }
            throw failures.removeFirst()
        }
    }
}

@MainActor
@Suite("LoginModel")
struct LoginModelTests {

    private let recorder = SignInRecorder()

    private func model(failing failures: AuthenticationFailure...) -> LoginModel {
        recorder.failures = failures
        return LoginModel(signIn: recorder.action)
    }

    private func filled(_ model: LoginModel) {
        model.handle(.emailChanged("ana@b.com"))
        model.handle(.passwordChanged("secret"))
    }

    @Test("typing updates the fields")
    func typing() {
        let model = model()

        filled(model)

        #expect(model.state == LoginModel.State(email: "ana@b.com", password: "secret"))
    }

    @Test("blank fields show the credentials message and never reach the closure")
    func blank() async {
        let model = model()
        model.handle(.emailChanged("   "))

        await model.submit()

        #expect(model.state.message == AuthMessage(kind: .error, text: CredentialsError.emptyFields.message))
        #expect(recorder.calls.isEmpty)
        #expect(!model.state.isSubmitting)
    }

    @Test("a successful submit sends the typed credentials and renders nothing")
    func success() async throws {
        let model = model()
        filled(model)

        await model.submit()

        #expect(recorder.calls == [try LoginCredentials(email: "ana@b.com", password: "secret")])
        #expect(model.state.message == nil)
        #expect(!model.state.isSubmitting)
    }

    @Test("a failure surfaces the mapped copy")
    func failure() async {
        let model = model(failing: .unreachable)
        filled(model)

        await model.submit()

        #expect(model.state.message == AuthMessage(kind: .offline, text: "Sem conexão com o servidor"))
    }

    @Test("an abandoned sign-in leaves no message and re-enables the form")
    func abandoned() async {
        let model = model(failing: .abandoned)
        filled(model)

        await model.submit()

        #expect(model.state.message == nil)
        #expect(!model.state.isSubmitting)
    }

    @Test("a presentable server text passes through verbatim")
    func rejected() async {
        let model = model(failing: .rejected("A senha precisa ter ao menos 8 caracteres"))
        filled(model)

        await model.submit()

        #expect(model.state.message?.text == "A senha precisa ter ao menos 8 caracteres")
    }

    @Test("submitting is raised while the call is open, and a second submit is ignored")
    func submitting() async {
        let model = model()
        filled(model)
        recorder.hold()

        let first = Task { await model.submit() }
        await recorder.untilSuspended()
        #expect(model.state.isSubmitting)
        await model.submit()
        recorder.release()
        await first.value

        #expect(recorder.calls.count == 1)
        #expect(!model.state.isSubmitting)
    }

    @Test("a stale message is cleared when a new submit starts")
    func staleMessage() async {
        let model = model(failing: .server)
        filled(model)
        await model.submit()
        #expect(model.state.message != nil)
        recorder.hold()

        let second = Task { await model.submit() }
        await recorder.untilSuspended()
        #expect(model.state.message == nil)
        recorder.release()
        await second.value
    }

    @Test("the second consecutive wrong password discloses that recovery is unavailable; the first does not")
    func recoveryDisclosure() async {
        let model = model(failing: .invalidCredentials, .invalidCredentials)
        filled(model)

        await model.submit()
        #expect(model.state.message == AuthMessage(kind: .error, text: "E-mail ou senha incorretos"))
        await model.submit()

        #expect(model.state.message == AuthMessage(
            kind: .error, text: "E-mail ou senha incorretos",
            secondary: "A recuperação de senha ainda não está disponível neste app."
        ))
    }

    @Test("a different failure in between restarts the count")
    func consecutiveOnly() async {
        let model = model(failing: .invalidCredentials, .unreachable, .invalidCredentials)
        filled(model)

        await model.submit()
        await model.submit()
        await model.submit()

        #expect(model.state.message?.secondary == nil)
    }

    @Test("the submit action runs the sign-in")
    func submitAction() async {
        let model = model()
        filled(model)

        model.handle(.submit)
        while recorder.calls.isEmpty { await Task.yield() }

        #expect(recorder.calls.count == 1)
    }

    @Test("the register action leaves the form untouched: navigating is the view's job")
    func register() {
        let model = model()
        filled(model)
        let before = model.state

        model.handle(.register)

        #expect(model.state == before)
    }
}
