package com.tokama.linkmanager

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

class SettingsActivity : AppCompatActivity() {

    private var appliedUiStateSignature: String = ""

    private lateinit var statusBarSpacer: View
    private lateinit var toolbar: Toolbar
    private lateinit var settingsContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        AppUiSettings.applySavedNightMode(this)
        super.onCreate(savedInstanceState)

        /*
            Ab Android 15 wird Edge-to-Edge für targetSdk 35+ standardmäßig
            erzwungen. enableEdgeToEdge() aktiviert das empfohlene
            abwärtskompatible Verhalten auch auf älteren Android-Versionen,
            ohne die veraltete StatusBar-API zu verwenden.
         */
        enableEdgeToEdge()

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = !AppUiSettings.isDarkModeActive(this)

        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        statusBarSpacer = findViewById(R.id.statusBarSpacerSettings)
        toolbar = findViewById(R.id.toolbarSettings)
        settingsContainer = findViewById(R.id.settingsContainer)

        applySystemBarInsets()
        setupToolbar()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }

        appliedUiStateSignature = AppUiSettings.buildUiStateSignature(this)
    }

    override fun onResume() {
        super.onResume()

        val currentUiStateSignature = AppUiSettings.buildUiStateSignature(this)
        if (currentUiStateSignature != appliedUiStateSignature) {
            recreate()
        }
    }

    private fun applySystemBarInsets() {
        val settingsContainerBasePaddingLeft = settingsContainer.paddingLeft
        val settingsContainerBasePaddingTop = settingsContainer.paddingTop
        val settingsContainerBasePaddingRight = settingsContainer.paddingRight
        val settingsContainerBasePaddingBottom = settingsContainer.paddingBottom

        val toolbarBasePaddingLeft = toolbar.paddingLeft
        val toolbarBasePaddingTop = toolbar.paddingTop
        val toolbarBasePaddingRight = toolbar.paddingRight
        val toolbarBasePaddingBottom = toolbar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpacer) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val layoutParams = view.layoutParams

            if (layoutParams.height != statusBarInsets.top) {
                layoutParams.height = statusBarInsets.top
                view.layoutParams = layoutParams
            }

            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                toolbarBasePaddingLeft + systemBarInsets.left,
                toolbarBasePaddingTop,
                toolbarBasePaddingRight + systemBarInsets.right,
                toolbarBasePaddingBottom
            )
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(settingsContainer) { view, windowInsets ->
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = settingsContainerBasePaddingLeft + systemBarInsets.left,
                top = settingsContainerBasePaddingTop,
                right = settingsContainerBasePaddingRight + systemBarInsets.right,
                bottom = settingsContainerBasePaddingBottom + systemBarInsets.bottom
            )
            windowInsets
        }

        ViewCompat.requestApplyInsets(findViewById(android.R.id.content))
    }

    private fun setupToolbar() {
        toolbar.title = getString(R.string.settings)
        toolbar.subtitle = null

        val backIcon = ContextCompat.getDrawable(
            this,
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )?.mutate()

        if (backIcon != null) {
            val wrappedBackIcon = DrawableCompat.wrap(backIcon)
            DrawableCompat.setTint(
                wrappedBackIcon,
                ContextCompat.getColor(this, android.R.color.white)
            )
            toolbar.navigationIcon = wrappedBackIcon
        } else {
            toolbar.navigationIcon = null
        }

        toolbar.navigationContentDescription =
            getString(androidx.appcompat.R.string.abc_action_bar_up_description)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
