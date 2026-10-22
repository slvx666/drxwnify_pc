package com.drxwnify.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import com.drxwnify.desktop.ui.theme.Look

/**
 * Right-click menus drawn by the app in the theme's colours, font and corners, with an icon per item,
 * instead of the plain white system-looking one Compose shows by default (also used by text fields).
 */
object ThemedContextMenu : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return
        val t = theme
        val close = { state.status = ContextMenuState.Status.Closed }
        val shape = if (t.look == Look.Terminal) RectangleShape else RoundedCornerShape(if (t.look == Look.Atlas) 4.dp else 12.dp)
        Popup(
            popupPositionProvider = rememberPopupPositionProviderAtPosition(status.rect.center),
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
            onKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { close(); true } else false },
        ) {
            Column(
                Modifier.padding(10.dp).shadow(18.dp, shape).clip(shape).background(t.menu)
                    .border(if (t.look == Look.Terminal) 2.dp else 1.dp, t.panelBorder, shape)
                    .width(IntrinsicSize.Max).widthIn(min = 220.dp).padding(6.dp),
            ) {
                items().forEach { item -> MenuRow(item.label) { close(); item.onClick() } }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered) t.fillHover else Color.Transparent, tween(100))
    val danger = label.startsWith("Удалить")
    val color = if (danger) t.accent2 else t.text
    Row(
        Modifier.fillMaxWidth().height(36.dp).clip(if (t.look == Look.Terminal) RectangleShape else RoundedCornerShape(8.dp)).background(bg)
            .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(menuIcon(label), size = 16.sp, color = if (danger) t.accent2 else if (hovered) t.text else t.dim)
        Spacer(Modifier.width(12.dp))
        Txt(label, size = 14.sp, weight = if (hovered) FontWeight.SemiBold else FontWeight.Medium, color = color)
    }
}

/** An icon for a menu item, by what it says. */
private fun menuIcon(label: String): String {
    val l = label.lowercase()
    return when {
        l.startsWith("играть дальше") -> Ti.playerTrackNext
        l.startsWith("играть") -> Ti.playerPlay
        l.startsWith("добавить в очередь") -> Ti.list
        l.startsWith("радио") -> Ti.radio
        l.startsWith("отправить") -> Ti.send
        l.startsWith("поделиться") -> Ti.share
        l.startsWith("открыть обложку") -> Ti.photo
        l.contains("плейлист") && (l.startsWith("добавить") || l.startsWith("в плейлист")) -> Ti.playlistAdd
        l.startsWith("другая версия") -> Ti.arrowsRightLeft
        l.startsWith("о треке") -> Ti.infoCircle
        l.startsWith("показать") -> Ti.folderOpen
        l.startsWith("в любимые") -> Ti.heart
        l.contains("любимых") -> Ti.heartFilled
        l.startsWith("скачать") -> Ti.download
        l.startsWith("удалить") -> Ti.trash
        l.startsWith("убрать") -> Ti.x
        l.startsWith("к исполнителю") -> Ti.userCircle
        l.startsWith("к альбому") -> Ti.album
        l.startsWith("закрепить") -> Ti.pin
        l.startsWith("открепить") -> Ti.pinFilled
        l.startsWith("в медиатеку") -> Ti.circlePlus
        l.startsWith("подписаться") -> Ti.userPlus
        l.startsWith("отписаться") -> Ti.user
        l.startsWith("поднять") -> Ti.chevronUp
        l.startsWith("опустить") -> Ti.chevronDown
        l.startsWith("выбрать") || l.startsWith("выделить") || l.startsWith("select") -> Ti.check
        l.startsWith("копир") || l.startsWith("copy") -> Ti.copy
        l.startsWith("встав") || l.startsWith("paste") -> Ti.clipboardCopy
        l.startsWith("выреза") || l.startsWith("cut") -> Ti.edit
        l.startsWith("открыть") -> Ti.externalLink
        else -> Ti.dots
    }
}
