package com.funnelbase.demo.trading.data

import kotlin.random.Random

/**
 * Lorem ipsum, generated rather than pasted.
 *
 * Every screen that needs filler prose gets it from here so the demo has no wall of hardcoded
 * placeholder text to scroll past in the source. Sentences are assembled from the classic word
 * list with real punctuation and capitalisation, which is what makes the result read as prose
 * at a glance instead of as an obvious block of test data.
 */
object Lorem {

    private val WORDS = (
        "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor " +
            "incididunt ut labore et dolore magna aliqua enim ad minim veniam quis nostrud " +
            "exercitation ullamco laboris nisi aliquip ex ea commodo consequat duis aute irure " +
            "in reprehenderit voluptate velit esse cillum eu fugiat nulla pariatur excepteur " +
            "sint occaecat cupidatat non proident sunt culpa qui officia deserunt mollit anim " +
            "id est laborum perspiciatis unde omnis iste natus error accusantium doloremque"
        ).split(" ")

    /** One sentence of [wordCount] words, capitalised and full-stopped. */
    fun sentence(random: Random, wordCount: Int = random.nextInt(8, 16)): String {
        val words = List(wordCount) { WORDS.random(random) }
        return words.joinToString(" ").replaceFirstChar(Char::uppercase) + "."
    }

    /** A paragraph of [sentences] sentences. */
    fun paragraph(random: Random, sentences: Int = random.nextInt(3, 6)): String =
        List(sentences) { sentence(random) }.joinToString(" ")

    /**
     * A headline: title-cased, no full stop.
     *
     * Deliberately shorter than a sentence — a headline that runs to sixteen words wraps to
     * three lines in a list row and stops looking like a headline.
     */
    fun headline(random: Random, wordCount: Int = random.nextInt(4, 8)): String =
        List(wordCount) { WORDS.random(random) }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
