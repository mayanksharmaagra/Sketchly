package com.jrprofessor.sketchly.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.BlockedUser
import com.jrprofessor.sketchly.data.repository.BlockRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class BlockViewModel @Inject constructor(
    private val blockRepository: BlockRepository,
    private val sketchlyRepository: SketchlyRepository,
) : ViewModel() {

    val blockedUsers: StateFlow<List<BlockedUser>> = blockRepository.getBlockedUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unblockUser(targetUserId: String) {
        viewModelScope.launch {
            Log.d("BlockViewModel", "unblockUser: requesting unblock of $targetUserId")
            val result = blockRepository.unblockUser(targetUserId)
            if (result.isSuccess) {
                Log.d("BlockViewModel", "unblockUser: SUCCESS")
            } else {
                Log.e("BlockViewModel", "unblockUser: FAILED — ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** Called by NavGraph / Viewer / ContactHistory screens to perform a block. */
    fun blockUser(
        targetUserId: String,
        targetDisplayName: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            Log.d("BlockViewModel", "blockUser: requesting block of $targetUserId ('$targetDisplayName')")
            val result = blockRepository.blockUser(targetUserId, targetDisplayName, reason)
            if (result.isSuccess) {
                Log.d("BlockViewModel", "blockUser: SUCCESS — removing Room sketches from $targetUserId")
                // Remove blocked sender's messages from the local Room inbox immediately
                // so they stop appearing in the Dashboard feed without requiring
                // any Firestore write permission on /scribbles.
                sketchlyRepository.deleteReceivedSketchesFrom(targetUserId)
                Log.d("BlockViewModel", "blockUser: Room cleanup complete")
            } else {
                Log.e("BlockViewModel", "blockUser: FAILED — ${result.exceptionOrNull()?.message}")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

private val BLOCK_AVATAR_COLORS = listOf(
    Color(0xFF3D405B), Color(0xFF70A18A), Color(0xFFE07A5F),
    Color(0xFFBF4E6D), Color(0xFF4A6FA5), Color(0xFF8B6914),
)
private fun blockAvatarColor(seed: String) =
    BLOCK_AVATAR_COLORS[abs(seed.hashCode()) % BLOCK_AVATAR_COLORS.size]

@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    viewModel: BlockViewModel = hiltViewModel(),
) {
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    var unblockTarget by remember { mutableStateOf<BlockedUser?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .navigationBarsPadding(),
    ) {
        SketchlyTopBar(title = "Blocked Users", onBack = onBack)
        HorizontalDivider(color = TextEditorBorderColor.copy(alpha = 0.5f))

        if (blockedUsers.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔓", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No blocked users",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = AppNameColor,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "People you block won't be able\nto send you scribbles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn {
                item {
                    Text(
                        text = "${blockedUsers.size} BLOCKED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            fontSize = 11.sp,
                        ),
                        color = ButtonGold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                items(blockedUsers, key = { it.blockedUserId }) { blocked ->
                    BlockedUserRow(
                        blocked = blocked,
                        onUnblock = { unblockTarget = blocked },
                    )
                    HorizontalDivider(
                        color = TextEditorBorderColor.copy(alpha = 0.45f),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }

    // Unblock confirmation dialog
    unblockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unblockTarget = null },
            title = {
                Text(
                    text = "Unblock ${target.blockedDisplayName}?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppNameColor,
                )
            },
            text = {
                Text(
                    text = "They'll be able to send you scribbles again. " +
                           "You won't automatically be reconnected — that happens when either of you draws next.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unblockUser(target.blockedUserId)
                        unblockTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonGold),
                ) {
                    Text("Unblock", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { unblockTarget = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgColor,
        )
    }
}

@Composable
private fun BlockedUserRow(
    blocked: BlockedUser,
    onUnblock: () -> Unit,
) {
    val avatarColor = blockAvatarColor(blocked.blockedUserId)
    val initials = blocked.blockedDisplayName
        .trim().split(" ")
        .take(2)
        .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
        .ifBlank { "?" }
    val dateStr = remember(blocked.blockedAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(blocked.blockedAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Initials avatar
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = avatarColor,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = blocked.blockedDisplayName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = AppNameColor,
            )
            Text(
                text = "Blocked on $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        OutlinedButton(
            onClick = onUnblock,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ButtonGold),
        ) {
            Text("Unblock", fontSize = 13.sp)
        }
    }
}
