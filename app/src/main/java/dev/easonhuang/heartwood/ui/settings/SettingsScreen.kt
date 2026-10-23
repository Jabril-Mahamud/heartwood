package dev.easonhuang.heartwood.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import dev.easonhuang.heartwood.BuildConfig
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.DashboardPreferences
import dev.easonhuang.heartwood.data.ExportFormat
import dev.easonhuang.heartwood.data.ExportManager
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.WeekStyle
import dev.easonhuang.heartwood.widget.WeekWidget
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle

private const val REPO_URL = "https://github.com/GuyOnWifi/heartwood"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    manager: HealthConnectManager,
    exporter: ExportManager,
    dashboardPrefs: DashboardPreferences,
    bottomInset: Dp,
    onManagePermissions: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showFormatDialog by remember { mutableStateOf(false) }
    val weekStyle by dashboardPrefs.weekStyle.collectAsStateWithLifecycle(WeekStyle.DEFAULT)
    var pickingColorFor by remember { mutableStateOf<DayOfWeek?>(null) }

    // The week widget only re-reads prefs when it renders, so nudge it after every change.
    fun saveWeek(block: suspend () -> Unit) {
        scope.launch {
            block()
            WeekWidget().updateAll(context)
        }
    }

    suspend fun runExport(uri: Uri, format: ExportFormat) {
        val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
        val metrics = Metric.entries.filter { manager.permissionFor(it) in granted }
        if (metrics.isEmpty()) {
            snackbar.showSnackbar(resources.getString(R.string.export_nothing)); return
        }
        exporter.export(uri, metrics, format).fold(
            onSuccess = { count -> snackbar.showSnackbar(
                    resources.getQuantityString(R.plurals.export_success, count, count, format.label)
                ) },
            onFailure = { snackbar.showSnackbar(resources.getString(R.string.export_failed, it.message)) },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.CSV) } } }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri -> uri?.let { scope.launch { runExport(it, ExportFormat.JSON) } } }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel(stringResource(R.string.settings_section_data)) }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.settings_manage_access),
                        subtitle = stringResource(R.string.settings_manage_access_summary),
                        onClick = onManagePermissions,
                    )
                    SettingRow(
                        icon = Icons.Rounded.MonitorHeart,
                        title = stringResource(R.string.settings_open_hc),
                        subtitle = stringResource(R.string.settings_open_hc_summary),
                        onClick = {
                            runCatching { context.startActivity(HealthConnectManager.settingsIntent()) }
                                .onFailure { scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_open_hc_failed)) } }
                        },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_export),
                        subtitle = stringResource(R.string.settings_export_summary),
                        onClick = { showFormatDialog = true },
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_section_week)) }
            item {
                SettingsCard {
                    WeekStartRow(weekStyle.start) { day -> saveWeek { dashboardPrefs.setWeekStart(day) } }
                    // Listed in week order, so the rows match the rings from outer to inner.
                    (0L until 7L).map { weekStyle.start.plus(it) }.forEach { day ->
                        DayColorRow(day, weekStyle.color(day)) { pickingColorFor = day }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.settings_section_about)) }
            item {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        subtitle = stringResource(R.string.settings_version_summary),
                        onClick = {},
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        title = stringResource(R.string.settings_open_source),
                        subtitle = stringResource(R.string.settings_open_source_summary),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                        },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }

    pickingColorFor?.let { day ->
        DayColorDialog(
            day = day,
            current = weekStyle.color(day),
            onPick = { color ->
                pickingColorFor = null
                saveWeek { dashboardPrefs.setDayColor(day, color) }
            },
            onDismiss = { pickingColorFor = null },
        )
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text(stringResource(R.string.export_dialog_title)) },
            text = { Text(stringResource(R.string.export_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    csvLauncher.launch("heartwood-export.csv")
                }) { Text("CSV") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    jsonLauncher.launch("heartwood-export.json")
                }) { Text("JSON") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(9.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ponytail: 12 evenly spaced hues at the default rings' saturation/lightness. Any colour here
// reads on both themes; a free-form picker would need contrast checks.
private val swatches = List(12) { Color.hsl(hue = it * 30f, saturation = 0.70f, lightness = 0.60f) }

@Composable
private fun DayOfWeek.fullName() = getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekStartRow(start: DayOfWeek, onChange: (DayOfWeek) -> Unit) {
    val options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(stringResource(R.string.settings_week_start), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.size(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, day ->
                SegmentedButton(
                    selected = start == day,
                    onClick = { onChange(day) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(day.fullName()) }
            }
        }
    }
}

@Composable
private fun DayColorRow(day: DayOfWeek, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.settings_day_color, day.fullName()), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(16.dp))
        Text(day.fullName(), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayColorDialog(day: DayOfWeek, current: Color, onPick: (Color?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_day_color, day.fullName())) },
        text = {
            // 4 per row keeps 12 swatches an even grid at the 48dp touch-target size.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4,
            ) {
                swatches.forEachIndexed { i, swatch ->
                    val selected = swatch.toArgb() == current.toArgb()
                    val label = stringResource(R.string.settings_color_swatch, i + 1, swatches.size)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) { onPick(swatch) }
                            .semantics { contentDescription = label },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.settings_color_reset)) } },
    )
}
