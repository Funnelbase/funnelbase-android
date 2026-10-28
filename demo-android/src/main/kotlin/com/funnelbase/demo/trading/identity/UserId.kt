package com.funnelbase.demo.trading.identity

import java.security.MessageDigest
import java.util.Locale

/**
 * Turns a username into the stable user id the analytics backend sees.
 *
 * There is no sign-up API in this demo, so the id has to come from the only thing the user
 * supplies. Deriving it from a hash means the same username always produces the same id — on a
 * relaunch, after a reinstall, and on a different device — which is what makes it usable as a
 * `user_id` at all. A random id would make every sign-in a new person.
 *
 * SHA-256 rather than `String.hashCode()`: 32 bits collide at a few tens of thousands of names,
 * and this value is what a person's whole event history hangs off.
 */
object UserId {

    /**
     * @param username as typed. Trimmed and lowercased first, so " Ada " and "ada" are one
     *   person rather than two — the same normalisation a real login would apply.
     */
    fun forUsername(username: String): String {
        val normalized = username.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        // 16 hex characters — 64 bits, which is plenty for a demo and short enough to read off
        // a screen and look up in the dashboard.
        return "u_" + digest.joinToString("", limit = 8, truncated = "") { "%02x".format(it) }
    }
}
