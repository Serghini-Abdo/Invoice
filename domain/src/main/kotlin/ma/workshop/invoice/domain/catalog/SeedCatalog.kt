package ma.workshop.invoice.domain.catalog

import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula

/**
 * The products a new installation starts with. They are only examples: the workshop can edit,
 * deactivate or add its own. None carries a price; the rate is typed for every invoice item.
 */
object SeedCatalog {

    private class Seed(val name: String, val illustration: String, val formula: PricingFormula, val unit: LengthUnit)

    private val seeds = listOf(
        Seed("باب حديد", "door", PricingFormula.AREA, LengthUnit.METER),
        Seed("نافذة ألمنيوم", "window", PricingFormula.AREA, LengthUnit.METER),
        Seed("باب الكراج", "garage", PricingFormula.AREA, LengthUnit.METER),
        Seed("إطار حديد", "frame", PricingFormula.PERIMETER, LengthUnit.METER),
        Seed("مفصلات", "hinges", PricingFormula.PER_ITEM, LengthUnit.METER),
        Seed("بوابة حديدية", "gate", PricingFormula.AREA, LengthUnit.METER),
        Seed("شباك حماية", "grille", PricingFormula.AREA, LengthUnit.METER),
        Seed("درابزين", "railing", PricingFormula.AREA, LengthUnit.METER),
        Seed("سياج", "fence", PricingFormula.AREA, LengthUnit.METER),
        Seed("ستارة حديدية", "shutter", PricingFormula.AREA, LengthUnit.METER),
        Seed("قفل", "lock", PricingFormula.PER_ITEM, LengthUnit.METER),
    )

    /** Ids are assigned by the database; [id] 0 means "not yet inserted". */
    fun products(now: Long): List<ProductDefinition> = seeds.mapIndexed { index, seed ->
        ProductDefinition(
            id = 0,
            name = seed.name,
            illustration = seed.illustration,
            formula = seed.formula,
            defaultUnit = seed.unit,
            category = null,
            isActive = true,
            sortOrder = index,
            createdAt = now,
            updatedAt = now,
        )
    }
}
