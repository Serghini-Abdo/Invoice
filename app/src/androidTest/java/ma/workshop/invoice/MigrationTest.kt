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
        helper.runMigrationsAndValidate(name, 1, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT customerName, totalCentimes FROM invoices WHERE id = 'inv-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("أحمد", cursor.getString(0))
                org.junit.Assert.assertEquals(126000L, cursor.getLong(1))
            }
        }
    }
}
