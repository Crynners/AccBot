package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.TransactionDao
import com.accbot.dca.data.local.TransactionEntity
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.TransactionStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ParsedTransaction(
    val orderId: String,
    val executedAt: Instant,
    val cryptoAmount: BigDecimal,
    val crypto: String,
    val price: BigDecimal,
    val fiat: String,
    val fee: BigDecimal,
    val feeAsset: String,
    val fiatAmount: BigDecimal
)

sealed class CsvImportResult {
    data class Success(val importedCount: Int) : CsvImportResult()
    data class Error(val message: String) : CsvImportResult()
}

class ImportCoinmateCsvUseCase @Inject constructor(
    private val transactionDao: TransactionDao
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    fun parseCsv(content: String, crypto: String, fiat: String): List<ParsedTransaction> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        // Skip header row
        return lines.drop(1).mapNotNull { line ->
            parseLine(line, crypto, fiat)
        }
    }

    /**
     * Parse a semicolon-delimited CSV line, respecting quoted fields.
     * Handles fields that contain semicolons within double quotes.
     */
    private fun splitCsvLine(line: String, delimiter: Char = ';'): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = false
                    }
                }
                c == delimiter && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun parseLine(line: String, crypto: String, fiat: String): ParsedTransaction? {
        val fields = splitCsvLine(line)
        if (fields.size < 13) return null

        val orderId = fields[0].trim()
        val dateStr = fields[1].trim()
        val type = fields[2].trim()
        val cryptoAmount = fields[3].trim()
        val cryptoCurrency = fields[4].trim()
        val price = fields[5].trim()
        val priceCurrency = fields[6].trim()
        val fee = fields[7].trim()
        val feeCurrency = fields[8].trim()
        val total = fields[9].trim()
        // fields[10] = total currency
        // Find Status field - it's at index 12 in the standard Coinmate export
        val status = fields[12].trim()

        // Only import MARKET_BUY with OK status
        if (type != "MARKET_BUY") return null
        if (status != "OK") return null

        // Only import matching crypto/fiat pair
        if (!cryptoCurrency.equals(crypto, ignoreCase = true)) return null
        if (!priceCurrency.equals(fiat, ignoreCase = true)) return null

        return try {
            val executedAt = LocalDateTime.parse(dateStr, dateFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()

            ParsedTransaction(
                orderId = orderId,
                executedAt = executedAt,
                cryptoAmount = BigDecimal(cryptoAmount),
                crypto = cryptoCurrency.uppercase(),
                price = BigDecimal(price),
                fiat = priceCurrency.uppercase(),
                fee = BigDecimal(fee),
                feeAsset = feeCurrency.uppercase(),
                fiatAmount = BigDecimal(total).abs()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun countNew(
        parsed: List<ParsedTransaction>,
        existingOrderIds: Set<String>
    ): Pair<Int, Int> {
        val newCount = parsed.count { it.orderId !in existingOrderIds }
        val skippedCount = parsed.size - newCount
        return newCount to skippedCount
    }

    fun toEntities(
        parsed: List<ParsedTransaction>,
        planId: Long,
        exchange: Exchange,
        existingOrderIds: Set<String>
    ): List<TransactionEntity> {
        return parsed
            .filter { it.orderId !in existingOrderIds }
            .map { tx ->
                TransactionEntity(
                    planId = planId,
                    exchange = exchange,
                    crypto = tx.crypto,
                    fiat = tx.fiat,
                    fiatAmount = tx.fiatAmount,
                    cryptoAmount = tx.cryptoAmount,
                    price = tx.price,
                    fee = tx.fee,
                    feeAsset = tx.feeAsset,
                    status = TransactionStatus.COMPLETED,
                    exchangeOrderId = tx.orderId,
                    executedAt = tx.executedAt
                )
            }
    }

    suspend fun getExistingOrderIds(planId: Long): Set<String> {
        return transactionDao.getExchangeOrderIdsByPlan(planId).toSet()
    }

    suspend fun importTransactions(entities: List<TransactionEntity>): CsvImportResult {
        return try {
            transactionDao.insertTransactions(entities)
            CsvImportResult.Success(entities.size)
        } catch (e: Exception) {
            CsvImportResult.Error(e.message ?: "Failed to import transactions")
        }
    }
}
