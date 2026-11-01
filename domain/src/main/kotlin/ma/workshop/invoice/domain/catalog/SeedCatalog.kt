package ma.workshop.invoice.domain.catalog

import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.pricing.Measurements
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula

/**
 * The products a new installation starts with. They are only examples: the workshop can edit,
 * deactivate or add its own. None carries a price; the rate is typed for every invoice item.
 */
object SeedCatalog {

    private class Seed(
        val name: String,
        val illustration: String,
        val formula: PricingFormula,
        val unit: LengthUnit,
        val measurements: Measurements = formula.measurements,
    )

    /**
     * The owner's own products, named as he says them (Darija, or French written in Arabic) and drawn after
     * his sketches (2026-11-01). Methods follow his paper invoices: windows by area, faux-cadres by perimeter,
     * the rest per piece. A balcony grille is priced per piece but shows its width and height.
     */
    private val workshopSeeds = listOf(
        Seed("باب", "sk_door", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("شباك", "sk_window", PricingFormula.AREA, LengthUnit.METER),
        Seed("فوكادر", "sk_fauxcadre", PricingFormula.PERIMETER, LengthUnit.METER),
        Seed("شباك بالكو", "sk_balcony", PricingFormula.PER_ITEM, LengthUnit.METER, Measurements.BOTH),
        Seed("ضواية السطح", "sk_skylight", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("تشارات", "sk_tchara", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("روكار", "sk_regard", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("كاش ريدو", "sk_cacherideau", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("باب الكراج", "sk_garage", PricingFormula.AREA, LengthUnit.METER),
    )

    // The original examples come first (tests and existing installations rely on their order); the owner's
    // products follow. The old "باب الكراج" example is replaced by his garage door.
    private val seeds = listOf(
        Seed("باب حديد", "door", PricingFormula.AREA, LengthUnit.METER),
        Seed("نافذة ألمنيوم", "window", PricingFormula.AREA, LengthUnit.METER),
        Seed("إطار حديد", "frame", PricingFormula.PERIMETER, LengthUnit.METER),
        Seed("مفصلات", "hinges", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("بوابة حديدية", "gate", PricingFormula.AREA, LengthUnit.METER),
        Seed("شباك حماية", "grille", PricingFormula.AREA, LengthUnit.METER),
        Seed("درابزين", "railing", PricingFormula.AREA, LengthUnit.METER),
        Seed("سياج", "fence", PricingFormula.AREA, LengthUnit.METER),
        Seed("ستارة حديدية", "shutter", PricingFormula.AREA, LengthUnit.METER),
        Seed("قفل", "lock", PricingFormula.PER_ITEM, LengthUnit.METER),
    ) + workshopSeeds

    /** Ids are assigned by the database; [id] 0 means "not yet inserted". */
    fun products(now: Long): List<ProductDefinition> = definitions(seeds, now, firstOrder = 0)

    /**
     * The owner's products, for an installation whose catalogue was seeded before they existed. The caller
     * skips any whose picture or name is already used and appends them after [firstOrder].
     */
    fun workshopProducts(now: Long, firstOrder: Int): List<ProductDefinition> = definitions(workshopSeeds, now, firstOrder)

    private fun definitions(list: List<Seed>, now: Long, firstOrder: Int): List<ProductDefinition> = list.mapIndexed { index, seed ->
        ProductDefinition(
            id = 0,
            name = seed.name,
            illustration = seed.illustration,
            formula = seed.formula,
            defaultUnit = seed.unit,
            category = null,
            isActive = true,
            sortOrder = firstOrder + index,
            createdAt = now,
            updatedAt = now,
            measurements = seed.measurements,
        )
    }
}
