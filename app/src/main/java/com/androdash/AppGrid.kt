package com.androdash

import android.content.Intent
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

// ── App icon card with long-press context menu ──────────────────────────────

@Composable
fun AppCard(
    app: AppInfo,
    isHidden: Boolean,
    onHideToggle: () -> Unit,
    onStop: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

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

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(4.dp)
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
                        onLongPress = {
                            menuExpanded = true
                        },
                    )
                }
                .scale(scale)
                .padding(8.dp),
        ) {
            Image(
                bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (isHidden) "Unhide" else "Hide") },
                onClick = {
                    menuExpanded = false
                    onHideToggle()
                },
            )
            DropdownMenuItem(
                text = { Text("Stop") },
                onClick = {
                    menuExpanded = false
                    onStop()
                },
            )
            DropdownMenuItem(
                text = { Text("Uninstall") },
                onClick = {
                    menuExpanded = false
                    onUninstall()
                },
            )
        }
    }
}

// ── Single page grid (4 columns x 6 rows) ──────────────────────────────────

@Composable
fun AppGridPage(
    apps: List<AppInfo>,
    isHiddenPage: Boolean,
    onHideToggle: (AppInfo) -> Unit,
    onStop: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    columns: Int = 4,
    rows: Int = 6,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 16.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < apps.size) {
                        val app = apps[index]
                        AppCard(
                            app = app,
                            isHidden = isHiddenPage,
                            onHideToggle = { onHideToggle(app) },
                            onStop = { onStop(app) },
                            onUninstall = { onUninstall(app) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Page indicator dots ─────────────────────────────────────────────────────

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    mainPageIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val dotSize by animateDpAsState(
                targetValue = if (isSelected) 10.dp else 6.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dot-size",
            )
            val dotAlpha by animateFloatAsState(
                targetValue = when {
                    isSelected -> 0.9f
                    index < mainPageIndex -> 0.25f // hidden section dots are dimmer
                    else -> 0.4f
                },
                animationSpec = tween(200),
                label = "dot-alpha",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha)),
            )
        }
    }
}
