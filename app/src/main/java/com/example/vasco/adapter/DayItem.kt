package com.example.vasco.adapter

import java.util.Date

data class DayItem(
    val dayLetter: String,
    val dayNumber: String,
    val fullDate: Date,
    var state: DayState = DayState.NORMAL
) 