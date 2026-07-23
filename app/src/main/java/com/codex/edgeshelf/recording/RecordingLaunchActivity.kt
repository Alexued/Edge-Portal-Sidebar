package com.codex.edgeshelf.recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.codex.edgeshelf.R

/**
 * A transparent, no-animation visible host for microphone FGS startup on Android 14+.
 * The rail launches this only after RECORD_AUDIO has been granted.
 */
@Suppress("CustomSplashScreen")
class RecordingLaunchActivity : Activity() {
    private var launchDispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            launchDispatched = true
            finishWithoutAnimation()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (launchDispatched) return
        launchDispatched = true
        runCatching { RecordingService.start(this) }
            .onFailure { error ->
                Log.w(TAG, "Unable to start recording service", error)
                Toast.makeText(
                    this,
                    getString(R.string.recording_start_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "EdgeShelfRecording"

        fun createIntent(context: Context): Intent =
            Intent(context, RecordingLaunchActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
    }
}
