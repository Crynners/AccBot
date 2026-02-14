package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.presentation.utils.DateFormatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Result of CSV export operation.
 */
sealed class CsvExportResult {
    /**
     * Export successful with CSV content and suggested filename.
     */
    data class Success(
        val csvContent: String,
        val suggestedFileName: String
    ) : CsvExportResult()

    /**
     * Export failed with error message.
     */
    data class Error(val message: String) : CsvExportResult()
}

/**
 * Use case for exporting transactions to CSV format.
 * Extracts CSV generation logic from HistoryViewModel for better testability
 * and to remove Context dependency from ViewModel.
 *
 * The actual file writing and sharing is handled by the UI layer,
 * which has access to Context.
 */
class ExportTransactionsToCsvUseCase @Inject constructor(
    private val transactionDao: TransactionDao
) {
    companion object {
        private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
    }

    /**
     * Generate CSV content from all transactions.
     *
     * @return CsvExportResult with content and filename, or error
     */
    suspend fun execute(): CsvExportResult {
        return try {
            val transactions = transactionDao.getAllTransactionsOnce()

            if (transactions.isEmpty()) {
                return CsvExportResult.Error("No transactions to export")
            }

            val csvContent = generateCsv(transactions)
            val fileName = "accbot_transactions_${fileNameFormatter.format(Instant.now())}.csv"

            CsvExportResult.Success(
                csvContent = csvContent,
                suggestedFileName = fileName
            )
        } catch (e: Exception) {
            CsvExportResult.Error(e.message ?: "Failed to export transactions")
        }
    }

    /**
     * Generate CSV string from transactions.
     */
    private fun generateCsv(transactions: List<TransactionEntity>): String {
        return buildString {
            // CSV header
            appendLine("Date,Exchange,Crypto,Fiat,Crypto Amount,Fiat Amount,Price,Fee,Fee Asset,Status,Order ID,Error")

            // Data rows
            transactions.forEach { tx ->
                append(DateFormatters.isoDateTime.format(tx.executedAt))
                append(",")
                append(tx.exchange.displayName)
                append(",")
                append(tx.crypto)
                append(",")
                append(tx.fiat)
                append(",")
                append(tx.cryptoAmount.toPlainString())
                append(",")
                append(tx.fiatAmount.toPlainString())
                append(",")
                append(tx.price.toPlainString())
                append(",")
                append(tx.fee.toPlainString())
                append(",")
                append(tx.feeAsset)
                append(",")
                append(tx.status.name)
                append(",")
                append(tx.exchangeOrderId ?: "")
                append(",")
                // Escape quotes in error message for CSV
                append("\"${tx.errorMessage?.replace("\"", "\"\"") ?: ""}\"")
                appendLine()
            }
        }
    }
}
