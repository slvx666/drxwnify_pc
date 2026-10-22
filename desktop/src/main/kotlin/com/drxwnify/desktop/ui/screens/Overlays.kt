package com.drxwnify.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.data.ioScope
import com.drxwnify.desktop.friends.Friends
import com.drxwnify.desktop.ui.AccountMenuState
import com.drxwnify.desktop.ui.Hairline
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Toggle
import com.drxwnify.desktop.ui.Tooltip
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.hand
import com.drxwnify.desktop.ui.rememberHover
import com.drxwnify.desktop.ui.theme
import com.drxwnify.desktop.ui.theme.Themes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val APP_VERSION = "0.1.0 beta"

@Composable
fun AccountMenu() {
    val t = theme
    val open = AccountMenuState.open
    if (open) Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { AccountMenuState.open = false })
    Box(Modifier.fillMaxSize().padding(top = 58.dp, end = 150.dp), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            open,
            enter = fadeIn(tween(140)) + scaleIn(tween(180, easing = FastOutSlowInEasing), initialScale = 0.94f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(110)) + scaleOut(tween(120), targetScale = 0.96f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            val me by Friends.me.collectAsState()
            val settings by Prefs.settings.state.collectAsState()
            Column(
                Modifier.width(270.dp).clip(RoundedCornerShape(16.dp)).background(t.menu)
                    .border(1.dp, t.panelBorder, RoundedCornerShape(16.dp)).padding(6.dp),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarPic(me?.picture, me?.name ?: "D", 38.dp)
                    Spacer(w = 10.dp)
                    Column {
                        Txt(me?.name?.ifBlank { null } ?: "Без профиля", size = 14.sp, weight = FontWeight.Bold)
                        Mono(me?.let { "${it.code.take(12)}…" } ?: "профиль настраивается в «Друзьях»", size = 10.sp)
                    }
                }
                Hairline(Modifier.padding(horizontal = 8.dp))
                Spacer(h = 4.dp)
                MenuItem(Ti.settings, "Настройки") { Nav.openSettings("appearance") }
                // Quick theme switch: one dot per theme.
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(Ti.palette, size = 17.sp, color = t.dim)
                    Spacer(w = 12.dp)
                    Txt("Тема", size = 13.sp, weight = FontWeight.SemiBold, color = t.dim, modifier = Modifier.weight(1f))
                    Themes.specs.forEach { sp ->
                        Tooltip(sp.name) {
                            val sel = Themes.spec(settings.theme).id == sp.id
                            Box(
                                Modifier.padding(start = 5.dp).size(18.dp).clip(CircleShape).border(2.dp, if (sel) t.text else Color.Transparent, CircleShape)
                                    .padding(3.dp).clip(CircleShape).background(sp.of(settings.themeDay).accent).hand()
                                    .clickable { Prefs.settings.update { it.copy(theme = sp.id) } },
                            )
                        }
                    }
                }
                // Day / night for whichever theme is on.
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(if (settings.themeDay) Ti.sun else Ti.moon, size = 17.sp, color = t.dim)
                    Spacer(w = 12.dp)
                    Txt(if (settings.themeDay) "Дневная тема" else "Ночная тема", size = 13.sp, weight = FontWeight.SemiBold, color = t.dim, modifier = Modifier.weight(1f))
                    Toggle(settings.themeDay) { v -> Prefs.settings.update { it.copy(themeDay = v) } }
                }
                MenuItem(Ti.users, "Друзья и мой профиль") { Nav.go(Screen.Friends) }
                MenuItem(Ti.news, "Что нового") { Nav.openSettings("about") }
                MenuItem(Ti.bulb, "Предложить идею") { Nav.openSettings("about") }
                MenuItem(Ti.refresh, "Обновления", APP_VERSION) { Nav.openSettings("about") }
                Hairline(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                MenuItem(Ti.power, "Закрыть Drxwnify") { kotlin.system.exitProcess(0) }
            }
        }
    }
}

private fun Color.compositeOver(bg: Color) = Color(red * alpha + bg.red * (1 - alpha), green * alpha + bg.green * (1 - alpha), blue * alpha + bg.blue * (1 - alpha), 1f)

@Composable
private fun MenuItem(glyph: String, text: String, right: String? = null, onClick: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) t.fillHover else Color.Transparent, tween(120))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).hoverable(src).hand()
            .clickable(interactionSource = src, indication = null) { AccountMenuState.open = false; onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(glyph, size = 17.sp, color = if (hovered) t.text else t.dim)
        Spacer(w = 12.dp)
        Txt(text, size = 13.sp, weight = FontWeight.SemiBold, color = if (hovered) t.text else t.dim, modifier = Modifier.weight(1f))
        if (right != null) Mono(right, size = 10.sp)
    }
}

/**
 * Short notes in the corner. Shown from any thread, changed only on the UI thread (lists changed from
 * background threads lost their removals, and notes hung for good). A note with a [key] (a progress)
 * is updated in place instead of piling up; at most three at a time.
 */
object Toast {
    data class Item(val id: Long, val key: String?, val text: String)

    val items = mutableStateListOf<Item>()
    private val ui = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val timers = HashMap<Long, kotlinx.coroutines.Job>()

    fun show(text: String, key: String? = null, durationMs: Long = 2800) {
        ui.launch {
            val at = key?.let { k -> items.indexOfFirst { it.key == k } } ?: -1
            val id = if (at >= 0) items[at].id else System.nanoTime()
            if (at >= 0) items[at] = Item(id, key, text) else {
                items += Item(id, key, text)
                while (items.size > 3) timers.remove(items.removeAt(0).id)?.cancel()
            }
            timers.remove(id)?.cancel()
            timers[id] = ui.launch { delay(durationMs); items.removeAll { it.id == id }; timers.remove(id) }
        }
    }
}

@Composable
fun Toasts() {
    val t = theme
    // Top right, under the top bar: clear of the player and of the picked-tracks bar at the bottom.
    Box(Modifier.fillMaxSize().padding(top = 74.dp, end = 24.dp), contentAlignment = Alignment.TopEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Toast.items.forEach { (id, _, text) ->
                androidx.compose.runtime.key(id) {
                    val visible = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { -it / 2 }, exit = fadeOut(tween(160)) + slideOutVertically { -it / 2 }) {
                        Box(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(t.menu).border(1.dp, t.panelBorder, RoundedCornerShape(99.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) { Txt(text, size = 13.sp, weight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
