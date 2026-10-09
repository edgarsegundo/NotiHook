package com.seudominio.notihook.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BR = Locale("pt", "BR")
private val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", BR)
private val timeOnly = SimpleDateFormat("HH:mm:ss", BR)

fun formatDateTime(millis: Long): String =
    if (millis <= 0L) "—" else dateTime.format(Date(millis))

fun formatTime(millis: Long): String =
    if (millis <= 0L) "—" else timeOnly.format(Date(millis))
