package com.drxwnify.desktop.ui.screens

import com.drxwnify.desktop.ui.clickHand
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.data.Downloads
import com.drxwnify.desktop.data.Paths
import com.drxwnify.desktop.data.Prefs
import com.drxwnify.desktop.playback.AudioEngine
import com.drxwnify.desktop.playback.Player
import com.drxwnify.desktop.ui.Chip
import com.drxwnify.desktop.ui.Hairline
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.IconBtn
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Panel
import com.drxwnify.desktop.ui.PillButton
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Toggle
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.hand
import com.drxwnify.desktop.ui.rememberHover
import com.drxwnify.desktop.ui.theme
import com.drxwnify.desktop.ui.theme.DrxTheme
import com.drxwnify.desktop.ui.theme.Fonts
import com.drxwnify.desktop.ui.theme.Themes

private data class Section(val id: String, val glyph: String, val title: String, val note: String)

private val sections = listOf(
    Section("appearance", Ti.palette, "Внешний вид", "Темы, фон, анимации, масштаб"),
    Section("player", Ti.waveSine, "Плеер и звук", "Очередь, переходы, устройство вывода"),
    Section("sources", Ti.database, "Источники и качество", "Откуда играть и в каком качестве"),
    Section("content", Ti.language, "Контент и тексты", "Каталог, регион, тексты песен"),
    Section("integrations", Ti.puzzle, "Интеграции", "Spotify, VK, Яндекс, Last.fm, Discord, Soulseek"),
    Section("downloads", Ti.folder, "Загрузки и хранилище", "Папка, формат, кэш"),
    Section("sync", Ti.deviceMobile, "Телефон и копии", "Синхронизация, резервная копия"),
    Section("privacy", Ti.shieldLock, "Приватность", "Что видят друзья, история"),
    Section("keys", Ti.keyboard, "Горячие клавиши", "Управление с клавиатуры"),
    Section("about", Ti.infoCircle, "О программе", "Версия, обновления, предложка"),
)

@Composable
fun SettingsOverlay() {
    AnimatedVisibility(
        Nav.settingsOpen,
        enter = fadeIn(tween(200)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.985f),
        exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.99f),
    ) {
        Box(Modifier.fillMaxSize().padding(top = 64.dp, bottom = 96.dp, start = 12.dp, end = 12.dp)) {
            Panel(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    SettingsNav()
                    Box(Modifier.width(1.dp).fillMaxHeight().background(theme.panelBorder))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AnimatedContent(Nav.settingsSection, transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(260)) { 18 }) togetherWith fadeOut(tween(120))
                        }) { id ->
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp)) {
                                val s = sections.first { it.id == id }
                                Txt(s.title, size = 28.sp, weight = theme.headingWeight, family = theme.heading, letterSpacing = (-0.6).sp)
                                Spacer(h = 4.dp)
                                Txt(s.note, size = 13.sp, color = theme.mute)
                                Spacer(h = 22.dp)
                                when (id) {
                                    "appearance" -> Appearance()
                                    "player" -> PlayerSection()
                                    "sources" -> Sources()
                                    "content" -> Content()
                                    "integrations" -> Integrations()
                                    "downloads" -> DownloadsSection()
                                    "sync" -> SyncSection()
                                    "privacy" -> Privacy()
                                    "keys" -> KeysSection()
                                    "about" -> About()
                                }
                                Spacer(h = 30.dp)
                            }
                        }
                        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) { IconBtn(Ti.x, { Nav.settingsOpen = false }, filled = true, tooltip = "Закрыть (Esc)") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNav() {
    val t = theme
    Column(Modifier.width(250.dp).fillMaxHeight().padding(vertical = 22.dp, horizontal = 10.dp)) {
        Row(Modifier.padding(start = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("水", size = 22.sp, family = Fonts.jpSerif, color = t.accent, weight = FontWeight.SemiBold)
            Spacer(w = 10.dp)
            Txt("Настройки", size = 20.sp, weight = FontWeight.ExtraBold)
        }
        sections.forEach { s ->
            val on = Nav.settingsSection == s.id
            val (src, hovered) = rememberHover()
            val bg by animateColorAsState(if (on) t.accent.copy(alpha = 0.10f) else if (hovered) t.fill else Color.Transparent, tween(150))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(10.dp)).background(bg).hoverable(src).hand()
                    .clickable(interactionSource = src, indication = null) { Nav.settingsSection = s.id }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(2.dp).height(18.dp).background(if (on) t.accent else Color.Transparent))
                Spacer(w = 10.dp)
                Ico(s.glyph, size = 17.sp, color = if (on) t.accent else t.dim)
                Spacer(w = 12.dp)
                Txt(s.title, size = 13.sp, weight = FontWeight.SemiBold, color = if (on || hovered) t.text else t.dim)
            }
        }
    }
}

// ---------- building blocks ----------

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = theme
    Txt(title.uppercase(), size = 11.sp, weight = FontWeight.ExtraBold, color = t.mute, letterSpacing = 1.4.sp)
    Spacer(h = 8.dp)
    Column(Modifier.fillMaxWidth(), content = content)
    Spacer(h = 26.dp)
}

@Composable
private fun SettingRow(title: String, note: String? = null, soon: Boolean = false, control: @Composable () -> Unit) {
    val t = theme
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(title, size = 14.sp, weight = FontWeight.SemiBold, color = if (soon) t.dim else t.text)
                    if (soon) { Spacer(w = 8.dp); Soon() }
                }
                if (note != null) { Spacer(h = 3.dp); Txt(note, size = 12.sp, color = t.mute, maxLines = 3, lineHeight = 17.sp) }
            }
            if (soon) Box(Modifier.graphicsLayer { alpha = 0.35f }) {
                control()
                // Not working yet: the control shows where it will be, but can't be changed.
                Box(Modifier.matchParentSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { })
            } else control()
        }
        Hairline()
    }
}

@Composable
private fun Soon() {
    val t = theme
    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(t.accent.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Txt("скоро", size = 10.sp, weight = FontWeight.Bold, color = t.accent)
    }
}

@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEach { (k, v) -> Chip(v, selected == k, { onPick(k) }) } }
}

// ---------- sections ----------

@Composable
private fun Appearance() {
    val s by Prefs.settings.state.collectAsState()
    Group("Тема") {
        SettingRow("Дневная тема", if (s.themeDay) "Та же тема, чуть светлее — тёмная, как у Telegram. Выключи — вернётся ночная" else "Сейчас ночная. Включи — та же тема станет чуть светлее") {
            Toggle(s.themeDay) { v -> Prefs.settings.update { it.copy(themeDay = v) } }
        }
        Spacer(h = 12.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val current = Themes.spec(s.theme).id
            Themes.specs.forEach { sp -> ThemeCard(sp.of(s.themeDay), current == sp.id) { Prefs.settings.update { it.copy(theme = sp.id) } } }
        }
    }
    Group("Фон") {
        SettingRow("Узоры на фоне", "Свои у каждой темы: знаки, лепестки, гирлянда, звёзды. Едва заметные") {
            Toggle(s.backgroundGlyphs) { v -> Prefs.settings.update { it.copy(backgroundGlyphs = v) } }
        }
        SettingRow("Движение фона", "Анимации темы: плывут знаки, падают лепестки, мерцают огоньки. Выключи, если отвлекает") {
            Toggle(s.backgroundMotion) { v -> Prefs.settings.update { it.copy(backgroundMotion = v) } }
        }
    }
    Group("Интерфейс") {
        SettingRow("Плавные анимации", "Переходы страниц, появление меню, панели") {
            Toggle(s.smoothAnimations) { v -> Prefs.settings.update { it.copy(smoothAnimations = v) } }
        }
        SettingRow("Масштаб", "Если всё кажется мелким на большом мониторе") {
            Choices(listOf("0.9" to "90%", "1.0" to "100%", "1.1" to "110%", "1.25" to "125%"), "%.2f".format(s.uiScale).trimEnd('0').trimEnd('.').let { if (it == "1") "1.0" else it }) { v ->
                Prefs.settings.update { it.copy(uiScale = v.toFloat()) }
            }
        }
        SettingRow("Боковые панели", "Медиатека и «Сейчас играет» вернутся к исходной ширине. То же — двойной щелчок по краю панели") {
            PillButton("Вернуть как было", Ti.layoutColumns, onClick = { com.drxwnify.desktop.ui.resetPanels(); Toast.show("Панели на месте") })
        }
        SettingRow("Язык", "Русский. Английский появится вместе с переводом на телефоне", soon = false) {
            Choices(listOf("ru" to "Русский"), s.language) { }
        }
    }
}

@Composable
private fun ThemeCard(th: DrxTheme, selected: Boolean, onClick: () -> Unit) {
    val t = theme
    val (src, hovered) = rememberHover()
    val ring by animateColorAsState(if (selected) t.accent else if (hovered) t.panelBorder.copy(alpha = 0.6f) else t.panelBorder, tween(180))
    Column(
        Modifier.width(196.dp).clip(RoundedCornerShape(16.dp)).border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(16.dp))
            .hoverable(src).hand().clickable(interactionSource = src, indication = null, onClick = onClick).padding(10.dp),
    ) {
        // A tiny picture of the window in that theme.
        Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(th.bg2, th.bg)))) {
            Row(Modifier.fillMaxSize().padding(7.dp)) {
                Box(Modifier.width(34.dp).fillMaxHeight().clip(RoundedCornerShape(th.panelRadius / 3)).background(th.panel.copy(alpha = (th.panel.alpha + 0.2f).coerceAtMost(1f))).border(1.dp, th.panelBorder, RoundedCornerShape(th.panelRadius / 3)))
                Spacer(w = 6.dp)
                Column(Modifier.weight(1f)) {
                    Box(Modifier.width(60.dp).height(7.dp).clip(RoundedCornerShape(3.dp)).background(th.text.copy(alpha = 0.8f)))
                    Spacer(h = 6.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i -> Box(Modifier.size(22.dp).clip(RoundedCornerShape(th.coverRadius / 3)).background(th.sample[i % th.sample.size].copy(alpha = 0.75f))) }
                    }
                    Spacer(h = 6.dp)
                    Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(th.panelRadius / 3)).background(th.panel.copy(alpha = (th.panel.alpha + 0.2f).coerceAtMost(1f))).border(1.dp, th.panelBorder, RoundedCornerShape(th.panelRadius / 3))) {
                        Box(Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(th.accent))
                    }
                }
            }
        }
        Spacer(h = 10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(th.name, size = 14.sp, weight = th.headingWeight, family = th.heading, modifier = Modifier.weight(1f))
            if (selected) Ico(Ti.circleCheck, size = 16.sp, color = t.accent)
        }
        Spacer(h = 2.dp)
        Txt(th.note, size = 11.sp, color = t.mute, maxLines = 2, lineHeight = 15.sp)
    }
}

@Composable
private fun PlayerSection() {
    val s by Prefs.settings.state.collectAsState()
    Group("Очередь") {
        SettingRow("Бесконечная очередь", "Когда очередь заканчивается, дальше играют похожие треки") {
            Toggle(s.endlessQueue) { v -> Prefs.settings.update { it.copy(endlessQueue = v) } }
        }
        SettingRow("Плавный переход между треками", "Кроссфейд, как на телефоне", soon = true) {
            Choices(listOf("0" to "Нет", "3" to "3 с", "6" to "6 с", "10" to "10 с"), s.crossfadeSec.toString()) { v -> Prefs.settings.update { it.copy(crossfadeSec = v.toInt()) } }
        }
    }
    Group("Звук") {
        SettingRow("Выравнивание громкости", "Тихие и громкие треки звучат одинаково", soon = true) {
            Toggle(s.normalize) { v -> Prefs.settings.update { it.copy(normalize = v) } }
        }
        SettingRow("Пропуск тишины", "Длинные паузы в треке пропускаются", soon = true) {
            Toggle(s.skipSilence) { v -> Prefs.settings.update { it.copy(skipSilence = v) } }
        }
        SettingRow("Стереобаланс", when {
            s.balance < -0.02f -> "Левее: ${(-s.balance * 100).toInt()}%"
            s.balance > 0.02f -> "Правее: ${(s.balance * 100).toInt()}%"
            else -> "По центру. Двойной щелчок по ползунку — в центр"
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("L", size = 11.sp)
                Spacer(w = 8.dp)
                com.drxwnify.desktop.ui.SeekBar((s.balance + 1f) / 2f, { v -> Player.setBalance(((v * 2f - 1f) * 20).toInt() / 20f) }, Modifier.width(180.dp), live = true)
                Spacer(w = 8.dp)
                Mono("R", size = 11.sp)
                Spacer(w = 10.dp)
                PillButton("Центр", onClick = { Player.setBalance(0f) })
            }
        }
        val devices = remember { listOf("" to "Системное") + AudioEngine.outputDevices().take(4).map { it to it.take(26) } }
        SettingRow("Устройство вывода", "Куда идёт звук. Применится со следующего трека") {
            Column(horizontalAlignment = Alignment.End) {
                devices.forEach { (k, v) ->
                    Chip(v, (s.outputDevice ?: "") == k, {
                        Prefs.settings.update { it.copy(outputDevice = k.ifBlank { null }) }
                        Player.engine.outputDevice = k.ifBlank { null }
                    }, Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Sources() {
    val s by Prefs.settings.state.collectAsState()
    Group("Качество") {
        SettingRow("Качество звука", "Максимальное — лучший доступный поток. Экономия — меньше трафика") {
            Choices(listOf("max" to "Максимальное", "balanced" to "Сбалансированное", "saver" to "Экономия"), s.quality) { v -> Prefs.settings.update { it.copy(quality = v) } }
        }
        SettingRow("Как выбирать источник", "Качество — ждёт лучший звук до 2 секунд (для загрузок до 8). Скорость — первое найденное") {
            Choices(listOf("quality" to "Качество", "accuracy" to "Точность", "speed" to "Скорость"), s.pickMode) { v -> Prefs.settings.update { it.copy(pickMode = v) } }
        }
    }
    Group("Источники") {
        val config by com.drxwnify.desktop.data.RemoteConfig.config.collectAsState()
        val names = mapOf(
            "soundcloud" to ("SoundCloud" to "Ремиксы, андеграунд и редкие релизы"),
            "youtube" to ("YouTube" to "Работает всегда, без входа"),
            "vk" to ("VK Музыка" to "MP3 320: свой вход в «Интеграциях» или общий аккаунт"),
            "lossless" to ("FLAC-зеркала" to if (config.losslessMirrors.isEmpty()) "Без потерь — сейчас зеркал в конфиге нет" else "Без потерь, зеркал: ${config.losslessMirrors.size}"),
            "bandcamp" to ("Bandcamp" to "Независимые релизы, бесплатные потоки"),
            "audius" to ("Audius" to "Свои релизы артистов: электроника, фонк, хип-хоп"),
            "soulseek" to ("Soulseek" to "FLAC от людей, когда все молчат"),
        )
        val order = com.drxwnify.desktop.playback.Sources.order()
        Txt("Ищут одновременно; при равном совпадении играет лучший звук, а при равном звуке — тот, что выше.", size = 12.sp, color = theme.mute, maxLines = 2)
        Spacer(h = 6.dp)
        order.forEachIndexed { i, id ->
            val (n, d) = names[id] ?: (id to "")
            SettingRow(n, if (id in config.disabledSources) "$d · сейчас выключен для всех" else d, soon = id == "soulseek") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ti.chevronUp, { if (i > 0) Prefs.settings.update { it.copy(sources = order.toMutableList().apply { add(i - 1, removeAt(i)) }) } }, size = 28.dp, iconSize = 14.sp, tooltip = "Выше")
                    IconBtn(Ti.chevronDown, { if (i < order.lastIndex) Prefs.settings.update { it.copy(sources = order.toMutableList().apply { add(i + 1, removeAt(i)) }) } }, size = 28.dp, iconSize = 14.sp, tooltip = "Ниже")
                    Spacer(w = 8.dp)
                    Toggle(id == "youtube" || (id !in s.disabledSources && id != "soulseek")) { v ->
                        if (id != "youtube" && id != "soulseek") Prefs.settings.update { it.copy(disabledSources = if (v) it.disabledSources - id else it.disabledSources + id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Content() {
    val s by Prefs.settings.state.collectAsState()
    Group("Каталог") {
        SettingRow("Откуда брать каталог", "Поиск, альбомы и исполнители. Spotify открыт и без входа; с аккаунтом — ещё твоя медиатека, любимые и миксы") {
            Choices(listOf("spotify" to "Spotify", "youtube" to "YouTube"), if (s.catalog == "yandex") "spotify" else s.catalog) { v ->
                Prefs.settings.update { it.copy(catalog = v) }
            }
        }
        SettingRow("Любимые вместе со Spotify", "Лайк здесь попадает в аккаунт, лайки с телефона и из Spotify — сюда") {
            Toggle(s.spotifySyncLikes) { v -> Prefs.settings.update { it.copy(spotifySyncLikes = v) }; if (v) com.drxwnify.desktop.data.ioScope.launch { com.drxwnify.desktop.catalog.LikesSync.pull() } }
        }
        SettingRow("Регион", "Что показывать в чартах и новинках") {
            Choices(listOf("RU" to "Россия", "KZ" to "Казахстан", "BY" to "Беларусь", "US" to "США"), s.contentRegion) { v ->
                Prefs.settings.update { it.copy(contentRegion = v) }
                com.metrolist.innertube.YouTube.locale = com.metrolist.innertube.models.YouTubeLocale(v, "ru")
                HomeFeed.load(force = true)
            }
        }
    }
    Group("Тексты песен") {
        SettingRow("LRCLIB", "Синхронизированные тексты, работает без входа") { Toggle(true) { } }
        SettingRow("BetterLyrics и KuGou", "Запасные источники, когда LRCLIB молчит", soon = true) { Toggle(false) { } }
        SettingRow("Перевод текстов", "ИИ-перевод строк под оригиналом, как на телефоне", soon = true) {
            Toggle(s.translateLyrics) { v -> Prefs.settings.update { it.copy(translateLyrics = v) } }
        }
    }
}

@Composable
private fun Integrations() {
    val t = theme
    val acc by Prefs.accounts.state.collectAsState()
    Txt("Аккаунты для каталога, любимых и миксов. Копия с телефона («Телефон и копии») переносит входы сама — второй раз входить не нужно.", size = 13.sp, color = t.dim, maxLines = 3, lineHeight = 19.sp)
    Spacer(h = 18.dp)
    val spotifyExpired by com.drxwnify.desktop.catalog.SpotifyCatalog.sessionExpired.collectAsState()
    Service(Ti.brandSpotify, "Spotify",
        when {
            acc.spotifySpDc == null -> "не подключён · медиатека, любимые, миксы"
            spotifyExpired -> "вход устарел — перенеси свежую копию с телефона"
            else -> "подключён" + (acc.spotifyName?.let { " · $it" } ?: "") + " · медиатека, любимые, миксы"
        },
        acc.spotifySpDc != null && !spotifyExpired,
        hint = "Проще всего — перенести копию с телефона («Телефон и копии»). Или вставь cookie sp_dc из браузера, где ты вошёл в open.spotify.com.", secret = true,
        onSave = { v -> Prefs.accounts.update { it.copy(spotifySpDc = v, spotifyAccessToken = null, spotifyTokenExpiry = 0) }; com.drxwnify.desktop.catalog.SpotifyCatalog.accountChanged() },
        onLogout = { com.drxwnify.desktop.catalog.SpotifyCatalog.logout() })
    Service(Ti.brandVk, "VK Музыка", if (acc.vkToken != null) "подключён · MP3 320" else "не подключён · MP3 320 и твои аудио", acc.vkToken != null,
        hint = "Токен VK (как на телефоне). Вход по логину и паролю появится в следующей сборке.", secret = true,
        onSave = { v -> Prefs.accounts.update { it.copy(vkToken = v) } }, onLogout = { Prefs.accounts.update { it.copy(vkToken = null) } })
    Service(null, "Яндекс Музыка", if (acc.yandexToken != null) "подключён · каталог и чарты" else "не подключён · каталог и чарты", acc.yandexToken != null, letter = "Я",
        hint = "OAuth-токен Яндекс Музыки.", secret = true,
        onSave = { v -> Prefs.accounts.update { it.copy(yandexToken = v) } }, onLogout = { Prefs.accounts.update { it.copy(yandexToken = null) } })
    Service(Ti.brandLastfm, "Last.fm", if (acc.lastfmUser != null) "скробблинг · ${acc.lastfmUser}" else "не подключён · скробблинг и жанры для главной", acc.lastfmUser != null,
        hint = "Имя пользователя Last.fm.", onSave = { v -> Prefs.accounts.update { it.copy(lastfmUser = v) } }, onLogout = { Prefs.accounts.update { it.copy(lastfmUser = null, lastfmSession = null) } })
    Service(Ti.brandDiscord, "Discord", "статус «Слушает…» в профиле", false, soon = true)
    Service(Ti.share3, "Soulseek", if (acc.soulseekUser != null) "аккаунт ${acc.soulseekUser}" else "не подключён · FLAC от людей", acc.soulseekUser != null,
        hint = "Имя пользователя Soulseek (пароль спросим при первом поиске).", onSave = { v -> Prefs.accounts.update { it.copy(soulseekUser = v) } },
        onLogout = { Prefs.accounts.update { it.copy(soulseekUser = null, soulseekPassword = null) } })
}

@Composable
private fun Service(
    glyph: String?, name: String, status: String, connected: Boolean,
    letter: String? = null, hint: String = "", secret: Boolean = false, soon: Boolean = false,
    onSave: ((String) -> Unit)? = null, onLogout: (() -> Unit)? = null,
) {
    val t = theme
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(t.fill), contentAlignment = Alignment.Center) {
                if (glyph != null) Ico(glyph, size = 22.sp, color = t.text) else Txt(letter ?: "?", size = 18.sp, weight = FontWeight.ExtraBold)
            }
            Spacer(w = 16.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Txt(name, size = 14.sp, weight = FontWeight.Bold); if (soon) { Spacer(w = 8.dp); Soon() } }
                Spacer(h = 3.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.drxwnify.desktop.ui.Dot(if (connected) t.ok else t.mute.copy(alpha = 0.6f))
                    Spacer(w = 7.dp)
                    Mono(status, size = 11.sp)
                }
            }
            when {
                soon -> Unit
                connected -> PillButton("Отключить", onClick = { onLogout?.invoke() })
                onSave != null -> PillButton(if (editing) "Отмена" else "Подключить", primary = !editing, onClick = { editing = !editing })
            }
        }
        AnimatedVisibility(editing) {
            Column(Modifier.padding(start = 60.dp, bottom = 14.dp)) {
                Txt(hint, size = 12.sp, color = t.mute, maxLines = 3)
                Spacer(h = 8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(t.fill).border(1.dp, t.panelBorder, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(value, { value = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(t.accent), visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(w = 10.dp)
                    PillButton("Сохранить", primary = true, onClick = { if (value.isNotBlank()) { onSave?.invoke(value.trim()); editing = false; value = ""; Toast.show("$name подключён") } })
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun DownloadsSection() {
    val s by Prefs.settings.state.collectAsState()
    Group("Папка") {
        SettingRow("Куда сохранять", Downloads.folder.absolutePath) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Открыть", Ti.folderOpen, onClick = { runCatching { java.awt.Desktop.getDesktop().open(Downloads.folder) } })
                PillButton("Изменить", onClick = {
                    val chooser = javax.swing.JFileChooser(Downloads.folder).apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Папка для музыки" }
                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) Prefs.settings.update { it.copy(downloadDir = chooser.selectedFile.absolutePath) }
                })
            }
        }
        SettingRow("Формат файлов", "Оригинал — поток сохраняется как есть, без потери качества (.opus, .m4a, .flac)") {
            Choices(listOf("original" to "Оригинал"), s.downloadFormat) { }
        }
    }
    Group("Кэш") {
        val size = remember { Paths.covers.listFiles()?.sumOf { it.length() } ?: 0L }
        SettingRow("Обложки", "Сохраняются, чтобы не качать заново: ${size / 1_048_576} МБ") {
            PillButton("Очистить", Ti.trash, onClick = { Paths.covers.listFiles()?.forEach { it.delete() }; Toast.show("Кэш обложек очищен") })
        }
        var audio by remember { mutableStateOf(com.drxwnify.desktop.playback.AudioCache.sizeBytes()) }
        SettingRow("Прослушанные треки", "Последние треки целиком, до 600 МБ: перемотка мгновенная, повтор без интернета. Сейчас ${audio / 1_048_576} МБ") {
            PillButton("Очистить", Ti.trash, onClick = { com.drxwnify.desktop.playback.AudioCache.clear(); audio = 0; Toast.show("Кэш треков очищен") })
        }
    }
}

@Composable
private fun SyncSection() {
    val t = theme
    Group("Телефон") {
        Txt("ПК и телефон — один аккаунт: любимые, плейлисты, история, «продолжить с места» и вход в сервисы. Всё ходит через Nostr-релеи в зашифрованном виде — своего сервера у нас нет.", size = 13.sp, color = t.dim, maxLines = 4, lineHeight = 19.sp)
        Spacer(h = 14.dp)
        SettingRow("Связать с телефоном", "На телефоне: Друзья → профиль → «Ключ восстановления». Вставь его в «Друзьях» здесь — это тот же профиль", soon = true) {
            PillButton("К друзьям", Ti.users, onClick = { Nav.go(com.drxwnify.desktop.ui.Screen.Friends) })
        }
    }
    Group("Резервная копия") {
        SettingRow("Перенести с телефона", "На телефоне: Настройки → Резервная копия → «Создать». Файл .backup перенесёт любимые, плейлисты, историю, друзей и входы в Spotify, Last.fm и другие сервисы") {
            PillButton("Открыть .backup…", Ti.deviceMobile, onClick = { Backup.import() })
        }
        SettingRow("Сохранить копию ПК", "Настройки, медиатека и очередь в один файл .drxw") {
            PillButton("Сохранить…", Ti.deviceFloppy, onClick = { Backup.export() })
        }
        SettingRow("Восстановить копию ПК", "Файл .drxw заменит текущие настройки и медиатеку") {
            PillButton("Открыть…", Ti.folderOpen, onClick = { Backup.import() })
        }
    }
}

@Composable
private fun Privacy() {
    val s by Prefs.settings.state.collectAsState()
    Group("Друзья") {
        SettingRow("Показывать, что я слушаю", "Друзья видят текущий трек и могут слушать вместе") {
            Toggle(s.shareListening) { v -> Prefs.settings.update { it.copy(shareListening = v) } }
        }
    }
    Group("История") {
        SettingRow("Не записывать историю", "Пока включено, прослушанное не попадает в историю и рекомендации") {
            Toggle(s.pauseHistory) { v -> Prefs.settings.update { it.copy(pauseHistory = v) } }
        }
    }
}

@Composable
private fun KeysSection() {
    val t = theme
    val s by Prefs.settings.state.collectAsState()
    val registered by com.drxwnify.desktop.GlobalHotkeys.registered.collectAsState()
    val taken by com.drxwnify.desktop.GlobalHotkeys.taken.collectAsState()
    var recordingFor by remember { mutableStateOf<com.drxwnify.desktop.HotkeyAction?>(null) }
    DisposableEffect(Unit) { onDispose { if (com.drxwnify.desktop.Hotkeys.recording) com.drxwnify.desktop.Hotkeys.recording = false } }
    Group("Плеер с клавиатуры") {
        SettingRow("Работают везде", "Даже когда окно свёрнуто или ты в игре. Выключено — только пока открыт Drxwnify") {
            Toggle(s.globalHotkeys) { v -> Prefs.settings.update { it.copy(globalHotkeys = v) } }
        }
        com.drxwnify.desktop.HotkeyAction.entries.forEach { a ->
            val combo = s.hotkeys[a.id].let { saved -> if (saved == null) a.default else com.drxwnify.desktop.Combo.decode(saved) }
            val note = when {
                combo == null -> "выключено"
                a.id in taken -> "это сочетание занято другой программой — выбери другое"
                s.globalHotkeys && a.id in registered -> "работает везде"
                else -> "работает в окне приложения"
            }
            SettingRow(a.label, note) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyRecorder(combo, recordingFor == a, taken = a.id in taken, onStart = {
                        recordingFor = a; com.drxwnify.desktop.Hotkeys.recording = true
                    }, onDone = { c ->
                        if (c != null) com.drxwnify.desktop.Hotkeys.set(a, c)
                        recordingFor = null; com.drxwnify.desktop.Hotkeys.recording = false
                    })
                    Spacer(w = 6.dp)
                    if (combo != a.default) IconBtn(Ti.arrowBackUp, { com.drxwnify.desktop.Hotkeys.reset(a) }, size = 30.dp, iconSize = 15.sp, tooltip = "Вернуть ${a.default.label}")
                    if (combo != null) IconBtn(Ti.x, { com.drxwnify.desktop.Hotkeys.set(a, null) }, size = 30.dp, iconSize = 15.sp, tooltip = "Выключить")
                }
            }
        }
        Spacer(h = 4.dp)
        Txt("Нажми на сочетание, чтобы задать своё: зажми Ctrl, Alt или Shift (лучше два-три сразу) и клавишу. Esc — отмена.", size = 12.sp, color = t.mute, maxLines = 3)
    }
    Spacer(h = 18.dp)
    Group("В окне приложения") {
        listOf(
            "Пробел" to "Пауза / продолжить", "Ctrl + K" to "Поиск", "Ctrl + →" to "Следующий трек", "Ctrl + ←" to "Предыдущий трек",
            "Ctrl + ↑ / ↓" to "Громкость", "Alt + ←" to "Назад", "Esc" to "Закрыть меню, снять выбор треков", "Двойной клик по треку" to "Играть",
            "Ctrl / Shift + клик по треку" to "Выбрать несколько треков", "Правый клик по треку" to "Меню трека",
        ).forEach { (k, v) ->
            SettingRow(v) { KeyCap(k) }
        }
    }
}

@Composable
private fun KeyCap(text: String, highlight: Boolean = false, warn: Boolean = false) {
    val t = theme
    val shape = RoundedCornerShape(if (t.look == com.drxwnify.desktop.ui.theme.Look.Terminal) 0.dp else 7.dp)
    Box(
        Modifier.clip(shape).background(if (highlight) t.accent.copy(alpha = 0.18f) else t.fill)
            .border(1.dp, if (highlight) t.accent else if (warn) t.accent2 else t.panelBorder, shape).padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Mono(text, size = 12.sp, color = if (highlight) t.accent else t.text) }
}

/** The combination as a key cap; clicked, it waits for a new one (needs at least one of Ctrl / Alt / Shift). */
@Composable
private fun KeyRecorder(combo: com.drxwnify.desktop.Combo?, recording: Boolean, taken: Boolean, onStart: () -> Unit, onDone: (com.drxwnify.desktop.Combo?) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    var held by remember { mutableStateOf("") }
    LaunchedEffect(recording) { if (recording) { held = ""; runCatching { focus.requestFocus() } } }
    Box(
        Modifier.focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (!recording) return@onPreviewKeyEvent false
                val n = e.nativeKeyEvent as? java.awt.event.KeyEvent ?: return@onPreviewKeyEvent true
                if (n.id != java.awt.event.KeyEvent.KEY_PRESSED) return@onPreviewKeyEvent true
                when {
                    n.keyCode == java.awt.event.KeyEvent.VK_ESCAPE -> onDone(null)
                    com.drxwnify.desktop.Combo.isModifier(n.keyCode) -> held = com.drxwnify.desktop.Combo.of(n).label.substringBeforeLast(" + ").let { if (it.isNotEmpty()) "$it + …" else "" }
                    else -> com.drxwnify.desktop.Combo.of(n).let { c -> if (c.mods != 0) onDone(c) else held = "нужен Ctrl, Alt или Shift" }
                }
                true
            }
            .clickHand { if (!recording) onStart() },
    ) {
        KeyCap(if (recording) held.ifEmpty { "нажми сочетание…" } else combo?.label ?: "не задано", highlight = recording, warn = taken)
    }
}

@Composable
private fun About() {
    val t = theme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(t.fill), contentAlignment = Alignment.Center) {
            Txt("水", size = 34.sp, family = Fonts.jpSerif, color = t.accent, weight = FontWeight.SemiBold)
        }
        Spacer(w = 16.dp)
        Column {
            Txt("Drxwnify для Windows", size = 18.sp, weight = FontWeight.ExtraBold)
            Mono("версия $APP_VERSION", size = 12.sp)
            Spacer(h = 4.dp)
            Txt("水 — «вода»: в музыку погружаешься, как в воду, с головой.", size = 12.sp, color = t.dim)
        }
    }
    Spacer(h = 24.dp)
    Group("Что нового") {
        listOf(
            "Первая бета для Windows: главная, поиск, альбомы, исполнители, плейлисты",
            "Плеер на FFmpeg: Opus, AAC, FLAC, HLS; текст песни по строкам",
            "Медиатека с любимыми и плейлистами, скачивание треков без перекодирования",
            "9 тем, у каждой своё оформление; дневной вариант — та же тема чуть светлее",
            "Выбор нескольких треков и отправка файлами, горячие клавиши, работающие везде",
            "Тянущиеся панели, свёртывание медиатеки в колонку обложек",
        ).forEach { line -> Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) { Txt("+", size = 13.sp, color = t.accent); Spacer(w = 8.dp); Txt(line, size = 13.sp, color = t.dim, maxLines = 2) } }
    }
    Group("Обратная связь") {
        SettingRow("Предложить идею", "Сообщение уйдёт разработчику через Nostr, как на телефоне", soon = true) { PillButton("Написать", Ti.bulb, onClick = { }) }
        SettingRow("Сайт", "drxwnify.pages.dev") { PillButton("Открыть", Ti.externalLink, onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://drxwnify.pages.dev")) } }) }
    }
}
