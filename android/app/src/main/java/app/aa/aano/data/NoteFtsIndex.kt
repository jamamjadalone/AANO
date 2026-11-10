// SQLite Full-Text Search (FTS4/unicode61) index for millisecond note retrieval.
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class NoteFtsIndex(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "notes_fts.db"
        private const val DB_VERSION = 1
        private const val TABLE_FTS = "notes_fts"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts4(
                id TEXT,
                title TEXT,
                content TEXT,
                folder TEXT,
                tags TEXT,
                tokenize=unicode61
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS")
        onCreate(db)
    }

    fun syncNote(note: Note) {
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete(TABLE_FTS, "id = ?", arrayOf(note.id))
                if (!note.deleted && !note.archived && !note.purged) {
                    val values = ContentValues().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("folder", note.folder)
                        put("tags", note.tags.joinToString(" "))
                    }
                    db.insert(TABLE_FTS, null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w("AANO", "FTS syncNote error: ${e.message}")
        }
    }

    fun deleteNote(id: String) {
        try {
            writableDatabase.delete(TABLE_FTS, "id = ?", arrayOf(id))
        } catch (e: Exception) {
            Log.w("AANO", "FTS deleteNote error: ${e.message}")
        }
    }

    fun syncAll(notes: List<Note>) {
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete(TABLE_FTS, null, null)
                val stmt = db.compileStatement(
                    "INSERT INTO $TABLE_FTS(id, title, content, folder, tags) VALUES (?, ?, ?, ?, ?)"
                )
                for (note in notes) {
                    if (!note.deleted && !note.archived && !note.purged) {
                        stmt.bindString(1, note.id)
                        stmt.bindString(2, note.title)
                        stmt.bindString(3, note.content)
                        stmt.bindString(4, note.folder)
                        stmt.bindString(5, note.tags.joinToString(" "))
                        stmt.executeInsert()
                        stmt.clearBindings()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w("AANO", "FTS syncAll error: ${e.message}")
        }
    }

    fun search(query: String): List<String> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        val tokens = clean.split(Regex("\\s+"))
            .map { it.replace("\"", "").replace("'", "").replace("*", "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val ftsQuery = tokens.joinToString(" ") { "$it*" }
        val results = mutableListOf<String>()

        try {
            val db = readableDatabase
            val cursor = db.rawQuery(
                "SELECT id FROM $TABLE_FTS WHERE $TABLE_FTS MATCH ?",
                arrayOf(ftsQuery)
            )
            cursor.use {
                val idCol = it.getColumnIndex("id")
                while (it.moveToNext()) {
                    if (idCol >= 0) {
                        results.add(it.getString(idCol))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AANO", "FTS query error: ${e.message}")
        }
        return results
    }
}
