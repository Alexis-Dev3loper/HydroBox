package com.hydrobox.app.auth

import com.hydrobox.app.auth.data.AUTH_SCHEMA_V4_STATEMENTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

class AuthSchemaMigrationTest {
    @Test
    fun v3ToV4PreservesProfileAndIrreversiblyDropsPlainPassword() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE users_local (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        lastName TEXT NOT NULL,
                        email TEXT NOT NULL,
                        passwordPlain TEXT NOT NULL,
                        avatarUri TEXT,
                        phonePrefix TEXT,
                        phone TEXT
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    INSERT INTO users_local
                        (name, lastName, email, passwordPlain, avatarUri, phonePrefix, phone)
                    VALUES
                        ('Hydro', 'Operator', 'operator@example.test', 'legacy-value-to-delete', NULL, '+52', '0000000000')
                    """.trimIndent()
                )
                AUTH_SCHEMA_V4_STATEMENTS.forEach(statement::execute)
            }

            val columns = mutableSetOf<String>()
            db.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(users_local)").use { rows ->
                    while (rows.next()) columns += rows.getString("name")
                }
            }
            assertFalse("passwordPlain must not survive schema v4", "passwordPlain" in columns)
            assertTrue("principalUuid" in columns)
            assertTrue("roleKey" in columns)

            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT name, lastName, email, principalUuid, roleKey, phonePrefix, phone FROM users_local"
                ).use { row ->
                    assertTrue(row.next())
                    assertEquals("Hydro", row.getString("name"))
                    assertEquals("Operator", row.getString("lastName"))
                    assertEquals("operator@example.test", row.getString("email"))
                    assertNull(row.getString("principalUuid"))
                    assertNull(row.getString("roleKey"))
                    assertEquals("+52", row.getString("phonePrefix"))
                    assertEquals("0000000000", row.getString("phone"))
                }
            }
        }
    }
}
