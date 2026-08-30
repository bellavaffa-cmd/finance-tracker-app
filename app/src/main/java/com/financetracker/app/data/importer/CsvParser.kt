package com.financetracker.app.data.importer

/**
 * A minimal RFC 4180 reader.
 *
 * Written by hand rather than split on commas, because a real bank export contains quoted fields
 * with commas inside them ("Smith, John"), doubled quotes, and occasionally a newline inside a
 * quoted description. Splitting on commas silently shifts every column after the first such field,
 * which corrupts the import in a way the user would only notice months later.
 *
 * Delimiter is detected rather than assumed: exports from European locales are frequently
 * semicolon-separated, and a comma-only reader turns those into a single-column file.
 */
object CsvParser {

    private const val BOM = '﻿'
    private val CANDIDATE_DELIMITERS = charArrayOf(',', ';', '\t', '|')

    /** Rows of fields. Ragged rows are preserved as-is; validation is the caller's job. */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val input = text.removePrefix(BOM.toString())
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()

        var inQuotes = false
        var index = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            // A trailing newline produces one empty field, which is not a row of data.
            if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) rows.add(row.toList())
            row.clear()
        }

        while (index < input.length) {
            val char = input[index]
            when {
                inQuotes && char == '"' ->
                    // A doubled quote inside a quoted field is a literal quote, not the end of it.
                    if (index + 1 < input.length && input[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }

                inQuotes -> field.append(char)

                char == '"' -> inQuotes = true

                char == delimiter -> endField()

                char == '\r' -> {
                    // Swallow CRLF as one break; a lone CR is treated as a break too.
                    if (index + 1 < input.length && input[index + 1] == '\n') index++
                    endRow()
                }

                char == '\n' -> endRow()

                else -> field.append(char)
            }
            index++
        }

        // Whatever is buffered when the text runs out is a final row without a trailing newline.
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * Picks the delimiter that yields the most consistent column count across the first few lines.
     * Counting occurrences alone is not enough - a description full of semicolons would win.
     */
    fun detectDelimiter(text: String): Char {
        val sample = text.removePrefix(BOM.toString())
            .lineSequence()
            .filter { it.isNotBlank() }
            .take(5)
            .toList()
        if (sample.isEmpty()) return ','

        return CANDIDATE_DELIMITERS.maxByOrNull { candidate ->
            val counts = sample.map { line -> countOutsideQuotes(line, candidate) }
            val first = counts.first()
            when {
                first == 0 -> -1
                // Consistency first, then column count, so a genuine delimiter beats a stray one.
                counts.all { it == first } -> 1000 + first
                else -> first
            }
        } ?: ','
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> count++
            }
        }
        return count
    }
}
