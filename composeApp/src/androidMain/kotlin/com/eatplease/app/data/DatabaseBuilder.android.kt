package com.eatplease.app.data

import androidx.room.Room
import androidx.room.RoomDatabase
import com.eatplease.app.EatPleaseApplication

actual fun eatPleaseDatabaseBuilder(): RoomDatabase.Builder<EatPleaseDatabase> {
    val context = EatPleaseApplication.instance
    return Room.databaseBuilder<EatPleaseDatabase>(
        context = context,
        name = context.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
    )
}
