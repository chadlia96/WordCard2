package com.newpos.wordcard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一条单词记录，包含词汇内容及本应用的复习进度。 */
@Entity(
    tableName = "anki_cards",
    indices = [
        Index(value = ["book_id", "sort_order"])
    ]
)
data class AnkiCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: String,
    val word: String,
    @ColumnInfo(name = "english_translation") val englishTranslation: String,
    @ColumnInfo(name = "chinese_translation") val chineseTranslation: String = "",
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "word_audio") val wordAudio: String,
    @ColumnInfo(name = "sentences_json") val sentences: List<String>,
    @ColumnInfo(name = "sentence_translations_json") val sentenceTranslations: List<String>,
    @ColumnInfo(name = "sentence_audios_json") val sentenceAudios: List<String>,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** 上次有效复习的 Unix 毫秒时间戳；新词为 null，单纯翻卡不更新。 */
    @ColumnInfo(name = "last_reviewed_at", defaultValue = "NULL") val lastReviewedAt: Long? = null,
    /** 有效复习次数；新词为 0，同一天多次认识只计一次。 */
    @ColumnInfo(name = "review_count", defaultValue = "0") val reviewCount: Int = 0,
    /** 上次有效复习的熟练度，范围 0～1；初始为 0，新词通过 reviewCount == 0 判断。 */
    @ColumnInfo(name = "last_proficiency", defaultValue = "0") val lastProficiency: Double = 0.0
)

/** 查询投影，不是额外的表。单词本列表和数量直接从单词表汇总。 */
data class AnkiBookSummary(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "word_count") val wordCount: Int
)

/** 选词用的查询投影，避免每次补词都读取例句和音频等完整内容。 */
data class AnkiCardCandidate(
    val id: Long,
    val word: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "last_proficiency") val lastProficiency: Double
)
