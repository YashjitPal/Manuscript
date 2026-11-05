package com.manuscript.ui.screens.database.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manuscript.core.json.Write
import com.manuscript.core.model.ChartConfig
import com.manuscript.core.model.Hue
import com.manuscript.core.model.NDataSource
import com.manuscript.core.model.NPage
import com.manuscript.core.model.NUser
import com.manuscript.core.model.NView
import com.manuscript.core.model.ViewType
import com.manuscript.core.util.Fmt
import com.manuscript.ui.LocalSession
import com.manuscript.ui.components.NotionIcon
import com.manuscript.ui.screens.database.aggregate
import com.manuscript.ui.screens.database.aggregatorLabel
import com.manuscript.ui.screens.database.groupRows
import com.manuscript.ui.screens.database.numberOf
import com.manuscript.ui.screens.database.value
import com.manuscript.ui.screens.properties.displayText
import com.manuscript.ui.theme.ChartHues
import com.manuscript.ui.theme.LocalNotionPalette
import com.manuscript.ui.theme.NotionPalette
import kotlin.math.max

@Immutable
data class ChartPoint(val label: String, val value: Double, val color: Color)

fun chartPoints(rows: List<NPage>, chart: ChartConfig, ds: NDataSource, users: Map<String, NUser>, palette: NotionPalette, primary: Color): List<ChartPoint> {
    val yProp = ds.prop(chart.yAxis?.propertyId)
    val aggregator = chart.yAxis?.aggregator ?: "count"
    var points: List<Triple<String, Double, Hue>> = when {
        chart.xAxis != null -> groupRows(rows, chart.xAxis, ds, users).map { g -> Triple(g.label, aggregate(g.rows, aggregator, yProp), g.hue) }
        chart.xAxisPropertyId != null -> {
            val x = ds.prop(chart.xAxisPropertyId)
            val y = ds.prop(chart.yAxisPropertyId)
            rows.map { r -> Triple(x?.let { r.value(it)?.displayText(it) }?.ifBlank { null } ?: r.title.ifBlank { "Untitled" }, y?.let { r.numberOf(it) } ?: 0.0, Hue.DEFAULT) }
        }
        else -> listOf(Triple("All", rows.size.toDouble(), Hue.DEFAULT))
    }
    if (chart.hideEmptyGroups) points = points.filter { it.second != 0.0 }
    points = when (chart.sort) {
        "x_ascending" -> points.sortedBy { it.first.lowercase() }
        "x_descending" -> points.sortedByDescending { it.first.lowercase() }
        "y_ascending" -> points.sortedBy { it.second }
        "y_descending" -> points.sortedByDescending { it.second }
        else -> points
    }
    if (chart.cumulative) {
        var sum = 0.0
        points = points.map { (l, v, h) -> sum += v; Triple(l, sum, h) }
    }
    val theme = chart.colorTheme
    val maxValue = points.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    return points.mapIndexed { i, (label, value, hue) ->
        val color = when {
            theme == null || theme == "colorful" -> palette.accent(ChartHues[i % ChartHues.size])
            theme == "auto" -> if (hue != Hue.DEFAULT) palette.accent(hue) else primary
            else -> {
                val base = palette.accent(Hue.of(theme)).takeIf { theme != "gray" } ?: palette.accent(Hue.GRAY)
                if (chart.colorByValue) lerp(base.copy(alpha = 0.35f), base, (value / maxValue).toFloat()) else base
            }
        }
        ChartPoint(label, value, color)
    }
}

private fun formatValue(v: Double): String = if (v % 1.0 == 0.0) Fmt.plainNumber(v) else "%.1f".format(v)

@Composable
fun ChartView(state: com.manuscript.ui.screens.database.DbUi, users: Map<String, NUser>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ds = state.source ?: return
    val chart = state.config.chart ?: ChartConfig()
    ChartContent(state.rows, chart, ds, users, modifier, compact, state.loadingRows)
}

@Composable
fun ChartContent(rows: List<NPage>, chart: ChartConfig, ds: NDataSource, users: Map<String, NUser>, modifier: Modifier, compact: Boolean, loading: Boolean = false) {
    val palette = LocalNotionPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val points = remember(rows, chart, ds, users, palette) { chartPoints(rows, chart, ds, users, palette, primary) }
    val progress = remember(chart.chartType, rows.size) { Animatable(0f) }
    LaunchedEffect(chart.chartType, rows.size) { progress.snapTo(0f); progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 120f)) }
    val height = when (chart.height) { "small" -> 220.dp; "large" -> 420.dp; "extra_large" -> 520.dp; else -> 320.dp }
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        when (chart.chartType) {
            "number" -> {
                val agg = chart.value?.aggregator ?: "count"
                val value = aggregate(rows, agg, ds.prop(chart.value?.propertyId))
                Column(Modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatValue(value * progress.value), style = if (compact) MaterialTheme.typography.displayMediumEmphasized else MaterialTheme.typography.displayLargeEmphasized, color = primary)
                    Text(aggregatorLabel(agg) + (ds.prop(chart.value?.propertyId)?.let { " of ${it.name}" } ?: ""), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "donut" -> DonutChart(points, chart, progress.value, Modifier.fillMaxWidth().heightIn(max = height))
            "line" -> LineChart(points, chart, progress.value, Modifier.fillMaxWidth().height(height))
            "bar" -> BarChart(points, chart, progress.value, Modifier.fillMaxWidth())
            else -> ColumnChart(points, chart, progress.value, Modifier.fillMaxWidth().height(height))
        }
        chart.caption?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ColumnChart(points: List<ChartPoint>, chart: ChartConfig, progress: Float, modifier: Modifier) {
    val maxValue = max(points.maxOfOrNull { it.value } ?: 1.0, chart.yAxisMax ?: 0.0).takeIf { it > 0 } ?: 1.0
    BoxWithConstraints(modifier) {
        val barWidth = ((maxWidth - 16.dp) / points.size.coerceAtLeast(1) - 12.dp).coerceIn(18.dp, 72.dp)
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { p ->
                Column(Modifier.width(barWidth).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (chart.showDataLabels) Text(formatValue(p.value), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    val fraction = ((p.value / maxValue).toFloat() * progress).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .width(barWidth)
                            .fillMaxHeight(fraction.coerceAtLeast(0.005f) * 0.82f)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(Brush.verticalGradient(listOf(p.color, p.color.copy(alpha = 0.75f)))),
                    )
                    Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp).heightIn(min = 32.dp))
                }
            }
        }
    }
}

@Composable
private fun BarChart(points: List<ChartPoint>, chart: ChartConfig, progress: Float, modifier: Modifier) {
    val maxValue = (points.maxOfOrNull { it.value } ?: 1.0).takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        points.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                Box(Modifier.weight(1f).height(28.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(((p.value / maxValue).toFloat() * progress).coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 6.dp, bottomStart = 6.dp))
                            .background(Brush.horizontalGradient(listOf(p.color.copy(alpha = 0.8f), p.color))),
                    )
                }
                if (chart.showDataLabels) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatValue(p.value), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 32.dp))
                }
            }
        }
    }
}

@Composable
private fun LineChart(points: List<ChartPoint>, chart: ChartConfig, progress: Float, modifier: Modifier) {
    val color = points.firstOrNull()?.color ?: MaterialTheme.colorScheme.primary
    val lineColor = if (chart.colorTheme == null || chart.colorTheme == "colorful") MaterialTheme.colorScheme.primary else color
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maxValue = (points.maxOfOrNull { it.value } ?: 1.0).takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (points.isEmpty()) return@Canvas
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            val pts = points.mapIndexed { i, p -> Offset(i * stepX, size.height - (p.value / maxValue).toFloat() * size.height * 0.9f * progress) }
            (0..4).forEach { i ->
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    if (chart.smoothLine) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        val mid = (cur.x - prev.x) / 2
                        cubicTo(prev.x + mid, prev.y, cur.x - mid, cur.y, cur.x, cur.y)
                    } else lineTo(pts[i].x, pts[i].y)
                }
            }
            if (!chart.hideLineFillArea) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)))
            }
            drawPath(path, lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
            pts.forEach { drawCircle(lineColor, radius = 9f, center = it); drawCircle(Color.White, radius = 4f, center = it) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            points.forEach { p ->
                Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DonutChart(points: List<ChartPoint>, chart: ChartConfig, progress: Float, modifier: Modifier) {
    val total = points.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    BoxWithConstraints(modifier) {
        val side = maxWidth >= 520.dp && chart.legendPosition != "bottom"
        val chartBlock: @Composable () -> Unit = {
            Box(Modifier.widthIn(max = 280.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.18f
                    val inset = stroke / 2
                    var start = -90f
                    points.forEach { p ->
                        val sweep = (p.value / total * 360f).toFloat() * progress
                        if (sweep > 0.5f) drawArc(
                            color = p.color,
                            startAngle = start + 1f,
                            sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatValue(total), style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val legend: @Composable () -> Unit = {
            if (chart.legendPosition != "off") FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                points.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(p.color))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (chart.donutLabels) {
                                "value" -> formatValue(p.value)
                                "name" -> p.label
                                else -> "${p.label} · ${formatValue(p.value)}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        if (side) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(Modifier.weight(1f)) { chartBlock() }
            Box(Modifier.weight(1f)) { legend() }
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            chartBlock()
            legend()
        }
    }
}

private data class WidgetData(val view: NView, val source: NDataSource?, val rows: List<NPage>)

@Composable
fun DashboardView(state: com.manuscript.ui.screens.database.DbUi, users: Map<String, NUser>, onOpen: (NPage) -> Unit, modifier: Modifier = Modifier) {
    val widgets = state.config.dashboard
    if (widgets.isEmpty()) {
        UnsupportedView("Empty dashboard", modifier)
        return
    }
    val rows = widgets.groupBy { it.rowIndex }.toSortedMap().values.toList()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            item {
                BoxWithConstraints {
                    if (maxWidth < 600.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { w -> DashboardWidgetCard(w.viewId, users, onOpen, Modifier.fillMaxWidth()) }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { w -> DashboardWidgetCard(w.viewId, users, onOpen, Modifier.weight(w.width.coerceIn(1, 12).toFloat())) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardWidgetCard(viewId: String, users: Map<String, NUser>, onOpen: (NPage) -> Unit, modifier: Modifier) {
    val session = LocalSession.current
    val data by produceState<WidgetData?>(null, viewId) {
        value = runCatching {
            val view = session.databases.view(viewId)
            val source = view.dataSourceId?.let { session.cache.storedDataSource(it) ?: session.databases.dataSource(it) }
            val rows = source?.let { ds ->
                session.databases.query(ds.id, view.filter, Write.sorts(view.sorts).takeIf { view.sorts.isNotEmpty() }, pageSize = 100).items
            } ?: emptyList()
            WidgetData(view, source, rows)
        }.getOrNull()
    }
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp)) {
            val d = data
            Text(d?.view?.name ?: "Loading…", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 16.dp))
            when {
                d == null -> LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                d.source == null -> Text("This widget can't be shown.", modifier = Modifier.padding(16.dp))
                d.view.type == ViewType.CHART -> ChartContent(d.rows, d.view.config.chart ?: ChartConfig(), d.source, users, Modifier, compact = true)
                else -> Column(Modifier.padding(top = 8.dp)) {
                    d.rows.take(8).forEach { row ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(row) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NotionIcon(row.icon, 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (d.rows.size > 8) Text("+${d.rows.size - 8} more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
