package com.drxwnify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drxwnify.desktop.catalog.NightReviews
import com.drxwnify.desktop.ui.Chip
import com.drxwnify.desktop.ui.Heading
import com.drxwnify.desktop.ui.HoverRow
import com.drxwnify.desktop.ui.Ico
import com.drxwnify.desktop.ui.Mono
import com.drxwnify.desktop.ui.Nav
import com.drxwnify.desktop.ui.Screen
import com.drxwnify.desktop.ui.Spacer
import com.drxwnify.desktop.ui.ThemedArt
import com.drxwnify.desktop.ui.Ti
import com.drxwnify.desktop.ui.Txt
import com.drxwnify.desktop.ui.theme
import kotlinx.coroutines.launch

private enum class ReviewSort(val label: String) { POSTED("по дате"), RATING("по оценке"), YEAR("по году"), LISTENERS("по слушателям"), ARTIST("по исполнителю") }

/**
 * drxwnslvt's: his album reviews from the Telegram channel — newest first, sortable, filtered by rating,
 * genre and year, searchable; a click opens the album in the catalog (or searches it when not found).
 */
@Composable
fun ReviewsScreen() {
    val t = theme
    val state by NightReviews.state.collectAsState()
    LaunchedEffect(Unit) { NightReviews.refresh() }
    var sort by remember { mutableStateOf(ReviewSort.POSTED) }
    var minRating by remember { mutableStateOf<Int?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val all = state.reviews
    val shown = remember(all, sort, minRating, genre, year, query) {
        val q = query.trim().lowercase()
        all.filter { r ->
            (minRating == null || r.rating >= minRating!!) && (genre == null || genre in r.genres) && (year == null || r.year == year) &&
                (q.isEmpty() || r.artist.lowercase().contains(q) || r.album.lowercase().contains(q))
        }.sortedWith(
            when (sort) {
                ReviewSort.POSTED -> compareByDescending<NightReviews.Review> { it.postDate }.thenByDescending { it.messageId }
                ReviewSort.RATING -> compareByDescending { it.rating }
                ReviewSort.YEAR -> compareByDescending { it.year ?: 0 }
                ReviewSort.LISTENERS -> compareByDescending { it.popularity }
                ReviewSort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
            },
        )
    }
    fun open(r: NightReviews.Review) {
        if (opening != null) return
        opening = r.id
        scope.launch {
            val id = NightReviews.findAlbum(r)
            opening = null
            if (id != null) Nav.go(Screen.Album(id))
            else { Toast.show("Альбом не нашёлся в каталоге — ищу"); Nav.searchText = "${r.artist} ${r.album}"; Nav.go(Screen.Search(Nav.searchText)) }
        }
    }
    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Heading("drxwnslvt's", size = 30.sp)
                    Spacer(h = 4.dp)
                    Mono(if (state.loading && all.isEmpty()) "загружаю обзоры…" else "${all.size} альбомов с оценками из канала", size = 12.sp)
                }
                // search inside the list
                Row(
                    Modifier.width(260.dp).height(38.dp).clip(RoundedCornerShape(t.chipRadius.coerceAtMost(19.dp))).background(t.fill).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Ti.search, size = 15.sp, color = t.mute)
                    Spacer(w = 8.dp)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Txt("исполнитель или альбом", size = 13.sp, color = t.mute)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 13.sp, fontFamily = t.body), cursorBrush = SolidColor(t.accent), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ReviewSort.entries.forEach { s -> Chip(s.label, sort == s, { sort = s }) }
                Spacer(w = 10.dp)
                listOf(9, 8, 7).forEach { r -> Chip("от $r", minRating == r, { minRating = if (minRating == r) null else r }) }
            }
        }
        item {
            val genres = remember(all) { all.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(14).map { it.key } }
            val years = remember(all) { all.mapNotNull { it.year }.distinct().sortedDescending().take(8) }
            Column(Modifier.padding(top = 8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genres.forEach { g -> Chip(g, genre == g, { genre = if (genre == g) null else g }) }
                }
                Spacer(h = 6.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    years.forEach { y -> Chip(y.toString(), year == y, { year = if (year == y) null else y }) }
                }
            }
        }
        if (state.error && all.isEmpty()) item { Empty(Ti.alertTriangle, "Обзоры не загрузились", "Сайт канала не ответил. Попробуй позже.") }
        item { Spacer(h = 12.dp) }
        shown.forEach { r -> item(key = r.id) { ReviewRow(r, r.id in state.newIds, opening == r.id) { open(r) } } }
    }
}

@Composable
private fun ReviewRow(r: NightReviews.Review, isNew: Boolean, busy: Boolean, onClick: () -> Unit) {
    val t = theme
    HoverRow(onClick, Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 2.dp)) {
        Box(Modifier.padding(8.dp).size(66.dp)) {
            ThemedArt(r.coverUrl, Modifier.fillMaxSize(), seed = r.id.hashCode())
            if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { Ico(Ti.loader2, size = 18.sp, color = Color.White) }
        }
        Spacer(w = 8.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(r.album, size = 15.5.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (isNew) {
                    Spacer(w = 8.dp)
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF0BE7E)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Txt("новое", size = 10.sp, weight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
            Txt(r.artist, size = 13.sp, color = t.dim)
            Mono(listOfNotNull(r.year?.toString(), r.genres.take(3).joinToString(", ").takeIf { it.isNotBlank() }, r.label.takeIf { it.isNotBlank() }).joinToString(" · "), size = 11.5.sp)
        }
        Mono(formatPostDate(r.postDate), size = 11.5.sp, modifier = Modifier.padding(horizontal = 14.dp))
        RatingPill(r.rating)
        Spacer(w = 14.dp)
    }
}

private fun formatPostDate(date: String): String = runCatching {
    java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("ru")))
}.getOrDefault(date)

private fun formatRating(r: Double): String =
    if (r == Math.floor(r)) r.toInt().toString() else java.math.BigDecimal(r).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** As on the phone: 10 green-gold, 6 and lower red, through yellow. */
@Composable
private fun RatingPill(rating: Double) {
    val k = ((rating - 6.0) / 4.0).coerceIn(0.0, 1.0).toFloat()
    val c = Color.hsv(125f * k, 0.75f, 0.9f)
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.16f)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Txt(formatRating(rating), size = 14.sp, weight = FontWeight.Bold, color = c)
    }
}
