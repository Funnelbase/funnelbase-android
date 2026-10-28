package com.funnelbase.sdk.internal

import java.io.File

/**
 * The offline queue: newline-delimited JSON on disk, one event per line.
 *
 * A file rather than SQLite because the access pattern is append-and-drain-from-the-front —
 * a database would be a dependency and a schema for something a text file does correctly.
 * Every operation is synchronized; callers are the SDK's single background executor plus
 * whatever thread the host app calls `track` on.
 */
internal class EventQueue(private val file: File, private val maxEvents: Int) {

    @Synchronized
    fun enqueue(serializedEvent: String) {
        // Newlines would corrupt the line-per-event framing. Serialized JSON never contains a
        // raw one, but a future change to the writer shouldn't be able to silently break this.
        require(!serializedEvent.contains('\n')) { "Serialized events must not contain newlines" }

        file.parentFile?.mkdirs()
        file.appendText(serializedEvent + "\n")
        trimToMaxSize()
    }

    @Synchronized
    fun peek(limit: Int): List<String> {
        if (!file.exists()) return emptyList()
        return file.useLines { lines -> lines.filter(String::isNotBlank).take(limit).toList() }
    }

    /**
     * Removes the first [count] events, which the caller has just uploaded successfully.
     *
     * Removal is separate from [peek] so a failed upload leaves the queue untouched and the
     * events are retried rather than lost.
     */
    @Synchronized
    fun removeFirst(count: Int) {
        if (count <= 0 || !file.exists()) return

        val remaining = file.useLines { lines -> lines.filter(String::isNotBlank).drop(count).toList() }
        if (remaining.isEmpty()) {
            file.delete()
        } else {
            file.writeText(remaining.joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun size(): Int =
        if (!file.exists()) 0 else file.useLines { lines -> lines.count(String::isNotBlank) }

    @Synchronized
    fun clear() {
        file.delete()
    }

    /**
     * Drops the *oldest* events past the cap.
     *
     * Dropping the newest would be cheaper but wrong: after a long offline stretch the recent
     * events are the ones still worth having.
     */
    private fun trimToMaxSize() {
        val lines = file.useLines { it.filter(String::isNotBlank).toList() }
        if (lines.size <= maxEvents) return
        file.writeText(lines.takeLast(maxEvents).joinToString("\n", postfix = "\n"))
    }
}
