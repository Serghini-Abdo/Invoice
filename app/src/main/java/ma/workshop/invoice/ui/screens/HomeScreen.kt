package ma.workshop.invoice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.components.strictClickable
import ma.workshop.invoice.ui.theme.AppColors

@Composable
private fun HomeCard(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .strictClickable(role = Role.Button, onClickLabel = text, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(84.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
    }
}

/** The home screen has exactly three actions and nothing else. */
@Composable
fun HomeScreen(
    onNewInvoice: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    notice: String? = null,
    backupAlert: String? = null,
    onBackupAlert: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        notice?.let { StatusBanner(it, BannerKind.Warning) }
        backupAlert?.let {
            StatusBanner(it, BannerKind.Warning, Modifier.clickable(role = Role.Button, onClickLabel = it, onClick = onBackupAlert))
        }
        HomeCard(Strings.NEW_INVOICE, Icons.Filled.Add, AppColors.Rust, Color.White, onNewInvoice, Modifier.weight(1f))
        HomeCard(Strings.PREVIOUS_INVOICES, Icons.AutoMirrored.Filled.ReceiptLong, AppColors.Steel, Color.White, onHistory, Modifier.weight(1f))
        HomeCard(Strings.SETTINGS, Icons.Filled.Settings, AppColors.SteelPale, AppColors.Steel, onSettings, Modifier.weight(1f))
    }
}
