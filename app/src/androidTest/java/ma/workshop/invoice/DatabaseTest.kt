package ma.workshop.invoice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.data.BackupRepository
import ma.workshop.invoice.data.DraftRepository
import ma.workshop.invoice.data.Environment
import ma.workshop.invoice.data.InvalidInvoiceException
import ma.workshop.invoice.data.InvoiceRepository
import ma.workshop.invoice.data.ProductRepository
import ma.workshop.invoice.data.SettingsRepository
import ma.workshop.invoice.data.db.AppDatabase
import ma.workshop.invoice.domain.backup.BackupData
import ma.workshop.invoice.domain.backup.InvoiceRecord
import ma.workshop.invoice.domain.flow.FlowEvent
import ma.workshop.invoice.domain.flow.InvoiceFlow
import ma.workshop.invoice.domain.invoice.ChangeType
import ma.workshop.invoice.domain.invoice.InvoiceVersioning
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite (not mocks): persistence, transactions and the rules that protect saved invoices. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var invoices: InvoiceRepository
    private lateinit var products: ProductRepository
    private lateinit var settings: SettingsRepository
    private lateinit var backups: BackupRepository
    private lateinit var drafts: DraftRepository

    private var clock = 1_700_000_000_000L
    private var ids = 0

    private fun env() = Environment(now = { clock++ }, newId = { "id-${++ids}" })

    private fun wire(database: AppDatabase) {
        db = database
        val env = env()
        invoices = InvoiceRepository(db, env)
        products = ProductRepository(db, env)
        settings = SettingsRepository(db)
        backups = BackupRepository(db)
        drafts = DraftRepository(db, env)
    }

    @Before
    fun setUp() {
        wire(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // Scenario 15: invoices survive closing and reopening
    @Test
    fun savedInvoiceSurvivesReopeningTheDatabase() = runBlocking {
        db.close()
        val name = "reopen-test.db"
        context.deleteDatabase(name)

        var file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        wire(file)
        val saved = invoices.save(TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500"), TestData.hinges("b", 4, "30"))))
        file.close()

        file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        wire(file)
        val reloaded = invoices.get(saved.id)
        assertEquals(saved, reloaded)
        assertEquals(TestData.dh("1380"), reloaded!!.total)
        file.close()
        context.deleteDatabase(name)
        wire(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build())
    }

    // Scenario 9, persisted
    @Test
    fun repeatedProductsArePersistedAsIndependentItems() = runBlocking {
        val saved = invoices.save(
            TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500"), TestData.door("b", "1.00", "2.00", "600"))),
        )
        val loaded = invoices.get(saved.id)!!
        assertEquals(2, loaded.items.size)
        assertEquals(listOf(TestData.dh("1260"), TestData.dh("1200")), loaded.items.map { it.pricing.total })
        assertEquals(listOf(TestData.dh("500"), TestData.dh("600")), loaded.items.map { it.pricing.unitRate })
        assertEquals(TestData.dh("2460"), loaded.total)
        // normalised measurements are stored for calculation
        val rows = db.invoices().getItems(saved.id)
        assertEquals(listOf(1_200_000L, 1_000_000L), rows.map { it.widthMicrometers })
        assertEquals(listOf(2_100_000L, 2_000_000L), rows.map { it.heightMicrometers })
    }

    // Scenario 13, persisted
    @Test
    fun editingOneRepeatedItemDoesNotChangeTheOther() = runBlocking {
        val saved = invoices.save(
            TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500"), TestData.door("b", "1.00", "2.00", "600"))),
        )
        val edited = TestData.draft(
            items = listOf(TestData.door("a", "1.20", "2.10", "550", quantity = 2), saved.items[1]),
            replacing = saved.id,
        )
        val updated = invoices.save(edited)
        val reloaded = invoices.get(saved.id)!!
        assertEquals(updated, reloaded)
        assertEquals(saved.items[1], reloaded.items[1])
        assertEquals(TestData.dh("2772"), reloaded.items[0].pricing.total) // 2.52 * 550 * 2
    }

    @Test
    fun editingKeepsTheIdAndNumberAndWritesAnAuditRevision() = runBlocking {
        val saved = invoices.save(TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500")), deposit = "100"))
        val updated = invoices.save(
            TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500", quantity = 2)), deposit = "300", replacing = saved.id),
        )
        assertEquals(saved.id, updated.id)
        assertEquals(saved.number, updated.number)
        assertEquals(saved.createdAt, updated.createdAt)
        assertEquals(2, updated.revision)
        assertEquals(TestData.dh("2220"), updated.remaining) // 2520 - 300

        val revisions = invoices.revisions(saved.id)
        assertEquals(1, revisions.size)
        assertEquals(saved, revisions[0].previous)
        assertTrue(revisions[0].changes.any { it.type == ChangeType.ITEM_CHANGED })
        assertTrue(revisions[0].changes.any { it.type == ChangeType.DEPOSIT_CHANGED })
        assertEquals(TestData.dh("1260"), revisions[0].previousTotal)
        assertEquals(TestData.dh("2520"), revisions[0].newTotal)
    }

    // Scenario 14, persisted
    @Test
    fun editingDeactivatingOrDeletingACatalogProductNeverChangesSavedInvoices() = runBlocking {
        val productId = products.save(
            ProductDefinition(0, "باب حديد", "door", PricingFormula.AREA, LengthUnit.METER),
        )
        val saved = invoices.save(TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500", productId = productId))))

        products.save(products.get(productId)!!.copy(name = "اسم جديد", illustration = "gate", formula = PricingFormula.PER_ITEM))
        assertEquals(saved, invoices.get(saved.id))

        products.setActive(productId, false)
        assertEquals(saved, invoices.get(saved.id))

        products.delete(productId)
        val afterDelete = invoices.get(saved.id)!!
        assertNull("deleted product leaves only a cleared link", afterDelete.items[0].productId)
        assertEquals(saved.items[0].copy(productId = null), afterDelete.items[0])
        assertEquals("باب حديد", afterDelete.items[0].productName)
        assertEquals("door", afterDelete.items[0].illustration)
        assertEquals(PricingFormula.AREA, afterDelete.items[0].pricing.formula)
        assertEquals(TestData.dh("1260"), afterDelete.total)
    }

    @Test
    fun invoiceNumbersAreSequentialAndNeverReused() = runBlocking {
        val a = invoices.save(TestData.draft(listOf(TestData.hinges("a", 1, "10"))))
        val b = invoices.save(TestData.draft(listOf(TestData.hinges("b", 1, "10"))))
        assertEquals(listOf(1L, 2L), listOf(a.number, b.number))
        invoices.delete(b.id)
        val c = invoices.save(TestData.draft(listOf(TestData.hinges("c", 1, "10"))))
        assertEquals(3L, c.number)
        assertEquals(4L, settings.get().nextInvoiceNumber)
    }

    @Test
    fun deletingAnInvoiceRemovesItsItemsAndRevisions() = runBlocking {
        val saved = invoices.save(TestData.draft(listOf(TestData.hinges("a", 1, "10"))))
        invoices.save(TestData.draft(listOf(TestData.hinges("a", 2, "10")), replacing = saved.id))
        invoices.delete(saved.id)
        assertNull(invoices.get(saved.id))
        assertTrue(db.invoices().getAllItems().isEmpty())
        assertTrue(db.invoices().getAllRevisions().isEmpty())
    }

    @Test
    fun anInconsistentInvoiceIsRejectedBeforeAnythingIsWritten() = runBlocking {
        try {
            invoices.save(TestData.draft(listOf(TestData.hinges("a", 1, "10")), deposit = "11"))
            fail("deposit above the total must be rejected")
        } catch (expected: InvalidInvoiceException) {
        }
        try {
            invoices.save(TestData.draft(emptyList()))
            fail("an invoice without items must be rejected")
        } catch (expected: InvalidInvoiceException) {
        }
        assertTrue(db.invoices().getAll().isEmpty())
        assertEquals(1L, settings.get().nextInvoiceNumber) // no number was consumed
    }

    @Test
    fun theStarterCatalogIsSeededExactlyOnce() = runBlocking {
        products.ensureSeeded()
        val first = db.products().getAll()
        assertTrue(first.size >= 5)
        assertTrue(first.any { it.name == "باب حديد" && it.formula == "area" })
        assertTrue(first.any { it.name == "إطار حديد" && it.formula == "perimeter" })
        assertTrue(first.any { it.name == "مفصلات" && it.formula == "per_item" })

        products.ensureSeeded()
        assertEquals(first.size, db.products().getAll().size)

        // removing every product must not bring the starter set back
        first.forEach { products.delete(it.id) }
        products.ensureSeeded()
        assertTrue(db.products().getAll().isEmpty())
    }

    @Test
    fun settingsPersistAndTheInvoiceCounterNeverMovesBackwards() = runBlocking {
        invoices.save(TestData.draft(listOf(TestData.hinges("a", 1, "10"))))
        settings.update { it.copy(workshopName = "ورشة الأمل", nextInvoiceNumber = 1) }
        val loaded = settings.get()
        assertEquals("ورشة الأمل", loaded.workshopName)
        assertEquals(2L, loaded.nextInvoiceNumber)
    }

    @Test
    fun anUnfinishedInvoiceDraftRoundTrips() = runBlocking {
        val flow = InvoiceFlow { "draft-item" }
        var state = flow.startNew()
        state = flow.reduce(state, FlowEvent.CustomerEdited("سعيد", "0600000000")).state
        drafts.save(state)
        assertEquals("سعيد", drafts.load()!!.customer.name)
        drafts.clear()
        assertNull(drafts.load())
    }

    @Test
    fun backupRoundTripsEverythingIncludingTheAuditTrail() = runBlocking {
        products.ensureSeeded()
        settings.update { it.copy(workshopName = "ورشة الأمل", workshopPhone = "0522000000") }
        invoices.save(
            TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500"), TestData.hinges("b", 4, "30")), deposit = "500"),
        )
        val second = invoices.save(TestData.draft(listOf(TestData.hinges("c", 2, "40"))))
        invoices.save(TestData.draft(listOf(TestData.hinges("c", 5, "40")), deposit = "50", replacing = second.id)) // creates a revision

        val exported = backups.export()
        assertEquals(2, exported.invoices.size)
        assertEquals(1, exported.invoices.sumOf { it.revisions.size })

        // wipe everything, then restore
        backups.restore(BackupData(exported.settings, emptyList(), emptyList()))
        assertTrue(db.invoices().getAll().isEmpty())
        assertTrue(db.products().getAll().isEmpty())

        backups.restore(exported)
        assertEquals(exported, backups.export())
    }

    // Scenario 19: a restore that fails midway leaves existing data exactly as it was
    @Test
    fun aFailedRestoreLeavesExistingDataUntouched() = runBlocking {
        products.ensureSeeded()
        val kept = invoices.save(TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500")), deposit = "100"))
        val before = backups.export()

        val clash1 = InvoiceVersioning.create(TestData.draft(listOf(TestData.hinges("x", 1, "10"))), "bad-1", number = 7, now = 1)
        val clash2 = InvoiceVersioning.create(TestData.draft(listOf(TestData.hinges("y", 1, "10"))), "bad-2", number = 7, now = 2) // same number
        val broken = BackupData(
            settings = before.settings.copy(workshopName = "SHOULD NOT APPEAR", nextInvoiceNumber = 100),
            products = emptyList(),
            invoices = listOf(InvoiceRecord(clash1, emptyList()), InvoiceRecord(clash2, emptyList())),
        )
        try {
            backups.restore(broken)
            fail("duplicate invoice numbers must make the restore fail")
        } catch (expected: Exception) {
        }

        assertEquals(before, backups.export())
        assertEquals(kept, invoices.get(kept.id))
        assertFalse(settings.get().workshopName == "SHOULD NOT APPEAR")
        assertNotNull(db.products().getAll().firstOrNull())
    }

    @Test
    fun restoringABackupWhoseItemsPointAtMissingProductsStillWorks() = runBlocking {
        val invoice = InvoiceVersioning.create(
            TestData.draft(listOf(TestData.door("a", "1.20", "2.10", "500", productId = 999))),
            "inv", number = 1, now = 1,
        )
        backups.restore(BackupData(ma.workshop.invoice.domain.settings.AppSettings(nextInvoiceNumber = 2), emptyList(), listOf(InvoiceRecord(invoice, emptyList()))))
        val loaded = invoices.get("inv")!!
        assertNull(loaded.items[0].productId)
        assertEquals(TestData.dh("1260"), loaded.total)
    }
}
