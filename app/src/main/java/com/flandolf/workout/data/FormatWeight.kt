package com.flandolf.workout.data

import android.annotation.SuppressLint

@SuppressLint("DefaultLocale")
fun formatWeight(weight: Float, round: Boolean = false): String {
    return if (weight % 1.0 == 0.0) {
        weight.toInt().toString()
    } else {
        if (round) String.format("%.1f", weight) else weight.toString()
    }
}