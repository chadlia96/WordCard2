package com.newpos.wordcard

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.newpos.wordcard.databinding.ActivityMainBinding
import com.newpos.wordcard.llm.LLMChat
import com.newpos.wordcard.utils.TtsUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 配置顶级导航页面（顶级页面左上角展示抽屉汉堡图标）
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_alphabet, R.id.nav_word_cards, R.id.nav_basic_knowledge),
            binding.drawerLayout
        )

        // 联动 Toolbar 与 NavController（自动处理汉堡菜单/返回图标及标题）
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 联动 NavigationView 与 NavController（自动处理点击导航与抽屉关闭）
        binding.navigationView.setupWithNavController(navController)
        binding.navigationView.menu.findItem(R.id.deepseek_api_key).setOnMenuItemClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            showApiKeyDialog()
            true
        }

        // 抽屉打开时，按下返回键优先关闭抽屉
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showApiKeyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_deepseek_api_key, null)
        val input = view.findViewById<TextInputEditText>(R.id.api_key_input)
        input.setText(LLMChat.getApiKey(this))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.deepseek_api_key)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_api_key) { _, _ ->
                LLMChat.saveApiKey(this, input.text.toString())
            }
            .show()
    }

    override fun onDestroy() {
        TtsUtils.shutdown()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
