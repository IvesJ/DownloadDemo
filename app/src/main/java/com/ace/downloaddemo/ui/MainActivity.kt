package com.ace.downloaddemo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace.downloaddemo.R
import com.ace.downloaddemo.databinding.ActivityMainBinding
import com.ace.downloaddemo.domain.model.VehicleDownloadState
import com.ace.downloaddemo.service.AutoDownloadService
import com.ace.downloaddemo.ui.adapter.FeatureListAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DownloadViewModel by viewModels()
    private lateinit var adapter: FeatureListAdapter
    private var vehicleAdapter: ArrayAdapter<String>? = null

    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "存储权限被拒绝，可能影响下载功能", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVehicleSelector()
        setupRecyclerView()
        observeViewModel()
        checkAndRequestPermissions()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "下载管理"
        setSupportActionBar(binding.toolbar)
    }

    /**
     * 设置车辆选择器
     */
    private fun setupVehicleSelector() {
        val vehicleSpinner = binding.spinnerVehicle

        // 初始化 ArrayAdapter
        vehicleAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        vehicleSpinner.adapter = vehicleAdapter

        // 车型选择监听
        vehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.onVehicleSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_updates -> {
                // 检查更新并清理不再需要的文件
                Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()
                viewModel.checkForUpdates()
                true
            }
            R.id.action_cleanup_files -> {
                // 仅清理不再需要的文件
                Toast.makeText(this, "正在清理存储...", Toast.LENGTH_SHORT).show()
                viewModel.cleanupUnusedFiles()
                true
            }
            R.id.action_auto_download -> {
                // 启动自动下载服务
                AutoDownloadService.start(this)
                Toast.makeText(this, "已启动自动下载服务", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_stop_auto_download -> {
                // 停止自动下载服务
                AutoDownloadService.stop(this)
                Toast.makeText(this, "已停止自动下载服务", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        adapter = FeatureListAdapter(
            onDownloadClick = { featureId ->
                viewModel.downloadFeature(featureId)
            },
            onRetryClick = { featureId ->
                viewModel.retryFeature(featureId)
            },
            onFeatureClick = { featureId ->
                viewModel.openFeature(featureId)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun observeViewModel() {
        // 观察可用车型列表
        lifecycleScope.launch {
            viewModel.vehicles.collect { vehicleList ->
                vehicleAdapter?.clear()
                vehicleAdapter?.addAll(vehicleList)
                vehicleAdapter?.notifyDataSetChanged()
            }
        }

        // 观察首页资源下载状态
        lifecycleScope.launch {
            viewModel.vehicleDownloadState.collect { state ->
                when (state) {
                    is VehicleDownloadState.Selected -> {
                        // 车型已选择，隐藏 loading，隐藏 features 列表
                        binding.layoutLoadingOverlay.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE
                    }
                    is VehicleDownloadState.Downloading -> {
                        // 下载中，显示全屏 loading，阻塞 UI
                        binding.layoutLoadingOverlay.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE

                        binding.progressBarLoading.isIndeterminate = false
                        binding.progressBarLoading.progress = (state.progress * 100).toInt()
                        binding.tvLoadingProgress.text = "${(state.progress * 100).toInt()}%"
                        binding.tvLoadingText.text = "正在加载首页资源..."

                        if (state.currentFile.isNotEmpty()) {
                            binding.tvLoadingFile.text = "当前文件: ${state.currentFile}"
                            binding.tvLoadingFile.visibility = View.VISIBLE
                        } else {
                            binding.tvLoadingFile.visibility = View.GONE
                        }
                    }
                    is VehicleDownloadState.Ready -> {
                        // 首页资源就绪，隐藏 loading，显示 features 列表
                        binding.layoutLoadingOverlay.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE

                        // 更新标题
                        binding.toolbar.title = "下载管理 - ${state.vehicleName}"
                    }
                    is VehicleDownloadState.Failed -> {
                        // 下载失败，隐藏 loading，隐藏 features 列表
                        binding.layoutLoadingOverlay.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE

                        Toast.makeText(
                            this@MainActivity,
                            "加载首页资源失败: ${state.error}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    null -> {
                        // 初始状态，隐藏 loading 和 features 列表
                        binding.layoutLoadingOverlay.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE
                    }
                }
            }
        }

        // 观察Features列表
        lifecycleScope.launch {
            viewModel.featuresState.collect { features ->
                adapter.submitList(features)
            }
        }

        // 观察加载状态
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // 可以在这里显示/隐藏加载进度
            }
        }

        // 观察错误消息
        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        // 观察默认车型首页下载状态
        lifecycleScope.launch {
            viewModel.defaultVehicleHomeReady.collect { isReady ->
                if (isReady) {
                    // 默认车型首页下载完成，显示提示
                    Toast.makeText(
                        this@MainActivity,
                        "默认车型首页资源已就绪",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Android 13+ 不需要存储权限（使用应用私有目录）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+，不需要存储权限
            return
        }

        // Android 6-12，检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 权限已授予
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // 显示权限说明
                    Toast.makeText(
                        this,
                        "需要存储权限以保存下载文件",
                        Toast.LENGTH_LONG
                    ).show()
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    // 直接请求权限
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
}
