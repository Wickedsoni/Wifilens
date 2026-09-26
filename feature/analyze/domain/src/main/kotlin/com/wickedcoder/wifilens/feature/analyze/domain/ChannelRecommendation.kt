package com.wickedcoder.wifilens.feature.analyze.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** What the Spectrum tab's BEST CHANNEL card shows. */
sealed interface ChannelAdvice {
    data class Switch(
        val channel: Int,
        /** "2.4" / "5" / "6", as in [ScannedNetwork.band]. */
        val band: String,
        val congestionScore: Int,
        /** 5 GHz channels 52-144 can be vacated by the router for radar — worth a caveat. */
        val isDfs: Boolean,
    ) : ChannelAdvice

    /** The connected network's channel is already at least as quiet as every alternative. */
    data object AlreadyOptimal : ChannelAdvice
}

/** 2.4 GHz: only 1/6/11 don't overlap each other. */
private val CANDIDATES_24 = listOf(1, 6, 11)
private val CANDIDATES_5 = listOf(
    36,
    40,
    44,
    48,
    52,
    56,
    60,
    64,
    100,
    104,
    108,
    112,
    116,
    120,
    124,
    128,
    132,
    136,
    140,
    144,
    149,
    153,
    157,
    161,
    165,
)

/** 6 GHz: 20 MHz channels 1, 5, 9 ... 233 (the preferred scanning channels are every fourth). */
private const val FIRST_6GHZ_CHANNEL = 1
private const val LAST_6GHZ_CHANNEL = 233
private const val CHANNEL_STEP_6GHZ = 4
private val CANDIDATES_6 = (FIRST_6GHZ_CHANNEL..LAST_6GHZ_CHANNEL step CHANNEL_STEP_6GHZ).toList()

/** 5 GHz channels 52-144 share spectrum with weather radar (DFS). */
private val DFS_CHANNELS = 52..144

/** Congestion is reported on a 0-100 scale. */
internal const val MAX_CONGESTION = 100

/** Signal range mapped onto that scale: [WEAKEST_RSSI_DBM] (unusable) is 0, weakest + [RSSI_SPAN_DB] is 100. */
private const val WEAKEST_RSSI_DBM = 100
private const val RSSI_SPAN_DB = 70

/** On 2.4 GHz a network up to this many channels away still leaks into ours, weaker the further it is. */
private const val OVERLAP_MAX_DISTANCE = 4
private const val OVERLAP_FALLOFF_DIVISOR = 5.0

internal fun isDfsChannel(channel: Int): Boolean = channel in DFS_CHANNELS

/** Congestion 0-100 from RSSI: -30dBm (very strong) -> ~100, -100dBm (unusable) -> ~0. */
internal fun rssiToCongestionContribution(rssiDbm: Int): Int =
    (((rssiDbm + WEAKEST_RSSI_DBM) * MAX_CONGESTION) / RSSI_SPAN_DB).coerceIn(0, MAX_CONGESTION)

/**
 * Congestion a network on [channel] would suffer from [networks]. Same-channel networks count in
 * full. On 2.4 GHz, channels 5 MHz apart overlap, so a network 1-4 channels away still leaks in
 * (linearly less the further away); 5/6 GHz 20 MHz channels don't overlap, so only same-channel
 * counts there.
 */
internal fun channelCongestion(channel: Int, band: BandFilter, networks: List<ScannedNetwork>): Int {
    val total = networks.sumOf { network ->
        val distance = abs(network.channel - channel)
        val weight = when {
            distance == 0 -> 1.0
            band == BandFilter.Band24 && distance in 1..OVERLAP_MAX_DISTANCE -> (OVERLAP_MAX_DISTANCE + 1 - distance) /
                OVERLAP_FALLOFF_DIVISOR
            else -> 0.0
        }
        rssiToCongestionContribution(network.rssiDbm) * weight
    }
    return total.roundToInt().coerceAtMost(MAX_CONGESTION)
}

/**
 * The quietest channel on [band] other than the one [connected] is on, or [ChannelAdvice.AlreadyOptimal]
 * if the connected channel is already at least as quiet. Null (card hidden) when there is nothing
 * trustworthy to say: not connected, or no scan data for that band yet.
 */
fun recommendChannel(networks: List<ScannedNetwork>, band: BandFilter, connected: ConnectedNetwork?): ChannelAdvice? {
    if (connected == null) return null
    val candidates = when (band) {
        BandFilter.Band24 -> CANDIDATES_24
        BandFilter.Band5 -> CANDIDATES_5
        BandFilter.Band6 -> CANDIDATES_6
        BandFilter.All -> return null
    }
    val inBand = networks.filter { it.band == band.label }
    if (inBand.isEmpty()) return null

    val sameBand = connected.band == band.label
    // The connected AP shows up in its own scan; its signal isn't congestion on its own channel.
    // (WifiInfo.ssid comes wrapped in quotes.)
    val connectedSsid = connected.ssid.trim('"')
    val others = if (sameBand) {
        inBand.filterNot { it.ssid == connectedSsid && it.channel == connected.channel }
    } else {
        inBand
    }

    val best = candidates
        .filterNot { sameBand && it == connected.channel }
        .map { it to channelCongestion(it, band, others) }
        // Quietest first; on a tie prefer a non-DFS channel, then the lower number.
        .sortedWith(compareBy({ it.second }, { isDfsChannel(it.first) }, { it.first }))
        .firstOrNull() ?: return null

    if (sameBand && channelCongestion(connected.channel, band, others) <= best.second) {
        return ChannelAdvice.AlreadyOptimal
    }
    return ChannelAdvice.Switch(
        channel = best.first,
        band = band.label,
        congestionScore = best.second,
        isDfs = band == BandFilter.Band5 && isDfsChannel(best.first),
    )
}
