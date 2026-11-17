package com.newpos.wordcard

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.newpos.wordcard.data.AnkiCardCandidate
import com.newpos.wordcard.data.AnkiCardEntity
import com.newpos.wordcard.data.AnkiDatabase
import com.newpos.wordcard.llm.LLMChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt
import kotlin.random.Random

class WordCardsViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val CURRENT_BOOK_ID = "currentBookId"
        const val PLAYBACK_SPEED = "playbackSpeed"
        const val SENTENCE_PROMPT = """
            将下面的俄语例句翻译成中文，然后用中文讲解其中的语法知识点。
            翻译时按语义块逐块翻译,相邻语义块之间用一个空格隔开。
            保持输出简短,语法解释不用太细
            示例:
            输入:Foi para casa e não pensou mais no achado.
            回复:
            他 回到了 家，并且 不再 想起 那个发现的东西。
            
            语法解释:
            1...
            2...
            
            输出纯文本,不要用Markdown语法,直接开始回复

            %s
        """
    }

    private val database = AnkiDatabase.open(application)
    private val preferences = application.getSharedPreferences("word_cards", Context.MODE_PRIVATE)
    private var mediaPlayer: MediaPlayer? = null
    private val pendingAudios = ArrayDeque<String>()

    var playbackSpeed: Float
        get() = preferences.getFloat(PLAYBACK_SPEED, 1f)
        set(value) {
            preferences.edit().putFloat(PLAYBACK_SPEED, value).apply()
        }
    private val queue = ArrayDeque<AnkiCardEntity>()
    private var previousCard: AnkiCardEntity? = null
    val hasPreviousCard get() = previousCard != null
    private val unfamiliarCounts = mutableMapOf<Long, Int>()
    private var unfamiliarDate = LocalDate.now()
    private var nextIsNew = true
    var bookIds: List<String> = emptyList()
        private set
    private val _currentBookId = MutableStateFlow<String?>(null)
    val currentBookId = _currentBookId.asStateFlow()
    private var refillJob: Job? = null
    private var reviewJob: Job? = null
    private var llmJob: Job? = null
    private val _currentCard = MutableStateFlow<AnkiCardEntity?>(null)
    val currentCard = _currentCard.asStateFlow()

    init {
        viewModelScope.launch {
            bookIds = withContext(Dispatchers.IO) { database.cards().getBooks().map { it.bookId } }
            val savedBookId = preferences.getString(CURRENT_BOOK_ID, null)
            val bookId = savedBookId?.takeIf { it in bookIds } ?: bookIds.firstOrNull()
            bookId?.let(::selectBook)
        }
    }

    fun askLLM(sentence: String, result: MutableLiveData<String>) {
        if (sentence.isBlank() || _currentCard.value == null) return
        cancelLLMRequest()
        llmJob = viewModelScope.launch {
            val application = getApplication<Application>()
            val text = try {
                LLMChat.chat(application, SENTENCE_PROMPT.trimIndent().format(sentence))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                exception.localizedMessage ?: application.getString(R.string.sentence_explanation_failed)
            }
            ensureActive()
            result.value = text
        }
    }

    fun cancelLLMRequest() {
        llmJob?.cancel()
        llmJob = null
    }

    fun playWord() {
        playAudio(listOfNotNull(_currentCard.value?.wordAudio))
    }

    fun playSentence() {
        playAudio(_currentCard.value?.sentenceAudios.orEmpty())
    }

    private fun playAudio(paths: List<String>) {
        stopAudio()
        pendingAudios.addAll(paths.filter { it.isNotBlank() })
        playNextAudio()
    }

    private fun playNextAudio() {
        mediaPlayer?.release()
        mediaPlayer = null
        val path = pendingAudios.removeFirstOrNull() ?: return
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnPreparedListener {
            if (mediaPlayer === it) {
                it.playbackParams = it.playbackParams.setSpeed(playbackSpeed)
                it.start()
            }
        }
        player.setOnCompletionListener { if (mediaPlayer === it) playNextAudio() }
        player.setOnErrorListener { failedPlayer, what, extra ->
            if (mediaPlayer === failedPlayer) {
                Log.e("WordCardsViewModel", "音频播放失败: $path ($what, $extra)")
                stopAudio()
            }
            true
        }
        try {
            getApplication<Application>().assets.openFd(path).use { descriptor ->
                player.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }
            player.prepareAsync()
        } catch (error: Exception) {
            Log.e("WordCardsViewModel", "无法播放音频: $path", error)
            stopAudio()
        }
    }

    fun stopAudio() {
        pendingAudios.clear()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setCurrentCard(card: AnkiCardEntity?) {
        if (_currentCard.value?.id != card?.id) {
            cancelLLMRequest()
            stopAudio()
        }
        _currentCard.value = card
    }

    fun selectBook(bookId: String) {
        if (bookId == _currentBookId.value || bookId !in bookIds) return
        refillJob?.cancel()
        queue.clear()
        previousCard = null
        nextIsNew = true
        setCurrentCard(null)
        _currentBookId.value = bookId
        preferences.edit().putString(CURRENT_BOOK_ID, bookId).apply()
        refill(limit = 20)
    }

    /** 两类候选词由调用方各自排序；这里只交替追加，跨批次保留下一次的来源。 */
    private fun enqueue(newCards: List<AnkiCardEntity>, reviewCards: List<AnkiCardEntity>, limit: Int) {
        var added = 0
        while (queue.size < 20 && added < limit) {
            val newCard = newCards.firstOrNull { candidate -> queue.none { it.id == candidate.id } }
            val reviewCard = reviewCards.firstOrNull { candidate -> queue.none { it.id == candidate.id } }
            val card = if (nextIsNew) newCard ?: reviewCard else reviewCard ?: newCard
            if (card == null) break
            queue.addLast(card)
            added++
            nextIsNew = card.reviewCount != 0
        }
        setCurrentCard(queue.firstOrNull())
    }

    private fun refill(limit: Int = 5) {
        val bookId = _currentBookId.value ?: return
        if (refillJob?.isActive == true || queue.size >= 15) return
        refillJob = viewModelScope.launch {
            reviewJob?.join()
            var batchSize = limit
            while (queue.size < 15) {
                val excludedIds = queue.map { it.id }
                val (newCards, reviewCards) = withContext(Dispatchers.IO) {
                    selectCandidates(bookId, excludedIds, batchSize)
                }
                if (newCards.isEmpty() && reviewCards.isEmpty()) break
                enqueue(newCards, reviewCards, batchSize)
                batchSize = 5
            }
        }
    }

    private fun selectCandidates(
        bookId: String,
        excludedIds: List<Long>,
        limit: Int
    ): Pair<List<AnkiCardEntity>, List<AnkiCardEntity>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStartedAt = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val candidates = database.cards().getCandidates(bookId, excludedIds, dayStartedAt)
        val newCandidates = candidates.filter { it.reviewCount == 0 }
            .sortedWith(compareBy<AnkiCardCandidate> { it.word.count { char -> char.isLetter() } }
                .thenBy { it.sortOrder }.thenBy { it.id })
            .take(limit)
        val reviewCandidates = candidates.filter { it.reviewCount > 0 }
            .map { it to it.priorityScore(today, zone) }
            .filter { it.second >= 1.0 }
            .sortedWith(compareByDescending<Pair<AnkiCardCandidate, Double>> { it.second }
                .thenBy { it.first.sortOrder }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
        val ids = (newCandidates + reviewCandidates).map { it.id }
        if (ids.isEmpty()) return emptyList<AnkiCardEntity>() to emptyList()
        val cards = database.cards().getCardsByIds(ids).associateBy { it.id }
        return newCandidates.mapNotNull { cards[it.id] } to reviewCandidates.mapNotNull { cards[it.id] }
    }

    private fun AnkiCardCandidate.priorityScore(today: LocalDate, zone: ZoneId): Double {
        val reviewedAt = lastReviewedAt ?: return 0.0
        val reviewedDate = Instant.ofEpochMilli(reviewedAt).atZone(zone).toLocalDate()
        val elapsedDays = ChronoUnit.DAYS.between(reviewedDate, today)
        val expectedGapDays = maxOf(1.0, sqrt(reviewCount.toDouble()) * lastProficiency)
        return elapsedDays / expectedGapDays
    }

    fun previousWord() {
        if (reviewJob?.isActive == true) return
        val card = previousCard ?: return
        previousCard = null
        // LearnAgain 的词仍在队列中，先移除原位置，避免同一词出现两次。
        queue.removeAll { it.id == card.id }
        queue.addFirst(card)
        setCurrentCard(card)
    }

    fun markLearned() {
        if (reviewJob?.isActive == true) return
        val card = queue.firstOrNull() ?: return
        resetUnfamiliarCountsIfNeeded()
        val reviewedAt = System.currentTimeMillis()
        val proficiency = 1.0 / (1 + (unfamiliarCounts[card.id] ?: 0))
        reviewJob = viewModelScope.launch {
            val reviewedCard = withContext(Dispatchers.IO) {
                database.cards().recordReview(card.id, reviewedAt, proficiency)
                database.cards().getCard(card.id)
            }
            if (queue.firstOrNull() !== card) return@launch
            queue.removeFirst()
            previousCard = reviewedCard ?: card
            setCurrentCard(queue.firstOrNull())
            refill()
        }
    }

    fun learnAgain() {
        if (reviewJob?.isActive == true) return
        val card = queue.removeFirstOrNull() ?: return
        previousCard = card
        resetUnfamiliarCountsIfNeeded()
        unfamiliarCounts[card.id] = (unfamiliarCounts[card.id] ?: 0) + 1
        // 下标 4 对应第 5 位；单词不足时直接放到队尾。
        val index = if (queue.size >= 4) Random.nextInt(4, queue.size + 1) else queue.size
        queue.add(index, card)
        setCurrentCard(queue.firstOrNull())
    }

    private fun resetUnfamiliarCountsIfNeeded() {
        val today = LocalDate.now()
        if (unfamiliarDate != today) {
            unfamiliarCounts.clear()
            unfamiliarDate = today
        }
    }

    override fun onCleared() {
        stopAudio()
        database.close()
        super.onCleared()
    }
}
