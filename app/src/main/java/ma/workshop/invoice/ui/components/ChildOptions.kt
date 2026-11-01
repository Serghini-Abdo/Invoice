package ma.workshop.invoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Options that only matter while their parent option is on. When [visible] is false they are **hidden**, not
 * greyed out, and nothing is composed; their saved values are never touched. Every parent / child pair in
 * Settings uses this one rule (DECISIONS.md, "Settings").
 */
@Composable
fun ChildOptions(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) { content() }
}
