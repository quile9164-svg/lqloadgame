package com.example.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppError
import com.example.core.security.SecurityUtils
import com.example.core.image.RenderEngine
import com.example.data.cos.CosCredential
import com.example.data.cos.CosUploadClient
import com.example.data.kgvn.KgvnWebSessionManager
import com.example.data.project.ProjectRepository
import com.example.data.project.ProjectSerialization
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*

class PosterEditorViewModel(
    private val projectRepository: ProjectRepository,
    private val webSessionManager: KgvnWebSessionManager
) : ViewModel() {

    private val _projectState = MutableStateFlow<Project>(Project(name = "New Poster"))
    val projectState: StateFlow<Project> = _projectState

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId

    // History undo/redo state
    private val undoList = LinkedList<Project>()
    private val redoList = LinkedList<Project>()

    // Upload & Apply State
    private val _applyProgress = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val applyProgress: StateFlow<ApplyState> = _applyProgress

    sealed class ApplyState {
        object Idle : ApplyState()
        data class Running(
            val currentStep: Int, // 1 to 7
            val progress: Float, // 0.0f to 1.0f
            val statusMessage: String
        ) : ApplyState()
        object Success : ApplyState()
        data class Failure(val error: AppError) : ApplyState()
    }

    init {
        // Save initial state to history
        recordHistory(_projectState.value)
    }

    fun loadProject(projectId: Int) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                _projectState.value = project
                undoList.clear()
                redoList.clear()
                recordHistory(project)
            }
        }
    }

    fun saveProjectLocal(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            // Render a small thumbnail
            val thumbnailPath = withContext(Dispatchers.IO) {
                try {
                    val bitmap = RenderEngine.renderProject(context, _projectState.value, 160, 252)
                    val file = File(context.filesDir, "thumb_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    bitmap.recycle()
                    file.absolutePath
                } catch (e: Exception) {
                    null
                }
            }

            val currentProj = _projectState.value.copy(
                lastUpdated = System.currentTimeMillis(),
                thumbnailPath = thumbnailPath ?: _projectState.value.thumbnailPath
            )
            val newId = projectRepository.insertProject(currentProj)
            _projectState.value = currentProj.copy(id = newId)
            onComplete()
        }
    }

    private fun recordHistory(project: Project) {
        // Limit to 45 steps for safety
        if (undoList.size >= 45) {
            undoList.removeFirst()
        }
        undoList.add(project)
        redoList.clear()
    }

    fun updateProjectState(updater: (Project) -> Project) {
        val current = _projectState.value
        val updated = updater(current)
        _projectState.value = updated
        recordHistory(updated)
    }

    fun undo() {
        if (undoList.size > 1) {
            val current = undoList.removeLast()
            redoList.add(current)
            val previous = undoList.last
            _projectState.value = previous
        }
    }

    fun redo() {
        if (redoList.isNotEmpty()) {
            val next = redoList.removeLast()
            undoList.add(next)
            _projectState.value = next
        }
    }

    // Layer manipulations
    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = layerId
    }

    fun addTextLayer() {
        val id = UUID.randomUUID().toString()
        val newLayer = LayerModel(
            id = id,
            name = "Text Layer",
            type = LayerType.TEXT,
            textContent = "Nhập chữ ở đây",
            x = 100f,
            y = 200f,
            width = 150f,
            height = 80f
        )
        updateProjectState { it.copy(layers = it.layers + newLayer) }
        selectLayer(id)
    }

    fun addStickerLayer(uri: String) {
        val id = UUID.randomUUID().toString()
        val newLayer = LayerModel(
            id = id,
            name = "Sticker",
            type = LayerType.STICKER,
            stickerUri = uri,
            x = 100f,
            y = 150f,
            width = 120f,
            height = 120f
        )
        updateProjectState { it.copy(layers = it.layers + newLayer) }
        selectLayer(id)
    }

    fun addShapeLayer(shapeType: ShapeType) {
        val id = UUID.randomUUID().toString()
        val newLayer = LayerModel(
            id = id,
            name = "Shape ${shapeType.name}",
            type = LayerType.SHAPE,
            shapeType = shapeType,
            x = 80f,
            y = 180f,
            width = 160f,
            height = 100f
        )
        updateProjectState { it.copy(layers = it.layers + newLayer) }
        selectLayer(id)
    }

    fun deleteSelectedLayer() {
        val selectedId = _selectedLayerId.value ?: return
        updateProjectState { it.copy(layers = it.layers.filter { layer -> layer.id != selectedId }) }
        selectLayer(null)
    }

    fun duplicateSelectedLayer() {
        val selectedId = _selectedLayerId.value ?: return
        val layer = _projectState.value.layers.find { it.id == selectedId } ?: return
        val newId = UUID.randomUUID().toString()
        val duplicated = layer.copy(
            id = newId,
            name = "${layer.name} Copy",
            x = layer.x + 20f,
            y = layer.y + 20f
        )
        updateProjectState { it.copy(layers = it.layers + duplicated) }
        selectLayer(newId)
    }

    fun updateLayerPosition(layerId: String, dx: Float, dy: Float) {
        _projectState.value = _projectState.value.copy(
            layers = _projectState.value.layers.map {
                if (it.id == layerId) {
                    it.copy(x = it.x + dx, y = it.y + dy)
                } else it
            }
        )
    }

    fun updateLayerTransform(layerId: String, scaleFactor: Float, rotationDelta: Float) {
        _projectState.value = _projectState.value.copy(
            layers = _projectState.value.layers.map {
                if (it.id == layerId) {
                    it.copy(
                        width = (it.width * scaleFactor).coerceIn(40f, 600f),
                        height = (it.height * scaleFactor).coerceIn(40f, 600f),
                        rotation = (it.rotation + rotationDelta) % 360f
                    )
                } else it
            }
        )
    }

    fun moveLayerZOrder(layerId: String, action: String) {
        val layers = _projectState.value.layers.toMutableList()
        val index = layers.indexOfFirst { it.id == layerId }
        if (index == -1) return

        val layer = layers.removeAt(index)
        when (action) {
            "FRONT" -> layers.add(layer)
            "BACK" -> layers.add(0, layer)
            "FORWARD" -> {
                val newIndex = (index + 1).coerceAtMost(layers.size)
                layers.add(newIndex, layer)
            }
            "BACKWARD" -> {
                val newIndex = (index - 1).coerceAtLeast(0)
                layers.add(newIndex, layer)
            }
        }
        updateProjectState { it.copy(layers = layers) }
    }

    fun alignSelectedLayer(action: String) {
        val selectedId = _selectedLayerId.value ?: return
        updateProjectState { proj ->
            proj.copy(
                layers = proj.layers.map { layer ->
                    if (layer.id == selectedId) {
                        // Canvas is 320 x 504
                        when (action) {
                            "LEFT" -> layer.copy(x = 10f)
                            "RIGHT" -> layer.copy(x = 320f - layer.width - 10f)
                            "TOP" -> layer.copy(y = 10f)
                            "BOTTOM" -> layer.copy(y = 504f - layer.height - 10f)
                            "CENTER_H" -> layer.copy(x = (320f - layer.width) / 2f)
                            "CENTER_V" -> layer.copy(y = (504f - layer.height) / 2f)
                            else -> layer
                        }
                    } else layer
                }
            )
        }
    }

    // APPLY POSTER FLOW (7 STEPS)
    fun resetApplyState() {
        _applyProgress.value = ApplyState.Idle
    }

    fun applyPoster(context: Context) {
        _applyProgress.value = ApplyState.Running(1, 0f, "Đang kiểm tra phiên đăng nhập KGVN...")

        viewModelScope.launch {
            try {
                // Step 1: Check Session
                val session = webSessionManager.sessionState.value
                if (session !is KgvnWebSessionManager.SessionState.Active) {
                    throw AppError(
                        step = 1,
                        title = "Phiên hết hạn",
                        message = "Vui lòng mở liên kết KGVN và đăng nhập lại trong WebView."
                    )
                }

                val roleId = session.campRoleId

                // Step 2: Create Poster ID
                updateApplyStatus(2, 0.2f, "Đang yêu cầu Poster ID từ máy chủ KGVN...")
                val posterId = requestPosterId(roleId)

                // Step 3: Prepare Image Bitmaps (Normal and Large)
                updateApplyStatus(3, 0.4f, "Đang xử lý hình ảnh độ phân giải cao...")
                val (normalBytes, largeBytes) = withContext(Dispatchers.Default) {
                    val normBmp = RenderEngine.renderProject(context, _projectState.value, 320, 504)
                    val lgBmp = RenderEngine.renderProject(context, _projectState.value, 1080, 1701)

                    val normStream = ByteArrayOutputStream()
                    normBmp.compress(Bitmap.CompressFormat.JPEG, 90, normStream)
                    val normArray = normStream.toByteArray()

                    val lgStream = ByteArrayOutputStream()
                    lgBmp.compress(Bitmap.CompressFormat.PNG, 100, lgStream)
                    val lgArray = lgStream.toByteArray()

                    normBmp.recycle()
                    lgBmp.recycle()

                    Pair(normArray, lgArray)
                }

                // Step 4: Upload standard poster to Tencent COS
                updateApplyStatus(4, 0.6f, "Đang tải poster chuẩn lên Tencent COS...")
                val normalCredential = getCosCredential(posterId, isLarge = false)
                val uploadClient = CosUploadClient()
                uploadClient.upload(4, normalCredential, normalBytes, "image/jpeg") { progress ->
                    updateApplyStatus(4, 0.6f + progress * 0.15f, "Tải poster chuẩn: ${(progress * 100).toInt()}%")
                }

                // Step 5: Upload large poster to Tencent COS
                updateApplyStatus(5, 0.75f, "Đang tải poster lớn lên Tencent COS...")
                val largeCredential = getCosCredential(posterId, isLarge = true)
                uploadClient.upload(5, largeCredential, largeBytes, "image/png") { progress ->
                    updateApplyStatus(5, 0.75f + progress * 0.15f, "Tải poster lớn: ${(progress * 100).toInt()}%")
                }

                // Step 6: Save and Apply Poster
                updateApplyStatus(6, 0.9f, "Đang áp dụng poster vào tài khoản KGVN...")
                val picUrl = "https://${normalCredential.bucket}.cos.${normalCredential.region}.myqcloud.com${normalCredential.path}"
                val largePicUrl = "https://${largeCredential.bucket}.cos.${largeCredential.region}.myqcloud.com${largeCredential.path}"
                savePosterOnKGVN(posterId, picUrl, largePicUrl)

                // Step 7: Optional Save Layout Edit Info
                updateApplyStatus(7, 0.95f, "Đang lưu cấu trúc poster cục bộ...")
                savePosterEditInfo(posterId)

                // Finish
                _applyProgress.value = ApplyState.Success

            } catch (e: AppError) {
                // REDACT all tokens and secrets before saving error details!
                val redactedDetails = SecurityUtils.redact(e.sanitizedDetails)
                _applyProgress.value = ApplyState.Failure(e.copy(sanitizedDetails = redactedDetails))
            } catch (e: Exception) {
                val redactedDetails = SecurityUtils.redact(e.stackTraceToString())
                _applyProgress.value = ApplyState.Failure(
                    AppError(
                        step = 1,
                        title = "Lỗi hệ thống",
                        message = e.localizedMessage ?: "Đã xảy ra lỗi không xác định.",
                        sanitizedDetails = redactedDetails
                    )
                )
            }
        }
    }

    private fun updateApplyStatus(step: Int, progress: Float, status: String) {
        _applyProgress.value = ApplyState.Running(step, progress, status)
    }

    // COROUTINE API CALLS WITH PAGE CONTEXT
    private suspend fun requestPosterId(roleId: String): String = suspendCoroutineWithTimeout(2) { continuation ->
        val params = JSONObject().apply {
            put("roleId", roleId)
        }
        webSessionManager.callApiInPageContext(2, "createposter", params) { success, responseJson, error ->
            if (success && responseJson != null) {
                try {
                    val obj = JSONObject(responseJson)
                    val data = obj.optJSONObject("data")
                    val posterId = data?.optString("posterId") ?: data?.optString("id")
                    if (!posterId.isNullOrEmpty()) {
                        continuation.resume(posterId)
                    } else {
                        continuation.resumeWithException(AppError(2, "Lỗi API KGVN", "Không thể trích xuất posterId từ phản hồi.", sanitizedDetails = responseJson))
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(AppError(2, "Lỗi phân tích cú pháp", "Dữ liệu trả về bị hỏng.", sanitizedDetails = responseJson))
                }
            } else {
                continuation.resumeWithException(AppError(2, "Lỗi máy chủ KGVN", error ?: "Yêu cầu tạo poster thất bại.", sanitizedDetails = error ?: ""))
            }
        }
    }

    private suspend fun getCosCredential(posterId: String, isLarge: Boolean): CosCredential = suspendCoroutineWithTimeout(if (isLarge) 5 else 4) { continuation ->
        val params = JSONObject().apply {
            put("posterId", posterId)
            put("isLarge", isLarge)
            put("fileName", if (isLarge) "${posterId}_large.png" else "$posterId.png")
        }
        val action = "getcoscredential"
        webSessionManager.callApiInPageContext(if (isLarge) 5 else 4, action, params) { success, responseJson, error ->
            if (success && responseJson != null) {
                try {
                    val root = JSONObject(responseJson)
                    val data = root.getJSONObject("data")
                    val credentials = data.getJSONObject("credentials")
                    
                    val cred = CosCredential(
                        tmpSecretId = credentials.getString("tmpSecretId"),
                        tmpSecretKey = credentials.getString("tmpSecretKey"),
                        token = credentials.getString("sessionToken"),
                        startTime = credentials.getLong("startTime"),
                        expiration = credentials.getLong("expiredTime"),
                        bucket = data.getString("bucket"),
                        region = data.getString("region"),
                        path = data.getString("path")
                    )
                    continuation.resume(cred)
                } catch (e: Exception) {
                    continuation.resumeWithException(AppError(if (isLarge) 5 else 4, "Lỗi COS Credentials", "Không thể lấy thông tin xác thực Tencent COS.", sanitizedDetails = responseJson))
                }
            } else {
                continuation.resumeWithException(AppError(if (isLarge) 5 else 4, "Lỗi KGVN API", error ?: "Lấy khóa tạm thời COS thất bại.", sanitizedDetails = error ?: ""))
            }
        }
    }

    private suspend fun savePosterOnKGVN(posterId: String, picUrl: String, largePicUrl: String): Boolean = suspendCoroutineWithTimeout(6) { continuation ->
        val params = JSONObject().apply {
            put("posterId", posterId)
            put("picUrl", picUrl)
            put("largePicUrl", largePicUrl)
        }
        webSessionManager.callApiInPageContext(6, "saveposter", params) { success, responseJson, error ->
            if (success) {
                continuation.resume(true)
            } else {
                continuation.resumeWithException(AppError(6, "Lỗi áp dụng poster", error ?: "Lưu poster lên hệ thống KGVN thất bại.", sanitizedDetails = error ?: ""))
            }
        }
    }

    private suspend fun savePosterEditInfo(posterId: String): Boolean = suspendCoroutineWithTimeout(7) { continuation ->
        val params = JSONObject().apply {
            put("posterId", posterId)
            put("editInfo", ProjectSerialization.serialize(_projectState.value))
        }
        webSessionManager.callApiInPageContext(7, "savepostereditinfo", params) { success, _, _ ->
            // Step 7 is optional, so we succeed even if the server rejects saving structure
            continuation.resume(true)
        }
    }

    // Helper to suspend Kotlin thread while waiting for JavaScript WebView Callback
    private suspend fun <T> suspendCoroutineWithTimeout(
        step: Int,
        block: (Continuation<T>) -> Unit
    ): T = withContext(Dispatchers.Main) {
        kotlin.coroutines.suspendCoroutine<T> { cont ->
            // Set up a 35 second timeout for safety
            val timer = Timer()
            var finished = false

            val continuationWrapper = object : Continuation<T> {
                override val context = cont.context
                override fun resumeWith(result: Result<T>) {
                    if (!finished) {
                        finished = true
                        timer.cancel()
                        cont.resumeWith(result)
                    }
                }
            }

            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (!finished) {
                        finished = true
                        cont.resumeWith(Result.failure(AppError(step, "Lỗi quá thời gian", "Yêu cầu vượt quá thời gian phản hồi (35s). Vui lòng kiểm tra kết nối mạng và thử lại.", retryable = true)))
                    }
                }
            }, 35000)

            block(continuationWrapper)
        }
    }
}
