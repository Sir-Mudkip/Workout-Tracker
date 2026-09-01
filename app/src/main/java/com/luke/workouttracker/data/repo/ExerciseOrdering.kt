package com.luke.workouttracker.data.repo

/**
 * Pure ordering arithmetic for the exercises within a day, kept separate from
 * [ProgramRepository] so it can be unit-tested without a database.
 *
 * Positions are list indices into a day's exercises sorted by `orderInDay`;
 * order values are the stored `orderInDay` column.
 */
object ExerciseOrdering {

    /**
     * The position [index] should swap with to move by [direction]
     * (-1 up, +1 down), or null when the move would run off either end
     * or [index] is not a real position.
     */
    fun swapTarget(size: Int, index: Int, direction: Int): Int? {
        if (index !in 0 until size) return null
        val target = index + direction
        return if (target in 0 until size) target else null
    }

    /**
     * Maps position -> new order value for the exercises whose order actually
     * changes when [currentOrders] (in list order) is re-packed to 0..N-1.
     * Deleting an exercise leaves a gap; re-packing closes it.
     */
    fun repackTargets(currentOrders: List<Int>): Map<Int, Int> =
        currentOrders.withIndex()
            .filter { (position, order) -> order != position }
            .associate { (position, _) -> position to position }

    /**
     * The order value for an exercise appended to a day that currently holds
     * [currentOrders]. One past the highest existing value rather than the
     * list size, so a gap left by a deletion can't produce a duplicate.
     */
    fun nextOrderInDay(currentOrders: List<Int>): Int =
        (currentOrders.maxOrNull()?.plus(1)) ?: 0
}
