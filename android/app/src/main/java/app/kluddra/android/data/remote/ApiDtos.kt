package app.kluddra.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiErrorDto(
    val code: String = "UNKNOWN",
    val message: String = "Unknown error"
)

@Serializable
data class ServerInfoDto(
    @SerialName("api_version") val apiVersion: String,
    @SerialName("server_version") val serverVersion: String,
    @SerialName("instance_id") val instanceId: String,
    @SerialName("setup_required") val setupRequired: Boolean
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0
)

@Serializable
data class SetupRequestDto(
    @SerialName("setup_code") val setupCode: String,
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class AuthTokensResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("session_id") val sessionId: String,
    val user: UserDto
)

@Serializable
data class SessionInfoDto(
    val id: String,
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("last_used_at") val lastUsedAt: Long = 0,
    @SerialName("expires_at") val expiresAt: Long = 0,
    val current: Boolean = false
)

@Serializable
data class SessionsResponseDto(
    val sessions: List<SessionInfoDto> = emptyList()
)

@Serializable
data class RevokeOthersResponseDto(
    @SerialName("revoked_count") val revokedCount: Int = 0
)

@Serializable
data class NoteDto(
    val id: String,
    val title: String = "",
    val body: String = "",
    val color: String = "",
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_read_only") val isReadOnly: Boolean = false,
    val version: Long = 1,
    @SerialName("server_seq") val serverSeq: Long = 0,
    @SerialName("conflict_of_note_id") val conflictOfNoteId: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("trashed_at") val trashedAt: Long? = null,
    @SerialName("purged_at") val purgedAt: Long? = null
)

@Serializable
data class ShoppingItemDto(
    val id: String,
    val text: String,
    @SerialName("is_checked") val isChecked: Boolean = false,
    @SerialName("sort_order") val sortOrder: String = "m000010",
    val version: Long = 1,
    @SerialName("server_seq") val serverSeq: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("deleted_at") val deletedAt: Long? = null
)

@Serializable
data class SyncPullResponseDto(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("server_version") val serverVersion: String? = null,
    @SerialName("api_version") val apiVersion: String? = null,
    @SerialName("current_seq") val currentSeq: Long,
    @SerialName("has_more") val hasMore: Boolean = false,
    val notes: List<NoteDto> = emptyList(),
    @SerialName("shopping_items") val shoppingItems: List<ShoppingItemDto> = emptyList()
)

@Serializable
data class SyncMutationDto(
    @SerialName("mutation_id") val mutationId: String,
    val entity: String,
    val op: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("base_version") val baseVersion: Long,
    val payload: JsonElement
)

@Serializable
data class SyncPushRequestDto(
    @SerialName("client_device") val clientDevice: String,
    val mutations: List<SyncMutationDto>
)

@Serializable
data class SyncMutationResultDto(
    @SerialName("mutation_id") val mutationId: String,
    val entity: String,
    @SerialName("entity_id") val entityId: String,
    val status: String,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    val note: NoteDto? = null,
    @SerialName("conflict_copy") val conflictCopy: NoteDto? = null,
    @SerialName("shopping_item") val shoppingItem: ShoppingItemDto? = null
)

@Serializable
data class SyncPushResponseDto(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("current_seq") val currentSeq: Long,
    val results: List<SyncMutationResultDto> = emptyList()
)

@Serializable
data class NoteUpsertPayloadDto(
    val title: String,
    val body: String,
    val color: String = "",
    @SerialName("is_pinned") val isPinned: Boolean,
    @SerialName("is_read_only") val isReadOnly: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class FlagPayloadDto(
    val value: Boolean
)

@Serializable
data class ColorPayloadDto(
    val color: String
)

@Serializable
data class ShoppingUpsertPayloadDto(
    val text: String,
    @SerialName("is_checked") val isChecked: Boolean,
    @SerialName("sort_order") val sortOrder: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)
