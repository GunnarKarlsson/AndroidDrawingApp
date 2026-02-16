package com.example.drawingapp.data

import android.content.Context
import android.util.Log
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.model.ListObjectsV2Result
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.example.drawingapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.amazonaws.services.s3.model.ObjectMetadata
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val USER_PREFIX = "users"
private const val PAGE_IDS_KEY = "page_ids.txt"
private const val NOTEBOOKS_KEY = "notebooks.json"
private const val ASSIGNMENTS_KEY = "notebook_assignments.json"

class S3BackupRepository(
    private val context: Context,
    private val localStore: LocalPageStore,
    private val bucketName: String = BuildConfig.AWS_S3_BUCKET,
    private val regionName: String = BuildConfig.AWS_REGION,
    private val cognitoPoolId: String = BuildConfig.AWS_COGNITO_POOL_ID
) {
    private val userId: String by lazy {
        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()
    }

    private val userPrefix: String get() = "$USER_PREFIX/$userId"

    fun isConfigured(): Boolean = bucketName.isNotBlank() && cognitoPoolId.isNotBlank()

    suspend fun backup(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("AWS not configured. Set AWS_S3_BUCKET and AWS_COGNITO_POOL_ID in BuildConfig."))
        }
        try {
            val credentialsProvider = CognitoCachingCredentialsProvider(
                context,
                cognitoPoolId,
                Regions.fromName(regionName)
            )
            val s3 = AmazonS3Client(credentialsProvider).apply {
                setRegion(Region.getRegion(Regions.fromName(regionName)))
            }
            val pageIds = localStore.loadPageIds()
            val pagesDir = File(context.filesDir, "pages")
            val pageIdsBytes = pageIds.joinToString("\n").toByteArray(Charsets.UTF_8)
            val metadata = ObjectMetadata().apply { contentLength = pageIdsBytes.size.toLong() }
            s3.putObject(bucketName, "$userPrefix/$PAGE_IDS_KEY", ByteArrayInputStream(pageIdsBytes), metadata)
            pageIds.forEach { pageId ->
                val pageDir = File(pagesDir, pageId)
                if (pageDir.exists()) {
                    pageDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val key = "$userPrefix/pages/$pageId/${file.name}"
                            s3.putObject(bucketName, key, file)
                        }
                    }
                }
            }
            val notebooksFile = File(context.filesDir, NOTEBOOKS_KEY)
            if (notebooksFile.exists()) {
                val bytes = notebooksFile.readBytes()
                val meta = ObjectMetadata().apply { contentLength = bytes.size.toLong() }
                s3.putObject(bucketName, "$userPrefix/$NOTEBOOKS_KEY", ByteArrayInputStream(bytes), meta)
            }
            val assignmentsFile = File(context.filesDir, ASSIGNMENTS_KEY)
            if (assignmentsFile.exists()) {
                val bytes = assignmentsFile.readBytes()
                val meta = ObjectMetadata().apply { contentLength = bytes.size.toLong() }
                s3.putObject(bucketName, "$userPrefix/$ASSIGNMENTS_KEY", ByteArrayInputStream(bytes), meta)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("S3Backup", "Backup failed", e)
            Result.failure(e)
        }
    }

    suspend fun restore(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("AWS not configured."))
        }
        try {
            val credentialsProvider = CognitoCachingCredentialsProvider(
                context,
                cognitoPoolId,
                Regions.fromName(regionName)
            )
            val s3 = AmazonS3Client(credentialsProvider).apply {
                setRegion(Region.getRegion(Regions.fromName(regionName)))
            }
            val listRequest = ListObjectsV2Request().withBucketName(bucketName).withPrefix("$userPrefix/")
            var result: ListObjectsV2Result?
            var pageIdsContent: String? = null
            var notebooksContent: String? = null
            var assignmentsContent: String? = null
            val pageFiles = mutableMapOf<String, MutableList<String>>()
            do {
                result = s3.listObjectsV2(listRequest)
                result.objectSummaries.forEach { summary ->
                    val key = summary.key
                    when {
                        key.endsWith(PAGE_IDS_KEY) -> {
                            val obj = s3.getObject(bucketName, key)
                            pageIdsContent = obj.objectContent.bufferedReader().readText()
                        }
                        key.endsWith(NOTEBOOKS_KEY) -> {
                            val obj = s3.getObject(bucketName, key)
                            notebooksContent = obj.objectContent.bufferedReader().readText()
                        }
                        key.endsWith(ASSIGNMENTS_KEY) -> {
                            val obj = s3.getObject(bucketName, key)
                            assignmentsContent = obj.objectContent.bufferedReader().readText()
                        }
                        key.contains("/pages/") -> {
                            val parts = key.removePrefix("$userPrefix/pages/").split("/", limit = 2)
                            if (parts.size == 2) {
                                pageFiles.getOrPut(parts[0]) { mutableListOf() }.add(key)
                            }
                        }
                    }
                }
                listRequest.continuationToken = result.nextContinuationToken
            } while (result?.isTruncated == true)

            notebooksContent?.let { text ->
                File(context.filesDir, NOTEBOOKS_KEY).writeText(text)
            }
            assignmentsContent?.let { text ->
                File(context.filesDir, ASSIGNMENTS_KEY).writeText(text)
            }
            pageIdsContent?.let { ids ->
                val idsList = ids.lines().filter { it.isNotBlank() }
                localStore.savePageIds(idsList)
            }
            val pagesDir = File(context.filesDir, "pages")
            pageFiles.forEach { (pageId, keys) ->
                val pageDir = File(pagesDir, pageId)
                pageDir.mkdirs()
                keys.forEach { key ->
                    val fileName = key.substringAfterLast("/")
                    val file = File(pageDir, fileName)
                    s3.getObject(bucketName, key).objectContent.use { input: S3ObjectInputStream ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("S3Backup", "Restore failed", e)
            Result.failure(e)
        }
    }
}
