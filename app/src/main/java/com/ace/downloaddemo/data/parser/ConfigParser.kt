package com.ace.downloaddemo.data.parser

import android.content.Context
import com.ace.downloaddemo.data.model.ConfigTab
import com.ace.downloaddemo.data.model.DownloadConfig
import com.ace.downloaddemo.data.model.ExhibitionInfo
import com.ace.downloaddemo.data.model.FeatureConfig
import com.ace.downloaddemo.data.model.FileInfo
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()

    /**
     * 解析JSON配置文件
     */
    suspend fun parse(jsonPath: String): DownloadConfig? = withContext(Dispatchers.IO) {
        try {
            val jsonFile = File(jsonPath)
            val jsonString = if (jsonFile.exists()) {
                jsonFile.readText()
            } else {
                // 尝试从assets读取
                context.assets.open(jsonPath).bufferedReader().use { it.readText() }
            }

            gson.fromJson(jsonString, DownloadConfig::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从JSON字符串解析
     */
    fun parseFromString(jsonString: String): DownloadConfig? {
        return try {
            gson.fromJson(jsonString, DownloadConfig::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 递归提取Feature的所有文件
     * 包括：cardResourceZip + 所有tabs和subTabs中的fileInfos
     */
    fun extractAllFiles(feature: FeatureConfig): List<FileInfo> {
        val files = mutableListOf<FileInfo>()

        // 添加cardResourceZip文件
        if (feature.cardResourceZipUrl.isNotEmpty()) {
            files.add(
                FileInfo(
                    fileType = 0,
                    fileName = "card_resource_${feature.id}.zip",
                    mainTitle = null,
                    subTitle = null,
                    selectIconName = null,
                    fileMd5 = feature.cardResourceZipMD5,
                    fileResUrl = feature.cardResourceZipUrl
                )
            )
        }

        // 递归提取所有tabs中的文件
        fun traverseTabs(tabs: List<ConfigTab>) {
            tabs.forEach { tab ->
                // 提取contents中的fileInfos
                tab.contents.forEach { content ->
                    files.addAll(content.fileInfos)
                }
                // 递归处理subTabs
                if (tab.subTabs.isNotEmpty()) {
                    traverseTabs(tab.subTabs)
                }
            }
        }

        traverseTabs(feature.configTabs)

        return files
    }

    /**
     * 统计Feature的文件总数
     */
    fun countFiles(feature: FeatureConfig): Int {
        return extractAllFiles(feature).size
    }

    /**
     * 统计所有Feature的文件总数
     */
    fun countAllFiles(config: DownloadConfig): Int {
        return config.exhibitionInfos.sumOf { exhibition ->
            exhibition.featureConfigs.sumOf { feature ->
                countFiles(feature)
            }
        }
    }

    /**
     * 从 ExhibitionInfo 提取首页资源文件列表
     * @param exhibitionInfo 展览信息
     * @return 首页资源文件列表，如果 homeResourceZipUrl 为空则返回空列表
     */
    fun extractHomeResources(exhibitionInfo: ExhibitionInfo): List<FileInfo> {
        val files = mutableListOf<FileInfo>()

        // 添加首页资源包（如果有）
        if (!exhibitionInfo.homeResourceZipUrl.isNullOrEmpty() &&
            !exhibitionInfo.homeResourceZipMD5.isNullOrEmpty()) {
            files.add(
                FileInfo(
                    fileType = 3,
                    fileName = "home_resource_${exhibitionInfo.id}.zip",
                    mainTitle = "首页资源包",
                    subTitle = exhibitionInfo.vehicle ?: "默认",
                    selectIconName = null,
                    fileMd5 = exhibitionInfo.homeResourceZipMD5!!,
                    fileResUrl = exhibitionInfo.homeResourceZipUrl!!
                )
            )
        }

        return files
    }
}
