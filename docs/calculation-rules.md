# Calculation rules

All money and measurement arithmetic lives in the pure-Kotlin `:domain` module
(`ma.workshop.invoice.domain.pricing`, `.money`, `.invoice`). Compose screens only display results.

## Representation (no floating point anywhere)

| Quantity | Stored as | Example |
|---|---|---|
| Money | `Long` **centimes** (1 DH = 100) | 1 260 DH = `126000` |
| A dimension | `Long` **thousandths of the unit typed** + the unit (`m` or `cm`) | 1.20 m = `1200` (m); 120 cm = `120000` (cm) |
| Normalised dimension | `Long` **micrometres** (`thousandths x 1000` for m, `x 10` for cm) | both of the above = `1 200 000` |
| Quantity | `Int`, 1 to 9 999, always starts at 1 | |
| Unit rate | money (centimes) | 500 DH/m2 = `50000` |
| Area / perimeter of one piece | exact `BigDecimal`, stored as plain text | `2.52` |

Centimetres are converted to metres **exactly** (micrometres are integers), so 120 cm and 1.20 m
are identical to the last digit.

## Formulas

```
AREA       area (m2)      = width (m) x height (m)
           item total     = area x rate per m2 x quantity

PERIMETER  perimeter (m)  = 2 x (width (m) + height (m))
           item total     = perimeter x rate per linear metre x quantity

PER_ITEM   item total     = quantity x rate per piece        (dimensions are not used or stored)

invoice total     = sum of the item totals
remaining balance = invoice total - deposit
```

No VAT, sales tax or any other tax is ever added.

## Rounding policy (one place, one rule)

* Every intermediate value is an exact `BigDecimal`; nothing is rounded along the way.
* **An item total is rounded exactly once, at the end, to whole centimes using HALF_UP.**
  Quantity is multiplied in *before* rounding (0.0005 m2 x 10 DH x 3 = 0.015 -> 0.02 DH, not 3 x 0.01).
* **The invoice total is the plain sum of the already-rounded item totals**, so the lines printed on an
  invoice always add up exactly to its total.
* Display formatting (for example showing an area as `2,52 m2`) never feeds back into a calculation.

## Validation (rejected, never silently corrected)

| Field | Rule |
|---|---|
| Width / height (area and perimeter products only) | required, > 0, at most 50 m, at most 3 decimals |
| Quantity | integer, 1 to 9 999 |
| Unit rate | required, > 0, at most 1 000 000 DH, at most 2 decimals |
| Deposit | empty means 0; must satisfy 0 <= deposit <= invoice total |
| Any number | digits with one `.` / `,` / `٫` separator (Western or Arabic-Indic digits); a minus sign is reported as "negative"; extra decimals are an error, not rounded |

## Snapshots

When an item is confirmed, its formula, measurements (with original units and normalised values),
quantity, unit rate, exact base amount and calculated total are stored on the invoice item. The
catalog product is only a convenience link (`ON DELETE SET NULL`); editing, deactivating or deleting a
product never changes an invoice. Backups recompute every item from its stored measurements and reject
any backup whose totals do not match.
