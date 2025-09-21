package com.flandolf.workout.data

/**
 * Compute total volume for a list of sets: sum(reps * weight).
 * Use Double accumulation to reduce rounding error, return Float for compatibility.
 */
fun computeVolume(sets: List<SetEntity>): Float {
    return sets.sumOf { (it.reps * it.weight).toDouble() }.toFloat()
}

