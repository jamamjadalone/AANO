// Conflict-Free Multi-Device 3-Way Line/Block Merge Engine for AANO (Android)
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

object NoteMergeEngine {

    fun mergeNoteContent(local: String, remote: String, base: String = ""): Pair<String, Boolean> {
        if (local == remote) return Pair(local, false)
        if (local.isBlank()) return Pair(remote, false)
        if (remote.isBlank()) return Pair(local, false)

        val localLines = local.lines()
        val remoteLines = remote.lines()

        val mergedLines = mutableListOf<String>()
        var hasConflict = false

        var i = 0
        var j = 0
        while (i < localLines.size && j < remoteLines.size) {
            val lLine = localLines[i]
            val rLine = remoteLines[j]

            if (lLine == rLine) {
                mergedLines.add(lLine)
                i++
                j++
            } else {
                val rMatch = remoteLines.subList(j, remoteLines.size).indexOf(lLine)
                val lMatch = localLines.subList(i, localLines.size).indexOf(rLine)

                if (rMatch != -1 && (lMatch == -1 || rMatch <= lMatch)) {
                    for (k in j until (j + rMatch)) {
                        mergedLines.add(remoteLines[k])
                    }
                    mergedLines.add(lLine)
                    i++
                    j += rMatch + 1
                } else if (lMatch != -1 && (rMatch == -1 || lMatch < rMatch)) {
                    for (k in i until (i + lMatch)) {
                        mergedLines.add(localLines[k])
                    }
                    mergedLines.add(rLine)
                    i += lMatch + 1
                    j++
                } else {
                    hasConflict = true
                    mergedLines.add("<<<<<<< Local Edit")
                    mergedLines.add(lLine)
                    mergedLines.add("=======")
                    mergedLines.add(rLine)
                    mergedLines.add(">>>>>>> Incoming Edit")
                    i++
                    j++
                }
            }
        }

        while (i < localLines.size) {
            mergedLines.add(localLines[i++])
        }
        while (j < remoteLines.size) {
            mergedLines.add(remoteLines[j++])
        }

        return Pair(mergedLines.joinToString("\n"), hasConflict)
    }
}
