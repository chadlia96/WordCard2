package com.newpos.wordcard

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.newpos.wordcard.databinding.FragmentBasicKnowledgeBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.io.IOException

class BasicKnowledgeFragment : Fragment(R.layout.fragment_basic_knowledge) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentBasicKnowledgeBinding.bind(view)
        val notes = BasicKnowledgeNotes(requireContext())
        val fileName = arguments?.getString(ARG_FILE_NAME)
        var markdown = try {
            fileName?.let(notes::read).orEmpty()
        } catch (e: IOException) {
            Toast.makeText(requireContext(), R.string.markdown_load_failed, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }
        val markwon = Markwon.builder(requireContext())
            .usePlugin(TablePlugin.create(requireContext()))
            .build()

        fun openEditor() {
            binding.markdownEditor.setText(markdown)
            binding.markdownScroll.visibility = View.GONE
            binding.editorLayout.visibility = View.VISIBLE
            binding.markdownEditor.requestFocus()
        }

        if (fileName == null) {
            openEditor()
        } else {
            markwon.setMarkdown(binding.markdownText, markdown)
            (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.basic_knowledge, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.itemId != R.id.edit_basic_knowledge) return false
                    if (binding.editorLayout.visibility != View.VISIBLE) openEditor()
                    return true
                }
            }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }

        binding.confirmEdit.setOnClickListener {
            val editedMarkdown = binding.markdownEditor.text.toString()
            try {
                notes.save(fileName, editedMarkdown)
            } catch (e: IOException) {
                Toast.makeText(requireContext(), R.string.markdown_save_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ViewCompat.getWindowInsetsController(binding.markdownEditor)
                ?.hide(WindowInsetsCompat.Type.ime())
            if (fileName == null) {
                findNavController().popBackStack()
                return@setOnClickListener
            }

            markdown = editedMarkdown
            markwon.setMarkdown(binding.markdownText, markdown)
            binding.markdownEditor.clearFocus()
            binding.editorLayout.visibility = View.GONE
            binding.markdownScroll.visibility = View.VISIBLE
        }
    }

    companion object {
        const val ARG_FILE_NAME = "fileName"
    }
}
