package com.drxwnify.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.friends.FriendData
import com.drxwnify.desktop.friends.Friends
import com.drxwnify.desktop.friends.Found
import com.drxwnify.desktop.friends.TrackRef
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Chip
import com.drxwnify.desktop.ui.Cover
import com.drxwnify.desktop.ui.Dot
import com.drxwnify.desktop.ui.EqBars
import com.drxwnify.desktop.ui.Hairline
import com.drxwnify.desktop.ui.HoverRow
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Panel
import com.drxwnify.desktop.ui.PillButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.hand
import com.drxwnify.desktop.ui.rememberHover
import com.drxwnify.desktop.ui.theme
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private val avatarCache = HashMap<String, ImageBitmap?>()

/** Profile pictures travel inside the profile event as a small base64 JPEG. */
@Composable
fun AvatarPic(b64: String?, name: String, size: Dp) {
    val t = theme
    val bmp = remember(b64) {
        b64?.let { key -> avatarCache.getOrPut(key.takeLast(64)) { runCatching { org.jetbrains.skia.Image.makeFromEncoded(java.util.Base64.getMimeDecoder().decode(key)).toComposeImageBitmap() }.getOrNull() } }
    }
    Box(Modifier.size(size).clip(CircleShape).background(t.fillHover), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Txt(name.ifBlank { "?" }.take(1).uppercase(), size = (size.value * 0.4f).sp, weight = FontWeight.ExtraBold, color = t.dim)
    }
}

fun ago(ms: Long): String {
    val d = (System.currentTimeMillis() - ms).coerceAtLeast(0) / 60_000
    return when { d < 2 -> "сейчас"; d < 60 -> "$d мин назад"; d < 24 * 60 -> "${d / 60} ч назад"; else -> "${d / 1440} дн. назад" }
}

private fun copy(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

@Composable
fun FriendsScreen() {
    val me by Friends.me.collectAsState()
    var opened by remember { mutableStateOf<String?>(null) }
    if (me == null) { Onboarding(); return }
    val o = opened
    if (o != null) FriendPage(o) { opened = null } else FriendsHome { opened = it }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, secret: Boolean = false) {
    val t = theme
    Box(modifier.height(42.dp).clip(RoundedCornerShape(12.dp)).background(t.fill).border(1.dp, t.panelBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Txt(hint, size = 13.sp, color = t.mute)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = t.body), cursorBrush = SolidColor(t.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Onboarding() {
    val t = theme
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Page {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Ico(Ti.users, size = 40.sp, color = t.accent)
                Spacer(h = 12.dp)
                Txt("Друзья", size = 30.sp, weight = t.headingWeight, family = t.heading)
                Spacer(h = 6.dp)
                Txt("Кто что слушает, «слушать вместе», любимые друзей. Без своего сервера — через Nostr.", size = 13.sp, color = t.dim)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.padding(22.dp)) {
                        Txt("Тот же профиль, что на телефоне", size = 16.sp, weight = FontWeight.ExtraBold)
                        Spacer(h = 6.dp)
                        Txt("На телефоне: Друзья → свой профиль → «Ключ восстановления». Скопируй его (начинается с nsec1…) и вставь сюда — друзья увидят тебя тем же человеком.", size = 12.sp, color = t.dim, maxLines = 5, lineHeight = 18.sp)
                        Spacer(h = 14.dp)
                        Field(key, { key = it; error = null }, "nsec1…", Modifier.fillMaxWidth(), secret = true)
                        Spacer(h = 10.dp)
                        PillButton("Войти", Ti.key, primary = true, onClick = { if (!Friends.importKey(key)) error = "Это не ключ восстановления" else Toast.show("Профиль подключён") })
                        error?.let { Spacer(h = 8.dp); Txt(it, size = 12.sp, color = t.accent2) }
                    }
                }
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.padding(22.dp)) {
                        Txt("Новый профиль", size = 16.sp, weight = FontWeight.ExtraBold)
                        Spacer(h = 6.dp)
                        Txt("Если телефона с Drxwnify нет. Ключ создаётся прямо здесь, на этом компьютере.", size = 12.sp, color = t.dim, maxLines = 4, lineHeight = 18.sp)
                        Spacer(h = 14.dp)
                        Field(name, { name = it }, "Как тебя видят друзья", Modifier.fillMaxWidth())
                        Spacer(h = 10.dp)
                        PillButton("Создать", Ti.plus, onClick = { if (name.isNotBlank()) Friends.createProfile(name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsHome(open: (String) -> Unit) {
    val t = theme
    val me by Friends.me.collectAsState()
    val friends by Friends.friends.collectAsState()
    val data by Friends.data.collectAsState()
    val requests by Friends.requests.collectAsState()
    val along by Friends.listeningTo.collectAsState()
    var input by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Found>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val m = me ?: return
    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarPic(m.picture ?: data[m.pubkey]?.avatar, m.name, 72.dp)
                Spacer(w = 18.dp)
                Column(Modifier.weight(1f)) {
                    Txt(m.name.ifBlank { "Мой профиль" }, size = 28.sp, weight = t.headingWeight, family = t.heading)
                    Spacer(h = 4.dp)
                    Mono("${m.code.take(16)}…${m.code.takeLast(6)}", size = 12.sp)
                    if (m.imported) { Spacer(h = 4.dp); Txt("Профиль с телефона: имя, аватар и любимые ведёт телефон, отсюда друзья видят, что играет на ПК", size = 11.sp, color = t.mute, maxLines = 2) }
                }
                PillButton("Мой код", Ti.copy, onClick = { copy(m.code); Toast.show("Код скопирован") })
                Spacer(w = 8.dp)
                PillButton("Ссылка", Ti.link, onClick = { Friends.inviteLink()?.let { copy(it); Toast.show("Ссылка-приглашение скопирована") } })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(input, { input = it; found = null }, "Код друга (npub…), ссылка-приглашение или имя", Modifier.weight(1f))
                Spacer(w = 10.dp)
                PillButton(if (searching) "Ищу…" else "Добавить", Ti.userPlus, primary = true, onClick = {
                    val pub = Friends.parseCode(input)
                    if (pub != null) { Friends.addFriend(pub); input = ""; Toast.show("Заявка отправлена") }
                    else if (input.trim().length >= 2) scope.launch { searching = true; found = Friends.searchByName(input); searching = false }
                })
            }
            found?.let { list ->
                Spacer(h = 10.dp)
                if (list.isEmpty()) Mono("никого не нашлось — по имени находятся только те, кто это разрешил")
                list.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarPic(f.avatar, f.name, 36.dp); Spacer(w = 12.dp)
                        Txt(f.name, size = 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        PillButton("Добавить", onClick = { Friends.addFriend(f.pubkey, f.name); found = null; input = ""; Toast.show("Заявка отправлена") })
                    }
                }
            }
        }
        if (requests.isNotEmpty()) {
            item { SectionTitle("Заявки", "${requests.size}") }
            requests.forEach { r ->
                item(key = "rq" + r.pubkey) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarPic(null, r.name, 40.dp); Spacer(w = 12.dp)
                        Column(Modifier.weight(1f)) { Txt(r.name.ifBlank { "Без имени" }, size = 14.sp, weight = FontWeight.Bold); Mono("хочет дружить · ${ago(r.at)}") }
                        PillButton("Принять", primary = true, onClick = { Friends.addFriend(r.pubkey, r.name) })
                        Spacer(w = 8.dp)
                        PillButton("Скрыть", onClick = { Friends.dismiss(r.pubkey) })
                    }
                }
            }
        }
        item { SectionTitle("Друзья", if (friends.isEmpty()) "пока никого" else "${friends.size}") }
        val sorted = friends.sortedWith(compareByDescending<com.drxwnify.desktop.friends.Friend> { data[it.pubkey]?.now?.live == true }.thenByDescending { data[it.pubkey]?.now?.at ?: 0L })
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = maxOf(1, (maxWidth / 300.dp).toInt())
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sorted.chunked(cols).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            line.forEach { f -> FriendCard(f.pubkey, f.name, data[f.pubkey], along == f.pubkey, Modifier.weight(1f)) { open(f.pubkey) } }
                            repeat(cols - line.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            Spacer(h = 26.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("Выйти из профиля на этом компьютере", modifier = Modifier.hand().clickable { Friends.logout() })
            }
        }
    }
}

@Composable
private fun FriendCard(pubkey: String, name: String, d: FriendData?, listening: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) t.fillHover else t.fill, tween(160))
    val now = d?.now
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, if (listening) t.accent.copy(alpha = 0.6f) else t.panelBorder, RoundedCornerShape(16.dp))
            .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onOpen).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box { AvatarPic(d?.avatar, d?.name ?: name, 44.dp); if (now?.live == true) Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(t.bg).padding(2.dp).clip(CircleShape).background(t.ok)) }
            Spacer(w = 12.dp)
            Column(Modifier.weight(1f)) {
                Txt(d?.name ?: name.ifBlank { "Друг" }, size = 15.sp, weight = FontWeight.ExtraBold)
                Mono(when { now == null -> "ничего не слышно"; now.live -> "слушает сейчас"; else -> "слушал(а) ${ago(now.at)}" })
            }
            if (pubkey == Friends.DEVELOPER_PUBKEY) Mono("разработчик", color = t.accent)
        }
        if (now != null) {
            Spacer(h = 12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(now.track.cover, Modifier.size(46.dp), radius = 8.dp)
                Spacer(w = 10.dp)
                Column(Modifier.weight(1f)) {
                    Txt(now.track.title, size = 13.sp, weight = FontWeight.Bold)
                    Txt(now.track.artist, size = 12.sp, color = t.dim)
                }
                if (now.live) EqBars()
            }
            if (now.live && now.listenAlong) {
                Spacer(h = 12.dp)
                if (listening) PillButton("Перестать слушать вместе", Ti.x, onClick = { Friends.stopListenAlong() })
                else PillButton("Слушать вместе", Ti.headphones, primary = true, onClick = { Friends.startListenAlong(pubkey); Toast.show("Слушаешь вместе с ${d?.name ?: "другом"}") })
            }
        }
    }
}

@Composable
private fun FriendPage(pubkey: String, back: () -> Unit) {
    val t = theme
    val data by Friends.data.collectAsState()
    val friends by Friends.friends.collectAsState()
    val d = data[pubkey]
    val name = d?.name ?: friends.firstOrNull { it.pubkey == pubkey }?.name ?: "Друг"
    var tab by remember { mutableStateOf("history") }
    val scope = rememberCoroutineScope()
    fun playRefs(refs: List<TrackRef>, i: Int, ctx: String) = scope.launch {
        val first = Friends.resolveRef(refs[i]) ?: return@launch
        val rest = refs.drop(i + 1).map { it.toTrack() }
        Player.play(listOf(first) + rest, 0, ctx)
    }
    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ti.arrowLeft, back, filled = true, tooltip = "К друзьям")
                Spacer(w = 16.dp)
                AvatarPic(d?.avatar, name, 84.dp)
                Spacer(w = 18.dp)
                Column(Modifier.weight(1f)) {
                    Txt(name, size = 30.sp, weight = t.headingWeight, family = t.heading)
                    d?.now?.let { n -> Mono(if (n.live) "слушает: ${n.track.title} — ${n.track.artist}" else "последний трек ${ago(n.at)}") }
                }
                if (pubkey != Friends.DEVELOPER_PUBKEY) PillButton("Удалить из друзей", onClick = { Friends.removeFriend(pubkey); back() })
            }
        }
        item {
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("История", tab == "history", { tab = "history" })
                Chip("Любимые${d?.likes?.total?.let { " · $it" } ?: ""}", tab == "likes", { tab = "likes" })
                Chip("Плейлисты", tab == "playlists", { tab = "playlists" })
            }
        }
        when (tab) {
            "history" -> {
                val h = d?.history.orEmpty()
                if (h.isEmpty()) item { Empty(Ti.history, "Пусто", "Друг ещё ничего не показал — или скрыл историю.") }
                h.forEachIndexed { i, r -> item(key = "h$i") { RefRow(r, i) { playRefs(h, i, "История $name") } } }
            }
            "likes" -> {
                val l = d?.likes?.tracks.orEmpty()
                if (l.isEmpty()) item { Empty(Ti.heart, "Пусто", "Любимые появятся, когда друг ими поделится.") }
                l.forEachIndexed { i, r -> item(key = "l$i") { RefRow(r, i) { playRefs(l, i, "Любимые $name") } } }
            }
            else -> {
                val p = d?.playlists.orEmpty()
                if (p.isEmpty()) item { Empty(Ti.playlist, "Пусто", "Плейлистов не видно.") }
                p.forEach { pl ->
                    item { SectionTitle(pl.name, "${pl.total} треков") }
                    pl.tracks.forEachIndexed { i, r -> item { RefRow(r, i) { playRefs(pl.tracks, i, pl.name) } } }
                }
            }
        }
    }
}

@Composable
private fun RefRow(r: TrackRef, i: Int, onPlay: () -> Unit) {
    val t = theme
    HoverRow(onClick = {}, onDoubleClick = onPlay, modifier = Modifier.fillMaxWidth().height(54.dp)) { hovered ->
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            if (hovered) Box(Modifier.hand().clickable(onClick = onPlay)) { Ico(Ti.playerPlayFilled, size = 15.sp, color = t.text) } else Mono("${i + 1}", size = 12.sp)
        }
        Cover(r.cover, Modifier.size(40.dp), radius = 6.dp)
        Spacer(w = 12.dp)
        Column(Modifier.weight(1f)) { Txt(r.title, size = 14.sp, weight = FontWeight.SemiBold); Txt(r.artist, size = 12.sp, color = t.dim) }
        if (r.at > 0) Mono(ago(r.at), size = 11.sp, modifier = Modifier.padding(end = 12.dp))
    }
}
