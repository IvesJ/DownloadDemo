package com.ace.downloaddemo.data.model

import com.google.gson.annotations.SerializedName

data class DownloadConfig(
    @SerializedName("exhibitionInfos")
    val exhibitionInfos: List<ExhibitionInfo>
)

data class ExhibitionInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("vehicle")
    val vehicle: String? = null,
    @SerializedName("coverName")
    val coverName: String? = null,
    @SerializedName("homeVideoName")
    val homeVideoName: String? = null,
    @SerializedName("qrCodeName")
    val qrCodeName: String? = null,
    @SerializedName("homeResourceZipMD5")
    val homeResourceZipMD5: String? = null,
    @SerializedName("homeResourceZipUrl")
    val homeResourceZipUrl: String? = null,
    @SerializedName("featureConfigs")
    val featureConfigs: List<FeatureConfig>
)

data class FeatureConfig(
    @SerializedName("id")
    val id: Int,
    @SerializedName("pageType")
    val pageType: Int,
    @SerializedName("mainTitle")
    val mainTitle: String,
    @SerializedName("subTitle")
    val subTitle: String,
    @SerializedName("cardBgName")
    val cardBgName: String? = null,
    @SerializedName("cardResourceZipMD5")
    val cardResourceZipMD5: String,
    @SerializedName("cardResourceZipUrl")
    val cardResourceZipUrl: String,
    @SerializedName("configTabs")
    val configTabs: List<ConfigTab>
)

data class ConfigTab(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("contents")
    val contents: List<Content>,
    @SerializedName("subTabs")
    val subTabs: List<ConfigTab> = emptyList()
)

data class Content(
    @SerializedName("type")
    val type: String,
    @SerializedName("fileInfos")
    val fileInfos: List<FileInfo>
)

data class FileInfo(
    @SerializedName("fileType")
    val fileType: Int,
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("mainTitle")
    val mainTitle: String? = null,
    @SerializedName("subTitle")
    val subTitle: String? = null,
    @SerializedName("selectIconName")
    val selectIconName: String? = null,
    @SerializedName("fileMd5")
    val fileMd5: String,
    @SerializedName("fileResUrl")
    val fileResUrl: String
)
