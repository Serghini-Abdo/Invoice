package ma.workshop.invoice

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ma.workshop.invoice.data.db.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the "updates never destroy saved invoices" rule. The exported schema JSON of every version is
 * checked in under app/schemas; when the version is raised, add a Migration and a test that opens the
 * previous version's database with data in it and runs [AppDatabase.MIGRATIONS].
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun version1SchemaMatchesTheEntitiesAndSurvivesTheMigrationChain() {
        val name = "migration-test.db"
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO invoices (id, number, createdAt, updatedAt, revision, customerName, customerPhone, " +
                    "totalCentimes, depositCentimes, remainingCentimes, itemCount) " +
                    "VALUES ('inv-1', 1, 1, 1, 1, 'أحمد', '', 126000, 0, 126000, 1)",
            )
        }
        helper.runMigrationsAndValidate(name, AppDatabase.MIGRATIONS.last().endVersion, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT customerName, totalCentimes FROM invoices WHERE id = 'inv-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("أحمد", cursor.getString(0))
                org.junit.Assert.assertEquals(126000L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun version1SettingsKeepTheirValuesAndGetTheNormalSpeechRate() {
        val name = "migration-settings.db"
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO app_settings (id, workshopName, workshopPhone, exportFormat, audioEnabled, speakItemTotals, " +
                    "speakInvoiceTotals, thankYouMessage, nextInvoiceNumber, catalogSeeded) " +
                    "VALUES (1, 'ورشة الأمل', '0522', 'pdf', 0, 1, 1, 'شكراً', 7, 1)",
            )
        }
        helper.runMigrationsAndValidate(name, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT workshopName, exportFormat, audioEnabled, nextInvoiceNumber, speechRate FROM app_settings").use { cursor ->
                assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("ورشة الأمل", cursor.getString(0))
                org.junit.Assert.assertEquals("pdf", cursor.getString(1))
                org.junit.Assert.assertEquals(0, cursor.getInt(2))
                org.junit.Assert.assertEquals(7L, cursor.getLong(3))
                org.junit.Assert.assertEquals("normal", cursor.getString(4))
            }
        }
    }

    @Test
    fun version2SettingsKeepTheirValuesAndSpeakDarija() {
        val name = "migration-settings-3.db"
        helper.createDatabase(name, 2).use { db ->
            db.execSQL(
                "INSERT INTO app_settings (id, workshopName, workshopPhone, exportFormat, audioEnabled, speakItemTotals, " +
                    "speakInvoiceTotals, thankYouMessage, nextInvoiceNumber, catalogSeeded, speechRate) " +
                    "VALUES (1, 'ورشة الأمل', '0522', 'pdf', 1, 1, 0, 'شكراً', 9, 1, 'fast')",
            )
        }
        helper.runMigrationsAndValidate(name, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT workshopName, speakInvoiceTotals, nextInvoiceNumber, speechRate, speechLanguage FROM app_settings").use { cursor ->
                assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("ورشة الأمل", cursor.getString(0))
                org.junit.Assert.assertEquals(0, cursor.getInt(1))
                org.junit.Assert.assertEquals(9L, cursor.getLong(2))
                org.junit.Assert.assertEquals("fast", cursor.getString(3))
                org.junit.Assert.assertEquals("darija", cursor.getString(4))
            }
        }
    }

    @Test
    fun version3SettingsKeepTheirValuesAndHideTheSpeakers() {
        val name = "migration-settings-4.db"
        helper.createDatabase(name, 3).use { db ->
            db.execSQL(
                "INSERT INTO app_settings (id, workshopName, workshopPhone, exportFormat, audioEnabled, speakItemTotals, " +
                    "speakInvoiceTotals, thankYouMessage, nextInvoiceNumber, catalogSeeded, speechRate, speechLanguage) " +
                    "VALUES (1, 'ورشة الأمل', '0522', 'pdf', 1, 1, 0, 'شكراً', 11, 1, 'slow', 'standard')",
            )
        }
        helper.runMigrationsAndValidate(name, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT workshopName, nextInvoiceNumber, speechRate, speechLanguage, showSpeakers FROM app_settings").use { cursor ->
                assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("ورشة الأمل", cursor.getString(0))
                org.junit.Assert.assertEquals(11L, cursor.getLong(1))
                org.junit.Assert.assertEquals("slow", cursor.getString(2))
                org.junit.Assert.assertEquals("standard", cursor.getString(3))
                org.junit.Assert.assertEquals(0, cursor.getInt(4))
            }
        }
    }
}
