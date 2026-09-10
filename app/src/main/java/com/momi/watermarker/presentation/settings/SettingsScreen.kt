package com.momi.watermarker.presentation.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.momi.watermarker.R
import com.momi.watermarker.domain.model.ThemeMode
import com.momi.watermarker.presentation.theme.ThemeViewModel

/**
 * App settings: appearance (light / dark / system) plus about / version details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onExit: () -> Unit = {},
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onExit)

    val context = LocalContext.current
    val info = remember(context) { AppInfo.from(context) }
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text(stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.app_studio_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.settings_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AppearanceCard(
                selected = themeMode,
                onSelect = themeViewModel::onThemeModeSelected,
            )

            InfoCard(
                title = stringResource(R.string.settings_about),
                rows = listOf(
                    stringResource(R.string.settings_version) to info.versionName,
                    stringResource(R.string.settings_build) to info.versionCode.toString(),
                    stringResource(R.string.settings_package) to info.packageName,
                    stringResource(R.string.settings_build_type) to stringResource(
                        if (info.isDebuggable) R.string.settings_build_debug
                        else R.string.settings_build_release,
                    ),
                ),
            )

            InfoCard(
                title = stringResource(R.string.settings_device),
                rows = listOf(
                    stringResource(R.string.settings_android) to stringResource(
                        R.string.settings_android_value,
                        info.androidRelease,
                        info.sdkInt,
                    ),
                    stringResource(R.string.settings_min_sdk) to info.minSdk.toString(),
                    stringResource(R.string.settings_target_sdk) to info.targetSdk.toString(),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(stringResource(mode.labelRes))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
                InfoRow(label = label, value = value)
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val isDebuggable: Boolean,
) {
    companion object {
        fun from(context: Context): AppInfo {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val applicationInfo = packageInfo.applicationInfo
            return AppInfo(
                versionName = packageInfo.versionName
                    ?: context.getString(R.string.settings_unknown_version),
                versionCode = packageInfo.longVersionCode,
                packageName = context.packageName,
                minSdk = applicationInfo?.minSdkVersion ?: 0,
                targetSdk = applicationInfo?.targetSdkVersion ?: 0,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                isDebuggable = applicationInfo != null &&
                    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
        }
    }
}
