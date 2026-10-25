package ma.workshop.invoice.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.ui.backup.BackupScreen
import ma.workshop.invoice.ui.backup.BackupViewModel
import ma.workshop.invoice.ui.flow.InvoiceFlowRoute
import ma.workshop.invoice.ui.history.HistoryScreen
import ma.workshop.invoice.ui.history.HistoryViewModel
import ma.workshop.invoice.ui.history.InvoiceDetailScreen
import ma.workshop.invoice.ui.history.InvoiceDetailViewModel
import ma.workshop.invoice.ui.screens.HomeScreen
import ma.workshop.invoice.ui.settings.CatalogManagementScreen
import ma.workshop.invoice.ui.settings.CatalogViewModel
import ma.workshop.invoice.ui.settings.ProductEditScreen
import ma.workshop.invoice.ui.settings.ProductEditViewModel
import ma.workshop.invoice.ui.settings.SettingsScreen
import ma.workshop.invoice.ui.settings.SettingsViewModel

private object Routes {
    const val HOME = "home"
    const val NEW_INVOICE = "invoice/new"
    const val EDIT_INVOICE = "invoice/edit/{id}"
    const val HISTORY = "history"
    const val DETAIL = "history/{id}"
    const val SETTINGS = "settings"
    const val CATALOG = "settings/catalog"
    const val PRODUCT = "settings/catalog/{productId}"
    const val BACKUP = "settings/backup"

    fun edit(id: String) = "invoice/edit/$id"
    fun detail(id: String) = "history/$id"
    fun product(id: Long) = "settings/catalog/$id"
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        // On tablets the content keeps a phone-like reading width, centred; the catalog grid adds columns.
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxHeight().widthIn(max = 720.dp),
        ) {

            composable(Routes.HOME) {
                // The notice about an interrupted restore is shown once.
                val notice = remember { container.startupNotice.also { container.startupNotice = null } }
                HomeScreen(
                    onNewInvoice = { nav.navigate(Routes.NEW_INVOICE) },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    notice = notice,
                )
            }

            composable(Routes.NEW_INVOICE) {
                InvoiceFlowRoute(
                    container = container,
                    editInvoiceId = null,
                    onExit = { nav.popBackStack() },
                    // Starting another invoice replaces this one so the new flow begins clean.
                    onNewInvoice = { nav.navigate(Routes.NEW_INVOICE) { popUpTo(Routes.HOME) } },
                    onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            }

            composable(
                Routes.EDIT_INVOICE,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                InvoiceFlowRoute(
                    container = container,
                    editInvoiceId = entry.arguments?.getString("id"),
                    onExit = { nav.popBackStack() },
                    onNewInvoice = { nav.navigate(Routes.NEW_INVOICE) { popUpTo(Routes.HOME) } },
                    onHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            }

            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(factory = VmFactory { HistoryViewModel(container) })
                val visible by vm.visible.collectAsStateWithLifecycle()
                val total by vm.totalCount.collectAsStateWithLifecycle()
                val query by vm.query.collectAsStateWithLifecycle()
                HistoryScreen(
                    invoices = visible,
                    totalCount = total,
                    query = query,
                    onQueryChange = { vm.query.value = it },
                    onOpen = { nav.navigate(Routes.detail(it)) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val vm: InvoiceDetailViewModel = viewModel(key = "detail-$id", factory = VmFactory { InvoiceDetailViewModel(container, id) })
                val state by vm.state.collectAsStateWithLifecycle()
                val export by vm.export.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val deleteFailed by vm.deleteFailed.collectAsStateWithLifecycle()
                val goBack by rememberUpdatedState { nav.popBackStack() }
                LaunchedEffect(vm) { vm.exits.collect { goBack() } }
                // Reload on every resume so returning from the edit screen shows the edited invoice.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
                InvoiceDetailScreen(
                    state = state,
                    export = export,
                    exportFormat = settings.exportFormat,
                    deleteFailed = deleteFailed,
                    onEdit = { nav.navigate(Routes.edit(id)) },
                    onPrepareShare = vm::prepareShare,
                    onShare = { vm.share(context, it) },
                    onDelete = vm::delete,
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = VmFactory { SettingsViewModel(container) })
                val settings by vm.settings.collectAsStateWithLifecycle()
                val speech by vm.speechStatus.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    speechStatus = speech,
                    onUpdate = vm::update,
                    onOpenCatalog = { nav.navigate(Routes.CATALOG) },
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                    onTestVoice = vm::testVoice,
                    onInstallVoice = {
                        try {
                            context.startActivity(vm.installVoiceIntent())
                        } catch (e: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(context, Strings.AUDIO_NO_ENGINE, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.CATALOG) {
                val vm: CatalogViewModel = viewModel(factory = VmFactory { CatalogViewModel(container) })
                val products by vm.products.collectAsStateWithLifecycle()
                CatalogManagementScreen(
                    products = products,
                    onSetActive = vm::setActive,
                    onEdit = { nav.navigate(Routes.product(it)) },
                    onAdd = { nav.navigate(Routes.product(0)) },
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                Routes.PRODUCT,
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                val productId = entry.arguments?.getLong("productId") ?: 0L
                val vm: ProductEditViewModel = viewModel(key = "product-$productId", factory = VmFactory { ProductEditViewModel(container, productId) })
                val state by vm.state.collectAsStateWithLifecycle()
                val goBack by rememberUpdatedState { nav.popBackStack() }
                LaunchedEffect(vm) { vm.exits.collect { goBack() } }
                ProductEditScreen(
                    state = state,
                    onEdit = vm::edit,
                    onSave = vm::save,
                    onDelete = vm::delete,
                    onDeactivateInstead = vm::deactivateInstead,
                    onBack = { nav.popBackStack() },
                )
            }

            composable(Routes.BACKUP) {
                val vm: BackupViewModel = viewModel(factory = VmFactory { BackupViewModel(container) })
                val state by vm.state.collectAsStateWithLifecycle()

                val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                    vm.onConsentResult(if (result.resultCode == Activity.RESULT_OK) result.data else null)
                }
                LaunchedEffect(vm) {
                    vm.consentRequests.collect { pending ->
                        consentLauncher.launch(IntentSenderRequest.Builder(pending).build())
                    }
                }
                val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) vm.exportToFile(context, uri)
                }
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) vm.importFromFile(context, uri)
                }
                BackupScreen(
                    state = state,
                    onDriveBackup = vm::backupToDrive,
                    onDriveRestore = vm::restoreFromDrive,
                    onFileExport = {
                        exportLauncher.launch("workshop-invoices-backup-${java.time.LocalDate.now()}.json")
                    },
                    onFileImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onConfirmRestore = vm::confirmRestore,
                    onCancelRestore = vm::cancelRestore,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
