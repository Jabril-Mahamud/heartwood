package dev.easonhuang.heartwood.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.MetricSummary
import dev.easonhuang.heartwood.data.WeekStyle
import dev.easonhuang.heartwood.data.formatValue
import dev.easonhuang.heartwood.data.title
import dev.easonhuang.heartwood.data.weekSoFar
import java.time.format.TextStyle

/**
 * This calendar week's steps as nested goal rings: the first day of the week is the outer ring and
 * each day after sits one ring in, so a day keeps its ring all week. Days still to come aren't drawn.
 * [summary] must pass [stepGoal]; its spark is the daily totals ending [MetricSummary.asOf].
 */
@Composable
fun WeekGoalCard(
    summary: MetricSummary,
    goal: Float,
    style: WeekStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // asOf, not now(): the spark is from the last refresh, which may have been before midnight.
    val week = weekSoFar(summary.spark, style.start, summary.asOf)
    val days = week.map { it.second }
    val daysMet = days.count { it >= goal }
    val average = summary.metric.formatValue(days.average().toFloat())
    val goalMet = pluralStringResource(R.plurals.summary_chart_goal_met, days.size, summary.metric.title, daysMet, days.size)
    val description = "$goalMet, ${stringResource(R.string.dashboard_week_avg, average)}"
    val locale = LocalLocale.current.platformLocale
    val rings = week.map { (date, value) -> Triple(date, value, style.color(date.dayOfWeek)) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.padding(20.dp).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                rings.forEachIndexed { i, (_, value, color) ->
                    GoalRing(progress = value / goal, color = color, diameter = (168 - 22 * i).dp, stroke = 8.dp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.summary_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("$daysMet/${days.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.dashboard_week_goal_met),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.dashboard_week_avg, average),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(6.dp))
                rings.forEach { (date, value, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 48.dp).padding(end = 8.dp),
                        )
                        Text(summary.metric.formatValue(value), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
