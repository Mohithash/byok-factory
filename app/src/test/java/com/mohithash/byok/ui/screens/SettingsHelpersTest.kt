package com.mohithash.byok.ui.screens

import com.mohithash.byok.engine.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class SettingsHelpersTest {
    @Test fun languageOptionsStartWithDefaultEnglishOnly() {
        val opts = settingsLanguageOptions()
        assertEquals("", opts.first())
        assertFalse(opts.any { it.equals("English", ignoreCase = true) })
        assertEquals(Prefs.LANGUAGES.size, opts.size)
        assertEquals(opts.size, opts.distinct().size)
    }

    @Test fun languageLabels() {
        assertEquals("English (default)", settingsLanguageLabel(""))
        assertEquals("English (default)", settingsLanguageLabel("english"))
        assertEquals("Spanish", settingsLanguageLabel("Spanish"))
    }

    @Test fun countsUseThousandsSeparatorsAndPlurals() {
        assertEquals("1 result", settingsCountOf(1, "result", Locale.US))
        assertEquals("0 results", settingsCountOf(0, "result", Locale.US))
        assertEquals("1,234 saved results", settingsCountOf(1234, "saved result", Locale.US))
        assertEquals("12,345,678,901", settingsNumber(12_345_678_901L, Locale.US))
        assertEquals("1.234", settingsNumber(1234, Locale.GERMANY))
    }

    @Test fun sinceDate() {
        val millis = LocalDate.of(2026, 3, 7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("7 Mar 2026", settingsSince(millis, ZoneId.of("UTC"), Locale.UK))
    }

    @Test fun backupFileName() {
        assertEquals("pantrypal-backup-2026-10-05.json", settingsBackupName("pantrypal", LocalDate.of(2026, 10, 5)))
    }
}
