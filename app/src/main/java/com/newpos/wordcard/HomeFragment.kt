package com.newpos.wordcard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.newpos.wordcard.databinding.FragmentHomeBinding
import com.newpos.wordcard.databinding.ItemAlphabetLetterBinding
import com.newpos.wordcard.databinding.ItemLetterExampleBinding
import com.newpos.wordcard.utils.TtsUtils

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var detailBehavior: BottomSheetBehavior<View>

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hideDetail()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        detailBehavior = BottomSheetBehavior.from(binding.wordDetail.root)
        binding.wordDetail.mask.setOnClickListener { hideDetail() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        initLettersGrid()
        TtsUtils.init(requireContext())
    }

    private fun initLettersGrid() {
        binding.letterGrid.removeAllViews()
        letterWords.keys.forEach { letter ->
            val itemBinding = ItemAlphabetLetterBinding.inflate(
                layoutInflater, binding.letterGrid, false
            )
            itemBinding.uppercaseLetter.text = letter.toString()
            itemBinding.lowercaseLetter.text = letter.lowercaseChar().toString()
            itemBinding.root.setOnClickListener { showDetail(letter) }
            binding.letterGrid.addView(itemBinding.root)
        }
    }

    private fun showDetail(letter: Char) {
        val entry = letterWords.getValue(letter)
        with(binding.wordDetail) {
            this.letter.text = "$letter ${letter.lowercaseChar()}"
            letterSound.setOnClickListener { TtsUtils.speak(requireContext(), entry.name) }
            examples.removeAllViews()
            entry.examples.forEach { example ->
                val row = ItemLetterExampleBinding.inflate(layoutInflater, examples, false)
                row.word.text = "${example.word}（${example.meaning}）\n${example.sound}"
                row.wordSound.setOnClickListener { TtsUtils.speak(requireContext(), example.word) }
                examples.addView(row.root)
            }
            detailScroll.scrollTo(0, 0)
        }
        detailBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        backCallback.isEnabled = true
    }

    private fun hideDetail() {
        TtsUtils.stop()
        detailBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        backCallback.isEnabled = false
    }

    override fun onDestroyView() {
        TtsUtils.stop()
        super.onDestroyView()
        _binding = null
    }
}
