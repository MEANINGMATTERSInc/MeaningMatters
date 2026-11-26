package com.example.core

enum class DiffLineType {
    UNCHANGED,
    ADDED,
    REMOVED
}

data class DiffLine(
    val type: DiffLineType,
    val text: String,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null
)

data class DiffSummary(
    val lines: List<DiffLine>,
    val addedLines: Int,
    val removedLines: Int,
    val unchangedLines: Int,
    val wordDelta: Long
)

/**
 * Deterministic Longest Common Subsequence (LCS) line diff engine
 * for comparing chapter manuscript checkpoints and current drafts.
 */
object TextDiffEngine {

    fun computeDiff(oldText: String, newText: String): DiffSummary {
        val oldLines = if (oldText.isEmpty()) emptyList() else oldText.split("\n")
        val newLines = if (newText.isEmpty()) emptyList() else newText.split("\n")

        val oldWordCount = countWordsInternal(oldText)
        val newWordCount = countWordsInternal(newText)
        val wordDelta = newWordCount - oldWordCount

        val m = oldLines.size
        val n = newLines.size

        // Guard against huge manuscripts exceeding DP memory bounds: chunk or fallback gracefully
        if (m * n > 250_000) {
            return computeFastFallbackDiff(oldLines, newLines, wordDelta)
        }

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val result = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        var added = 0
        var removed = 0
        var unchanged = 0

        while (i < m && j < n) {
            when {
                oldLines[i] == newLines[j] -> {
                    result.add(DiffLine(DiffLineType.UNCHANGED, oldLines[i], i + 1, j + 1))
                    unchanged++
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    result.add(DiffLine(DiffLineType.REMOVED, oldLines[i], i + 1, null))
                    removed++
                    i++
                }
                else -> {
                    result.add(DiffLine(DiffLineType.ADDED, newLines[j], null, j + 1))
                    added++
                    j++
                }
            }
        }
        while (i < m) {
            result.add(DiffLine(DiffLineType.REMOVED, oldLines[i], i + 1, null))
            removed++
            i++
        }
        while (j < n) {
            result.add(DiffLine(DiffLineType.ADDED, newLines[j], null, j + 1))
            added++
            j++
        }

        return DiffSummary(
            lines = result,
            addedLines = added,
            removedLines = removed,
            unchangedLines = unchanged,
            wordDelta = wordDelta
        )
    }

    private fun computeFastFallbackDiff(
        oldLines: List<String>,
        newLines: List<String>,
        wordDelta: Long
    ): DiffSummary {
        val result = mutableListOf<DiffLine>()
        val maxLen = maxOf(oldLines.size, newLines.size)
        var added = 0
        var removed = 0
        var unchanged = 0
        for (idx in 0 until maxLen) {
            val o = oldLines.getOrNull(idx)
            val n = newLines.getOrNull(idx)
            when {
                o == n && o != null -> {
                    result.add(DiffLine(DiffLineType.UNCHANGED, o, idx + 1, idx + 1))
                    unchanged++
                }
                else -> {
                    if (o != null) {
                        result.add(DiffLine(DiffLineType.REMOVED, o, idx + 1, null))
                        removed++
                    }
                    if (n != null) {
                        result.add(DiffLine(DiffLineType.ADDED, n, null, idx + 1))
                        added++
                    }
                }
            }
        }
        return DiffSummary(result, added, removed, unchanged, wordDelta)
    }

    private fun countWordsInternal(text: String): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        return trimmed.split(Regex("\\s+")).size.toLong()
    }
}
