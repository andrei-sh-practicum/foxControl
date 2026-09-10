package com.andrew.foxcontrol.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 2 to version 3.
 * Adds email reporting tables: EmailRecipient, EmailSettings, ReportSendLog.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS email_recipient (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1
            )
        """)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS email_settings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                smtp_host TEXT NOT NULL DEFAULT 'smtp.gmail.com',
                smtp_port INTEGER NOT NULL DEFAULT 587,
                login TEXT NOT NULL DEFAULT '',
                app_password TEXT NOT NULL DEFAULT '',
                from_address TEXT NOT NULL DEFAULT '',
                send_time INTEGER NOT NULL DEFAULT 1200
            )
        """)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS report_send_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                status TEXT NOT NULL,
                error TEXT,
                sent_at INTEGER
            )
        """)
    }
}
