package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DiagnosticRecordEntity
import com.example.ui.components.LtrText
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.StatusFault
import com.example.ui.theme.StatusFixed
import com.example.ui.theme.StatusFixedContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    records: List<DiagnosticRecordEntity>,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.US) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "ذخیره محلی گزارش‌های عیب‌یابی، بهینه‌سازی و تست سرعت",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (records.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearAll,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StatusFault.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = StatusFault,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("پاکسازی", color = StatusFault, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                item(key = "history_empty") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SlateSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.history_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.history_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Aggregate Summary Strip
                item(key = "history_stats") {
                    val avgDownload = records.map { it.downloadMbps }.filter { it > 0.0 }.average()
                        .takeIf { !it.isNaN() } ?: 0.0
                    val avgPing = records.map { it.internetPingMs }.filter { it > 0 }.average()
                        .takeIf { !it.isNaN() }?.toInt() ?: 0
                    val totalFixed = records.sumOf { it.issuesFixed }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryStatCard(
                            label = "میانگین دانلود",
                            value = "${"%.1f".format(avgDownload)} Mbps",
                            accent = SignalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatCard(
                            label = "میانگین پینگ",
                            value = if (avgPing > 0) "${avgPing} ms" else "--",
                            accent = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatCard(
                            label = "رفع عیب موفق",
                            value = "$totalFixed مورد",
                            accent = StatusFixed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                items(records, key = { it.id }) { rec ->
                    HistoryRecordCard(
                        record = rec,
                        formattedDate = dateFormatter.format(Date(rec.timestamp)),
                        onDelete = { onDeleteRecord(rec.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            LtrText(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                mono = true
            )
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: DiagnosticRecordEntity,
    formattedDate: String,
    onDelete: () -> Unit
) {
    val (typeLabel, typeIcon, badgeColor, badgeBg) = when (record.recordType) {
        "AUTO_FIX" -> HistoryBadge("رفع عیب خودکار", Icons.Filled.AutoFixHigh, SignalEmerald, SignalEmeraldContainer)
        "SPEED_TEST" -> HistoryBadge("تست سرعت", Icons.Filled.Speed, CyberCyan, CyberCyanContainer)
        else -> HistoryBadge("عیب‌یابی مودم", Icons.Filled.NetworkCheck, StatusFixed, StatusFixedContainer)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${record.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = badgeBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(typeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                            Text(text = typeLabel, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                        }
                    }
                    LtrText(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_record),
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = record.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryMiniPill("امتیاز", "${record.healthScore}", if (record.healthScore >= 75) SignalEmerald else StatusWarning, Modifier.weight(1f))
                HistoryMiniPill("دانلود", "${"%.1f".format(record.downloadMbps)}M", CyberCyan, Modifier.weight(1f))
                HistoryMiniPill("پینگ", if (record.internetPingMs > 0) "${record.internetPingMs}ms" else "--", TextPrimary, Modifier.weight(1f))
                HistoryMiniPill("مودم", record.gatewayIp, TextSecondary, Modifier.weight(1.3f))
            }
        }
    }
}

@Composable
private fun HistoryMiniPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SlateSurfaceElevated.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            LtrText(text = value, style = MaterialTheme.typography.labelSmall, color = color, mono = true)
        }
    }
}

private data class HistoryBadge(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val bg: androidx.compose.ui.graphics.Color
)
