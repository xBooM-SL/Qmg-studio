package com.namal.qmgstudio

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* ================= palette ================= */

private val Ink = Color(0xFF050B18)
private val Cyan = Color(0xFF7DE3FF)      // ice
private val Violet = Color(0xFF4F8CFF)    // azure
private val Pink = Color(0xFF9ED0FF)      // pale azure (kept name, no longer pink)
private val Mint = Color(0xFF6FF0D8)      // teal
private val Sakura = Color(0xFFDDF3FF)    // frost
private val Peach = Color(0xFFA9C7FF)     // soft blue
private val Danger = Color(0xFFFF6B6B)

private val Scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Cyan,
    background = Ink,
    surface = Color(0xFF0E1018),
    onSurface = Color.White,
    onBackground = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = Scheme) { App() } }
    }
}

/* ================= liquid ocean backdrop ================= */

@Composable
private fun NightBackdrop(animate: Boolean) {
    val inf = rememberInfiniteTransition(label = "bg")
    val t = inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    val bubbles = remember {
        val r = Random(11)
        // x, phase, size, speed, wobble
        List(26) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.8f, (1 + r.nextInt(3)).toFloat(), r.nextFloat()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tt = if (animate) t.value else 0.3f
        val twoPi = (2.0 * PI).toFloat()
        val ang = tt * twoPi

        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF030814),
                0.5f to Color(0xFF07173A),
                1f to Color(0xFF0A3566),
                startY = 0f,
                endY = h
            )
        )
        // slow drifting liquid orbs
        fun orb(cx: Float, cy: Float, r: Float, c: Color) {
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        }
        orb(w * (0.20f + 0.14f * sin(ang)), h * (0.20f + 0.06f * cos(ang)), w * 0.85f, Color(0x663D7BFF))
        orb(w * (0.85f - 0.12f * cos(ang)), h * (0.45f + 0.07f * sin(ang)), w * 0.80f, Color(0x4437D5D6))
        orb(w * (0.40f + 0.18f * cos(ang * 1f + 1f)), h * (0.78f - 0.06f * sin(ang)), w * 0.90f, Color(0x553355FF))
        orb(w * (0.75f + 0.08f * sin(ang + 2f)), h * (0.10f + 0.04f * cos(ang)), w * 0.55f, Color(0x33A5E8FF))

        // two liquid waves at the bottom
        fun wave(base: Float, amp: Float, speed: Float, phase: Float, c: Color) {
            val path = Path()
            path.moveTo(0f, h)
            val steps = 28
            for (i in 0..steps) {
                val x = w * i / steps
                val y = h * base + amp * h * sin(twoPi * (i.toFloat() / steps * 1.3f + tt * speed + phase))
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, c)
        }
        wave(0.90f, 0.012f, 2f, 0.0f, Color(0x331E5FFF))
        wave(0.94f, 0.010f, -3f, 0.4f, Color(0x4412A4D8))

        // rising bubbles
        for (b in bubbles) {
            val rise = 1f - ((tt * b[3] + b[1]) % 1f)
            val y = rise * (h + 40f) - 20f
            val x = b[0] * w + sin(twoPi * (tt * b[3] * 2f + b[4])) * 14f
            val rr = b[2] * density * 3.2f
            drawCircle(Color(0x14BFE6FF), radius = rr, center = Offset(x, y))
            drawCircle(Color(0x55DDF3FF), radius = rr, center = Offset(x, y), style = Stroke(width = 1f * density))
            drawCircle(Color(0x99FFFFFF), radius = rr * 0.25f, center = Offset(x - rr * 0.35f, y - rr * 0.35f))
        }
    }
}

@Composable
private fun LogoMark(sizeDp: Int, fast: Boolean) {
    val tr = rememberInfiniteTransition(label = "logo")
    val deg by tr.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (fast) 1600 else 9000, easing = LinearEasing), RepeatMode.Restart),
        label = "deg"
    )
    Canvas(Modifier.size(sizeDp.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.40f
        rotate(deg, c) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(Cyan, Violet, Pink, Cyan), c),
                radius = r,
                center = c,
                style = Stroke(width = size.minDimension * 0.13f)
            )
        }
        val p = Path()
        p.moveTo(c.x - r * 0.30f, c.y - r * 0.45f)
        p.lineTo(c.x + r * 0.52f, c.y)
        p.lineTo(c.x - r * 0.30f, c.y + r * 0.45f)
        p.close()
        drawPath(p, Color.White)
    }
}

/* ================= building blocks ================= */

@Composable
private fun GlassCard(title: String, accent: Color = Cyan, badge: String? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    // slow flowing wave along the bottom of every card
    val wtr = rememberInfiniteTransition(label = "cardwave")
    val wph by wtr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "wph"
    )
    var pressed by remember { mutableStateOf(false) }
    val pop by animateFloatAsState(if (pressed) 0.97f else 1f, spring(), label = "pop")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .clip(shape)
            .background(Color(0x990A1830))
            .drawBehind {
                val tp = (2.0 * PI).toFloat()
                fun wv(base: Float, amp: Float, ph: Float, dir: Float, c: Color) {
                    val path = Path()
                    path.moveTo(0f, size.height)
                    val n = 24
                    for (i in 0..n) {
                        val x = size.width * i / n
                        val y = size.height * base + size.height * amp * sin(tp * (i.toFloat() / n * 1.4f + dir * wph + ph))
                        path.lineTo(x, y)
                    }
                    path.lineTo(size.width, size.height)
                    path.close()
                    drawPath(path, c)
                }
                wv(0.80f, 0.030f, 0.00f, 1f, accent.copy(alpha = 0.16f))
                wv(0.88f, 0.025f, 0.35f, -1f, accent.copy(alpha = 0.22f))
            }
            .background(Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF), Color(0x1AFFFFFF))))
            .drawBehind {
                // glossy highlight across the top edge
                drawRect(
                    Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color.Transparent), startY = 0f, endY = size.height * 0.22f),
                    size = Size(size.width, size.height * 0.22f)
                )
            }
            .border(1.dp, Brush.linearGradient(listOf(Color(0xAAFFFFFF), Color(0x14FFFFFF), Color(0x66FFFFFF))), shape)
            .then(
                if (onClick != null) Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
                } else Modifier
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, Violet))),
                    contentAlignment = Alignment.Center
                ) { Text(badge, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title.uppercase(),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        content()
    }
}

@Composable
private fun GlowButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "glow")
    val shift by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "shift"
    )
    val shape = RoundedCornerShape(18.dp)
    val brush = if (enabled) {
        Brush.linearGradient(
            listOf(Cyan, Violet, Pink),
            start = Offset(shift, 0f),
            end = Offset(shift + 600f, 0f),
            tileMode = TileMode.Mirror
        )
    } else {
        Brush.linearGradient(listOf(Color(0xFF2A2D3A), Color(0xFF2A2D3A)))
    }
    val base = if (enabled) Modifier.shadow(14.dp, shape, ambientColor = Violet, spotColor = Cyan) else Modifier
    Box(
        base
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Color(0xFF7C8094),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.7f), Violet.copy(alpha = 0.7f))), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun MiniButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val fill = if (selected) Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.32f), Violet.copy(alpha = 0.55f)))
    else Brush.horizontalGradient(listOf(Color(0x14FFFFFF), Color(0x14FFFFFF)))
    Text(
        label,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, if (selected) Cyan else Color.White.copy(alpha = 0.2f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun Pill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val tap = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .then(tap)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Hint(hint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Violet,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0x22FFFFFF),
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Cyan,
    activeTrackColor = Violet,
    inactiveTrackColor = Color.White.copy(alpha = 0.14f)
)

@Composable
private fun PhoneMock(aspect: Float, height: Dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .height(height)
            .aspectRatio(aspect)
            .shadow(18.dp, shape, ambientColor = Violet, spotColor = Cyan)
            .clip(shape)
            .background(Color.Black)
            .border(3.dp, Brush.verticalGradient(listOf(Color(0xFF454A63), Color(0xFF15171F))), shape)
    ) {
        content()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B0B10))
                .border(1.dp, Color(0xFF2B2E3B), CircleShape)
        )
    }
}

@Composable
private fun NavBar(tab: Int, onTab: (Int) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val items = listOf("🎬" to "Create", "▶️" to "Preview", "💾" to "Save", "🧰" to "Tools")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
            .shadow(16.dp, shape, ambientColor = Violet, spotColor = Cyan)
            .clip(shape)
            .background(Color(0xCC0A1830))
            .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xAAFFFFFF), Color(0x14FFFFFF), Color(0x66FFFFFF))), shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in items.indices) {
            val sel = tab == i
            val sc by animateFloatAsState(if (sel) 1.08f else 1f, spring(), label = "navscale")
            val fill = if (sel) Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.28f), Violet.copy(alpha = 0.5f)))
            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(fill)
                    .clickable { onTab(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.graphicsLayer { scaleX = sc; scaleY = sc },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(items[i].first, fontSize = 17.sp)
                    Text(
                        items[i].second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sel) Color.White else Color.White.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Toast(text: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .systemBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Cyan, spotColor = Violet)
            .clip(shape)
            .background(Color(0xE60A1830))
            .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xAAFFFFFF), Color(0x22FFFFFF), Color(0x66FFFFFF))), shape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

private fun sizeText(bytes: Long): String =
    if (bytes >= 1024L * 1024L) String.format("%.1f MB", bytes / 1048576.0) else (bytes / 1024).toString() + " KB"

private fun mmss(s: Float): String {
    val t = s.coerceAtLeast(0f)
    val m = (t / 60f).toInt()
    return m.toString() + ":" + String.format("%04.1f", t - m * 60f)
}

private fun fitName(f: Fit): String = when (f) {
    Fit.FILL -> "Fill"
    Fit.FIT -> "Fit"
    Fit.STRETCH -> "Stretch"
    Fit.GLOW -> "Fit + glow"
}

/* ---------- live preview drawing (reads the frame index only while drawing, so playing never recomposes the screen) ---------- */

@Composable
private fun LiveCanvas(images: List<ImageBitmap>, fit: Fit, crop: Float, fadeFrames: Int, totalFrames: Int, idx: () -> Int) {
    val edges = remember(images) { arrayOfNulls<IntArray>(images.size) }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.Black)
        if (images.isEmpty()) return@Canvas
        val i = idx().coerceIn(0, images.size - 1)
        val img = images[i]
        val dw = size.width
        val dh = size.height
        val fw = img.width.toFloat()
        val fh = img.height.toFloat()
        when (fit) {
            Fit.FILL -> {
                val s = maxOf(dw / fw, dh / fh)
                val sw = (dw / s).toInt().coerceIn(1, img.width)
                val sh = (dh / s).toInt().coerceIn(1, img.height)
                drawImage(
                    img,
                    srcOffset = IntOffset(((img.width - sw) * crop).toInt(), ((img.height - sh) * crop).toInt()),
                    srcSize = IntSize(sw, sh),
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(dw.toInt(), dh.toInt())
                )
            }
            Fit.FIT -> {
                val s = minOf(dw / fw, dh / fh)
                val ow = (fw * s).toInt().coerceAtLeast(1)
                val oh = (fh * s).toInt().coerceAtLeast(1)
                drawImage(
                    img,
                    dstOffset = IntOffset(((dw - ow) / 2f).toInt(), ((dh - oh) / 2f).toInt()),
                    dstSize = IntSize(ow, oh)
                )
            }
            Fit.STRETCH -> drawImage(img, dstOffset = IntOffset(0, 0), dstSize = IntSize(dw.toInt(), dh.toInt()))
            Fit.GLOW -> {
                val s = minOf(dw / fw, dh / fh)
                val ow = (fw * s).toInt().coerceAtLeast(1)
                val oh = (fh * s).toInt().coerceAtLeast(1)
                val ox = (dw - ow) / 2f
                val oy = (dh - oh) / 2f
                val ec = edges[i] ?: (try { Engine.edgeColors(img.asAndroidBitmap()) } catch (e: Exception) { intArrayOf(0, 0, 0, 0) }).also { edges[i] = it }
                fun dk(c: Int, f: Float) = Color((android.graphics.Color.red(c) * f).toInt(), (android.graphics.Color.green(c) * f).toInt(), (android.graphics.Color.blue(c) * f).toInt())
                if (oy > 1f) {
                    drawRect(Brush.verticalGradient(listOf(dk(ec[0], 0.30f), dk(ec[0], 0.85f)), startY = 0f, endY = oy), Offset(0f, 0f), Size(dw, oy))
                    drawRect(Brush.verticalGradient(listOf(dk(ec[1], 0.85f), dk(ec[1], 0.30f)), startY = dh - oy, endY = dh), Offset(0f, dh - oy), Size(dw, oy))
                }
                if (ox > 1f) {
                    drawRect(Brush.horizontalGradient(listOf(dk(ec[2], 0.30f), dk(ec[2], 0.85f)), startX = 0f, endX = ox), Offset(0f, 0f), Size(ox, dh))
                    drawRect(Brush.horizontalGradient(listOf(dk(ec[3], 0.85f), dk(ec[3], 0.30f)), startX = dw - ox, endX = dw), Offset(dw - ox, 0f), Size(ox, dh))
                }
                drawImage(
                    img,
                    dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                    dstSize = IntSize(ow, oh)
                )
            }
        }
        if (fadeFrames > 0) {
            val n = totalFrames.coerceAtLeast(1)
            var gain = 256
            if (i < fadeFrames) gain = 256 * (i + 1) / (fadeFrames + 1)
            if (n - 1 - i < fadeFrames) gain = minOf(gain, 256 * (n - i) / (fadeFrames + 1))
            drawRect(Color.Black.copy(alpha = (1f - gain / 256f).coerceIn(0f, 1f)))
        }
    }
}

@Composable
private fun Timeline(durSec: Float, startSec: Float, spanSec: Float) {
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val pad = 9.dp.toPx()
        val w = size.width - pad * 2f
        val barH = 8.dp.toPx()
        val y = (size.height - barH) / 2f
        val d = durSec.coerceAtLeast(0.1f)
        drawRoundRect(Color.White.copy(alpha = 0.13f), Offset(pad, y), Size(w, barH), CornerRadius(barH / 2f))
        val x0 = pad + (startSec / d).coerceIn(0f, 1f) * w
        val x1 = pad + ((startSec + spanSec) / d).coerceIn(0f, 1f) * w
        val sw = maxOf(x1 - x0, 6f)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Cyan, Violet, Pink), startX = x0, endX = x0 + sw),
            Offset(x0, y), Size(sw, barH), CornerRadius(barH / 2f)
        )
        drawCircle(Color.White, 7.dp.toPx(), Offset(x0, size.height / 2f))
        drawCircle(Color.White, 7.dp.toPx(), Offset(x0 + sw, size.height / 2f))
    }
}

/* ================= main screen ================= */

@Composable
private fun App() {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var tab by remember { mutableIntStateOf(0) }
    var rootState by remember { mutableIntStateOf(Engine.rootState) }
    var makeBoot by remember { mutableStateOf(true) }
    var makeShutdown by remember { mutableStateOf(false) }
    var libOk by remember { mutableStateOf(Engine.findLib(ctx) != null) }
    var target by remember { mutableStateOf(Engine.loadTarget(ctx) ?: Engine.detectTarget(ctx, false)) }

    // source
    var srcUri by remember { mutableStateOf<Uri?>(null) }
    var srcName by remember { mutableStateOf("") }
    var srcDurMs by remember { mutableLongStateOf(0L) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }

    // options
    var startSec by remember { mutableFloatStateOf(0f) }
    var lenSec by remember { mutableFloatStateOf(6f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var fit by remember { mutableStateOf(Fit.FILL) }
    var crop by remember { mutableStateOf(0.5f) }
    var fadeFrames by remember { mutableIntStateOf(0) }
    var quality by remember { mutableIntStateOf(1) }
    var dither by remember { mutableStateOf(false) }
    var useTarget by remember { mutableStateOf(false) }
    var targetMb by remember { mutableFloatStateOf(2f) }

    // live stage (plays the chosen part of your clip with the current settings)
    var stageImages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var stageIdx by remember { mutableIntStateOf(0) }
    var stagePlaying by remember { mutableStateOf(true) }
    var clipLoading by remember { mutableStateOf(false) }
    var clipPct by remember { mutableIntStateOf(0) }
    var exactBmp by remember { mutableStateOf<Bitmap?>(null) }
    var exactBytes by remember { mutableIntStateOf(0) }
    var exactBusy by remember { mutableStateOf(false) }

    // work
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var convStart by remember { mutableLongStateOf(0L) }
    var liveFrame by remember { mutableStateOf<Bitmap?>(null) }
    var liveBytes by remember { mutableLongStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }
    val built = remember { mutableStateMapOf<Int, Built>() }
    var status by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }

    // app update (checked on open, or from the About card)
    var update by remember { mutableStateOf<Updater.Info?>(null) }
    var updBusy by remember { mutableStateOf(false) }
    var updProgress by remember { mutableFloatStateOf(0f) }
    var updChecking by remember { mutableStateOf(false) }

    fun checkUpdate(manual: Boolean) {
        if (updChecking) return
        updChecking = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Updater.check(ctx) } }
            updChecking = false
            val info = r.getOrNull()
            if (info != null) update = info
            else if (manual) status = if (r.isFailure) "Could not check for updates. Check your internet." else "You are on the latest version ✓"
        }
    }

    fun startUpdate(info: Updater.Info) {
        if (!Updater.canInstall(ctx)) {
            status = "Allow installs from QMG Studio, come back and tap Update again."
            Updater.openInstallPermission(ctx)
            return
        }
        updBusy = true
        updProgress = 0f
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Updater.download(ctx, info) { updProgress = it } } }
            updBusy = false
            val f = r.getOrNull()
            if (f != null) {
                update = null
                Updater.install(ctx, f)
            } else {
                status = "Update failed: " + (r.exceptionOrNull()?.message ?: "unknown error")
            }
        }
    }

    // result player
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewVersion by remember { mutableIntStateOf(0) }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    var playing by remember { mutableStateOf(true) }
    var frameNo by remember { mutableIntStateOf(0) }
    var frameTotal by remember { mutableIntStateOf(0) }
    var verifyText by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var gifBusy by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }

    // derived values
    val durSec = if (srcDurMs > 0) srcDurMs / 1000f else 3f
    val maxLen = minOf(Engine.MAX_SECONDS.toFloat(), ((durSec - startSec) / speed)).coerceAtLeast(1f)
    val len = lenSec.coerceIn(1f, maxLen)
    val maxStart = (durSec - len * speed).coerceAtLeast(0f)
    val frames = (len * Engine.FPS).toInt()
    val aspect = if (target.h > 0) target.w.toFloat() / target.h else 0.45f

    /* ----- pickers ----- */

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            srcUri = uri
            scope.launch {
                val info = withContext(Dispatchers.IO) {
                    Triple(Engine.displayName(ctx, uri), Engine.durationMs(ctx, uri), Engine.thumbnail(ctx, uri, 0L))
                }
                srcName = info.first
                srcDurMs = info.second
                startSec = 0f
                lenSec = minOf(6f, if (info.second > 0) info.second / 1000f else 3f).coerceAtLeast(1f)
                exactBmp = null
                val th = info.third
                stageImages = if (th != null) listOf(th.asImageBitmap()) else emptyList()
                stageIdx = 0
                status = ""
            }
        }
    }
    val libPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val ok = Engine.importLib(ctx, uri) && Engine.findLib(ctx) != null
            libOk = ok
            status = if (ok) "✅ Library imported" else "❌ That file did not work"
        }
    }
    val qmgOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val f = File(ctx.cacheDir, "opened.qmg")
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    withContext(Dispatchers.Main) {
                        previewFile = f; previewVersion++; playing = true; tab = 1
                        status = "Opened " + Engine.displayName(ctx, uri)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { status = "❌ Cannot open that file" }
                }
            }
        }
    }
    val saverBoot = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val f = built[0]?.file
        if (uri != null && f != null) {
            scope.launch(Dispatchers.IO) {
                val ok = Engine.writeTo(ctx, uri) { o -> f.inputStream().use { it.copyTo(o) } }
                withContext(Dispatchers.Main) { status = if (ok) "💾 bootsamsung.qmg saved ✔" else "❌ Save failed: " + Engine.lastError }
            }
        }
    }
    val saverShut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val f = built[1]?.file
        if (uri != null && f != null) {
            scope.launch(Dispatchers.IO) {
                val ok = Engine.writeTo(ctx, uri) { o -> f.inputStream().use { it.copyTo(o) } }
                withContext(Dispatchers.Main) { status = if (ok) "💾 shutdown.qmg saved ✔" else "❌ Save failed: " + Engine.lastError }
            }
        }
    }
    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) {
            val boot = built[0]?.file
            val shut = built[1]?.file
            val dir = target.stockDir
            scope.launch(Dispatchers.IO) {
                val ok = Engine.writeTo(ctx, uri) { o -> Engine.writeModuleZip(boot, boot, shut, dir, o) }
                withContext(Dispatchers.Main) { status = if (ok) "📦 Module zip saved ✔" else "❌ Save failed: " + Engine.lastError }
            }
        }
    }
    val gifSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/gif")) { uri: Uri? ->
        val f = previewFile
        val lib = Engine.findLib(ctx)
        if (uri != null && f != null && lib != null) {
            gifBusy = true
            status = "🎞️ Making GIF…"
            scope.launch(Dispatchers.IO) {
                var err: String? = null
                val ok = Engine.writeTo(ctx, uri) { o ->
                    err = Gif.export(f, lib.absolutePath, o) { d, t -> status = "🎞️ GIF frame $d / $t" }
                }
                withContext(Dispatchers.Main) {
                    gifBusy = false
                    status = if (err != null) "❌ " + err else if (ok) "🎞️ GIF saved ✔" else "❌ Save failed: " + Engine.lastError
                }
            }
        }
    }

    fun clipBase(): String = Engine.baseName(srcName)
    fun saveDirect(fileName: String, mime: String, okText: String, write: (java.io.OutputStream) -> Unit) {
        status = "💾 Saving…"
        scope.launch(Dispatchers.IO) {
            val where = Engine.saveMain(ctx, fileName, mime, write)
            withContext(Dispatchers.Main) {
                status = if (where != null) "$okText → $where ✔" else "❌ Save failed: " + Engine.lastError
            }
        }
    }

    /* ----- actions ----- */

    fun toggleBoot() { if (makeBoot && !makeShutdown) return; makeBoot = !makeBoot }
    fun toggleShutdown() { if (makeShutdown && !makeBoot) return; makeShutdown = !makeShutdown }

    // instant single frame while the start slider moves (the full clip reloads right after)
    fun scrubTo() {
        val u = srcUri ?: return
        scrubJob?.cancel()
        scrubJob = scope.launch {
            delay(120)
            val b = withContext(Dispatchers.IO) { Engine.thumbnail(ctx, u, (startSec * 1000).toLong()) }
            if (b != null) { stageImages = listOf(b.asImageBitmap()); stageIdx = 0 }
        }
    }

    fun runExact() {
        val u = srcUri ?: return
        if (!libOk || exactBusy || busy) return
        exactBusy = true
        val i = stageIdx
        scope.launch {
            val tMs = (startSec * 1000).toLong() + (i * 1000f / Engine.FPS * speed).toLong()
            val src = withContext(Dispatchers.IO) { Engine.frameAt(ctx, u, tMs) }
            val r = if (src != null) Engine.previewFrame(ctx, src, target, fit, dither, quality) else null
            if (r != null) {
                exactBmp = r.first
                exactBytes = r.second
            } else {
                status = "Exact view is busy, try again in a moment"
            }
            exactBusy = false
        }
    }

    fun startConvert() {
        val uri = srcUri ?: return
        busy = true
        status = ""
        done = 0
        total = 0
        convStart = System.currentTimeMillis()
        liveFrame = null
        liveBytes = 0L
        exactBmp = null
        val wantBoot = makeBoot
        val wantShut = makeShutdown
        val bootFile = File(ctx.filesDir, "boot.qmg")
        val shutFile = File(ctx.filesDir, "shutdown.qmg")
        val first = if (wantBoot) bootFile else shutFile
        val opts = Opts(
            startMs = (startSec * 1000).toLong(), lenSec = len, quality = quality, fit = fit,
            dither = dither, fadeFrames = fadeFrames, speed = speed,
            targetKb = if (useTarget) (targetMb * 1024).toInt() else 0
        )
        job = scope.launch {
            try {
                val b = Engine.convert(ctx, uri, target, opts, first, { d, t -> done = d; total = t }) { bm, bytes ->
                    liveFrame = bm
                    liveBytes = bytes
                }
                built.clear()   // never keep results of an older convert
                if (wantBoot) built[0] = b
                if (wantShut) {
                    if (first != shutFile) first.copyTo(shutFile, overwrite = true)
                    built[1] = b.copy(file = shutFile)
                }
                previewFile = first
                previewVersion++
                playing = true
                verifyText = ""
                val secs = (System.currentTimeMillis() - convStart) / 1000
                status = "✅ Done in " + secs + " s: " + b.frames + " frames, " + sizeText(b.bytes)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tab = 1
            } catch (e: CancellationException) {
                status = "Cancelled"
                throw e
            } catch (e: OutOfMemoryError) {
                status = "❌ Out of memory. Try a shorter clip or turn off the size target."
            } catch (e: Exception) {
                val at = e.stackTrace.firstOrNull { it.className.startsWith("com.namal") }
                val where = if (at != null) " @ " + at.fileName + ":" + at.lineNumber else ""
                status = "❌ " + e.javaClass.simpleName + ": " + (e.message ?: "no message") + where
            } finally {
                busy = false
                liveFrame = null
            }
        }
    }

    fun requestRootUi() {
        status = "🔐 Asking for root… (allow it in your root manager)"
        scope.launch(Dispatchers.IO) {
            val ok = Engine.requestRoot()
            withContext(Dispatchers.Main) {
                rootState = if (ok) 1 else 2
                status = if (ok) "✅ Root granted" else "❌ Root not available or denied. You can still make animations and save the module (.zip) to flash yourself."
            }
        }
    }

    fun detectWithRoot() {
        status = "🔎 Looking for your stock animation… (allow root if asked)"
        scope.launch(Dispatchers.IO) {
            val t = Engine.detectTarget(ctx, true)
            Engine.saveTarget(ctx, t)
            withContext(Dispatchers.Main) {
                target = t
                status = "📱 Using " + t.w + "×" + t.h + " (" + t.source + ")"
            }
        }
    }

    fun backup() {
        if (backingUp) return
        backingUp = true
        status = "🛟 Backing up… (allow root if asked)"
        scope.launch(Dispatchers.IO) {
            val n = Engine.backupStock(ctx)
            val t = Engine.detectTarget(ctx, true)
            withContext(Dispatchers.Main) {
                if (t.stockDir != null) { target = t; Engine.saveTarget(ctx, t) }
                status = if (n > 0) "🛟 Backed up $n file(s) → " + Engine.lastBackupDir
                else "❌ No stock .qmg found / root denied. " + Engine.lastError
                backingUp = false
            }
        }
    }

    fun runVerify() {
        val f = previewFile ?: return
        verifying = true
        verifyText = "Checking…"
        scope.launch(Dispatchers.IO) {
            val r = Engine.verify(ctx, f)
            withContext(Dispatchers.Main) { verifyText = r; verifying = false }
        }
    }

    /* ----- effects ----- */

    // the exact (real encoder) picture is only valid for the settings it was made with
    LaunchedEffect(fit, dither, quality, startSec, lenSec, speed, srcUri, target) { exactBmp = null }

    // loads the chosen part of the clip as small frames (debounced) so the stage can play it live
    LaunchedEffect(srcUri, startSec, lenSec, speed) {
        val u = srcUri ?: return@LaunchedEffect
        delay(400)
        clipLoading = true
        clipPct = 0
        val c = try {
            Engine.previewClip(ctx, u, (startSec * 1000).toLong(), len, speed) { d, t -> clipPct = d * 100 / maxOf(1, t) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (c != null) {
            stageImages = c.frames.map { it.asImageBitmap() }
            stageIdx = 0
        }
        clipLoading = false
    }

    // stage player: just steps an index at the animation's own frame rate
    LaunchedEffect(stageImages, stagePlaying, tab, busy, exactBmp) {
        if (tab != 0 || !stagePlaying || busy || stageImages.size < 2 || exactBmp != null) return@LaunchedEffect
        while (isActive) {
            delay(1000L / Engine.FPS)
            stageIdx = (stageIdx + 1) % stageImages.size
        }
    }

    // result player: decodes the .qmg frame by frame, the same way the phone will
    LaunchedEffect(previewFile, previewVersion, tab, playing) {
        val f = previewFile
        val lib = Engine.findLib(ctx)
        if (tab != 1 || !playing || f == null || lib == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                while (isActive) {
                    val n = Reader.open(f.absolutePath, lib.absolutePath)
                    if (n <= 0) {
                        status = "⚠️ Cannot open this .qmg for preview"
                        break
                    }
                    frameTotal = n
                    var i = 0
                    while (isActive) {
                        val t0 = System.nanoTime()
                        val b = Reader.next(true) ?: break
                        previewBmp = b
                        i++
                        frameNo = i
                        val spent = (System.nanoTime() - t0) / 1000000L
                        delay(maxOf(1L, 1000L / Engine.FPS - spent))
                    }
                    val rc = Reader.lastRc
                    Reader.close()
                    if (rc < 0) {
                        status = if (rc == -5) "⚠️ Preview not available: this file uses features the built-in decoder does not support yet"
                        else "⚠️ Preview error ($rc)"
                        break
                    }
                }
            } finally {
                Reader.close()
            }
        }
    }

    val scroll = rememberScrollState()
    // ask for root when the app opens, then learn the size of this phone's stock animation
    LaunchedEffect(Unit) {
        val ok = withContext(Dispatchers.IO) { Engine.requestRoot() }
        rootState = if (ok) 1 else 2
        if (ok) {
            if (Engine.loadTarget(ctx) == null) {
                val t = withContext(Dispatchers.IO) { Engine.detectTarget(ctx, true) }
                Engine.saveTarget(ctx, t)
                target = t
            }
        } else {
            status = "Root is not available. You can make animations and save a module (.zip) to flash yourself; one-tap install needs root."
        }
    }

    LaunchedEffect(tab) { scroll.scrollTo(0) }

    // look for a newer release every time the app opens
    LaunchedEffect(Unit) { checkUpdate(false) }

    /* ----- layout ----- */

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val stageH = (maxHeight * 0.27f).coerceIn(170.dp, 260.dp)
        val bigPhoneH = (maxHeight * 0.5f).coerceIn(260.dp, 430.dp)

        NightBackdrop(!busy)

        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // header
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LogoMark(42, busy)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "QMG Studio",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(listOf(Cyan, Violet, Pink)),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Pill(Engine.deviceName(), Cyan)
                        Pill("Android " + Build.VERSION.RELEASE, Violet)
                        Pill(target.w.toString() + "×" + target.h, Violet)
                        Pill(if (libOk) "Decoder ✓" else "Decoder ✗", if (libOk) Mint else Danger)
                        Pill(
                            when (rootState) { 1 -> "Root ✓"; 2 -> "Root ✗ (tap)"; else -> "Root …" },
                            when (rootState) { 1 -> Mint; 2 -> Danger; else -> Peach }
                        ) { requestRootUi() }
                    }
                }
            }

            // pinned stage: live preview while editing, progress while converting
            if (tab == 0 && busy) {
                val shape = RoundedCornerShape(24.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(shape)
                        .background(Color(0xE60A1630))
                        .border(1.dp, Brush.horizontalGradient(listOf(Pink.copy(alpha = 0.6f), Violet.copy(alpha = 0.6f))), shape)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PhoneMock(aspect, stageH) {
                        val lf = liveFrame
                        if (lf != null) {
                            Image(
                                bitmap = lf.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CONVERTING", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { if (total > 0) done / total.toFloat() else 0f },
                                    modifier = Modifier.size(70.dp),
                                    strokeWidth = 6.dp,
                                    color = Cyan,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )
                                Text(
                                    (if (total > 0) (done * 100 / total) else 0).toString() + "%",
                                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Label("Frame $done / $total")
                                Hint("So far " + sizeText(liveBytes))
                                val eta = if (done > 0 && total > done)
                                    ((System.currentTimeMillis() - convStart) / 1000f / done * (total - done)).toInt() else -1
                                if (eta >= 0) Hint("about $eta s left")
                            }
                        }
                        MiniButton("Cancel", Modifier.fillMaxWidth()) { job?.cancel() }
                    }
                }
            } else if (tab == 0 && srcUri != null) {
                val shape = RoundedCornerShape(24.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(shape)
                        .background(Color(0xE60A1630))
                        .border(1.dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.55f), Violet.copy(alpha = 0.55f))), shape)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PhoneMock(aspect, stageH) {
                        val ex = exactBmp
                        if (ex != null) {
                            Image(
                                bitmap = ex.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        } else {
                            LiveCanvas(stageImages, fit, crop, fadeFrames, frames) { stageIdx }
                        }
                        // tap the phone to pause / play
                        Box(
                            Modifier.fillMaxSize().clickable {
                                if (exactBmp != null) { exactBmp = null; stagePlaying = true }
                                else stagePlaying = !stagePlaying
                            }
                        )
                        if (clipLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(20.dp),
                                strokeWidth = 2.dp,
                                color = Cyan
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("LIVE PREVIEW", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(
                            fitName(fit) + (if (fadeFrames > 0) " · fade" else "") + (if (speed != 1f) " · " + speed + "×" else ""),
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Hint(frames.toString() + " frames · " + String.format("%.1f", len) + " s")
                        Hint(
                            if (clipLoading) "Loading your clip… " + clipPct + "%"
                            else if (exactBmp != null) "Real encoder view · " + (exactBytes / 1024) + " KB for this frame"
                            else if (stagePlaying) "Playing live · tap the phone to pause"
                            else "Paused · tap the phone to play"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniButton(if (stagePlaying) "⏸" else "▶") { stagePlaying = !stagePlaying }
                            MiniButton(if (exactBusy) "…" else "🔬 Exact", Modifier.weight(1f)) { runExact() }
                        }
                        GlowButton("⚡  Convert", enabled = libOk) { startConvert() }
                    }
                }
            }

            // scrolling content
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!libOk) {
                    GlassCard("Decoder library", Pink) {
                        Hint("libQmageDecoder.so was not found on this phone. Pick a copy of it (from /system/lib64 of a Samsung device).")
                        GhostButton("Pick libQmageDecoder.so") { libPicker.launch("*/*") }
                    }
                }

                /* ================= CREATE ================= */
                if (tab == 0) {
                    if (!Engine.isSamsung()) {
                        GlassCard("Not a Samsung phone", Danger) {
                            Hint("QMG (.qmg) boot animations only run on Samsung phones (One UI). You can still make an animation here and use it on a Samsung phone.")
                        }
                    }
                    GlassCard("Video or GIF", Cyan, "1") {
                        if (srcName.isNotEmpty()) {
                            Text(srcName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Hint("Whole clip: " + String.format("%.1f", durSec) + " s")
                        } else {
                            Hint("Pick any video or GIF. It plays on the phone above while you edit, and every change shows up there instantly.")
                        }
                        GlowButton(if (srcUri == null) "🎬  Pick a video or GIF" else "🎬  Change") {
                            sourcePicker.launch(arrayOf("video/*", "image/gif"))
                        }
                    }

                    GlassCard("What are you making?", Pink, "2") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("🚀 Boot", makeBoot) { toggleBoot() }
                            Chip("🌙 Shutdown", makeShutdown) { toggleShutdown() }
                        }
                        Hint(
                            if (makeBoot && makeShutdown) "Both: the same animation is used for starting and shutting down."
                            else if (makeBoot) "Plays while the phone starts (also used for the loop)."
                            else "Plays when the phone shuts down."
                        )
                        val hb = built[0]
                        val hs = built[1]
                        if (hb != null || hs != null) {
                            Hint(
                                "Already made: " +
                                    (if (hb != null) "boot " + sizeText(hb.bytes) else "") +
                                    (if (hb != null && hs != null) ", " else "") +
                                    (if (hs != null) "shutdown " + sizeText(hs.bytes) else "")
                            )
                        }
                    }

                    AnimatedVisibility(visible = srcUri != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            GlassCard("Trim", Violet, "3") {
                                Timeline(durSec, startSec, len * speed)
                                Label(
                                    mmss(startSec) + "  →  " + mmss(startSec + len * speed) +
                                        "   ·   " + String.format("%.1f", len) + " s output"
                                )
                                if (maxLen > 1.05f) {
                                    Hint("Length")
                                    Slider(value = len, onValueChange = { lenSec = it }, valueRange = 1f..maxLen, colors = sliderColors())
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Chip("Whole clip", false) { lenSec = maxLen }
                                        Chip("3 s", false) { lenSec = minOf(3f, maxLen) }
                                        Chip("6 s", false) { lenSec = minOf(6f, maxLen) }
                                    }
                                }
                                if (maxStart > 0.2f) {
                                    Hint("Start at")
                                    Slider(
                                        value = startSec.coerceIn(0f, maxStart),
                                        onValueChange = { startSec = it; scrubTo() },
                                        valueRange = 0f..maxStart,
                                        colors = sliderColors()
                                    )
                                }
                                Label("Speed")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("0.5×", speed == 0.5f) { speed = 0.5f }
                                    Chip("1×", speed == 1f) { speed = 1f }
                                    Chip("1.5×", speed == 1.5f) { speed = 1.5f }
                                    Chip("2×", speed == 2f) { speed = 2f }
                                }
                            }

                            GlassCard("Look", Mint, "4") {
                                Label("Fit to screen")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("Fill", fit == Fit.FILL) { fit = Fit.FILL }
                                    Chip("Fit (bars)", fit == Fit.FIT) { fit = Fit.FIT }
                                    Chip("Stretch", fit == Fit.STRETCH) { fit = Fit.STRETCH }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("Fit + glow (no bars, no crop)", fit == Fit.GLOW) { fit = Fit.GLOW }
                                }
                                if (fit == Fit.FILL) {
                                    Label("Fill: which part to keep")
                                    Slider(
                                        value = crop,
                                        onValueChange = { crop = it; Engine.crop = it },
                                        valueRange = 0f..1f,
                                        colors = sliderColors()
                                    )
                                }
                                Label("Fade from / to black")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("Off", fadeFrames == 0) { fadeFrames = 0 }
                                    Chip("Short", fadeFrames == 4) { fadeFrames = 4 }
                                    Chip("Long", fadeFrames == 8) { fadeFrames = 8 }
                                }
                                Hint("Fit and fade change the phone above right away.")
                            }

                            GlassCard("Quality", Peach, "5") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("✨ Best", quality == 0) { quality = 0 }
                                    Chip("⚖️ Balanced", quality == 1) { quality = 1 }
                                    Chip("📦 Small", quality == 2) { quality = 2 }
                                }
                                Toggle("Dithering", "Smoother gradients (adds a little noise, bigger file)", dither) { dither = it }
                                Toggle("Target file size", "Squeeze the animation to about this size", useTarget) { useTarget = it }
                                if (useTarget) {
                                    Label("About " + String.format("%.1f", targetMb) + " MB")
                                    Slider(value = targetMb, onValueChange = { targetMb = it }, valueRange = 0.3f..8f, colors = sliderColors())
                                }
                                Hint("Quality and dithering show up with the 🔬 Exact button (it runs the real encoder on one frame). " + target.w + "×" + target.h + " · " + Engine.FPS + " fps")
                            }

                            if (!busy) GlowButton("⚡  Convert", enabled = libOk) { startConvert() }
                        }
                    }

                    GlassCard("Your phone", Violet) {
                        Hint("Size used: " + target.w + "×" + target.h + " (" + target.source + "). QMG Studio copies the size of your stock animation so it fits your Samsung exactly.")
                        GhostButton("🔎  Match my phone (uses root)") { detectWithRoot() }
                    }
                }

                /* ================= PREVIEW ================= */
                if (tab == 1) {
                    GlassCard("Result preview", Cyan) {
                        val pf = previewFile
                        if (pf == null) {
                            Hint("Nothing to show yet. Convert a video, or open any .qmg file from the Tools tab.")
                        } else {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                PhoneMock(aspect, bigPhoneH) {
                                    val pb = previewBmp
                                    if (pb != null) {
                                        Image(
                                            bitmap = pb.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                            LinearProgressIndicator(
                                progress = { if (frameTotal > 0) frameNo / frameTotal.toFloat() else 0f },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                                color = Cyan,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                            Label("Frame " + frameNo + " / " + frameTotal + "   ·   " + sizeText(pf.length()))
                            Hint("This is decoded from the .qmg file itself, so it is what the phone will show.")
                            GhostButton(if (playing) "⏸  Pause" else "▶️  Play") { playing = !playing }
                        }
                    }

                    if (previewFile != null) {
                        GlassCard("Check on this phone", Mint) {
                            Hint("Runs this phone's own Samsung decoder on the file and compares the pictures. Experimental: if the app closes, your file is already saved.")
                            if (verifyText.isNotEmpty()) Label(verifyText)
                            GhostButton(if (verifying) "Checking…" else "🔬  Verify with system decoder") { if (!verifying) runVerify() }
                        }
                        GlassCard("Share as GIF", Pink) {
                            Hint("Turns the animation into a GIF (360 px wide) you can post or send.")
                            GhostButton(if (gifBusy) "Working…" else "🎞️  Export GIF") {
                                if (!gifBusy) {
                                    val f = previewFile
                                    val lib = Engine.findLib(ctx)
                                    if (f != null && lib != null) {
                                        gifBusy = true
                                        status = "🎞️ Making GIF…"
                                        scope.launch(Dispatchers.IO) {
                                            var err: String? = null
                                            val where = Engine.saveMain(ctx, clipBase() + "_QmgStudio.gif", "image/gif") { o ->
                                                err = Gif.export(f, lib.absolutePath, o) { d, t -> status = "🎞️ GIF frame $d / $t" }
                                            }
                                            withContext(Dispatchers.Main) {
                                                gifBusy = false
                                                status = if (err != null) "❌ " + err else if (where != null) "🎞️ GIF saved → $where ✔" else "❌ Save failed: " + Engine.lastError
                                            }
                                        }
                                    } else status = "Make an animation first"
                                }
                            }
                        }
                    }
                }

                /* ================= INSTALL ================= */
                if (tab == 2) {
                    GlassCard("Your animations", Cyan, onClick = {
                        if (built.isNotEmpty()) {
                            val boot = built[0]?.file; val shut = built[1]?.file; val dir = target.stockDir
                            saveDirect(clipBase() + "_QmgStudio-module.zip", "application/zip", "📦 Module zip") { o -> Engine.writeModuleZip(boot, boot, shut, dir, o) }
                        } else status = "Make an animation first"
                    }) {
                        for (s in 0..1) {
                            val b = built[s]
                            val name = if (s == 0) "Boot animation" else "Shutdown animation"
                            if (b != null) Label("✔ " + name + " · " + b.frames + " frames · " + sizeText(b.bytes))
                            else Hint("— " + name + ": not made yet")
                        }
                        Hint("Make them in the Create tab. Boot also covers the loop file. Tap this card to save the module .zip at once.")
                    }

                    val any = built.isNotEmpty()
                    GlassCard("Save", Violet) {
                        Hint("Tap a button and the file is saved at once into Download/QmgStudio. Then flash the module .zip in KernelSU / Magisk Manager → Modules → Install from storage. Back up your stock files first (Tools tab).")
                        GhostButton("📦  Save as module (.zip)") {
                            if (any) {
                                val boot = built[0]?.file; val shut = built[1]?.file; val dir = target.stockDir
                                saveDirect(clipBase() + "_QmgStudio-module.zip", "application/zip", "📦 Module zip") { o -> Engine.writeModuleZip(boot, boot, shut, dir, o) }
                            } else status = "Make an animation first"
                        }
                        if (built[0] != null) GhostButton("💾  Save bootsamsung.qmg") {
                            val f = built[0]?.file
                            if (f != null) saveDirect(clipBase() + "_bootsamsung.qmg", "application/octet-stream", "💾 Boot animation") { o -> f.inputStream().use { it.copyTo(o) } }
                        }
                        if (built[1] != null) GhostButton("💾  Save shutdown.qmg") {
                            val f = built[1]?.file
                            if (f != null) saveDirect(clipBase() + "_shutdown.qmg", "application/octet-stream", "💾 Shutdown animation") { o -> f.inputStream().use { it.copyTo(o) } }
                        }
                    }

                    GlassCard("Undo", Pink) {
                        Hint("Removes the module. Reboot to get your stock animation back.")
                        GhostButton("↩️  Uninstall module (root)") { if (rootState == 1) confirm = "uninstall" else status = "Root is needed: tap the Root chip at the top to ask again" }
                        GhostButton("🔄  Reboot now (root)") { if (rootState == 1) confirm = "reboot" else status = "Root is needed: tap the Root chip at the top to ask again" }
                    }
                }

                /* ================= TOOLS ================= */
                if (tab == 3) {
                    GlassCard("Safety", Mint) {
                        Hint("Copies your stock animations (bootsamsung.qmg, shutdown.qmg …) to QmgStudio/backup in your main storage. Uses root if needed.")
                        GhostButton(if (backingUp) "Working…" else "🛟  Backup stock animations") { backup() }
                    }

                    GlassCard("Open a .qmg", Cyan) {
                        Hint("Preview any .qmg file, then export it as a GIF.")
                        GhostButton("📂  Choose a .qmg file") { qmgOpener.launch(arrayOf("*/*")) }
                        val bf = Engine.backupFiles(ctx)
                        if (bf.isNotEmpty()) {
                            Label("Your backups")
                            for (f in bf) {
                                GhostButton("▶️  " + f.name + "  (" + sizeText(f.length()) + ")") {
                                    previewFile = f; previewVersion++; playing = true; tab = 1
                                }
                            }
                        }
                    }

                    GlassCard("Decoder library", Violet) {
                        Hint(
                            if (libOk) "Found. The delta table is read from your phone's own Samsung library, nothing from Samsung is bundled."
                            else "Not found on this phone."
                        )
                        GhostButton("Pick libQmageDecoder.so manually") { libPicker.launch("*/*") }
                    }

                    GlassCard("About", Pink) {
                        Text("QMG Studio " + Engine.appVersion(ctx), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Hint("Made by " + Engine.AUTHOR + " · open source (MIT).")
                        Hint("Samsung .qmg boot animations: any size that is a multiple of 4, keyframe + delta frames, verified bit-exact against Samsung's own decoder.")
                        Hint("github.com/xBooM-SL/Qmg-studio")
                        GhostButton("⭐  Open on GitHub") { uriHandler.openUri(Engine.REPO_URL) }
                        GhostButton(if (updChecking) "Checking…" else "🔄  Check for updates") { checkUpdate(true) }
                        GhostButton("⬇️  Latest releases") { uriHandler.openUri(Engine.RELEASES_URL) }
                        GhostButton("🐞  Report a problem") { uriHandler.openUri(Engine.ISSUES_URL) }
                    }
                }
            }

            NavBar(tab) { tab = it }
        }

        if (status.isNotEmpty()) Toast(status) { status = "" }

        val up = update
        if (up != null) {
            AlertDialog(
                onDismissRequest = { if (!updBusy) update = null },
                containerColor = Color(0xFF0B1629),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFD5D8E6),
                title = { Text("Update available 🚀") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val mb = if (up.size > 0) "  ·  " + String.format("%.1f MB", up.size / 1048576f) else ""
                        Text("v" + up.version + " is ready (you have " + Updater.installedVersion(ctx) + ")" + mb)
                        if (up.notes.isNotEmpty() && !updBusy) {
                            Text(up.notes, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.verticalScroll(rememberScrollState()).height(110.dp))
                        }
                        if (updBusy) {
                            LinearProgressIndicator(progress = { updProgress }, modifier = Modifier.fillMaxWidth(), color = Cyan)
                            Text("Downloading… " + (updProgress * 100).toInt() + "%", fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !updBusy, onClick = { startUpdate(up) }) { Text(if (updBusy) "Please wait" else "Update now", color = Cyan) }
                },
                dismissButton = {
                    if (!updBusy) TextButton(onClick = { update = null }) { Text("Later", color = Color.White) }
                }
            )
        }

        val c = confirm
        if (c != null) {
            val title = when (c) {
                "install" -> "Install now?"
                "uninstall" -> "Remove the module?"
                else -> "Reboot now?"
            }
            val msg = when (c) {
                "install" -> "The animation is written into a KernelSU/Magisk module and used after the next reboot. Make sure you backed up your stock animations."
                "uninstall" -> "Your stock animation comes back after the next reboot."
                else -> "The phone restarts immediately."
            }
            AlertDialog(
                onDismissRequest = { confirm = null },
                containerColor = Color(0xFF0B1629),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFD5D8E6),
                title = { Text(title) },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        scope.launch(Dispatchers.IO) {
                            val ok: Boolean
                            val text: String
                            if (c == "install") {
                                ok = Engine.installModuleRoot(ctx, built[0]?.file, built[0]?.file, built[1]?.file, target.stockDir)
                                text = if (ok) "⚡ Installed. Reboot to see it." else "❌ " + Engine.lastError
                            } else if (c == "uninstall") {
                                ok = Engine.uninstallModuleRoot()
                                text = if (ok) "↩️ Module removed. Reboot to apply." else "❌ " + Engine.lastError
                            } else {
                                ok = Engine.rebootRoot()
                                text = if (ok) "Rebooting…" else "❌ " + Engine.lastError
                            }
                            withContext(Dispatchers.Main) { status = text }
                        }
                    }) { Text("Continue", color = Cyan) }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Color.White) } }
            )
        }
    }
}
