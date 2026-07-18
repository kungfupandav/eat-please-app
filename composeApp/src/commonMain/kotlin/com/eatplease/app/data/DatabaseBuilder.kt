package com.eatplease.app.data

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val DATABASE_FILE_NAME = "eat_please.db"

expect fun eatPleaseDatabaseBuilder(): RoomDatabase.Builder<EatPleaseDatabase>

fun createEatPleaseDatabase(
    builder: RoomDatabase.Builder<EatPleaseDatabase> = eatPleaseDatabaseBuilder(),
): EatPleaseDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
