package com.codex.edgeshelf.screenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EdgeShelfAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureInProgress = AtomicBoolean(false)
    private lateinit var screenshotRepository: ScreenshotRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        screenshotRepository = ScreenshotRepository(applicationContext)
        ScreenshotController.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ScreenshotController.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ScreenshotController.detach(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    internal fun capture(callback: (ScreenshotCaptureResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(ScreenshotCaptureResult.Unsupported)
            return
        }
        captureApi30(callback)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureApi30(callback: (ScreenshotCaptureResult) -> Unit) {
        if (!captureInProgress.compareAndSet(false, true)) {
            callback(ScreenshotCaptureResult.Busy)
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace,
                        )
                        hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        screenshot.hardwareBuffer.close()
                    }
                    if (bitmap == null) {
                        finishCapture(callback, ScreenshotCaptureResult.Failed())
                        return
                    }
                    serviceScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                screenshotRepository.saveScreenshot(bitmap)
                            }
                        }.fold(
                            onSuccess = { entry -> ScreenshotCaptureResult.Saved(entry.uri) },
                            onFailure = { ScreenshotCaptureResult.Failed() },
                        )
                        bitmap.recycle()
                        finishCapture(callback, result)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    finishCapture(callback, ScreenshotCaptureResult.Failed(errorCode))
                }
            },
        )
    }

    private fun finishCapture(
        callback: (ScreenshotCaptureResult) -> Unit,
        result: ScreenshotCaptureResult,
    ) {
        captureInProgress.set(false)
        if (result is ScreenshotCaptureResult.Saved) {
            ScreenshotController.publishSaved()
        }
        callback(result)
    }
}

object ScreenshotController {
    private var serviceReference = WeakReference<EdgeShelfAccessibilityService>(null)
    private val mutableConnected = MutableStateFlow(false)
    private val mutableSavedSerial = MutableStateFlow(0L)

    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    val savedSerial: StateFlow<Long> = mutableSavedSerial.asStateFlow()

    internal fun attach(service: EdgeShelfAccessibilityService) {
        serviceReference = WeakReference(service)
        mutableConnected.value = true
    }

    internal fun detach(service: EdgeShelfAccessibilityService) {
        if (serviceReference.get() === service) {
            serviceReference.clear()
            mutableConnected.value = false
        }
    }

    internal fun publishSaved() {
        mutableSavedSerial.value += 1L
    }

    fun capture(callback: (ScreenshotCaptureResult) -> Unit) {
        val service = serviceReference.get()
        if (service == null) {
            mutableConnected.value = false
            callback(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    ScreenshotCaptureResult.Unsupported
                } else {
                    ScreenshotCaptureResult.ServiceUnavailable
                },
            )
            return
        }
        service.capture(callback)
    }
}
