package com.smartremote.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BTN = Color(0xFF0E0F13)
private val BTN_PRESSED = Color(0xFF262830)
private val FG = Color(0xFFE3E3E9)
var vibrateOn = true

class MainActivity : ComponentActivity() {
    private val client = RemoteClient()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) { App(client) }
            }
        }
    }
    override fun onDestroy() { client.close(); super.onDestroy() }
}

@Composable
fun App(client: RemoteClient) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    var connected by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("TV") }
    var status by remember { mutableStateOf("") }
    val found = remember { mutableStateMapOf<String, String>() }
    val h0 = prefs.getString("host", "") ?: ""
    val p0 = prefs.getString("pin", "") ?: ""

    DisposableEffect(Unit) {
        client.onState = { ok, msg ->
            connected = ok
            if (ok) {
                name = msg; status = ""
                prefs.edit().putString("host", client.host).putString("pin", client.pin).apply()
            } else status = msg
        }
        val d = TvDiscovery(ctx, { n, h -> found[n] = h }, { n -> found.remove(n) })
        d.start()
        if (h0.isNotEmpty() && p0.isNotEmpty()) { status = "Conectando…"; client.connect(h0, p0) }
        onDispose { d.stop() }
    }

    if (connected) RemoteScreen(name, client::send) { client.close(); connected = false }
    else PairScreen(status, found, h0, p0) { h, p -> status = "Conectando…"; client.connect(h, p) }
}

@Composable
fun PairScreen(status: String, found: Map<String, String>, h0: String, p0: String, onConnect: (String, String) -> Unit) {
    var host by remember { mutableStateOf(h0) }
    var pin by remember { mutableStateOf(p0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Conectar a la TV", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Abre «Smart Remote TV» en la TV (misma red WiFi), activa su servicio y escribe aquí el PIN que muestra.", color = Color(0xFFB0B3BF))
        if (found.isNotEmpty()) Text("TVs encontradas", fontWeight = FontWeight.SemiBold)
        found.forEach { (n, h) ->
            Button(onClick = { host = h }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BTN, contentColor = FG)) { Text("$n  ($h)") }
        }
        OutlinedTextField(host, { host = it }, label = { Text("IP de la TV") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(pin, { pin = it }, label = { Text("PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        Button(onClick = { onConnect(host.trim(), pin.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
        if (status.isNotEmpty()) Text(status, color = Color(0xFFFFB4AB))
    }
}

@Composable
fun Pressable(
    modifier: Modifier, shape: Shape = CircleShape, flat: Boolean = false, hold: Boolean = false,
    onPress: () -> Unit, content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val hf = LocalHapticFeedback.current
    val cb by rememberUpdatedState(onPress)
    Box(
        modifier.clip(shape)
            .background(if (pressed) BTN_PRESSED else if (flat) Color.Transparent else BTN)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    if (vibrateOn) hf.performHapticFeedback(HapticFeedbackType.LongPress)
                    cb()
                    if (hold) coroutineScope {
                        val j = launch { delay(450); while (true) { cb(); delay(110) } }
                        tryAwaitRelease(); j.cancel()
                    } else tryAwaitRelease()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
fun RBtn(
    icon: ImageVector, modifier: Modifier, flat: Boolean = false, hold: Boolean = false,
    shape: Shape = CircleShape, iconSize: Dp = 38.dp, onPress: () -> Unit
) = Pressable(modifier, shape, flat, hold, onPress) { Icon(icon, null, tint = FG, modifier = Modifier.size(iconSize)) }

@Composable
fun Pill(top: ImageVector, bottom: ImageVector, modifier: Modifier, onTop: () -> Unit, onBottom: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(50)).background(BTN)) {
        RBtn(top, Modifier.weight(1f).fillMaxWidth(), flat = true, hold = true, shape = RectangleShape, onPress = onTop)
        RBtn(bottom, Modifier.weight(1f).fillMaxWidth(), flat = true, hold = true, shape = RectangleShape, onPress = onBottom)
    }
}

@Composable
fun RemoteScreen(name: String, send: (String) -> Unit, onDisconnect: () -> Unit) {
    var showKb by remember { mutableStateOf(false) }
    var showPad by remember { mutableStateOf(false) }
    var showMouse by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var vib by remember { mutableStateOf(vibrateOn) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton({ showMouse = true }) { Icon(Icons.Rounded.Mouse, null, tint = Color(0xFFC9CBD6), modifier = Modifier.size(32.dp)) }
            IconButton({ showKb = true }) { Icon(Icons.Rounded.Keyboard, null, tint = Color(0xFFC9CBD6), modifier = Modifier.size(32.dp)) }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Color(0xFFC9CBD6)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(if (vib) "Vibración: sí" else "Vibración: no") }, { vib = !vib; vibrateOn = vib; menu = false })
                    DropdownMenuItem({ Text("Cambiar de TV") }, { menu = false; onDisconnect() })
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 12.dp
            val cell = (maxWidth - gap * 3) / 4
            val dp = maxWidth
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                // Barra multimedia
                Row(Modifier.fillMaxWidth().height(cell).clip(RoundedCornerShape(50)).background(BTN)) {
                    RBtn(Icons.Rounded.FastRewind, Modifier.weight(1f).fillMaxHeight(), flat = true, shape = RectangleShape) { send("REW") }
                    RBtn(Icons.Rounded.PlayArrow, Modifier.weight(1f).fillMaxHeight(), flat = true, shape = RectangleShape) { send("PLAY") }
                    RBtn(Icons.Rounded.Pause, Modifier.weight(1f).fillMaxHeight(), flat = true, shape = RectangleShape) { send("PAUSE") }
                    RBtn(Icons.Rounded.FastForward, Modifier.weight(1f).fillMaxHeight(), flat = true, shape = RectangleShape) { send("FF") }
                }
                // Volumen / mute / CC / brillo
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Pill(Icons.Rounded.VolumeUp, Icons.Rounded.VolumeDown, Modifier.width(cell).height(cell * 2 + gap), { send("VOL_UP") }, { send("VOL_DOWN") })
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        RBtn(Icons.Rounded.VolumeOff, Modifier.size(cell)) { send("MUTE") }
                        RBtn(Icons.Rounded.Dialpad, Modifier.size(cell)) { showPad = true }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        RBtn(Icons.Rounded.ClosedCaption, Modifier.size(cell)) { send("CC") }
                        RBtn(Icons.Rounded.MoreHoriz, Modifier.size(cell)) { send("MORE") }
                    }
                    Pill(Icons.Rounded.BrightnessHigh, Icons.Rounded.BrightnessLow, Modifier.width(cell).height(cell * 2 + gap), { send("BRI_UP") }, { send("BRI_DOWN") })
                }
                // Atrás / inicio / menú / encendido
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    RBtn(Icons.Rounded.ArrowBack, Modifier.size(cell)) { send("BACK") }
                    RBtn(Icons.Rounded.Home, Modifier.size(cell)) { send("HOME") }
                    RBtn(Icons.Rounded.FormatListBulleted, Modifier.size(cell)) { send("MENU") }
                    RBtn(Icons.Rounded.PowerSettingsNew, Modifier.size(cell)) { send("POWER") }
                }
                // D-pad circular
                Box(Modifier.size(dp).clip(CircleShape).background(BTN)) {
                    val s = dp * 0.26f
                    RBtn(Icons.Rounded.KeyboardArrowUp, Modifier.align(Alignment.TopCenter).padding(top = dp * 0.03f).size(s), flat = true, hold = true, iconSize = 64.dp) { send("UP") }
                    RBtn(Icons.Rounded.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter).padding(bottom = dp * 0.03f).size(s), flat = true, hold = true, iconSize = 64.dp) { send("DOWN") }
                    RBtn(Icons.Rounded.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart).padding(start = dp * 0.03f).size(s), flat = true, hold = true, iconSize = 64.dp) { send("LEFT") }
                    RBtn(Icons.Rounded.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd).padding(end = dp * 0.03f).size(s), flat = true, hold = true, iconSize = 64.dp) { send("RIGHT") }
                    Pressable(Modifier.align(Alignment.Center).size(dp * 0.28f).border(10.dp, FG, CircleShape), CircleShape, flat = true, onPress = { send("OK") }) {}
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showKb) {
        var text by remember { mutableStateOf("") }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { fr.requestFocus() }
        AlertDialog(
            onDismissRequest = { showKb = false },
            title = { Text("Teclado") },
            text = {
                OutlinedTextField(text, { n ->
                    if (n.length > text.length && n.startsWith(text)) send("TEXT:" + n.substring(text.length).replace("\n", " "))
                    else if (n.length < text.length) repeat(text.length - n.length) { send("DEL") }
                    text = n
                }, modifier = Modifier.focusRequester(fr).fillMaxWidth())
            },
            confirmButton = { TextButton({ showKb = false }) { Text("Cerrar") } },
            dismissButton = { TextButton({ send("OK") }) { Text("Enter / OK") } }
        )
    }
    if (showPad) {
        AlertDialog(
            onDismissRequest = { showPad = false },
            title = { Text("Números") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    listOf("123", "456", "789", "0").forEach { row ->
                        Row { row.forEach { d -> TextButton({ send("TEXT:$d") }, Modifier.size(72.dp)) { Text("$d", fontSize = 26.sp) } } }
                    }
                }
            },
            confirmButton = { TextButton({ showPad = false }) { Text("Cerrar") } },
            dismissButton = { TextButton({ send("DEL") }) { Text("Borrar") } }
        )
    }
    if (showMouse) {
        Dialog({ showMouse = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF08090B)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Panel táctil", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton({ send("SCROLL:UP") }) { Icon(Icons.Rounded.KeyboardArrowUp, null, modifier = Modifier.size(36.dp)) }
                        IconButton({ send("SCROLL:DOWN") }) { Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(36.dp)) }
                        TextButton({ showMouse = false }) { Text("Cerrar") }
                    }
                    Box(
                        Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(28.dp)).background(BTN)
                            .pointerInput(Unit) { detectTapGestures(onTap = { send("CLICK") }) }
                            .pointerInput(Unit) {
                                detectDragGestures { c, d ->
                                    c.consume()
                                    send("MOVE:${(d.x * 1.8f).roundToInt()},${(d.y * 1.8f).roundToInt()}")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("Desliza para mover el cursor\nToca para hacer clic", color = Color(0xFF7C7F8C)) }
                }
            }
        }
    }
}
