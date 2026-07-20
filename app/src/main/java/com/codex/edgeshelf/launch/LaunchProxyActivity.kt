package com.codex.edgeshelf.launch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log

class LaunchProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finishWithoutAnimation()
            return
        }

        val target = targetIntent()
        val bounds = launchBounds()
        if (target == null || bounds == null || target.component?.className == javaClass.name) {
            finishWithoutAnimation()
            return
        }

        runCatching {
            startActivity(target, FreeformLaunchOptions.create(bounds))
        }.recoverCatching { error ->
            Log.d(TAG, "Freeform launch failed; using normal launch", error)
            startActivity(Intent(target))
        }.onFailure { error ->
            Log.w(TAG, "Unable to launch target application", error)
        }
        finishWithoutAnimation()
    }

    private fun targetIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TARGET_INTENT)
        }?.let(::Intent)

    private fun launchBounds(): Rect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LAUNCH_BOUNDS, Rect::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_LAUNCH_BOUNDS)
        }?.let(::Rect)

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "EdgeShelfLaunch"
        private const val EXTRA_TARGET_INTENT = "com.codex.edgeshelf.extra.TARGET_INTENT"
        private const val EXTRA_LAUNCH_BOUNDS = "com.codex.edgeshelf.extra.LAUNCH_BOUNDS"

        fun createIntent(context: Context, target: Intent, bounds: Rect): Intent =
            Intent(context, LaunchProxyActivity::class.java)
                .putExtra(EXTRA_TARGET_INTENT, Intent(target))
                .putExtra(EXTRA_LAUNCH_BOUNDS, Rect(bounds))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
