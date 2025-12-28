package com.ace.downloaddemo.core.validation

import android.util.Log
import com.ace.downloaddemo.core.MockConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MD5Validator @Inject constructor() {

    companion object {
        private const val TAG = "MD5Validator"
    }

    // MD5缓存，避免重复计算
    private val md5Cache = mutableMapOf<String, String>()

    /**
     * 校验文件MD5是否匹配
     */
    suspend fun validate(file: File, expectedMd5: String): Boolean {
        Log.d(TAG, "🔍 开始校验: ${file.name}")

        if (expectedMd5.isEmpty()) {
            // 如果没有提供MD5，则认为校验通过
            Log.d(TAG, "⚠️ 未提供MD5，跳过校验: ${file.name}")
            return true
        }

        // ==================== 模拟MD5校验 ====================
        // 由于download.json中的MD5都是mock数据，无法真实校验
        // 这里模拟校验通过，但保留所有逻辑
        // 配置开关: MockConfig.MOCK_MD5_VALIDATION
        if (MockConfig.MOCK_MD5_VALIDATION) {
            Log.i(TAG, "🎭 [模拟模式] 校验文件: ${file.name}")
            // 模拟校验：只要文件存在就认为校验通过
            val isValid = file.exists() && file.isFile && file.length() > 0
            if (isValid) {
                Log.i(TAG, "✅ [模拟] 校验通过: ${file.name} (${file.length() / 1024}KB)")
            } else {
                Log.w(TAG, "❌ [模拟] 校验失败: ${file.name}")
            }
            return isValid
        }
        // ========================================================

        val cachedMd5 = md5Cache[file.absolutePath]
        if (cachedMd5 != null) {
            Log.d(TAG, "💾 使用缓存MD5: ${file.name}")
            val result = cachedMd5.equals(expectedMd5, ignoreCase = true)
            Log.i(TAG, if (result) "✅ 校验通过（缓存）: ${file.name}" else "❌ 校验失败（缓存）: ${file.name}")
            return result
        }

        Log.d(TAG, "⏳ 计算MD5: ${file.name}")
        val calculatedMd5 = calculateMD5(file)
        md5Cache[file.absolutePath] = calculatedMd5
        Log.d(TAG, "📊 计算结果: $calculatedMd5, 预期: $expectedMd5")

        val result = calculatedMd5.equals(expectedMd5, ignoreCase = true)
        Log.i(TAG, if (result) "✅ 校验通过: ${file.name}" else "❌ 校验失败: ${file.name}")
        return result
    }

    /**
     * 计算文件的MD5值
     */
    suspend fun calculateMD5(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            return@withContext ""
        }

        try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 清除MD5缓存
     */
    fun clearCache() {
        md5Cache.clear()
    }

    /**
     * 清除特定文件的MD5缓存
     */
    fun clearCache(filePath: String) {
        md5Cache.remove(filePath)
    }
}
