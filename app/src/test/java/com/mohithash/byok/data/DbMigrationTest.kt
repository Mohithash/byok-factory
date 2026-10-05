package com.mohithash.byok.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A v1.0 install's database (schema as Room generated it for v1) must open under v1.1 with every row intact. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DbMigrationTest {
    @Test fun v1DatabaseUpgradesInPlace() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `toolId` TEXT NOT NULL, `toolTitle` TEXT NOT NULL, `emoji` TEXT NOT NULL, `title` TEXT NOT NULL, `inputSummary` TEXT NOT NULL, `json` TEXT NOT NULL, `ticks` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1-hash')")
            db.execSQL("INSERT INTO results (toolId, toolTitle, emoji, title, inputSummary, json, ticks, favorite, createdAt) VALUES ('cook','Cook','🍳','Omelette','eggs','{\"title\":\"Omelette\"}','0:1',1,1000)")
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, AppDb::class.java, name).addMigrations(AppDb.MIGRATION_1_2).build()
        val rows = room.results().allOnce()
        assertEquals(1, rows.size)
        val r = rows.single()
        assertEquals("Omelette", r.title); assertEquals("0:1", r.ticks); assertEquals(true, r.favorite)
        assertEquals("", r.inputsJson); assertEquals(0L, r.rootId); assertEquals("", r.note); assertEquals(r.id, r.threadId)
        val follow = room.results().insert(r.copy(id = 0, title = "Vegan omelette", rootId = r.id, inputsJson = "{\"question\":\"vegan?\"}", createdAt = 2000))
        assertEquals(listOf(r.id, follow), room.results().threadOnce(r.id).map { it.id })
        room.close()
    }
}
