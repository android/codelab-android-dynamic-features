/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.samples.dynamicapps

import android.content.Intent
import android.os.Bundle
import android.support.constraint.Group
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus


private const val packageName = "com.google.android.samples.dynamicapps.ondemand"

private const val kotlinSampleClassname = "$packageName.KotlinSampleActivity"

private const val javaSampleClassname = "$packageName.JavaSampleActivity"

private const val nativeSampleClassname = "$packageName.NativeSampleActivity"

/** Activity that displays buttons and handles loading of feature modules. */
class MainActivity : AppCompatActivity() {

    /** Listener used to handle changes in state for install requests. */
    private val listener = SplitInstallStateUpdatedListener { state ->
        val multiInstall = state.moduleNames().size > 1
        state.moduleNames().forEach { name ->
            // Handle changes in state.
            when (state.status()) {
                SplitInstallSessionStatus.DOWNLOADING -> {
                    displayLoadingState(state, "Downloading $name")
                }
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    startIntentSender(state.resolutionIntent().intentSender, null, 0, 0, 0)
                }
                SplitInstallSessionStatus.DOWNLOADED or SplitInstallSessionStatus.INSTALLED -> {
                    onSuccessfulLoad(name, launch = !multiInstall)
                }
            }
        }
    }

    private val clickListener = View.OnClickListener {
        when (it.id) {
            R.id.btn_load_kotlin -> loadAndLaunchModule(getString(R.string.module_feature_kotlin))
            R.id.btn_load_java -> loadAndLaunchModule(getString(R.string.module_feature_java))
            R.id.btn_load_assets -> loadAndLaunchModule(getString(R.string.module_assets))
            R.id.btn_load_native -> loadAndLaunchModule(getString(R.string.module_native))
            R.id.btn_load_all -> loadAllFeatures()
        }
    }

    private lateinit var manager: SplitInstallManager

    private lateinit var progress: Group
    private lateinit var buttons: Group
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = SplitInstallManagerFactory.create(this)
        initializeViews()
    }

    /**
     * Load a feature by module name.
     * @param name The name of the feature module to load.
     */
    private fun loadAndLaunchModule(name: String) {
        updateProgressMessage("Loading module $name")
        manager.registerListener(listener)
        // Skip loading if the module already is installed. Perform success action directly.
        if (manager.installedModules.contains(name)) {
            updateProgressMessage("Already installed")
            onSuccessfulLoad(name, launch = true)
            return
        }

        // Create request to install a feature module by name.
        val request = SplitInstallRequest.newBuilder()
                .addModule(name)
                .build()

        // Load and install the requested feature module.
        manager.startInstall(request)

        updateProgressMessage("Starting install for $name")
    }

    /** Display assets loaded from the assets feature module. */
    private fun displayAssets() {
        // Get the asset manager with a refreshed context, to access content of newly installed apk.
        val assetManager = createPackageContext(packageName, 0).assets
        // Now treat it like any other asset file.
        val assets = assetManager.open("assets.txt")
        val assetContent = assets.bufferedReader()
                .use {
                    it.readText()
                }

        AlertDialog.Builder(this)
                .setTitle("Asset content")
                .setMessage(assetContent)
                .show()
    }

    /** Load all features but do not launch any of them. */
    private fun loadAllFeatures() {
        // Request all known modules to be downloaded in a single session.
        val request = SplitInstallRequest.newBuilder()
                .addModule(getString(R.string.module_feature_kotlin))
                .addModule(getString(R.string.module_feature_java))
                .addModule(getString(R.string.module_native))
                .addModule(getString(R.string.module_assets))
                .build()

        manager.registerListener {
            Toast.makeText(this, "Loading ${it.moduleNames()}", Toast.LENGTH_LONG).show()
        }

        // Start the install with above request.
        manager.startInstall(request)
    }

    /**
     * Define what to do once a feature module is loaded successfully.
     * @param moduleName The name of the successfully loaded module.
     * @param launch `true` if the feature module should be launched, else `false`.
     */
    private fun onSuccessfulLoad(moduleName: String, launch: Boolean) {
        if (launch) {
            when (moduleName) {
                getString(R.string.module_feature_kotlin) -> launchActivity(kotlinSampleClassname)
                getString(R.string.module_feature_java) -> launchActivity(javaSampleClassname)
                getString(R.string.module_native) -> launchActivity(nativeSampleClassname)
                getString(R.string.module_assets) -> displayAssets()
            }
        }
        // Make sure to dispose of the listener once it's no longer needed.
        manager.unregisterListener(listener)
        displayButtons()
    }

    /** Launch an activity by its class name. */
    private fun launchActivity(className: String) {
        Intent().setClassName(packageName, className)
                .also {
                    startActivity(it)
                }
    }

    /** Display a loading state to the user. */
    private fun displayLoadingState(state: SplitInstallSessionState, message: String) {
        displayProgress()

        progressBar.max = state.totalBytesToDownload().toInt()
        progressBar.progress = state.bytesDownloaded().toInt()

        updateProgressMessage(message)
    }

    /** Set up all view variables. */
    private fun initializeViews() {
        buttons = findViewById(R.id.buttons)
        progress = findViewById(R.id.progress)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        setupClickListener()
    }

    /** Set all click listeners required for the buttons on the UI. */
    private fun setupClickListener() {

        setClickListener(R.id.btn_load_kotlin, clickListener)
        setClickListener(R.id.btn_load_java, clickListener)
        setClickListener(R.id.btn_load_assets, clickListener)
        setClickListener(R.id.btn_load_native, clickListener)
        setClickListener(R.id.btn_load_all, clickListener)
    }

    private fun setClickListener(id: Int, listener: View.OnClickListener) {
        findViewById<View>(id).setOnClickListener(listener)
    }

    private fun updateProgressMessage(message: String) {
        if (progress.visibility != View.VISIBLE) displayProgress()
        progressText.text = message
    }

    /** Display progress bar and text. */
    private fun displayProgress() {
        progress.visibility = View.VISIBLE
        buttons.visibility = View.GONE
    }

    /** Display buttons to accept user input. */
    private fun displayButtons() {
        progress.visibility = View.GONE
        buttons.visibility = View.VISIBLE
    }
}