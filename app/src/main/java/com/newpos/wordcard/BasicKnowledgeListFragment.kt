package com.newpos.wordcard

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import java.io.IOException

class BasicKnowledgeListFragment : Fragment(R.layout.fragment_basic_knowledge_list) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val notes = BasicKnowledgeNotes(requireContext())
        val files = try {
            notes.files()
        } catch (e: IOException) {
            Toast.makeText(requireContext(), R.string.markdown_load_failed, Toast.LENGTH_SHORT).show()
            emptyList()
        }
        val list = view as ListView
        list.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, files.map(notes::preview))
        list.setOnItemClickListener { _, _, position, _ ->
            findNavController().navigate(
                R.id.nav_basic_knowledge_detail,
                bundleOf(BasicKnowledgeFragment.ARG_FILE_NAME to files[position].name)
            )
        }

        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.basic_knowledge, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.edit_basic_knowledge) return false
                findNavController().navigate(R.id.nav_basic_knowledge_detail)
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}
