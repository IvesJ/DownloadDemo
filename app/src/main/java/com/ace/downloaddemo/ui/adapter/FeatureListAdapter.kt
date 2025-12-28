package com.ace.downloaddemo.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ace.downloaddemo.databinding.ItemFeatureBinding
import com.ace.downloaddemo.domain.model.FeatureDownloadState
import com.ace.downloaddemo.ui.model.FeatureUIState

class FeatureListAdapter(
    private val onDownloadClick: (Int) -> Unit,
    private val onRetryClick: (Int) -> Unit,
    private val onFeatureClick: (Int) -> Unit
) : ListAdapter<FeatureUIState, FeatureListAdapter.FeatureViewHolder>(FeatureDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val binding = ItemFeatureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeatureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        holder.bind(getItem(position), onDownloadClick, onRetryClick, onFeatureClick)
    }

    class FeatureViewHolder(
        private val binding: ItemFeatureBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            feature: FeatureUIState,
            onDownloadClick: (Int) -> Unit,
            onRetryClick: (Int) -> Unit,
            onFeatureClick: (Int) -> Unit
        ) {
            binding.tvTitle.text = feature.title
            binding.tvSubtitle.text = feature.subtitle

            when (val state = feature.downloadState) {
                is FeatureDownloadState.Idle -> {
                    binding.btnAction.text = "下载"
                    binding.btnAction.isEnabled = true
                    binding.layoutProgress.visibility = View.GONE
                    binding.tvError.visibility = View.GONE

                    binding.btnAction.setOnClickListener {
                        onDownloadClick(feature.id)
                    }
                }

                is FeatureDownloadState.Downloading -> {
                    val progressPercent = (state.progress * 100).toInt()
                    binding.btnAction.text = "$progressPercent%"
                    binding.btnAction.isEnabled = false
                    binding.layoutProgress.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE

                    binding.progressBar.progress = progressPercent
                    binding.tvProgress.text = "$progressPercent%"

                    if (state.currentFile.isNotEmpty()) {
                        binding.tvCurrentFile.text = "正在下载: ${state.currentFile}"
                        binding.tvCurrentFile.visibility = View.VISIBLE
                    } else {
                        binding.tvCurrentFile.visibility = View.GONE
                    }

                    if (state.totalFiles > 0) {
                        binding.tvFileCount.text = "已完成 ${state.completedFiles}/${state.totalFiles} 个文件"
                        binding.tvFileCount.visibility = View.VISIBLE
                    } else {
                        binding.tvFileCount.visibility = View.GONE
                    }
                }

                is FeatureDownloadState.Completed -> {
                    binding.btnAction.text = "已完成"
                    binding.btnAction.isEnabled = true
                    binding.layoutProgress.visibility = View.GONE
                    binding.tvError.visibility = View.GONE

                    binding.btnAction.setOnClickListener {
                        onFeatureClick(feature.id)
                    }
                }

                is FeatureDownloadState.Failed -> {
                    binding.btnAction.text = "重试"
                    binding.btnAction.isEnabled = true
                    binding.layoutProgress.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "下载失败: ${state.error}"

                    if (state.failedFile.isNotEmpty()) {
                        binding.tvError.text = "下载失败: ${state.error} (${state.failedFile})"
                    }

                    binding.btnAction.setOnClickListener {
                        onRetryClick(feature.id)
                    }
                }

                is FeatureDownloadState.Canceled -> {
                    binding.btnAction.text = "下载"
                    binding.btnAction.isEnabled = true
                    binding.layoutProgress.visibility = View.GONE
                    binding.tvError.visibility = View.GONE

                    binding.btnAction.setOnClickListener {
                        onDownloadClick(feature.id)
                    }
                }
            }
        }
    }

    private class FeatureDiffCallback : DiffUtil.ItemCallback<FeatureUIState>() {
        override fun areItemsTheSame(oldItem: FeatureUIState, newItem: FeatureUIState): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FeatureUIState, newItem: FeatureUIState): Boolean {
            return oldItem == newItem
        }
    }
}
