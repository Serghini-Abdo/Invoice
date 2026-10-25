package ma.workshop.invoice

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ma.workshop.invoice.ui.AppNav
import ma.workshop.invoice.ui.theme.InvoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones stay in portrait (the keypad layouts are designed for it); tablets may rotate.
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        // Forced left-to-right whatever the phone's language (see InvoiceTheme); Arabic text aligns itself.
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val container = (application as InvoiceApp).container
        setContent {
            InvoiceTheme {
                AppNav(container)
            }
        }
    }
}
