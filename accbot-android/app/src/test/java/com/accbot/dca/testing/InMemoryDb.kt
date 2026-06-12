package com.accbot.dca.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.accbot.dca.data.local.DcaDatabase

/**
 * Builds an in-memory [DcaDatabase] for JVM unit tests (Robolectric).
 * Allows main-thread queries so tests can use the *Sync DAO methods directly.
 */
fun buildInMemoryDb(): DcaDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        DcaDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
