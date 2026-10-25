package ma.workshop.invoice

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
        enableEdgeToEdge()
        // The app is Arabic-only: lay out right-to-left whatever the phone's language is.
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val container = (application as InvoiceApp).container
        setContent {
            InvoiceTheme {
                AppNav(container)
            }
        }
    }
}
