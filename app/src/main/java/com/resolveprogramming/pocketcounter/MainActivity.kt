package com.resolveprogramming.pocketcounter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.resolveprogramming.pocketcounter.data.capture.CaptureIngestor
import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.local.BiometricSettingsStore
import com.resolveprogramming.pocketcounter.data.local.CaptureSettingsStore
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.navigation.PocketNavHost
import com.resolveprogramming.pocketcounter.ui.theme.LocalPocketThemeController
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.rememberPocketThemeController
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    @Inject
    lateinit var captureSettingsStore: CaptureSettingsStore

    @Inject
    lateinit var biometricSettingsStore: BiometricSettingsStore

    @Inject
    lateinit var appLockState: AppLockState

    @Inject
    lateinit var ingestor: CaptureIngestor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch — a recreation (rotation / process restore) keeps the launch
        // intent, and re-handling it would double-ingest the forwarded message.
        if (savedInstanceState == null) handleSharedText(intent)
        setContent {
            val controller = rememberPocketThemeController()
            val darkTheme = controller.darkTheme ?: isSystemInDarkTheme()
            CompositionLocalProvider(LocalPocketThemeController provides controller) {
                PocketTheme(darkTheme = darkTheme, density = controller.density) {
                    PocketNavHost(
                        tokenStore,
                        captureSettingsStore,
                        biometricSettingsStore,
                        appLockState,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    /**
     * Push-only capture: SMS isn't read directly. Instead the user forwards a bank SMS (or any
     * text) into the app via the share sheet / text-selection menu; we route it through the same
     * [CaptureIngestor] pipeline (parse → POST → "Para revisar"). Non-financial text is dropped by
     * the ingestor's relevance gate.
     */
    private fun handleSharedText(intent: Intent?) {
        val text = run {
            when (intent?.action) {
                Intent.ACTION_SEND -> return@run intent.getStringExtra(Intent.EXTRA_TEXT)
                Intent.ACTION_PROCESS_TEXT ->
                    return@run intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            null
        }?.trim()
        if (!text.isNullOrBlank()) {
            ingestor.submit(
                CapturedMessage(
                    app = "SMS encaminhado",
                    channel = NotificationChannel.SMS,
                    text = text,
                    receivedAt = Instant.now(),
                ),
            )
            Toast.makeText(this, "Mensagem recebida — confira em “Para revisar”.", Toast.LENGTH_SHORT).show()
            // Consume the payload so a later recreation can't replay it.
            intent?.removeExtra(Intent.EXTRA_TEXT)
            intent?.removeExtra(Intent.EXTRA_PROCESS_TEXT)
        }
    }
}
