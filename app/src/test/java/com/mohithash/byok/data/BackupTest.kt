package com.mohithash.byok.data

import com.mohithash.byok.engine.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private fun r(id: Long, root: Long = 0, at: Long = id * 10, title: String = "R$id") =
        BackupResult(id = id, toolId = "t", toolTitle = "T", title = title, json = "{}", createdAt = at, rootId = root)

    @Test fun roundTrips() {
        val b = Backup(appId = "pantry_pal", appName = "Pantry Pal", exportedAt = 5, profile = mapOf("diet" to "vegan"),
            prefs = Prefs(theme = "dark", language = "French"), results = listOf(r(1), r(2, root = 1)))
        assertEquals(b, Backups.decode(Backups.encode(b)))
    }

    @Test fun rejectsForeignFiles() {
        assertThrows(IllegalArgumentException::class.java) { Backups.decode("{\"hello\":1}") }
        assertThrows(IllegalArgumentException::class.java) { Backups.decode("""{"format":"other","appId":"x"}""") }
        assertThrows(IllegalArgumentException::class.java) { Backups.decode("not json") }
    }

    @Test fun importPutsRootsFirstAndSkipsExisting() {
        val items = listOf(r(3, root = 1, at = 30), r(1, at = 10), r(2, at = 20), r(4, root = 2, at = 5))
        val order = Backups.importOrder(items, setOf(Backups.identity(20, "R2", "t")))
        assertEquals(listOf(1L, 4L, 3L), order.map { it.id })
    }

    @Test fun linkTranslatesThreadIdsAndOrphansBecomeRoots() {
        val ids = mapOf(1L to 101L)
        assertEquals(101L, Backups.link(r(3, root = 1), ids).rootId)
        assertEquals(0L, Backups.link(r(4, root = 2), ids).rootId)
        assertEquals(0L, Backups.link(r(1), ids).rootId)
    }
}
