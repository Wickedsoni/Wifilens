package com.wickedcoder.wifilens.core.database

import androidx.room.withTransaction

/**
 * Runs several DAO calls as one atomic transaction. Room only notifies its observing Flows after the
 * transaction commits, so a multi-table write (plan + cells + rooms) produces a single consistent
 * re-emission instead of one half-written snapshot per table. Wrapped here so feature modules don't
 * need Room's types on their own classpath.
 */
class TransactionRunner(private val database: WifiLensDatabase) {
    suspend fun <R> run(block: suspend () -> R): R = database.withTransaction(block)
}
