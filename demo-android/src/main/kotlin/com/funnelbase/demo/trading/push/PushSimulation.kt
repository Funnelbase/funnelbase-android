package com.funnelbase.demo.trading.push

/**
 * Payloads shaped exactly as Funnelbase sends them, so the app can show what a campaign will
 * look like without Firebase, a project, or a network at all.
 *
 * They exist because the parts of push a developer most wants to see — the image, the buttons,
 * the collapsing — are the parts that need a provider to arrive. Handing the same `data` map to
 * `Analytics.push.handleMessage` exercises everything after the transport: the parse, the
 * notification, the receipt event and the tap.
 *
 * The image url points at a local server on the host's port 9099, as an emulator reaches it (any
 * server that answers /media/sample.png with an image will do). A device on the same network
 * wants the host's address instead.
 */
object PushSimulation {

    const val EMULATOR_IMAGE_URL = "http://10.0.2.2:9099/media/sample.png"

    /** Title, body, a deep link, and nothing else: what most campaigns are. */
    fun basic(deliveryId: String): Map<String, String> = mapOf(
        "delivery_id" to deliveryId,
        "run_id" to SIMULATED_RUN,
        "title" to "AAPL is up 2.4%",
        "body" to "Apple leads the index this morning after yesterday's close.",
        "deep_link" to "trade://instrument/AAPL",
    )

    /** An image, a long body and two buttons that lead to different places. */
    fun rich(deliveryId: String, imageUrl: String = EMULATOR_IMAGE_URL): Map<String, String> = mapOf(
        "delivery_id" to deliveryId,
        "run_id" to SIMULATED_RUN,
        "title" to "Your order filled",
        "body" to "12 shares of MSFT at $412.80. The rest of the order was cancelled at the close, " +
            "and the cash is back in your account.",
        "image_url" to imageUrl,
        "deep_link" to "trade://orders",
        "buttons" to """[{"id":"view_order","title":"View order","deep_link":"trade://instrument/MSFT"},""" +
            """{"id":"markets","title":"Markets","deep_link":"trade://markets"}]""",
    )

    /**
     * Two of these replace one another in the notification drawer rather than stacking, which is
     * what a collapse key is for.
     */
    fun collapsing(deliveryId: String): Map<String, String> = mapOf(
        "delivery_id" to deliveryId,
        "run_id" to SIMULATED_RUN,
        "title" to "Market close",
        "body" to "The index finished at ${(4_800..5_200).random()}, a new print each time.",
        "collapse_key" to "market_close",
    )

    /**
     * The run id a simulated push claims. A real one is a uuid the dashboard shows; this is
     * deliberately not one, so a receipt from a simulation is recognisable in the data rather
     * than counting against a campaign that was never sent.
     */
    const val SIMULATED_RUN = "simulated-local-run"
}
