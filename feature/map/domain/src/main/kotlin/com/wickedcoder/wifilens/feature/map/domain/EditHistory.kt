package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.GridPlan

/** Snapshots kept per direction; bounded so a long paint session on a large grid stays cheap. */
const val MAX_UNDO_DEPTH = 20

/** Bounded undo/redo of whole-grid snapshots (cell painting only). */
class EditHistory {
    private val undoStack = ArrayDeque<GridPlan>()
    private val redoStack = ArrayDeque<GridPlan>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Remembers [before] as the state to return to; a fresh edit invalidates anything redoable. */
    fun record(before: GridPlan) {
        undoStack.addLast(before)
        if (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeFirst()
        redoStack.clear()
    }

    /** The previous plan (pushing [current] onto redo), or null when there is nothing to undo. */
    fun undo(current: GridPlan): GridPlan? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    /** The next plan (pushing [current] onto undo), or null when there is nothing to redo. */
    fun redo(current: GridPlan): GridPlan? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
