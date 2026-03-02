package com.androdash

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)

// ── Animated app icon card ────────────────────────────────────────────────────

@Composable
fun AppCard(app: AppInfo) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }

    // Spring-based scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "icon-scale",
    )

    // Subtle background highlight on press
    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "icon-bg-alpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        val launch = context.packageManager
                            .getLaunchIntentForPackage(app.packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (launch != null) context.startActivity(launch)
                    },
                )
            }
            .scale(scale)
            .padding(8.dp),
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ── App drawer (slides up from bottom) ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(apps: List<AppInfo>, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(250),
        ) + fadeOut(animationSpec = tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        modifier = Modifier.animateItemPlacement(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ),
                    )
                }
            }
        }
    }
}

// Overload accepting a Modifier so animateItemPlacement can be threaded through
@Composable
fun AppCard(app: AppInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "icon-scale",
    )

    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "icon-bg-alpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        val launch = context.packageManager
                            .getLaunchIntentForPackage(app.packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (launch != null) context.startActivity(launch)
                    },
                )
            }
            .scale(scale)
            .padding(8.dp),
    ) {
        Image(
            bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Home screen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Load installed apps once
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    icon = ri.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    // Animated drawer handle height (pill indicator at bottom)
    val pillHeight by animateDpAsState(
        targetValue = if (drawerOpen) 3.dp else 5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pill-height",
    )

    // Wallpaper gradient darkens when drawer opens
    val gradientAlpha by animateFloatAsState(
        targetValue = if (drawerOpen) 0f else 1f,
        animationSpec = tween(300),
        label = "wallpaper-overlay-alpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Background gradient (sits behind everything)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460)),
                    ),
                ),
        )

        // Overlay that fades away when the drawer opens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D).copy(alpha = 1f - gradientAlpha)),
        )

        // Swipe-up hint pill at the bottom center
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(width = 48.dp, height = pillHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.5f))
                .clickable { drawerOpen = !drawerOpen },
        )

        // App drawer (animated slide-up panel)
        AppDrawer(apps = apps, visible = drawerOpen)

        // Tap outside the drawer to close it
        if (drawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    }) { drawerOpen = false },
            )
        }
    }
}
