package com.newpos.wordcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.newpos.wordcard.data.AnkiCardEntity
import com.newpos.wordcard.databinding.FragmentWordCardsBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class WordCardsFragment : Fragment() {

    private var _binding: FragmentWordCardsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WordCardsViewModel by viewModels()
    private val supplementalText = MutableLiveData("")
    private var playbackSpeedDialog: AlertDialog? = null
    private var displayedCardId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWordCardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.wordBook.isEnabled = false
        binding.wordBook.setOnClickListener { showBooks() }
        binding.playbackSpeed.setOnClickListener { showPlaybackSpeed() }
        binding.previousWord.setOnClickListener { viewModel.previousWord() }
        binding.learnAgain.setOnClickListener { viewModel.learnAgain() }
        binding.markLearned.setOnClickListener { viewModel.markLearned() }
        binding.word.setOnClickListener { viewModel.playWord() }
        binding.sentences.setOnClickListener { viewModel.playSentence() }
        binding.sentencesAudio.setOnClickListener { viewModel.playSentenceByTTS() }
        binding.sentences.setOnLongClickListener {
            if (displayedCardId == viewModel.currentCard.value?.id) {
                viewModel.askLLM(binding.sentences.text.toString(), supplementalText)
            }
            true
        }
        supplementalText.observe(viewLifecycleOwner) { text ->
            binding.supplementalText.text = text
            binding.supplementalTextScroll.scrollTo(0, 0)
            binding.supplementalTextScroll.isVisible = !text.isNullOrBlank()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.currentBookId, viewModel.currentCard, ::Pair)
                    .collect { (bookId, selectedCard) ->
                        val card = selectedCard?.takeIf { it.bookId == bookId }
                        if (displayedCardId != card?.id) {
                            displayedCardId = card?.id
                            setSupplementalText(null)
                        }
                        binding.wordBook.text = bookId.orEmpty()
                        binding.wordBook.isEnabled = bookId != null
                        binding.wordCard.isVisible = card != null || viewModel.hasPreviousCard
                        setCard(card)
                    }
            }
        }
    }

    fun setSupplementalText(text: CharSequence?) {
        if (_binding == null) return
        supplementalText.value = text?.takeIf { displayedCardId != null && it.isNotBlank() }
            ?.toString().orEmpty()
    }

    private fun showBooks() {
        val bookIds = viewModel.bookIds
        val currentBookId = viewModel.currentBookId.value
        val popup = PopupMenu(requireContext(), binding.wordBook)
        bookIds.forEachIndexed { index, bookId ->
            popup.menu.add(0, index, index, bookId).isChecked = bookId == currentBookId
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            val bookId = bookIds[item.itemId]
            if (bookId != currentBookId) {
                viewModel.selectBook(bookId)
            }
            true
        }
        popup.show()
    }


    private fun showPlaybackSpeed() {
        val slider = Slider(requireContext()).apply {
            valueFrom = 0.5f
            valueTo = 2f
            stepSize = 0.1f
            value = viewModel.playbackSpeed
            labelBehavior = LabelFormatter.LABEL_GONE
            contentDescription = getString(R.string.playback_speed)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.playback_speed_value, slider.value))
            .setView(slider)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        slider.addOnChangeListener { _, speed, fromUser ->
            if (fromUser) {
                viewModel.playbackSpeed = speed
                dialog.setTitle(getString(R.string.playback_speed_value, speed))
            }
        }
        dialog.setOnDismissListener { playbackSpeedDialog = null }
        playbackSpeedDialog = dialog
        dialog.show()
    }

    private fun setCard(card: AnkiCardEntity?) {
        val sentences = card?.sentences.orEmpty().joinToString("\n")
        binding.word.text = card?.word.orEmpty()
        binding.word.isVisible = card != null
        binding.word.isEnabled = binding.word.text.isNotBlank()
        binding.partOfSpeech.text = card?.partOfSpeech.orEmpty()
        binding.partOfSpeech.isGone = binding.partOfSpeech.text.isBlank()
        binding.englishTranslation.text = card?.englishTranslation.orEmpty()
        binding.englishTranslation.isGone = binding.englishTranslation.text.isBlank()
        binding.chineseTranslation.text = card?.chineseTranslation.orEmpty()
        binding.chineseTranslation.isGone = binding.chineseTranslation.text.isBlank()
        binding.sentences.text = sentences
        binding.sentences.isGone = sentences.isBlank()
        binding.sentenceTranslations.text = card?.sentenceTranslations.orEmpty().joinToString("\n")
        binding.sentenceTranslations.isGone = binding.sentenceTranslations.text.isBlank()
    }

    override fun onStop() {
        viewModel.stopAudio()
        super.onStop()
    }

    override fun onDestroyView() {
        playbackSpeedDialog?.dismiss()
        viewModel.cancelLLMRequest()
        setSupplementalText(null)
        super.onDestroyView()
        displayedCardId = null
        _binding = null
    }
}
