package com.hydrobox.app.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SensitiveBackupRulesTest {
    @Test
    fun manifestActivatesRulesThatExcludeSessionPreferencesAndDatabase() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val legacyRules = File("src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = File("src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))

        listOf(legacyRules, extractionRules).forEach { rules ->
            assertTrue(rules.contains("hydrobox_secure_session.xml"))
            assertTrue(rules.contains("datastore/auth_prefs.preferences_pb"))
            assertTrue(rules.contains("hydro_local.db"))
            assertTrue(rules.contains("hydro_local.db-wal"))
            assertTrue(rules.contains("hydro_local.db-shm"))
        }
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))

        val mainSources = File("src/main/java/com/hydrobox/app")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(mainSources.contains("passwordPlain"))
        assertFalse(mainSources.contains("fallbackToDestructiveMigration"))
        assertFalse(mainSources.contains("var pass by rememberSaveable"))
    }
}
