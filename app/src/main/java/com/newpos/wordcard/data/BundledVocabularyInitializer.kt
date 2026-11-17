package com.newpos.wordcard.data

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** 在 Room 建库事务中导入内置词库，完成后数据库才可供查询。 */
class BundledVocabularyInitializer(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val payload = readVocabulary()
        insertVocabulary(db, payload, payload.getString("bookId"))
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        val payload = readVocabulary()
        val bookId = payload.getString("bookId")
        // 开发阶段重建或此前留下空库时，在开放查询前补齐内置词库。
        if (containsBook(db, bookId)) return
        db.beginTransaction()
        try {
            if (!containsBook(db, bookId)) insertVocabulary(db, payload, bookId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun containsBook(db: SupportSQLiteDatabase, bookId: String): Boolean = db.query(
        "SELECT 1 FROM anki_cards WHERE book_id = ? LIMIT 1", arrayOf(bookId)
    ).use { it.moveToFirst() }

    private fun readVocabulary(): JSONObject =
        context.assets.open("vocabulary/words.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }

    private fun insertVocabulary(db: SupportSQLiteDatabase, payload: JSONObject, bookId: String) {
        val words = payload.getJSONArray("words")
        require(words.length() > 0) { "内置词库为空" }
        // 使用回调提供的连接，避免在建库过程中重新打开数据库或调用 DAO。
        db.compileStatement("""
            INSERT INTO anki_cards (
                book_id, word, english_translation, chinese_translation, part_of_speech,
                word_audio, sentences_json, sentence_translations_json, sentence_audios_json, sort_order
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { statement ->
            for (index in 0 until words.length()) {
                val word = words.getJSONObject(index)
                statement.bindString(1, bookId)
                statement.bindString(2, word.getString("word"))
                statement.bindString(3, word.getString("englishTranslation"))
                statement.bindString(4, word.getString("chineseTranslation"))
                statement.bindString(5, word.getString("partOfSpeech"))
                statement.bindString(6, word.getString("wordAudio"))
                statement.bindString(7, word.getJSONArray("sentences").toString())
                statement.bindString(8, word.getJSONArray("sentenceTranslations").toString())
                statement.bindString(9, word.getJSONArray("sentenceAudios").toString())
                statement.bindLong(10, word.getInt("sortOrder").toLong())
                // 主键自增，三个复习字段使用数据库默认值。
                statement.executeInsert()
            }
        }
    }
}
