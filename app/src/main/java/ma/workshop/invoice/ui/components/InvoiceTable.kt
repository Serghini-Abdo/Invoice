package ma.workshop.invoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.workshop.invoice.domain.format.ArabicTerms
import ma.workshop.invoice.domain.format.DocumentRow
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.theme.appPalette

/** Picture sizes of the compact table: 36 dp on the item list, 32 dp on the review and the invoice detail. */
object TablePicture {
    val ItemList: Dp = 36.dp
    val Review: Dp = 32.dp
    val Detail: Dp = 32.dp
}

private val AmountColumn = 66.dp
private val QuantityColumn = 44.dp
private val QuantityMargin = 8.dp
private const val FormulaWeight = 1.2f
private const val NameWeight = 1f

private val BodySize = 13.sp
private val FormulaSize = 12.sp

/**
 * The compact invoice table, the same on the item list, the review and the invoice detail, and laid out like
 * the paper: left to right amount (DH) | measurement and calculation | quantity | product with its picture.
 * No divider lines; a long calculation wraps inside its own cell.
 */
@Composable
fun InvoiceTableHeader(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.appPalette
    val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.grayStrong)
    Row(
        modifier
            .fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(6.dp))
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArabicText(ArabicTerms.AMOUNT_HEADER, Modifier.width(AmountColumn).padding(horizontal = 3.dp), style, textAlign = TextAlign.Left)
        ArabicText(ArabicTerms.MEASURE_AND_CALCULATION, Modifier.weight(FormulaWeight).padding(horizontal = 3.dp), style)
        ArabicText(ArabicTerms.QUANTITY, Modifier.width(QuantityColumn).padding(horizontal = 2.dp), style, textAlign = TextAlign.Center, maxLines = 1)
        ArabicText(ArabicTerms.PRODUCT, Modifier.weight(NameWeight).padding(horizontal = 3.dp), style)
    }
}

/** One table row. [index] only names the test tags (`table-amount-0`, `table-name-0`, ...). */
@Composable
fun InvoiceTableRow(row: DocumentRow, picture: Dp, modifier: Modifier = Modifier, index: Int = row.position - 1) {
    val palette = MaterialTheme.appPalette
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArabicText(
            row.totalNumber,
            Modifier.width(AmountColumn).padding(horizontal = 3.dp).testTag("table-amount-$index"),
            TextStyle(fontSize = BodySize, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Left,
        )
        Column(Modifier.weight(FormulaWeight).padding(horizontal = 3.dp).testTag("table-formula-$index"), horizontalAlignment = Alignment.End) {
            row.measures?.let { ArabicText(it, style = TextStyle(fontSize = BodySize)) }
            ArabicText(row.formula, style = TextStyle(fontSize = FormulaSize, color = palette.grayStrong))
        }
        Box(
            Modifier.width(QuantityColumn).padding(horizontal = QuantityMargin / 2).testTag("table-quantity-$index"),
            contentAlignment = Alignment.Center,
        ) {
            ArabicText(row.quantity.toString(), style = TextStyle(fontSize = BodySize, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        }
        Row(Modifier.weight(NameWeight).padding(horizontal = 3.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            ArabicText(
                row.name,
                Modifier.weight(1f).testTag("table-name-$index"),
                TextStyle(fontSize = BodySize, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(4.dp))
            IllustrationImage(row.illustration, Modifier.size(picture))
        }
    }
}

/** The title strip and every row, for the review and the invoice detail. */
@Composable
fun InvoiceTable(rows: List<DocumentRow>, picture: Dp, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("invoice-table")) {
        InvoiceTableHeader()
        rows.forEachIndexed { index, row -> InvoiceTableRow(row, picture, index = index) }
    }
}

/** The "خالص" badge: a check icon and the word, so paid is never colour alone. */
@Composable
fun PaidBadge(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.appPalette
    Row(
        modifier
            .background(palette.paidContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("paid-badge"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.paid, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        ArabicText(Strings.FULLY_PAID, style = MaterialTheme.typography.titleMedium, color = palette.paid, fontWeight = FontWeight.Bold)
    }
}
