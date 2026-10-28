package com.funnelbase.demo.trading.data

import kotlin.random.Random

data class NewsArticle(
    val id: String,
    val headline: String,
    val summary: String,
    val source: String,
    val minutesAgo: Int,
)

/**
 * The news feed: generated headlines and summaries.
 *
 * Headlines are prefixed with a real ticker from the launch's instrument list so the feed looks
 * like it belongs to this app rather than to a lorem ipsum generator, while the body stays
 * obviously synthetic.
 */
object News {

    private val SOURCES = listOf("Market Wire", "Global Desk", "Trading Floor", "The Ticker Report")

    fun generate(random: Random, instruments: List<Instrument>, count: Int = 20): List<NewsArticle> {
        // Accumulated, not `index * random(...)`: multiplying is not monotonic — article 1 could
        // draw 69 and article 2 draw 2x9=18 — and the feed would show "1h ago" above "12m ago".
        // Adding a positive gap each time is what actually makes the list order the sort order.
        var minutesAgo = 0

        return (0 until count).map { index ->
            val instrument = instruments.random(random)
            minutesAgo += random.nextInt(9, 70)

            NewsArticle(
                id = "news-$index",
                headline = "${instrument.symbol}: ${Lorem.headline(random)}",
                summary = Lorem.paragraph(random, sentences = random.nextInt(2, 4)),
                source = SOURCES.random(random),
                minutesAgo = minutesAgo,
            )
        }
    }
}
