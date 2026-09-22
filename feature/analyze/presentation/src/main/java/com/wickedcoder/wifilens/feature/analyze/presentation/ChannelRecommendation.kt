package com.wickedcoder.wifilens.feature.analyze.presentation

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
    36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144,
    149, 153, 157, 161, 165,
)
private val CANDIDATES_6 = (1..233 step 4).toList()

internal fun isDfsChannel(channel: Int): Boolean = channel in 52..144

/** Congestion 0-100 from RSSI: -30dBm (very strong) -> ~100, -100dBm (unusable) -> ~0. */
internal fun rssiToCongestionContribution(rssiDbm: Int): Int =
    (((rssiDbm + 100) * 100) / 70).coerceIn(0, 100)

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
            band == BandFilter.Band24 && distance in 1..4 -> (5 - distance) / 5.0
            else -> 0.0
        }
        rssiToCongestionContribution(network.rssiDbm) * weight
    }
    return total.roundToInt().coerceAtMost(100)
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
