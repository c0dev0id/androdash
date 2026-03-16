package com.androdash

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val installTime: Long = 0L,
)

@Suppress("DEPRECATION")
private fun loadInstalledApps(pm: PackageManager): List<AppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
    return resolveInfos
        .map { ri ->
            val installTime = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        ri.activityInfo.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    ).firstInstallTime
                } else {
                    pm.getPackageInfo(ri.activityInfo.packageName, 0).firstInstallTime
                }
            } catch (_: PackageManager.NameNotFoundException) {
                0L
            }
            AppInfo(
                label = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon = ri.loadIcon(pm),
                installTime = installTime,
            )
        }
        .sortedByDescending { it.installTime }
}

private const val APPS_PER_PAGE = 24 // 4 columns x 6 rows

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val hiddenRepo = remember { HiddenAppsRepository(context) }
    var hiddenPackages by remember { mutableStateOf(hiddenRepo.getHiddenPackages()) }

    // Load installed apps
    LaunchedEffect(Unit) {
        allApps = loadInstalledApps(context.packageManager)
    }

    // Listen for app install/uninstall to refresh the list
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                allApps = loadInstalledApps(ctx.packageManager)
                hiddenPackages = hiddenRepo.getHiddenPackages()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Derived lists
    val visibleApps = allApps.filter { it.packageName !in hiddenPackages }
    val hiddenApps = allApps.filter { it.packageName in hiddenPackages }
    val visiblePages = visibleApps.chunked(APPS_PER_PAGE)

    // Page layout: [hidden | main | overflow...]
    // Page 0 = hidden apps, Page 1 = main (newest), Page 2+ = overflow
    val totalPages = 1 + visiblePages.size.coerceAtLeast(1)
    val mainPageIndex = 1

    val pagerState = rememberPagerState(
        initialPage = mainPageIndex,
        pageCount = { totalPages },
    )

    // Clamp current page if total pages shrinks (e.g., after unhiding all hidden apps)
    LaunchedEffect(totalPages) {
        if (pagerState.currentPage >= totalPages) {
            pagerState.scrollToPage(totalPages - 1)
        }
    }

    // Action handlers
    val onHideToggle: (AppInfo, Boolean) -> Unit = { app, isCurrentlyHidden ->
        if (isCurrentlyHidden) {
            hiddenRepo.setVisible(app.packageName)
        } else {
            hiddenRepo.setHidden(app.packageName)
        }
        hiddenPackages = hiddenRepo.getHiddenPackages()
    }

    val onStop: (AppInfo) -> Unit = { app ->
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(app.packageName)
    }

    val onUninstall: (AppInfo) -> Unit = { app ->
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        context.startActivity(intent)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E),
                            Color(0xFF0F3460),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            // Pager takes most of the space
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        // Hidden apps page
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Hidden",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                            )
                            AppGridPage(
                                apps = hiddenApps,
                                isHiddenPage = true,
                                onHideToggle = { app -> onHideToggle(app, true) },
                                onStop = onStop,
                                onUninstall = onUninstall,
                            )
                        }
                    }
                    else -> {
                        val pageApps = visiblePages.getOrElse(pageIndex - 1) { emptyList() }
                        AppGridPage(
                            apps = pageApps,
                            isHiddenPage = false,
                            onHideToggle = { app -> onHideToggle(app, false) },
                            onStop = onStop,
                            onUninstall = onUninstall,
                        )
                    }
                }
            }

            // Page indicator
            PageIndicator(
                pageCount = totalPages,
                currentPage = pagerState.currentPage,
                mainPageIndex = mainPageIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )
        }
    }
}
