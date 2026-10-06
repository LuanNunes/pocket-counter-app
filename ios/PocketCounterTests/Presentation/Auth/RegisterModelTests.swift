import Testing
@testable import PocketCounter

@MainActor
@Suite("RegisterModel")
struct RegisterModelTests {
    private func model(
        _ outcome: Result<Void, AuthenticationFailure> = .success(()),
        onRegister: @escaping @MainActor (Registration) -> Void = { _ in }
    ) -> RegisterModel {
        RegisterModel { registration throws(AuthenticationFailure) in
            onRegister(registration)
            try outcome.get()
        }
    }

    private func fill(_ model: RegisterModel, password: String = "segredo12") {
        model.handle(.nameChanged("Ana"))
        model.handle(.emailChanged("ana@b.com"))
        model.handle(.passwordChanged(password))
    }

    @Test("submitting is blocked until every field is filled and the password is long enough")
    func canSubmit() {
        let model = model()
        #expect(!model.state.canSubmit)

        model.handle(.nameChanged("Ana"))
        model.handle(.emailChanged("ana@b.com"))
        model.handle(.passwordChanged("curta"))
        #expect(!model.state.canSubmit)

        model.handle(.passwordChanged("segredo12"))
        #expect(model.state.canSubmit)
    }

    @Test("a blank name blocks submitting even with a valid e-mail and password")
    func blankName() {
        let model = model()
        model.handle(.nameChanged("   "))
        model.handle(.emailChanged("ana@b.com"))
        model.handle(.passwordChanged("segredo12"))

        #expect(!model.state.canSubmit)
    }

    @Test("the length rule counts UTF-16 units, like the backend, not characters")
    func emojiPassword() {
        let model = model()
        model.handle(.nameChanged("Ana"))
        model.handle(.emailChanged("ana@b.com"))
        // One grapheme cluster, 11 UTF-16 units — the backend accepts it, so we must too.
        model.handle(.passwordChanged("👨‍👩‍👧‍👦"))

        #expect(model.state.canSubmit)
    }

    @Test("a short password that reaches submit is reported, and never sent")
    func shortPasswordNeverSent() async {
        var sent = false
        let model = model(onRegister: { _ in sent = true })
        fill(model, password: "curta")

        await model.submit()

        #expect(!sent)
        #expect(model.state.message?.text == CredentialsError.passwordTooShort(minimum: 8).message)
    }

    @Test("a successful registration leaves no message: the gate unmounts the screen")
    func success() async {
        var received: Registration?
        let model = model(onRegister: { received = $0 })
        fill(model)

        await model.submit()

        #expect(received?.email == "ana@b.com")
        #expect(received?.name == "Ana")
        #expect(model.state.message == nil)
        #expect(!model.state.isSubmitting)
    }

    @Test("a taken e-mail is reported in the register path's words")
    func emailTaken() async {
        let model = model(.failure(.emailAlreadyRegistered))
        fill(model)

        await model.submit()

        #expect(model.state.message == AuthFailureMessage.text(for: .emailAlreadyRegistered, path: .register))
        #expect(model.state.message != nil)
    }

    @Test("abandoning renders nothing at all")
    func abandoned() async {
        let model = model(.failure(.abandoned))
        fill(model)

        await model.submit()

        #expect(model.state.message == nil)
        #expect(!model.state.isSubmitting)
    }

    @Test("a stale message is cleared when a new submit starts")
    func staleMessageCleared() async {
        let model = model(.failure(.emailAlreadyRegistered))
        fill(model)
        await model.submit()
        #expect(model.state.message != nil)

        let fresh = self.model()
        fresh.handle(.nameChanged("Ana"))
        fresh.handle(.emailChanged("ana@b.com"))
        fresh.handle(.passwordChanged("segredo12"))
        await fresh.submit()

        #expect(fresh.state.message == nil)
    }

    @Test("a second submit while one is open is ignored")
    func reentry() async {
        let recorder = RegisterRecorder()
        let model = RegisterModel(register: recorder.action)
        fill(model)
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
}

/// Lets a test observe the in-flight state, the way `SignInRecorder` does for Login.
@MainActor
private final class RegisterRecorder {
    private(set) var calls: [Registration] = []
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

    var action: RegisterAction {
        { [self] registration throws(AuthenticationFailure) in
            calls.append(registration)
            if holding { await withCheckedContinuation { waiter = $0 } }
        }
    }
}
