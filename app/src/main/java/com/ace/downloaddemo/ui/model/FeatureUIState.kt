package com.ace.downloaddemo.ui.model

import com.ace.downloaddemo.data.model.FileInfo
import com.ace.downloaddemo.domain.model.FeatureDownloadState

data class FeatureUIState(
    val id: Int,
    val title: String,
    val subtitle: String,
    val downloadState: FeatureDownloadState,
    val files: List<FileInfo>
)
