package com.eatplease.app.detection

/**
 * Starts and stops the platform detection pipeline (camera + classifier).
 * The UI only talks to this interface; platform PRs provide camera-backed
 * implementations, while [FakeWatchController] powers the app until then.
 */
interface WatchController {
    suspend fun startWatching()
    suspend fun stopWatching()
}
