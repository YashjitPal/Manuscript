package com.manuscript.ui.screens.database.views

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NDate
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NPage
import com.manuscript.core.model.PropType
import com.manuscript.core.model.PropValue
import com.manuscript.core.model.PropertySchema
import com.manuscript.core.util.Fmt
import com.manuscript.ui.components.EmptyState
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.screens.database.DbUi
import com.manuscript.ui.screens.database.dateOf
import com.manuscript.ui.screens.database.endDateOf
import com.manuscript.ui.screens.database.value
import com.manuscript.ui.theme.LocalHaptics
import com.manuscript.ui.theme.LocalNotionPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The hue of the first select or status value, used to color calendar chips and timeline bars. */
fun NPage.accentHue(ds: NDataSource): Hue {
    ds.properties.forEach { p ->
        when (val v = value(p)) {
            is PropValue.Status -> v.option?.let { return it.color }
            is PropValue.Select -> v.option?.let { return it.color }
            else -> Unit
        }
    }
    return Hue.BLUE
}

private fun shift(page: NPage, prop: PropertySchema, days: Long): PropValue? {
    val v = page.value(prop) as? PropValue.DateV ?: return null
    val d = v.date ?: return null
    fun move(s: String): String = runCatching {
        if (s.length <= 10) LocalDate.parse(s).plusDays(days).toString()
        else java.time.OffsetDateTime.parse(s).plusDays(days).toString()
    }.getOrElse {
        runCatching { java.time.LocalDateTime.parse(s).plusDays(days).toString() }.getOrDefault(s)
    }
    return PropValue.DateV(prop.id, NDate(move(d.start), d.end?.let(::move), d.timeZone))
}

@Composable
fun CalendarView(state: DbUi, actions: ViewActions, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val palette = LocalNotionPalette.current
    val haptics = LocalHaptics.current
    val dateProp = ds.prop(state.config.datePropertyId) ?: ds.properties.firstOrNull { it.type == PropType.DATE }
    if (dateProp == null) {
        EmptyState(Icons.Rounded.CalendarMonth, "No date property", "Add a date property to use the calendar.", modifier, polygon = MaterialShapes.Square)
        return
    }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthKey)
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selectedDay = LocalDate.parse(selected)
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekdays = (0 until 7).map { firstDay.plus(it.toLong()) }.filter { state.config.showWeekends != false || (it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY) }
    val events = remember(state.rows, dateProp) {
        val map = HashMap<LocalDate, MutableList<NPage>>()
        state.rows.forEach { row ->
            val start = row.dateOf(dateProp) ?: return@forEach
            val end = row.endDateOf(dateProp) ?: start
            var d = start
            var n = 0
            while (!d.isAfter(end) && n < 62) { map.getOrPut(d) { mutableListOf() }.add(row); d = d.plusDays(1); n++ }
        }
        map
    }
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
    val gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
    val weeks = ChronoUnit.WEEKS.between(gridStart, gridEnd.plusDays(1)).toInt().coerceAtLeast(5)
    var hoverDay by remember { mutableStateOf<LocalDate?>(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomy = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month") }
                TextButton(onClick = { monthKey = YearMonth.now().toString(); selected = LocalDate.now().toString() }) { Text("Today") }
                IconButton(onClick = { monthKey = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                weekdays.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(6.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().weight(if (roomy) 1f else 0.62f).padding(horizontal = 8.dp)) {
                (0 until weeks).forEach { w ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        weekdays.forEach { dow ->
                            val weekStart = gridStart.plusWeeks(w.toLong())
                            val date = weekStart.with(TemporalAdjusters.nextOrSame(dow)).let { if (it.isBefore(weekStart.plusDays(7))) it else weekStart }
                            val inMonth = YearMonth.from(date) == month
                            val isToday = date == LocalDate.now()
                            val isSelected = date == selectedDay
                            val dayEvents = events[date].orEmpty()
                            val target = remember(date, state.rows) {
                                object : DragAndDropTarget {
                                    override fun onDrop(event: DragAndDropEvent): Boolean {
                                        hoverDay = null
                                        val data = event.toAndroidDragEvent().localState as? String ?: return false
                                        val parts = data.split('|')
                                        if (parts.size < 2) return false
                                        val pageId = parts[0]
                                        val fromDay = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return false
                                        val page = state.rows.firstOrNull { it.id == pageId } ?: return false
                                        val delta = ChronoUnit.DAYS.between(fromDay, date)
                                        if (delta != 0L) shift(page, dateProp, delta)?.let { haptics.confirm(); actions.update(page, dateProp, it) }
                                        return true
                                    }
                                    override fun onEntered(event: DragAndDropEvent) { hoverDay = date }
                                    override fun onExited(event: DragAndDropEvent) { if (hoverDay == date) hoverDay = null }
                                    override fun onEnded(event: DragAndDropEvent) { hoverDay = null }
                                }
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            hoverDay == date -> MaterialTheme.colorScheme.primaryContainer
                                            isSelected && !roomy -> MaterialTheme.colorScheme.secondaryContainer
                                            inMonth -> MaterialTheme.colorScheme.surfaceContainerLow
                                            else -> Color.Transparent
                                        },
                                    )
                                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
                                    .combinedClickable(
                                        onClick = { selected = date.toString(); haptics.tick() },
                                        onDoubleClick = { if (actions.editable) actions.create(mapOf(dateProp.name to PropValue.DateV(dateProp.id, NDate(date.toString())))) },
                                    )
                                    .padding(4.dp),
                            ) {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium),
                                        color = when {
                                            isToday -> MaterialTheme.colorScheme.onPrimary
                                            inMonth -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                    )
                                }
                                if (roomy) {
                                    dayEvents.take(3).forEach { row ->
                                        val hue = row.accentHue(ds)
                                        Text(
                                            row.title.ifBlank { "Untitled" },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.optionContent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(palette.option(hue))
                                                .dragAndDropSource { _ ->
                                                    if (!actions.editable) null
                                                    else DragAndDropTransferData(ClipData.newPlainText("page", row.id), localState = "${row.id}|$date")
                                                }
                                                .clickable { actions.open(row) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (dayEvents.size > 3) Text("+${dayEvents.size - 3} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (dayEvents.isNotEmpty()) {
                                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayEvents.take(3).forEach { row ->
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent(row.accentHue(ds))))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!roomy) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.friendlyDay(selectedDay), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                    if (actions.editable) FilledTonalButton(onClick = {
                        actions.create(mapOf(dateProp.name to PropValue.DateV(dateProp.id, NDate(selectedDay.toString()))))
                    }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(0.38f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dayEvents = events[selectedDay].orEmpty()
                    if (dayEvents.isEmpty()) item {
                        Text("Nothing scheduled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    }
                    items(dayEvents, key = { it.id }) { row ->
                        val hue = row.accentHue(ds)
                        Surface(onClick = { actions.open(row) }, shape = RoundedCornerShape(18.dp), color = palette.background(hue)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(palette.accent(hue)))
                                Spacer(Modifier.width(12.dp))
                                NotionIcon(row.icon, 20.dp, showFallback = false)
                                if (row.icon != null) Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall)
                                    (row.value(dateProp) as? PropValue.DateV)?.date?.let { Text(Fmt.date(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Zoom(val label: String, val dayWidth: Dp) {
    DAY("Day", 56.dp), WEEK("Week", 24.dp), MONTH("Month", 7.dp), QUARTER("Quarter", 3.dp), YEAR("Year", 1.2.dp)
}

@Composable
fun TimelineView(state: DbUi, actions: ViewActions, modifier: Modifier = Modifier) {
    val ds = state.source ?: return
    val palette = LocalNotionPalette.current
    val density = LocalDensity.current
    val dateProp = ds.prop(state.config.datePropertyId) ?: ds.properties.firstOrNull { it.type == PropType.DATE }
    if (dateProp == null) {
        EmptyState(Icons.Rounded.CalendarMonth, "No date property", "Add a date property to use the timeline.", modifier, polygon = MaterialShapes.Arch)
        return
    }
    val endProp = ds.prop(state.config.endDatePropertyId)
    var zoom by rememberSaveable {
        mutableStateOf(
            when (state.config.zoom) { "hours", "day" -> Zoom.DAY; "week", "bi_week" -> Zoom.WEEK; "quarter" -> Zoom.QUARTER; "year", "5_years" -> Zoom.YEAR; else -> Zoom.MONTH },
        )
    }
    val dated = remember(state.rows, dateProp, endProp) {
        state.rows.mapNotNull { row ->
            val start = row.dateOf(dateProp) ?: return@mapNotNull null
            val end = endProp?.let { row.dateOf(it) } ?: row.endDateOf(dateProp) ?: start
            Triple(row, start, if (end.isBefore(start)) start else end)
        }.sortedBy { it.second }
    }
    val undated = state.rows.filter { r -> dated.none { it.first.id == r.id } }
    val today = LocalDate.now()
    val origin = (dated.minOfOrNull { it.second } ?: today).coerceAtMost(today).minusDays(14)
    val last = (dated.maxOfOrNull { it.third } ?: today).coerceAtLeast(today).plusDays(30)
    val totalDays = ChronoUnit.DAYS.between(origin, last).toInt() + 1
    val scroll = rememberScrollState()
    LaunchedEffect(zoom, origin) {
        val todayX = with(density) { (zoom.dayWidth * ChronoUnit.DAYS.between(origin, today).toFloat()).roundToPx() }
        scroll.scrollTo((todayX - with(density) { 80.dp.roundToPx() }).coerceAtLeast(0))
    }
    val rowHeight = 46.dp
    val showTable = state.config.showTable != false
    BoxWithConstraints(modifier.fillMaxSize()) {
        val tableWidth = if (showTable) (if (maxWidth > 700.dp) 240.dp else 150.dp) else 0.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                Zoom.entries.forEachIndexed { i, z ->
                    ToggleButton(
                        checked = zoom == z,
                        onCheckedChange = { zoom = z },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            Zoom.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) { Text(z.label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp)) {
                Spacer(Modifier.width(tableWidth))
                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Box(Modifier.width(zoom.dayWidth * totalDays).fillMaxHeight()) {
                        var d = origin
                        var guard = 0
                        while (!d.isAfter(last) && guard++ < 4000) {
                            val x = zoom.dayWidth * ChronoUnit.DAYS.between(origin, d).toFloat()
                            val label = when (zoom) {
                                Zoom.DAY -> if (d.dayOfMonth == 1 || d == origin) d.format(DateTimeFormatter.ofPattern("MMM d")) else "${d.dayOfMonth}"
                                Zoom.WEEK -> if (d.dayOfWeek == DayOfWeek.MONDAY) d.format(DateTimeFormatter.ofPattern("MMM d")) else null
                                else -> if (d.dayOfMonth == 1) d.format(DateTimeFormatter.ofPattern(if (zoom == Zoom.YEAR) "MMM yy" else "MMM yyyy")) else null
                            }
                            if (label != null) Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(x = x + 4.dp, y = 12.dp),
                            )
                            d = d.plusDays(1)
                        }
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 160.dp)) {
                items(dated, key = { it.first.id }) { (row, start, end) ->
                    val hue = row.accentHue(ds)
                    Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                        if (showTable) Row(
                            Modifier.width(tableWidth).fillMaxHeight().clickable { actions.open(row) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NotionIcon(row.icon, 18.dp, showFallback = false)
                            if (row.icon != null) Spacer(Modifier.width(6.dp))
                            Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                            Box(Modifier.width(zoom.dayWidth * totalDays).fillMaxHeight()) {
                                val todayX = zoom.dayWidth * ChronoUnit.DAYS.between(origin, today).toFloat()
                                Box(Modifier.offset(x = todayX).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                                val x = zoom.dayWidth * ChronoUnit.DAYS.between(origin, start).toFloat()
                                val w = (zoom.dayWidth * (ChronoUnit.DAYS.between(start, end) + 1).toFloat()).coerceAtLeast(10.dp)
                                Box(
                                    Modifier
                                        .offset(x = x)
                                        .padding(vertical = 8.dp)
                                        .width(w)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.option(hue))
                                        .border(1.dp, palette.accent(hue).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        .clickable { actions.open(row) }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (w > 60.dp) Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.labelMedium, color = palette.optionContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (w <= 60.dp) Text(
                                    row.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    modifier = Modifier.offset(x = x + w + 6.dp).align(Alignment.CenterStart),
                                )
                            }
                        }
                    }
                }
                if (undated.isNotEmpty()) {
                    item {
                        Text("No date (${undated.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    items(undated, key = { "u" + it.id }) { row ->
                        Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable { actions.open(row) }.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
                if (actions.editable) item {
                    NewRowButton { actions.create(mapOf(dateProp.name to PropValue.DateV(dateProp.id, NDate(today.toString(), today.plusDays(3).toString())))) }
                }
                item { Spacer(Modifier.heightIn(min = 1.dp)) }
            }
        }
    }
}
