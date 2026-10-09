package com.resolveprogramming.pocketcounter.ui.quickadd

import com.resolveprogramming.pocketcounter.data.repository.IntentReadFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every failure gets its own sentence: "tente de novo" is unactionable when rewriting is the fix. */
class ReadFailureCopyTest {

    @Test
    fun offline_offersTheManualFormAndSaysWhereTheSentenceGoes() {
        assertEquals(
            "Sem conexão. Você pode lançar manualmente — a frase vai no campo Descrição.",
            readFailureCopy(IntentReadFailure.Offline, retrySeconds = null),
        )
    }

    @Test
    fun timeout_asksForARetry() {
        assertEquals(
            "A leitura demorou demais. Tente de novo ou lance manualmente.",
            readFailureCopy(IntentReadFailure.Timeout, retrySeconds = null),
        )
    }

    @Test
    fun badRequest_asksForARewrite() {
        assertEquals(
            "Não entendi essa frase. Tente reescrever ou lance manualmente.",
            readFailureCopy(IntentReadFailure.NotUnderstood, retrySeconds = null),
        )
    }

    @Test
    fun serverError_blamesTheServerNotTheSentence() {
        assertEquals(
            "Não foi possível ler a frase agora. Tente de novo ou lance manualmente.",
            readFailureCopy(IntentReadFailure.ServerError, retrySeconds = null),
        )
    }

    @Test
    fun unknown_readsLikeAServerError() {
        assertEquals(
            "Não foi possível ler a frase agora. Tente de novo ou lance manualmente.",
            readFailureCopy(IntentReadFailure.Unknown, retrySeconds = null),
        )
    }

    @Test
    fun rateLimited_countsDownTheSecondsTheServerAskedFor() {
        assertEquals(
            "Muitos lançamentos seguidos. Tente de novo em 30s.",
            readFailureCopy(IntentReadFailure.RateLimited(30), retrySeconds = 30),
        )
    }

    @Test
    fun rateLimited_withNothingLeftToWait_justSaysTryAgain() {
        assertEquals("Tente de novo.", readFailureCopy(IntentReadFailure.RateLimited(30), retrySeconds = null))
    }

    @Test
    fun rateLimited_withNoRetryAfterHeader_justSaysTryAgain() {
        assertEquals("Tente de novo.", readFailureCopy(IntentReadFailure.RateLimited(null), retrySeconds = null))
    }
}
