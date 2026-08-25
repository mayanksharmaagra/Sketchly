package com.jrprofessor.sketchly.ui.screens.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.ui.navigation.Screen
import com.jrprofessor.sketchly.ui.theme.DeepCharcoal
import com.jrprofessor.sketchly.ui.theme.InkDefault
import com.jrprofessor.sketchly.ui.theme.NoteCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.Primary
import com.jrprofessor.sketchly.ui.theme.Tertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Metadata for each screen tile in the preview grid */
private data class ScreenTileInfo(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val accentColor: Color,
    val preview: @Composable () -> Unit,
)

@Preview
@Composable
fun PreviewScreen(){
    ScreenPreviewScreen()
}
@Composable
fun ScreenPreviewScreen(
    onNavigateTo: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val tiles = remember { buildTiles() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Screen Preview",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                    Text(
                        text = "${tiles.size} screens · tap to navigate",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Grid ──
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(tiles, key = { _, tile -> tile.route }) { index, tile ->
                    ScreenTile(
                        tile = tile,
                        index = index,
                        onClick = { onNavigateTo(tile.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenTile(
    tile: ScreenTileInfo,
    index: Int,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(0.82f) }

    LaunchedEffect(index) {
        delay(index * 60L)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        )
    }

    var pressed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .scale(scale.value)
            .clip(NoteCardShape)
            .clickable(onClick = onClick)
            .background(PaperIvory)
            .border(
                width = 1.dp,
                color = tile.accentColor.copy(alpha = 0.25f),
                shape = NoteCardShape,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Thumbnail Preview ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(tile.accentColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            tile.preview()
        }

        // ── Label row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(tile.accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = tile.accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = InkDefault,
                ),
            )
        }
    }
}

// ── Per-screen preview thumbnails ──

@Composable
private fun DrawPreview() {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val paint = androidx.compose.ui.graphics.Paint().apply {
            color = Primary
            strokeWidth = 6f
            strokeCap = StrokeCap.Round
        }

        // Simulate hand-drawn strokes
        val path1 = Path().apply {
            moveTo(w * 0.1f, h * 0.4f)
            cubicTo(w * 0.25f, h * 0.2f, w * 0.45f, h * 0.6f, w * 0.6f, h * 0.35f)
        }
        val path2 = Path().apply {
            moveTo(w * 0.2f, h * 0.65f)
            cubicTo(w * 0.4f, h * 0.8f, w * 0.65f, h * 0.5f, w * 0.85f, h * 0.7f)
        }

        drawPath(path1, Primary, style = DrawStroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path2, Tertiary, style = DrawStroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Color palette circles
        val palette = listOf(InkDefault, Primary, Tertiary, Color(0xFF8B6914), Color(0xFFBF4E6D))
        palette.forEachIndexed { i, color ->
            drawCircle(color, radius = 10f, center = Offset(w * 0.1f + i * 30f, h * 0.88f))
        }
    }
}

@Composable
private fun InboxPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { i ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PaperIvory)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Primary.copy(alpha = 0.15f + i * 0.05f), CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(7.dp)
                            .background(InkDefault.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(5.dp)
                            .background(InkDefault.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    )
                }
                if (i == 0) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Primary, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun CirclePreview() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val radii = listOf(70f, 100f, 130f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            radii.forEachIndexed { i, r ->
                drawCircle(
                    color = Primary.copy(alpha = 0.12f - i * 0.02f),
                    radius = r,
                    center = center,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Group,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Primary.copy(alpha = 0.2f), CircleShape),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SettingsPreview() {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(DeepCharcoal.copy(alpha = 0.1f), CircleShape)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AccountCircle, null, tint = DeepCharcoal, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PaperIvory)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(10.dp).background(DeepCharcoal.copy(alpha = 0.3f), CircleShape))
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(5.dp).background(InkDefault.copy(0.35f), RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun ArchivePreview() {
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Filter chips row
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("All", "Sent", "Recv").forEach { label ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (label == "All") Tertiary else Tertiary.copy(0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 7.sp,
                        color = if (label == "All") Color.White else Tertiary,
                    )
                }
            }
        }
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PaperIvory.copy(alpha = 0.9f))
            )
        }
    }
}

@Composable
private fun AuthPreview() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = Primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Primary.copy(0.3f), RoundedCornerShape(8.dp))
                    .background(Color.White)
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(50))
                .background(Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Sign In", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ViewerPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperIvory),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.05f, h * 0.5f)
                cubicTo(w * 0.25f, h * 0.15f, w * 0.55f, h * 0.8f, w * 0.95f, h * 0.3f)
            }
            drawPath(path, DeepCharcoal, style = DrawStroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Emoji bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(0.07f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("❤️", "😂", "👏", "🔥").forEach { emoji ->
                Text(text = emoji, fontSize = 10.sp)
            }
        }
    }
}

private fun buildTiles(): List<ScreenTileInfo> = listOf(
    ScreenTileInfo(
        label = "Draw",
        icon = Icons.Rounded.Draw,
        route = Screen.Draw.route,
        accentColor = Primary,
        preview = { DrawPreview() },
    ),
    ScreenTileInfo(
        label = "Inbox",
        icon = Icons.Rounded.Inbox,
        route = Screen.Inbox.route,
        accentColor = Color(0xFF4A6FA5),
        preview = { InboxPreview() },
    ),
    ScreenTileInfo(
        label = "Circle",
        icon = Icons.Rounded.Group,
        route = Screen.Circle.route,
        accentColor = Primary,
        preview = { CirclePreview() },
    ),
    ScreenTileInfo(
        label = "Archive",
        icon = Icons.Rounded.Archive,
        route = Screen.Archive.route,
        accentColor = Tertiary,
        preview = { ArchivePreview() },
    ),
    ScreenTileInfo(
        label = "Viewer",
        icon = Icons.Rounded.Visibility,
        route = Screen.Viewer.createRoute("preview"),
        accentColor = DeepCharcoal,
        preview = { ViewerPreview() },
    ),
    ScreenTileInfo(
        label = "Settings",
        icon = Icons.Rounded.Settings,
        route = Screen.Settings.route,
        accentColor = DeepCharcoal,
        preview = { SettingsPreview() },
    ),
    ScreenTileInfo(
        label = "Sign In",
        icon = Icons.Rounded.Lock,
        route = Screen.Auth.route,
        accentColor = Primary,
        preview = { AuthPreview() },
    ),
)
