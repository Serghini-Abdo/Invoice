package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.backup.BackupCodec
import ma.workshop.invoice.domain.backup.BackupParseResult
import ma.workshop.invoice.domain.backup.BackupProblemCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupTest {

    private val data = Fixtures.backupData()
    private val json = BackupCodec.encode(BackupCodec.toDocument(data, now = 1_700_000_200_000, appVersion = "1.0.0"))

    private fun invalid(text: String): BackupParseResult.Invalid {
        val result = BackupCodec.parse(text)
        assertIs<BackupParseResult.Invalid>(result, "expected the backup to be rejected")
        return result
    }

    private fun codes(text: String) = invalid(text).problems.map { it.code }.toSet()

    @Test
    fun `a backup round-trips exactly`() {
        val result = BackupCodec.parse(json)
        assertIs<BackupParseResult.Valid>(result)
        assertEquals(data, result.data)
        assertEquals(1, result.document.version)
        assertEquals("workshop-invoice-backup", result.document.format)
    }

    @Test
    fun `settings preferences and customer snapshots are part of the backup`() {
        val restored = (BackupCodec.parse(json) as BackupParseResult.Valid).data
        assertEquals("ورشة الأمل للحدادة", restored.settings.workshopName)
        assertEquals("أحمد", restored.invoices[0].invoice.customer.name)
        assertEquals(2, restored.products.size)
        assertEquals(1, restored.invoices[1].revisions.size)
    }

    @Test
    fun `revisions keep the full previous version`() {
        val restored = (BackupCodec.parse(json) as BackupParseResult.Valid).data
        val revision = restored.invoices[1].revisions.single()
        assertEquals(Fixtures.dh("80"), revision.previous.total) // 2 * 40
        assertEquals(Fixtures.dh("200"), restored.invoices[1].invoice.total) // 5 * 40
    }

    @Test
    fun `empty and non-JSON input is rejected`() {
        assertEquals(setOf(BackupProblemCode.NOT_A_BACKUP), codes(""))
        assertEquals(setOf(BackupProblemCode.NOT_A_BACKUP), codes("   "))
        assertEquals(setOf(BackupProblemCode.NOT_A_BACKUP), codes("this is not json"))
        assertEquals(setOf(BackupProblemCode.NOT_A_BACKUP), codes("[1,2,3]"))
    }

    // Scenario 19: an interrupted download looks like a truncated file.
    @Test
    fun `a truncated file is rejected`() {
        for (cut in listOf(json.length / 2, json.length - 1, 100, json.length - 40)) {
            val result = BackupCodec.parse(json.substring(0, cut))
            assertIs<BackupParseResult.Invalid>(result, "cut at $cut should be rejected")
        }
    }

    @Test
    fun `a different JSON document is not a backup`() {
        assertEquals(setOf(BackupProblemCode.NOT_A_BACKUP), codes("""{"hello":"world"}"""))
        assertEquals(setOf(BackupProblemCode.NOT_A_BACKUP), codes("""{"format":"something-else","version":1}"""))
    }

    @Test
    fun `a backup from a newer app version is refused with a specific reason`() {
        val newer = json.replace("\"version\":1,", "\"version\":2,")
        assertEquals(setOf(BackupProblemCode.UNSUPPORTED_VERSION), codes(newer))
    }

    @Test
    fun `missing required sections are corrupt`() {
        assertEquals(
            setOf(BackupProblemCode.CORRUPT),
            codes("""{"format":"workshop-invoice-backup","version":1,"createdAt":1}"""),
        )
        assertEquals(setOf(BackupProblemCode.CORRUPT), codes("""{"format":"workshop-invoice-backup"}"""))
    }

    @Test
    fun `a tampered item total is detected by recomputing from the measurements`() {
        // 1.20 x 2.10 m at 500 DH/m2 is 1260 DH = 126000 centimes
        assertTrue(json.contains("\"totalCentimes\":126000"))
        val tampered = json.replace("\"totalCentimes\":126000", "\"totalCentimes\":100")
        val problems = invalid(tampered).problems
        assertTrue(problems.all { it.code == BackupProblemCode.INCONSISTENT })
        assertTrue(problems.any { it.detail.contains("does not match") }, problems.toString())
    }

    @Test
    fun `a tampered quantity or rate is detected`() {
        val badQuantity = json.replace("\"quantity\":4", "\"quantity\":0")
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(badQuantity))
        val badRate = json.replace("\"unitRateCentimes\":50000", "\"unitRateCentimes\":-50000")
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(badRate))
    }

    @Test
    fun `a deposit above the total is rejected`() {
        val bad = json.replace("\"depositCentimes\":50000", "\"depositCentimes\":99999999")
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(bad))
    }

    @Test
    fun `unknown enum values are rejected not defaulted`() {
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(json.replace("\"formula\":\"area\"", "\"formula\":\"volume\"")))
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(json.replace("\"exportFormat\":\"png\"", "\"exportFormat\":\"gif\"")))
    }

    @Test
    fun `duplicate invoice numbers and ids are rejected`() {
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(json.replace("\"number\":2,", "\"number\":1,")))
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(json.replace("\"id\":\"inv-2\"", "\"id\":\"inv-1\"")))
    }

    @Test
    fun `the invoice counter must stay ahead of existing numbers so numbers are never reused`() {
        val bad = json.replace("\"nextInvoiceNumber\":3", "\"nextInvoiceNumber\":2")
        assertEquals(setOf(BackupProblemCode.INCONSISTENT), codes(bad))
    }

    @Test
    fun `unknown extra fields from a newer minor version are ignored`() {
        val withExtra = json.replace("\"appVersion\":\"1.0.0\"", "\"appVersion\":\"1.0.0\",\"futureField\":{\"a\":1}")
        assertIs<BackupParseResult.Valid>(BackupCodec.parse(withExtra))
    }

    @Test
    fun `an empty database is a valid backup`() {
        val empty = ma.workshop.invoice.domain.backup.BackupData(Fixtures.settings(1), emptyList(), emptyList())
        val text = BackupCodec.encode(BackupCodec.toDocument(empty, 1, ""))
        assertEquals(empty, (BackupCodec.parse(text) as BackupParseResult.Valid).data)
    }

    @Test
    fun `invoices of deleted products stay valid`() {
        // items keep a snapshot; a productId with no matching catalog row is fine
        val noProducts = data.copy(products = emptyList())
        val text = BackupCodec.encode(BackupCodec.toDocument(noProducts, 1, ""))
        assertIs<BackupParseResult.Valid>(BackupCodec.parse(text))
    }
}
