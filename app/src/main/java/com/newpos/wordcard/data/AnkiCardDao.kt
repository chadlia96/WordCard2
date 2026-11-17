package com.newpos.wordcard.data

import androidx.room.Dao
import androidx.room.Query
import java.time.Instant
import java.time.ZoneId

@Dao
abstract class AnkiCardDao {
    @Query("SELECT book_id, COUNT(*) AS word_count FROM anki_cards GROUP BY book_id ORDER BY book_id")
    abstract fun getBooks(): List<AnkiBookSummary>

    @Query("SELECT * FROM anki_cards WHERE book_id = :bookId ORDER BY sort_order, id")
    abstract fun getCards(bookId: String): List<AnkiCardEntity>

    @Query("SELECT COUNT(*) FROM anki_cards WHERE book_id = :bookId")
    abstract fun countCards(bookId: String): Int

    @Query("SELECT * FROM anki_cards WHERE id = :id")
    abstract fun getCard(id: Long): AnkiCardEntity?

    @Query("SELECT * FROM anki_cards ORDER BY id LIMIT 1")
    abstract fun getFirstCard(): AnkiCardEntity?

    /** 先排除队列中的词，只读取计算词长和复习分数所需的字段。 */
    @Query("""
        SELECT id, word, sort_order, review_count, last_reviewed_at, last_proficiency
        FROM anki_cards
        WHERE book_id = :bookId AND id NOT IN (:excludedIds)
            AND (review_count = 0 OR (review_count > 0 AND last_reviewed_at < :dayStartedAt))
    """)
    abstract fun getCandidates(bookId: String, excludedIds: List<Long>, dayStartedAt: Long): List<AnkiCardCandidate>

    @Query("SELECT * FROM anki_cards WHERE id IN (:ids)")
    abstract fun getCardsByIds(ids: List<Long>): List<AnkiCardEntity>

    @Query("""
        UPDATE anki_cards
        SET last_reviewed_at = :reviewedAt,
            review_count = review_count + CASE WHEN last_reviewed_at >= :dayStartedAt THEN 0 ELSE 1 END,
            last_proficiency = :proficiency
        WHERE id = :id
    """)
    protected abstract fun updateLearningState(id: Long, reviewedAt: Long, proficiency: Double, dayStartedAt: Long): Int

    /** 认识时提交学习结果；按设备本地日期，同一天只增加一次有效复习次数。 */
    fun recordReview(id: Long, reviewedAt: Long, proficiency: Double): Int {
        require(reviewedAt >= 0 && proficiency in 0.0..1.0)
        val zone = ZoneId.systemDefault()
        val dayStartedAt = Instant.ofEpochMilli(reviewedAt).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return updateLearningState(id, reviewedAt, proficiency, dayStartedAt)
    }
}
