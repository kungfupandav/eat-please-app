package com.eatplease.app.platform

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Binds to [MainActivity] so Settings can request RECORD_AUDIO on demand.
 * Register the launcher in the Activity (before STARTED), then [bind] it.
 */
object AndroidPermissionRequester {
    private var activityRef: WeakReference<ComponentActivity>? = null
    private var launcher: ActivityResultLauncher<String>? = null
    private var pending: Continuation<Boolean>? = null

    fun bind(activity: ComponentActivity, permissionLauncher: ActivityResultLauncher<String>) {
        activityRef = WeakReference(activity)
        launcher = permissionLauncher
    }

    fun unbind(activity: ComponentActivity) {
        if (activityRef?.get() === activity) {
            pending?.resume(false)
            pending = null
            launcher = null
            activityRef = null
        }
    }

    fun onResult(granted: Boolean) {
        pending?.resume(granted)
        pending = null
    }

    suspend fun request(permission: String): Boolean {
        val activity = activityRef?.get() ?: return false
        if (ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val launch = launcher ?: return false
        return suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            launch.launch(permission)
        }
    }
}
