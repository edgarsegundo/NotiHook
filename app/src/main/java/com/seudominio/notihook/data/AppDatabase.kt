package com.seudominio.notihook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CapturedEvent::class, ParsedMessage::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        /**
         * Fase 1: migração destrutiva é aceitável (ver README). Qualquer mudança
         * de schema apaga os dados locais.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "notihook.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
