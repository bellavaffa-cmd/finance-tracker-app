package com.financetracker.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.txn.TransactionDetail
import com.financetracker.app.data.txn.TxnType

/**
 * One transaction line, shared by the dashboard and the transactions list.
 *
 * The amount is shown in the account's own currency rather than converted to base, because when
 * you are checking whether a purchase was recorded correctly you want the number that was on the
 * receipt, not a derived one.
 */
@Composable
fun TransactionRow(
    detail: TransactionDetail,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    tags: List<Pair<String, Int>> = emptyList(),
    isSplit: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (detail.type == TxnType.TRANSFER) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(18.dp)
            )
        } else {
            CategoryDot(detail.categoryColorArgb, size = 10, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    primaryLabel(detail, isSplit),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (detail.recurringRuleId != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Autorenew,
                        contentDescription = "From a recurring rule",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(14.dp)
                    )
                }
                if (detail.attachmentName != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "Has a receipt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(13.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                secondaryLabel(detail, showDate, isSplit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Three is what fits before the amount starts getting crowded; the rest are
                    // summarised rather than wrapping the row onto a second line.
                    tags.take(3).forEach { (name, colorArgb) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(colorArgb).copy(alpha = 0.20f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "#" + name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(colorArgb)
                            )
                        }
                    }
                    if (tags.size > 3) {
                        Text(
                            "+" + (tags.size - 3),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedAmount(detail),
                style = MaterialTheme.typography.bodyLarge,
                color = when (detail.type) {
                    TxnType.INCOME -> amountColor(true)
                    TxnType.EXPENSE -> amountColor(false)
                    TxnType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                maxLines = 1
            )
            // A cross-currency transfer needs both legs visible, or the destination balance moving
            // by a different number than the one on screen looks like a bug.
            crossCurrencyLeg(detail)?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun primaryLabel(detail: TransactionDetail, isSplit: Boolean): String = when {
    detail.payee.isNotBlank() -> detail.payee
    detail.type == TxnType.TRANSFER -> "${detail.accountName} to ${detail.toAccountName.orEmpty()}"
    detail.categoryName != null -> detail.categoryName
    // A split legitimately has no category of its own, so it must not be labelled the same way as
    // an entry the user forgot to categorise.
    isSplit -> "Split payment"
    else -> "Uncategorised"
}

private fun secondaryLabel(detail: TransactionDetail, showDate: Boolean, isSplit: Boolean): String {
    val parts = mutableListOf<String>()
    if (showDate) parts += shortDate(detail.dateMillis)
    when (detail.type) {
        TxnType.TRANSFER -> parts += "${detail.accountName} to ${detail.toAccountName.orEmpty()}"
        else -> {
            // A split has no single category of its own, so say so rather than "Uncategorised",
            // which would read like a mistake the user needs to fix.
            val category = when {
                isSplit -> "Split"
                detail.parentCategoryName != null -> "${detail.parentCategoryName} / ${detail.categoryName}"
                else -> detail.categoryName ?: "Uncategorised"
            }
            parts += category
            parts += detail.accountName
        }
    }
    if (detail.note.isNotBlank()) parts += detail.note
    return parts.joinToString(" · ")
}

private fun signedAmount(detail: TransactionDetail): String {
    val formatted = Money.format(detail.amountMinor, detail.accountCurrency)
    return when (detail.type) {
        TxnType.INCOME -> "+$formatted"
        TxnType.EXPENSE -> "-$formatted"
        TxnType.TRANSFER -> formatted
    }
}

private fun crossCurrencyLeg(detail: TransactionDetail): String? {
    if (detail.type != TxnType.TRANSFER) return null
    val toCurrency = detail.toAccountCurrency ?: return null
    if (toCurrency == detail.accountCurrency) return null
    val landed = detail.toAmountMinor ?: return null
    return "to ${Money.format(landed, toCurrency)}"
}
