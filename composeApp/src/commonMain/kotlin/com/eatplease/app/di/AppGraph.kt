package com.eatplease.app.di

import com.eatplease.app.data.DetectionRepository
import com.eatplease.app.data.EatPleaseDatabase
import com.eatplease.app.data.WatchDao
import com.eatplease.app.data.createEatPleaseDatabase
import com.eatplease.app.detection.FakeFrameClassifier
import com.eatplease.app.detection.FakeWatchController
import com.eatplease.app.detection.FrameClassifier
import com.eatplease.app.detection.PaceAnalyzer
import com.eatplease.app.detection.WatchController
import com.eatplease.app.detection.WatchSessionManager
import com.eatplease.app.settings.CameraSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface AppGraph {

    val sessionManager: WatchSessionManager
    val repository: DetectionRepository
    val watchController: WatchController
    val cameraSettings: CameraSettings
    val paceAnalyzer: PaceAnalyzer

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(): EatPleaseDatabase = createEatPleaseDatabase()

    @Provides
    fun provideWatchDao(database: EatPleaseDatabase): WatchDao = database.watchDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(dao: WatchDao): DetectionRepository = DetectionRepository(dao)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionManager(
        repository: DetectionRepository,
        scope: CoroutineScope,
    ): WatchSessionManager = WatchSessionManager(repository, scope)

    // The fake classifier/controller pair is replaced by the platform
    // camera + MoViNet pipelines in the follow-up PRs.
    @Provides
    @SingleIn(AppScope::class)
    fun provideClassifier(): FrameClassifier = FakeFrameClassifier()

    @Provides
    @SingleIn(AppScope::class)
    fun provideWatchController(
        manager: WatchSessionManager,
        scope: CoroutineScope,
        cameraSettings: CameraSettings,
    ): WatchController = createPlatformWatchController(manager, scope, cameraSettings)
        classifier: FrameClassifier,
        scope: CoroutineScope,
    ): WatchController = FakeWatchController(manager, classifier, scope)

    @Provides
    @SingleIn(AppScope::class)
    fun providePaceAnalyzer(): PaceAnalyzer = PaceAnalyzer()

    @Provides
    @SingleIn(AppScope::class)
    fun provideCameraSettings(): CameraSettings = CameraSettings()
}

/** Process-wide graph holder; created lazily on first UI use. */
object Di {
    val graph: AppGraph by lazy { createGraph<AppGraph>() }
}
