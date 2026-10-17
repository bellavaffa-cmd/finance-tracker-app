package com.financetracker.app.domain

/** RFC 4180 CSV with the delimiter sniffed from the header, since bank exports use , ; or tab. */
object Csv {

    fun detectDelimiter(text: String): Char {
        val firstLine = StringBuilder()
        var quoted = false
        for (c in text) {
            if (c == '"') quoted = !quoted
            if (!quoted && (c == '\n' || c == '\r')) break
            if (!quoted) firstLine.append(c)
        }
        return listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }
            ?.takeIf { d -> firstLine.contains(d) } ?: ','
    }

    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val src = text.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endRow() {
            row.add(field.toString()); field.clear()
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
            row.clear()
        }
        while (i < src.length) {
            val c = src[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                delimiter -> { row.add(field.toString()); field.clear() }
                '\r' -> { endRow(); if (i + 1 < src.length && src[i + 1] == '\n') i++ }
                '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    fun escape(field: String, delimiter: Char = ','): String =
        if (field.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } || field != field.trim())
            "\"" + field.replace("\"", "\"\"") + "\""
        else field

    fun write(rows: List<List<String>>, delimiter: Char = ','): String =
        rows.joinToString("\r\n", postfix = "\r\n") { r -> r.joinToString(delimiter.toString()) { escape(it, delimiter) } }
}
