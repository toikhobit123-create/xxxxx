package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.engine.PriorityHolder
import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardType
import com.example.model.HandCardInstance
import com.example.model.Keyword
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard
import com.example.ui.components.ArenaBoardNexusOverlay
import com.example.ui.components.BattleBoard
import com.example.ui.components.BattleBoardDropHighlight
import com.example.ui.components.CenterlineScrollButton
import com.example.ui.components.FilmGrainOverlay
import com.example.ui.components.FreljordBattlefieldBackground
import com.example.ui.components.HandCardView
import com.example.ui.components.PlayerHandOverlay
import com.example.ui.components.StackedDeckPile
import com.example.ui.components.TacticalManaActionColumn
import com.example.ui.components.UnitToken
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SemanticAttack
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceOverlay
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface ActiveDragState {
    val pointerInRoot: Offset

    data class HandCardDrag(
        val card: HandCardInstance,
        override val pointerInRoot: Offset
    ) : ActiveDragState

    data class BenchUnitDrag(
        val unit: UnitOnBoard,
        override val pointerInRoot: Offset
    ) : ActiveDragState

    data class CombatUnitDrag(
        val unit: UnitOnBoard,
        override val pointerInRoot: Offset
    ) : ActiveDragState
}

@Composable
fun CombatScreen(
    combat: CombatState,
    revivesRemaining: Int,
    onPlayCard: (String) -> Unit,
    onPlayTargetedCard: (String, String?) -> Unit = { cardId, _ -> onPlayCard(cardId) },
    onEnterAttack: () -> Unit,
    onCancelAttack: () -> Unit,
    onToggleAttacker: (String) -> Unit,
    onSelectChallengerAttacker: (String?) -> Unit = {},
    onChallengeEnemyUnit: (String, String) -> Unit = { _, _ -> },
    onClearChallenge: (String) -> Unit = {},
    onDragBenchUnitToAttack: (String) -> Unit = {},
    onRemoveAttacker: (String) -> Unit = {},
    onCommitAttack: () -> Unit,
    onSelectEnemyAttackerForBlock: (String?) -> Unit,
    onAssignBlocker: (String, String) -> Unit,
    onRemoveBlocker: (String) -> Unit,
    onCommitBlock: () -> Unit,
    onResolveStack: () -> Unit,
    onPassTurn: () -> Unit,
    onVictoryContinue: () -> Unit,
    onUseRevive: () -> Unit,
    onAcceptDefeat: () -> Unit,
    onAbandonRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inspectedHandCard by remember { mutableStateOf<HandCardInstance?>(null) }
    var inspectHandDetailOnly by remember { mutableStateOf(false) }
    var isHandExpanded by remember { mutableStateOf(false) }
    var inspectedUnit by remember { mutableStateOf<UnitOnBoard?>(null) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showPowerInfo by remember { mutableStateOf(false) }
    var showGraveyardDialog by remember { mutableStateOf(false) }
    var showMatchInfoDialog by remember { mutableStateOf(false) }
    var showEmotePicker by remember { mutableStateOf(false) }
    var activeEmote by remember { mutableStateOf<CombatEmoteOption?>(null) }
    var showForfeitConfirm by remember { mutableStateOf(false) }

    var pendingTargetCard by remember { mutableStateOf<HandCardInstance?>(null) }
    var pendingFirstUnitId by remember { mutableStateOf<String?>(null) }

    var activeDrag by remember { mutableStateOf<ActiveDragState?>(null) }
    var dragWarningText by remember { mutableStateOf<String?>(null) }
    var activeLevelUpBanner by remember { mutableStateOf<UnitOnBoard?>(null) }
    val knownChampionLevels = remember { mutableStateMapOf<String, Boolean>() }
    val shownLevelUpTemplates = remember { mutableStateMapOf<String, Boolean>() }
    var champTrackingInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(activeEmote) {
        if (activeEmote != null) {
            kotlinx.coroutines.delay(1800)
            activeEmote = null
        }
    }
    LaunchedEffect(dragWarningText) {
        if (dragWarningText != null) {
            kotlinx.coroutines.delay(1400)
            dragWarningText = null
        }
    }
    LaunchedEffect(activeLevelUpBanner) {
        if (activeLevelUpBanner != null) {
            kotlinx.coroutines.delay(2100)
            activeLevelUpBanner = null
        }
    }
    LaunchedEffect(combat.playerHand, combat.priorityHolder, combat.phase) {
        val current = inspectedHandCard
        if (current != null && combat.playerHand.none { it.instanceId == current.instanceId }) {
            inspectedHandCard = null
        }
        val currentTarget = pendingTargetCard
        if (currentTarget != null &&
            (combat.priorityHolder != PriorityHolder.PLAYER ||
                combat.phase == CombatPhase.GAME_OVER ||
                combat.playerHand.none { it.instanceId == currentTarget.instanceId })
        ) {
            pendingTargetCard = null
            pendingFirstUnitId = null
        }
        if (combat.phase == CombatPhase.GAME_OVER || combat.playerHand.isEmpty()) {
            isHandExpanded = false
        }
    }
    LaunchedEffect(combat.playerBoard, combat.enemyBoard) {
        val current = inspectedUnit
        if (current != null &&
            combat.playerBoard.none { it.instanceId == current.instanceId } &&
            combat.enemyBoard.none { it.instanceId == current.instanceId }
        ) {
            inspectedUnit = null
        }

        val allChamps = (combat.playerBoard + combat.enemyBoard).filter { it.template.isChampion }
        if (!champTrackingInitialized) {
            allChamps.forEach {
                knownChampionLevels[it.instanceId] = it.isLeveledUp
                if (it.isLeveledUp) {
                    shownLevelUpTemplates["${it.ownerIsPlayer}_${it.template.id}"] = true
                }
            }
            champTrackingInitialized = true
        } else {
            val newlyLeveled = allChamps.firstOrNull { u ->
                val champKey = "${u.ownerIsPlayer}_${u.template.id}"
                u.isLeveledUp &&
                    knownChampionLevels[u.instanceId] != true &&
                    shownLevelUpTemplates[champKey] != true
            }
            if (newlyLeveled != null) {
                shownLevelUpTemplates["${newlyLeveled.ownerIsPlayer}_${newlyLeveled.template.id}"] = true
                activeLevelUpBanner = newlyLeveled
            }
            allChamps.forEach { knownChampionLevels[it.instanceId] = it.isLeveledUp }
        }
    }

    // Track Nexus HP deltas for hit reaction
    var prevPlayerHp by remember { mutableIntStateOf(combat.playerNexusHp) }
    var playerNexusDelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(combat.playerNexusHp) {
        if (combat.playerNexusHp < prevPlayerHp) {
            playerNexusDelta = prevPlayerHp - combat.playerNexusHp
            prevPlayerHp = combat.playerNexusHp
            kotlinx.coroutines.delay(650)
            playerNexusDelta = 0
        } else {
            prevPlayerHp = combat.playerNexusHp
        }
    }

    var prevEnemyHp by remember { mutableIntStateOf(combat.enemyNexusHp) }
    var enemyNexusDelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(combat.enemyNexusHp) {
        if (combat.enemyNexusHp < prevEnemyHp) {
            enemyNexusDelta = prevEnemyHp - combat.enemyNexusHp
            prevEnemyHp = combat.enemyNexusHp
            kotlinx.coroutines.delay(650)
            enemyNexusDelta = 0
        } else {
            prevEnemyHp = combat.enemyNexusHp
        }
    }

    // Drop target bounds in root coordinates
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var playerBenchBounds by remember { mutableStateOf(Rect.Zero) }
    var sharedBattlefieldBounds by remember { mutableStateOf(Rect.Zero) }
    val enemyAttackerBounds = remember { mutableStateMapOf<String, Rect>() }
    val enemyUnitBounds = remember { mutableStateMapOf<String, Rect>() }
    val playerUnitBounds = remember { mutableStateMapOf<String, Rect>() }
    var enemyNexusBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(combat.attackingUnitIds) {
        val activeSet = combat.attackingUnitIds.toSet()
        enemyAttackerBounds.keys.retainAll(activeSet)
    }

    BackHandler {
        when {
            activeDrag != null -> activeDrag = null
            pendingTargetCard != null -> {
                pendingTargetCard = null
                pendingFirstUnitId = null
            }
            combat.selectedAttackerForChallenger != null -> onSelectChallengerAttacker(null)
            showEmotePicker -> showEmotePicker = false
            showGraveyardDialog -> showGraveyardDialog = false
            showMatchInfoDialog -> showMatchInfoDialog = false
            inspectedHandCard != null -> inspectedHandCard = null
            isHandExpanded -> isHandExpanded = false
            inspectedUnit != null -> inspectedUnit = null
            showPowerInfo -> showPowerInfo = false
            else -> showForfeitConfirm = true
        }
    }

    val latestCombat by rememberUpdatedState(combat)

    val triggerCardPlayOrTargeting: (HandCardInstance, Offset?) -> Unit = { card, pointer ->
        val c = latestCombat
        val enemyTargets = c.enemyBoard.filter { it.template.type != CardType.LANDMARK }
        val allyTargets = c.playerBoard.filter { it.template.type != CardType.LANDMARK }
        val hitEnemyId = pointer?.let { ptr ->
            enemyUnitBounds.entries.firstOrNull { (id, rect) ->
                enemyTargets.any { it.instanceId == id } && rect.inflate(24f).contains(ptr)
            }?.key
        }
        val hitAllyId = pointer?.let { ptr ->
            playerUnitBounds.entries.firstOrNull { (id, rect) ->
                allyTargets.any { it.instanceId == id } && rect.inflate(24f).contains(ptr)
            }?.key
        }
        val hitEnemyNexus = pointer != null && enemyNexusBounds.inflate(28f).contains(pointer)

        when {
            card.template.type == CardType.EQUIPMENT || card.template.id == "card_brightsteel_protector" -> {
                when {
                    hitAllyId != null -> onPlayTargetedCard(card.instanceId, hitAllyId)
                    allyTargets.isEmpty() -> {
                        if (card.template.type == CardType.EQUIPMENT) {
                            dragWarningText = "Cần có đồng minh trên bàn để trang bị!"
                        } else {
                            onPlayTargetedCard(card.instanceId, null)
                        }
                    }
                    else -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                card.template.effectType == CardEffectType.DRAIN -> {
                when {
                    hitEnemyNexus -> onPlayTargetedCard(card.instanceId, "ENEMY_NEXUS")
                    hitEnemyId != null -> onPlayTargetedCard(card.instanceId, hitEnemyId)
                    hitAllyId != null -> onPlayTargetedCard(card.instanceId, hitAllyId)
                    else -> {
                        // Luôn mở chế độ chọn mục tiêu để người chơi tự chọn quân địch hoặc Nhà Chính địch
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.STUN_TARGET ||
                card.template.effectType == CardEffectType.FROSTBITE_TARGET ||
                card.template.effectType == CardEffectType.FROSTBITE_TWO ||
                card.template.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL ||
                card.template.effectType == CardEffectType.RECALL_OR_STUN -> {
                when {
                    enemyTargets.isEmpty() -> {
                        if (card.template.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL) {
                            onPlayTargetedCard(card.instanceId, null)
                        } else {
                            dragWarningText = "Không có kẻ địch hợp lệ để chọn mục tiêu!"
                        }
                    }
                    hitEnemyId != null -> onPlayTargetedCard(card.instanceId, hitEnemyId)
                    else -> {
                        // Bắt buộc người chơi bấm chọn mục tiêu kể cả khi chỉ có 1 kẻ địch
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.GIVE_BARRIER_AND_DRAW ||
                card.template.effectType == CardEffectType.RALLY_AND_SHIELD ||
                card.template.effectType == CardEffectType.FREE_ATTACK -> {
                val validAllies = if (card.template.effectType == CardEffectType.FREE_ATTACK) {
                    allyTargets.filter { it.canAttack }
                } else {
                    allyTargets
                }
                when {
                    validAllies.isEmpty() -> {
                        dragWarningText = "Không có đồng minh hợp lệ để chọn mục tiêu!"
                    }
                    hitAllyId != null && validAllies.any { it.instanceId == hitAllyId } ->
                        onPlayTargetedCard(card.instanceId, hitAllyId)
                    else -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE -> {
                when {
                    allyTargets.isEmpty() || enemyTargets.isEmpty() -> {
                        dragWarningText = "Cần ít nhất 1 đồng minh và 1 kẻ địch để Đấu Tay Đôi!"
                    }
                    hitAllyId != null -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = hitAllyId
                    }
                    else -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            else -> onPlayTargetedCard(card.instanceId, null)
        }
    }

    val handleHandCardDrop: (HandCardInstance, Offset) -> Unit = { card, pointer ->
        val c = latestCombat
        val isUnitLike = card.template.type == CardType.UNIT ||
            card.template.type == CardType.CHAMPION ||
            card.template.type == CardType.LANDMARK
        val isInCombat = c.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            c.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val onTargetRect = enemyUnitBounds.values.any { it.inflate(24f).contains(pointer) } ||
            playerUnitBounds.values.any { it.inflate(24f).contains(pointer) } ||
            enemyNexusBounds.inflate(28f).contains(pointer)
        val validZone = if (isUnitLike) {
            playerBenchBounds.inflate(40f).contains(pointer) ||
                sharedBattlefieldBounds.inflate(24f).contains(pointer)
        } else {
            sharedBattlefieldBounds.inflate(40f).contains(pointer) || onTargetRect
        }
        if (!c.canPlayCard(card)) {
            dragWarningText = when {
                c.priorityHolder != PriorityHolder.PLAYER -> "Chờ lượt của đối thủ"
                isUnitLike && c.playerBoard.size >= 6 -> "Hàng chờ đã đầy (6/6)"
                card.template.spellSpeed == SpellSpeed.FAST && isInCombat && c.playerFastSpellUsedInCombat ->
                    "Mỗi đợt giao tranh chỉ dùng được 1 bài phép Nhanh!"
                card.template.spellSpeed == SpellSpeed.FOCUS && (isInCombat || c.spellStack.isNotEmpty()) ->
                    "Phép Tập Trung chỉ dùng ngoài giao tranh khi hàng chờ trống!"
                card.template.spellSpeed == SpellSpeed.SLOW && (isInCombat || c.spellStack.isNotEmpty()) ->
                    "Phép Chậm chỉ dùng ngoài giao tranh khi hàng chờ trống!"
                else -> "Không thể ra lá ${card.template.name}"
            }
        } else if (validZone) {
            triggerCardPlayOrTargeting(card, pointer)
            inspectedHandCard = null
            isHandExpanded = false
        } else {
            dragWarningText = if (isUnitLike) "Kéo lên bàn đấu để triệu hồi"
            else "Kéo vào sân băng hoặc mục tiêu để dùng phép"
        }
        activeDrag = null
    }

    val blockRejectionMessage: (UnitOnBoard, UnitOnBoard) -> String = { blocker, attacker ->
        when {
            attacker.keywords.contains(Keyword.FEARSOME) && blocker.effectivePower < 3 ->
                "Địch có Đáng Sợ — cần quân thủ có ít nhất 3 Công!"
            attacker.keywords.contains(Keyword.ELUSIVE) &&
                !blocker.keywords.contains(Keyword.ELUSIVE) &&
                !blocker.keywords.contains(Keyword.SHARPSIGHT) ->
                "Địch có Lẩn Tránh — cần quân có Lẩn Tránh hoặc Tinh Mắt!"
            attacker.keywords.contains(Keyword.BRASH) && blocker.health < 3 ->
                "Địch có Hung Hãn — cần quân thủ có ít nhất 3 Máu!"
            else -> "${blocker.template.name} không thể chặn ${attacker.template.name}"
        }
    }

    val findClosestHitAttackerId: (CombatState, Offset) -> String? = { c, pointer ->
        val activeIds = c.attackingUnitIds.toSet()
        enemyAttackerBounds.entries
            .filter { (id, rect) -> activeIds.contains(id) && rect.inflate(32f).contains(pointer) }
            .minByOrNull { (_, rect) ->
                val dx = pointer.x - rect.center.x
                val dy = pointer.y - rect.center.y
                dx * dx + dy * dy
            }?.key
    }

    val handleBenchUnitDrop: (UnitOnBoard, Offset) -> Unit = { unit, pointer ->
        val c = latestCombat
        val inField = sharedBattlefieldBounds.inflate(28f).contains(pointer)
        if (c.priorityHolder != PriorityHolder.PLAYER) {
            dragWarningText = "Hãy chờ tới lượt của bạn"
        } else if (c.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && !c.playerInitiatedCombat) {
            if (c.blockCommitted) {
                dragWarningText = "Đã chốt đội hình chặn"
            } else if (!unit.canBlock) {
                dragWarningText = "${unit.template.name} không thể chặn"
            } else {
                val directHitId = findClosestHitAttackerId(c, pointer)
                val targetAttackerId = directHitId ?: if (inField) {
                    c.selectedEnemyAttackerForBlock
                        ?: c.attackingUnitIds.firstOrNull { atkId ->
                            val atk = c.enemyBoard.find { it.instanceId == atkId }
                            !c.lockedBlockAttackerIds.contains(atkId) &&
                                !c.blockPairings.containsKey(atkId) &&
                                atk != null &&
                                unit.canBlockAttacker(atk)
                        }
                } else null
                val targetEnemy = targetAttackerId?.let { id -> c.enemyBoard.find { it.instanceId == id } }
                when {
                    targetEnemy == null -> if (inField) {
                        dragWarningText = "Kéo vào làn quân địch tấn công để chặn"
                    }
                    c.lockedBlockAttackerIds.contains(targetEnemy.instanceId) -> {
                        dragWarningText = "Kẻ địch này đã khóa chặn (Thách Đấu)"
                    }
                    !unit.canBlockAttacker(targetEnemy) -> {
                        dragWarningText = blockRejectionMessage(unit, targetEnemy)
                    }
                    else -> {
                        onAssignBlocker(targetEnemy.instanceId, unit.instanceId)
                    }
                }
            }
        } else if (((c.phase == CombatPhase.MAIN_ACTION_PHASE && c.playerHasAttackToken && c.spellStack.isEmpty()) ||
                (c.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && c.playerHasAttackToken && c.playerInitiatedCombat && c.spellStack.isEmpty()))
        ) {
            if (!unit.canAttack) {
                dragWarningText = "${unit.template.name} không thể tấn công"
            } else if (inField) {
                onDragBenchUnitToAttack(unit.instanceId)
            } else {
                dragWarningText = "Kéo vào giữa sân để tấn công"
            }
        } else {
            dragWarningText = if (!c.playerHasAttackToken) "Chờ Thẻ Tấn Công của bạn" else "Không thể di chuyển bài quân lúc này"
        }
        activeDrag = null
    }

    val handleCombatUnitDrop: (UnitOnBoard, Offset) -> Unit = { unit, pointer ->
        val c = latestCombat
        val backToBench = playerBenchBounds.inflate(36f).contains(pointer)
        if (c.priorityHolder == PriorityHolder.PLAYER) {
            when (c.phase) {
                CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> {
                    if (backToBench && c.playerHasAttackToken && c.spellStack.isEmpty()) {
                        onRemoveAttacker(unit.instanceId)
                    }
                }
                CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                    if (!c.playerInitiatedCombat && !c.blockCommitted) {
                        if (backToBench) {
                            val attackerId = c.blockPairings.entries.firstOrNull { it.value == unit.instanceId }?.key
                            if (attackerId != null) onRemoveBlocker(attackerId)
                        } else {
                            val directHitId = findClosestHitAttackerId(c, pointer)
                            val targetEnemy = directHitId?.let { id -> c.enemyBoard.find { it.instanceId == id } }
                            if (targetEnemy != null) {
                                when {
                                    c.lockedBlockAttackerIds.contains(targetEnemy.instanceId) -> {
                                        dragWarningText = "Kẻ địch này đã khóa chặn (Thách Đấu)"
                                    }
                                    !unit.canBlockAttacker(targetEnemy) -> {
                                        dragWarningText = blockRejectionMessage(unit, targetEnemy)
                                    }
                                    else -> {
                                        onAssignBlocker(targetEnemy.instanceId, unit.instanceId)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        activeDrag = null
    }

    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val safeStart = safeInsets.calculateLeftPadding(layoutDirection)
    val safeEnd = safeInsets.calculateRightPadding(layoutDirection)
    val safeTop = safeInsets.calculateTopPadding()
    val safeBottom = safeInsets.calculateBottomPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .clipToBounds()
    ) {
        val vw = { percent: Float -> maxWidth * (percent / 100f) }
        val vh = { percent: Float -> maxHeight * (percent / 100f) }

        val descFontSize = (maxHeight.value * 0.022f).coerceIn(10f, 13.5f).sp
        val statFontSize = (maxHeight.value * 0.028f).coerceIn(12f, 17f).sp

        // Board token sizing (landscape compact portrait tokens)
        val unitTileWidth = minOf(vw(9.4f), vh(18.5f) * 0.80f).coerceAtLeast(48.dp)
        val unitTileHeight = minOf(vh(19.5f), unitTileWidth * 1.24f).coerceAtLeast(52.dp)
        val unitGap = vw(1.0f)

        // Hand card sizing for bottom-right overlapping curved fan
        val handCardHeight = vh(22.5f).coerceIn(72.dp, 128.dp)
        val handCardWidth = handCardHeight * 0.68f

        val championName = remember(combat.championId) {
            CardCatalog.getChampion(combat.championId).name
        }

        val activePendingCard = pendingTargetCard
        val highlightedTargetEnemyIds: Set<String> = when {
            activePendingCard == null -> emptySet()
            activePendingCard.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                activePendingCard.template.effectType == CardEffectType.DRAIN ||
                activePendingCard.template.effectType == CardEffectType.STUN_TARGET ||
                activePendingCard.template.effectType == CardEffectType.FROSTBITE_TARGET ||
                activePendingCard.template.effectType == CardEffectType.FROSTBITE_TWO ||
                activePendingCard.template.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL ||
                activePendingCard.template.effectType == CardEffectType.RECALL_OR_STUN ->
                combat.enemyBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE && pendingFirstUnitId != null ->
                combat.enemyBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            else -> emptySet()
        }
        val highlightedTargetAllyIds: Set<String> = when {
            activePendingCard == null -> emptySet()
            activePendingCard.template.type == CardType.EQUIPMENT ||
                activePendingCard.template.id == "card_brightsteel_protector" ||
                activePendingCard.template.effectType == CardEffectType.GIVE_BARRIER_AND_DRAW ||
                activePendingCard.template.effectType == CardEffectType.RALLY_AND_SHIELD ->
                combat.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            activePendingCard.template.effectType == CardEffectType.FREE_ATTACK ->
                combat.playerBoard.filter { it.template.type != CardType.LANDMARK && it.canAttack }.map { it.instanceId }.toSet()
            activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE && pendingFirstUnitId == null ->
                combat.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            else -> emptySet()
        }

        val onEnemyUnitClick: (UnitOnBoard) -> Unit = { unit ->
            inspectedHandCard = null
            if (activePendingCard != null && highlightedTargetEnemyIds.contains(unit.instanceId)) {
                if (activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE) {
                    val allyId = pendingFirstUnitId
                    if (allyId != null) {
                        onPlayTargetedCard(activePendingCard.instanceId, "$allyId|${unit.instanceId}")
                        pendingTargetCard = null
                        pendingFirstUnitId = null
                    }
                } else {
                    onPlayTargetedCard(activePendingCard.instanceId, unit.instanceId)
                    pendingTargetCard = null
                    pendingFirstUnitId = null
                }
            } else if (combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                combat.selectedAttackerForChallenger != null
            ) {
                val activeChallengerId = combat.selectedAttackerForChallenger
                val activeChallengerUnit = combat.playerBoard.find { it.instanceId == activeChallengerId }
                val canPullTarget = activeChallengerUnit != null &&
                    (activeChallengerUnit.keywords.contains(Keyword.CHALLENGER) || unit.keywords.contains(Keyword.VULNERABLE))
                if (unit.template.type != CardType.LANDMARK && !unit.isStunned && !unit.isShackled && canPullTarget) {
                    onChallengeEnemyUnit(activeChallengerId, unit.instanceId)
                } else {
                    dragWarningText = "Không thể Thách Đấu mục tiêu này"
                }
            } else if (combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                combat.challengedPairings.containsValue(unit.instanceId)
            ) {
                val pairedAtk = combat.challengedPairings.entries.firstOrNull { it.value == unit.instanceId }?.key
                if (pairedAtk != null) onClearChallenge(pairedAtk)
            } else if (combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                !combat.playerInitiatedCombat &&
                !combat.blockCommitted &&
                combat.attackingUnitIds.contains(unit.instanceId)
            ) {
                if (combat.lockedBlockAttackerIds.contains(unit.instanceId)) {
                    dragWarningText = "Đơn vị bị Thách Đấu bắt buộc phải chặn"
                } else if (combat.blockPairings.containsKey(unit.instanceId)) {
                    onRemoveBlocker(unit.instanceId)
                } else {
                    onSelectEnemyAttackerForBlock(unit.instanceId)
                }
            } else {
                inspectedUnit = unit
            }
        }

        val onPlayerUnitClick: (UnitOnBoard) -> Unit = { unit ->
            inspectedHandCard = null
            if (activePendingCard != null && highlightedTargetAllyIds.contains(unit.instanceId)) {
                if (activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE) {
                    pendingFirstUnitId = unit.instanceId
                } else {
                    onPlayTargetedCard(activePendingCard.instanceId, unit.instanceId)
                    pendingTargetCard = null
                    pendingFirstUnitId = null
                }
            } else if (combat.priorityHolder == PriorityHolder.PLAYER) {
                when (combat.phase) {
                    CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> {
                        if (combat.playerHasAttackToken && combat.playerInitiatedCombat && combat.spellStack.isEmpty()) {
                            val isAttacking = combat.attackingUnitIds.contains(unit.instanceId)
                            val hasVulnerableTarget = combat.enemyBoard.any {
                                it.keywords.contains(Keyword.VULNERABLE) &&
                                    it.template.type != CardType.LANDMARK &&
                                    !it.isStunned &&
                                    !it.isShackled
                            }
                            val canChallenge = (unit.keywords.contains(Keyword.CHALLENGER) &&
                                combat.enemyBoard.any { it.template.type != CardType.LANDMARK && !it.isStunned && !it.isShackled }) ||
                                hasVulnerableTarget
                            when {
                                isAttacking && combat.challengedPairings.containsKey(unit.instanceId) ->
                                    onClearChallenge(unit.instanceId)
                                isAttacking && canChallenge && combat.selectedAttackerForChallenger != unit.instanceId ->
                                    onSelectChallengerAttacker(unit.instanceId)
                                else -> onToggleAttacker(unit.instanceId)
                            }
                        } else {
                            inspectedUnit = unit
                        }
                    }
                    CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                        if (!combat.playerInitiatedCombat && !combat.blockCommitted) {
                            val targetEnemy = combat.selectedEnemyAttackerForBlock
                                ?: combat.attackingUnitIds.firstOrNull { atkId ->
                                    val atk = combat.enemyBoard.find { it.instanceId == atkId }
                                    !combat.lockedBlockAttackerIds.contains(atkId) &&
                                        !combat.blockPairings.containsKey(atkId) &&
                                        atk != null &&
                                        unit.canBlockAttacker(atk)
                                }
                            if (targetEnemy != null && !combat.lockedBlockAttackerIds.contains(targetEnemy)) {
                                onAssignBlocker(targetEnemy, unit.instanceId)
                            } else {
                                inspectedUnit = unit
                            }
                        } else {
                            inspectedUnit = unit
                        }
                    }
                    else -> inspectedUnit = unit
                }
            } else {
                inspectedUnit = unit
            }
        }

        val currentDrag = activeDrag
        val playerBenchHighlight = when (currentDrag) {
            is ActiveDragState.HandCardDrag -> {
                val c = currentDrag.card
                val isUnit = c.template.type == CardType.UNIT || c.template.type == CardType.CHAMPION || c.template.type == CardType.LANDMARK
                if (isUnit) {
                    if (combat.canPlayCard(c)) BattleBoardDropHighlight.VALID else BattleBoardDropHighlight.INVALID
                } else BattleBoardDropHighlight.NONE
            }
            is ActiveDragState.CombatUnitDrag -> BattleBoardDropHighlight.VALID
            else -> BattleBoardDropHighlight.NONE
        }
        val battlefieldHighlight = when (currentDrag) {
            is ActiveDragState.HandCardDrag -> {
                val c = currentDrag.card
                val isSpellOrGear = c.template.type == CardType.SPELL || c.template.type == CardType.EQUIPMENT || c.template.type == CardType.CURSE
                if (isSpellOrGear) {
                    if (combat.canPlayCard(c)) BattleBoardDropHighlight.VALID else BattleBoardDropHighlight.INVALID
                } else BattleBoardDropHighlight.NONE
            }
            is ActiveDragState.BenchUnitDrag -> {
                val canMoveToField = combat.priorityHolder == PriorityHolder.PLAYER && when (combat.phase) {
                    CombatPhase.MAIN_ACTION_PHASE ->
                        combat.playerHasAttackToken && combat.spellStack.isEmpty() && currentDrag.unit.canAttack
                    CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ->
                        combat.playerHasAttackToken && combat.playerInitiatedCombat && combat.spellStack.isEmpty() && currentDrag.unit.canAttack
                    CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ->
                        !combat.playerInitiatedCombat && !combat.blockCommitted && currentDrag.unit.canBlock
                    else -> false
                }
                if (canMoveToField) BattleBoardDropHighlight.VALID else BattleBoardDropHighlight.INVALID
            }
            else -> BattleBoardDropHighlight.NONE
        }

        // LAYER 1: Full-Bleed Freljord Frosted Ice & Carved Stone Battlefield + Glowing Metallic-Gold Centerline
        FreljordBattlefieldBackground(modifier = Modifier.fillMaxSize())

        // LAYER 2: Central Seamless BattleBoard (AI Bench on top rim, Ice Combat Arena centered on centerline, Player Bench on bottom rim)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = safeStart + vw(16f),
                    end = safeEnd + vw(16f),
                    top = safeTop + vh(7.2f),
                    bottom = safeBottom + vh(2.5f)
                )
        ) {
            BattleBoard(
                combat = combat,
                modifier = Modifier.fillMaxSize(),
                playerBenchHighlight = playerBenchHighlight,
                battlefieldHighlight = battlefieldHighlight,
                draggingBenchUnitId = (currentDrag as? ActiveDragState.BenchUnitDrag)?.unit?.instanceId,
                draggingCombatUnitId = (currentDrag as? ActiveDragState.CombatUnitDrag)?.unit?.instanceId,
                isDraggingBenchBlocker = currentDrag is ActiveDragState.BenchUnitDrag &&
                    combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    !combat.playerInitiatedCombat,
                draggedBenchUnit = (currentDrag as? ActiveDragState.BenchUnitDrag)?.unit,
                draggedCombatUnit = (currentDrag as? ActiveDragState.CombatUnitDrag)?.unit,
                tileWidthOverride = unitTileWidth,
                tileHeightOverride = unitTileHeight,
                unitGapOverride = unitGap,
                statFontSizeOverride = statFontSize,
                descFontSizeOverride = descFontSize,
                highlightedTargetEnemyIds = highlightedTargetEnemyIds,
                highlightedTargetAllyIds = highlightedTargetAllyIds,
                onPlayerBenchBoundsChanged = { playerBenchBounds = it },
                onSharedBattlefieldBoundsChanged = { sharedBattlefieldBounds = it },
                onEnemyAttackerBoundsChanged = { id, rect -> enemyAttackerBounds[id] = rect },
                onEnemyBenchUnitBoundsChanged = { id, rect -> enemyUnitBounds[id] = rect },
                onPlayerUnitBoundsChanged = { id, rect -> playerUnitBounds[id] = rect },
                onPlayerUnitClick = onPlayerUnitClick,
                onEnemyUnitClick = onEnemyUnitClick,
                onUnitLongClick = { inspectedUnit = it },
                onBenchUnitDragStart = { unit, ptr ->
                    inspectedHandCard = null
                    activeDrag = ActiveDragState.BenchUnitDrag(unit, ptr)
                },
                onBenchUnitDrag = { unit, ptr -> activeDrag = ActiveDragState.BenchUnitDrag(unit, ptr) },
                onBenchUnitDragEnd = { unit, ptr -> handleBenchUnitDrop(unit, ptr) },
                onCombatUnitDragStart = { unit, ptr -> activeDrag = ActiveDragState.CombatUnitDrag(unit, ptr) },
                onCombatUnitDrag = { unit, ptr -> activeDrag = ActiveDragState.CombatUnitDrag(unit, ptr) },
                onCombatUnitDragEnd = { unit, ptr -> handleCombatUnitDrop(unit, ptr) },
                onResolveStack = onResolveStack
            )
        }

        // LAYER 3: Top-Left Opponent Nameplate + Hand Count + Match Info
        val enemyPriorityBorder by animateColorAsState(
            targetValue = if (combat.priorityHolder == PriorityHolder.ENEMY) SemanticDanger else BorderSubtle,
            animationSpec = tween(durationMillis = 150),
            label = "enemy_hud_border"
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = safeStart + GameSpacing.sm, top = safeTop + GameSpacing.sm)
                .zIndex(20f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            Surface(
                modifier = Modifier
                    .testTag("enemy_hand_count")
                    .clickable(onClick = { showPowerInfo = true }),
                shape = RoundedCornerShape(GameRadius.control),
                color = SurfaceOverlay,
                border = BorderStroke(1.dp, enemyPriorityBorder),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    Text(
                        text = combat.encounterNode.enemyName,
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (descFontSize.value * 0.96f).coerceIn(10f, 13.5f).sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = vw(13f))
                    )
                    Surface(
                        shape = RoundedCornerShape(GameRadius.badge),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "Bài: ${combat.enemyHandCount}",
                            style = statNumericStyle(
                                fontSize = (descFontSize.value * 0.84f).coerceAtLeast(9f).sp,
                                color = AccentIce
                            ),
                            modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 2.dp)
                        )
                    }
                }
            }

            // Match Info 48.dp accessible touch target with crisp outlined badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("match_info_button")
                    .clickable { showMatchInfoDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = SurfaceOverlay,
                    border = BorderStroke(1.dp, BorderGoldMuted),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Thông tin trận đấu",
                            tint = AccentIce,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // LAYER 3B: Top-Center Opponent Fanned Hand Card Backs + Enemy Deck Pile
        if (combat.enemyHandCount > 0 || combat.enemyDrawPool.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = safeTop + GameSpacing.xs)
                    .zIndex(20f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                if (combat.enemyHandCount > 0) {
                    EnemyFannedCardBacks(
                        count = minOf(combat.enemyHandCount, 6),
                        cardWidth = vh(5.0f).coerceIn(18.dp, 28.dp),
                        cardHeight = vh(6.8f).coerceIn(24.dp, 36.dp)
                    )
                }
                if (combat.enemyDrawPool.isNotEmpty()) {
                    StackedDeckPile(
                        cardCount = combat.enemyDrawPool.size,
                        deckWidth = vh(4.6f).coerceIn(18.dp, 26.dp),
                        deckHeight = vh(6.4f).coerceIn(24.dp, 34.dp),
                        fontSize = (descFontSize.value * 0.74f).coerceAtLeast(8f).sp
                    )
                }
            }
        }

        // LAYER 4: Top-Right Compact Tactical Status Pill (Power, Foe Mana, Round, Forfeit)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = safeEnd + GameSpacing.sm, top = safeTop + GameSpacing.sm)
                .zIndex(20f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            Surface(
                shape = RoundedCornerShape(GameRadius.card),
                color = SurfaceOverlay,
                border = BorderStroke(1.dp, BorderGoldMuted),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    if (combat.encounterNode.enemyPowerTitle.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(GameRadius.badge))
                                .clickable { showPowerInfo = true }
                                .padding(horizontal = GameSpacing.xs, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = "Sức mạnh địch",
                                tint = SemanticAttack,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = combat.encounterNode.enemyPowerTitle,
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = (descFontSize.value * 0.88f).coerceAtLeast(9.5f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = vw(16f))
                            )
                        }
                    }

                    Text(
                        text = "Địch ${combat.enemyMana}/${combat.enemyMaxMana}",
                        style = statNumericStyle(
                            fontSize = (descFontSize.value * 0.88f).coerceAtLeast(9.5f).sp,
                            color = TextSecondary
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(GameRadius.badge),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderGoldMuted)
                    ) {
                        Text(
                            text = "V${combat.round}",
                            style = statNumericStyle(
                                fontSize = (descFontSize.value * 0.86f).coerceAtLeast(9.5f).sp,
                                color = AccentGold
                            ),
                            modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 2.dp)
                        )
                    }
                }
            }

            // Compact Forfeit / Surrender 48.dp touch target
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { showForfeitConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = SurfaceOverlay,
                    border = BorderStroke(1.dp, SemanticDanger.copy(alpha = 0.65f)),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "Đầu hàng",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // LAYER 5: Built-in Arena Nexuses (Top-Left Red Enemy Nexus & Bottom-Left Blue Player Nexus) + Centerline Scroll Log Button
        ArenaBoardNexusOverlay(
            enemyHp = combat.enemyNexusHp,
            enemyMaxHp = combat.enemyMaxHp,
            enemyHitDelta = enemyNexusDelta,
            playerHp = combat.playerNexusHp,
            playerMaxHp = combat.playerMaxHp,
            playerHitDelta = playerNexusDelta,
            onEnemyNexusBoundsChanged = { enemyNexusBounds = it },
            onEnemyNexusClick = {
                val pending = pendingTargetCard
                if (pending != null &&
                    (pending.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                        pending.template.effectType == CardEffectType.DRAIN)
                ) {
                    onPlayTargetedCard(pending.instanceId, "ENEMY_NEXUS")
                    pendingTargetCard = null
                    pendingFirstUnitId = null
                } else {
                    showPowerInfo = true
                }
            },
            onPlayerNexusClick = { showMatchInfoDialog = true },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(21f)
        )

        // Square Carved-Gold Scroll Log Button directly on left end of golden centerline
        CenterlineScrollButton(
            size = vh(10.5f).coerceIn(38.dp, 48.dp),
            onClick = { showLogDialog = true },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = safeStart + vw(1.2f))
                .zIndex(22f)
        )

        // LAYER 6: Bottom-Left Player Nameplate + PoC Powers + Emote + Stacked Deck Pile + Graveyard
        val playerTurnBorder by animateColorAsState(
            targetValue = if (combat.priorityHolder == PriorityHolder.PLAYER) AccentGold else BorderSubtle,
            animationSpec = tween(durationMillis = 150),
            label = "player_turn_hud_border"
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = safeStart + GameSpacing.sm, bottom = safeBottom + GameSpacing.sm)
                .zIndex(20f),
            verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            AnimatedVisibility(
                visible = activeEmote != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(200))
            ) {
                activeEmote?.let { emote ->
                    Surface(
                        shape = RoundedCornerShape(GameRadius.control),
                        color = SurfaceOverlay,
                        border = BorderStroke(1.dp, BorderGoldMuted)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                        ) {
                            Icon(emote.icon, contentDescription = emote.label, tint = AccentGold, modifier = Modifier.size(13.dp))
                            Text(
                                text = emote.label,
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = (descFontSize.value * 0.88f).coerceAtLeast(9f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                Surface(
                    shape = RoundedCornerShape(GameRadius.control),
                    color = SurfaceOverlay,
                    border = BorderStroke(1.dp, playerTurnBorder),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                    ) {
                        Text(
                            text = championName,
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = (descFontSize.value * 0.95f).coerceIn(10f, 13.5f).sp,
                            color = AccentGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (combat.activePowers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(GameRadius.badge))
                                    .clickable { showMatchInfoDialog = true }
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                combat.activePowers.take(3).forEach { _ ->
                                    Icon(
                                        imageVector = Icons.Outlined.Bolt,
                                        contentDescription = "Sức mạnh đang kích hoạt",
                                        tint = AccentIce,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("emote_button")
                                .clickable { showEmotePicker = !showEmotePicker },
                            shape = CircleShape,
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, BorderGoldMuted)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.EmojiEmotions,
                                    contentDescription = "Biểu cảm",
                                    tint = AccentGold,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                StackedDeckPile(
                    cardCount = combat.playerDrawPile.size,
                    deckWidth = vh(7.2f).coerceIn(26.dp, 38.dp),
                    deckHeight = vh(10.0f).coerceIn(36.dp, 52.dp),
                    fontSize = (descFontSize.value * 0.92f).coerceAtLeast(10f).sp
                )

                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("graveyard_button")
                        .clickable { showGraveyardDialog = true },
                    shape = RoundedCornerShape(GameRadius.control),
                    color = SurfaceOverlay,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = "Nghĩa địa",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${combat.playerGraveyard.size + combat.enemyGraveyard.size}",
                            style = statNumericStyle(
                                fontSize = (descFontSize.value * 0.85f).coerceAtLeast(9.5f).sp,
                                color = TextPrimary
                            )
                        )
                    }
                }
            }
        }

        // LAYER 7: Right-Edge Clean Obsidian Mana & Turn Action Column (above hand layer)
        val dialHeight = vh(49f).coerceIn(148.dp, 240.dp)
        val dialWidth = (dialHeight * 0.96f).coerceIn(152.dp, 196.dp)

        TacticalManaActionColumn(
            combat = combat,
            panelWidth = dialWidth,
            onEnterAttack = onEnterAttack,
            onCancelAttack = onCancelAttack,
            onCommitAttack = onCommitAttack,
            onCommitBlock = onCommitBlock,
            onPassTurn = onPassTurn,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = safeEnd + GameSpacing.xs)
                .zIndex(38f)
        )

        // LAYER 7B: Center-Screen Turn & Combat Phase Banner (Sentence case, non-touch-blocking Box)
        var centerBannerLabel by remember { mutableStateOf<String?>(null) }
        var centerBannerSubtitle by remember { mutableStateOf<String?>(null) }
        var centerBannerIsPlayer by remember { mutableStateOf(true) }
        var centerBannerSeq by remember { mutableIntStateOf(0) }

        LaunchedEffect(
            combat.round,
            combat.phase,
            combat.priorityHolder,
            combat.playerInitiatedCombat,
            combat.blockCommitted
        ) {
            val bannerSpec: Triple<String, String?, Boolean>? = when {
                combat.phase == CombatPhase.GAME_OVER -> null
                combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && combat.playerInitiatedCombat ->
                    Triple("Tấn công", "Chọn quân xuất kích", true)
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    !combat.playerInitiatedCombat &&
                    combat.priorityHolder == PriorityHolder.PLAYER &&
                    !combat.blockCommitted ->
                    Triple("Phòng thủ", "Xếp quân chặn đòn địch", false)
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    combat.playerInitiatedCombat &&
                    combat.priorityHolder == PriorityHolder.ENEMY ->
                    Triple("Tấn công", "Đối thủ đang chọn quân chặn", true)
                combat.phase == CombatPhase.MAIN_ACTION_PHASE && combat.priorityHolder == PriorityHolder.PLAYER ->
                    Triple("Tới lượt bạn", if (combat.playerHasAttackToken) "Bạn đang giữ kiếm tấn công" else null, true)
                combat.phase == CombatPhase.MAIN_ACTION_PHASE && combat.priorityHolder == PriorityHolder.ENEMY ->
                    Triple("Tới lượt đối thủ", null, false)
                else -> null
            }
            if (bannerSpec != null && bannerSpec.first != centerBannerLabel) {
                centerBannerLabel = bannerSpec.first
                centerBannerSubtitle = bannerSpec.second
                centerBannerIsPlayer = bannerSpec.third
                centerBannerSeq++
            }
        }

        LaunchedEffect(centerBannerSeq) {
            if (centerBannerLabel != null) {
                kotlinx.coroutines.delay(1350L)
                centerBannerLabel = null
            }
        }

        AnimatedVisibility(
            visible = centerBannerLabel != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(72f)
        ) {
            val accent = if (centerBannerIsPlayer) AccentGold else SemanticDanger
            Box(
                modifier = Modifier
                    .testTag("center_turn_phase_banner")
                    .clip(RoundedCornerShape(GameRadius.card))
                    .background(SurfaceOverlay)
                    .border(1.dp, accent, RoundedCornerShape(GameRadius.card))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = GameSpacing.xl, vertical = GameSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                ) {
                    Text(
                        text = centerBannerLabel.orEmpty(),
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (centerBannerIsPlayer) AccentGold else TextPrimary
                    )
                    centerBannerSubtitle?.let { sub ->
                        Text(
                            text = sub,
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (centerBannerIsPlayer) AccentIce else SemanticDanger
                        )
                    }
                }
            }
        }

        // LAYER 8: Two-State Player Hand Overlay (Collapsed BottomEnd stack <-> Expanded BottomCenter fan)
        val isPlayerTurnForHand = combat.priorityHolder == PriorityHolder.PLAYER && combat.phase != CombatPhase.GAME_OVER
        PlayerHandOverlay(
            cards = combat.playerHand,
            cardKey = { it.instanceId },
            isExpanded = isHandExpanded,
            onExpandedChange = { expanded ->
                isHandExpanded = expanded
                if (!expanded) {
                    inspectedHandCard = null
                }
            },
            canDragCards = isPlayerTurnForHand,
            draggingCardKey = (currentDrag as? ActiveDragState.HandCardDrag)?.card?.instanceId,
            rightCollapsedMargin = maxOf(16.dp, dialWidth * 0.18f),
            bottomCollapsedMargin = 6.dp,
            onCardDragStart = { card, ptr ->
                inspectedHandCard = null
                activeDrag = ActiveDragState.HandCardDrag(card, ptr)
            },
            onCardDrag = { card, ptr -> activeDrag = ActiveDragState.HandCardDrag(card, ptr) },
            onCardDragEnd = { card, ptr -> handleHandCardDrop(card, ptr) },
            modifier = Modifier
                .fillMaxSize()
                .padding(start = safeStart, end = safeEnd, bottom = safeBottom)
                .zIndex(if (isHandExpanded) 42f else 25f)
        ) { handCard, expanded, isDragging, cardW, cardH ->
            HandCardView(
                handCard = handCard,
                effectiveCost = combat.effectivePlayCost(handCard),
                playable = combat.canPlayCard(handCard),
                isFocused = expanded && inspectedHandCard?.instanceId == handCard.instanceId,
                cardWidth = cardW,
                cardHeight = cardH,
                descFontSize = descFontSize,
                statFontSize = statFontSize,
                fanAngle = 0f,
                fanLiftY = 0.dp,
                isDragging = isDragging,
                enablePressElevation = expanded,
                testTag = "hand_card_${handCard.template.id}",
                onClick = {
                    if (!expanded) {
                        inspectedUnit = null
                        isHandExpanded = true
                    } else {
                        inspectedUnit = null
                        inspectHandDetailOnly = combat.priorityHolder != PriorityHolder.PLAYER
                        inspectedHandCard = if (inspectedHandCard?.instanceId == handCard.instanceId) null else handCard
                    }
                },
                onLongClick = {
                    if (!expanded) {
                        inspectedUnit = null
                        isHandExpanded = true
                    } else {
                        inspectedUnit = null
                        inspectHandDetailOnly = true
                        inspectedHandCard = handCard
                    }
                }
            )
        }

        // Targeting / Challenger Selection Prompt Banner
        val activeChallenger = combat.selectedAttackerForChallenger?.let { cid ->
            combat.playerBoard.find { it.instanceId == cid }
        }
        val promptMessage = when {
            activePendingCard != null &&
                activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE &&
                pendingFirstUnitId == null ->
                "Chọn đồng minh đánh cặp cho ${activePendingCard.template.name}"
            activePendingCard != null &&
                activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE ->
                "Chọn kẻ địch đối đầu cho ${activePendingCard.template.name}"
            activePendingCard != null && (activePendingCard.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                activePendingCard.template.effectType == CardEffectType.DRAIN) ->
                "Chọn quân địch hoặc Nhà Chính địch cho ${activePendingCard.template.name}"
            activePendingCard != null ->
                "Chọn mục tiêu cho ${activePendingCard.template.name}"
            activeChallenger != null ->
                if (activeChallenger.keywords.contains(Keyword.CHALLENGER)) {
                    "Chọn quân địch để ${activeChallenger.template.name} kéo vào Thách Đấu"
                } else {
                    "Chọn quân địch Dễ Bị Tổn Thương để ${activeChallenger.template.name} kéo"
                }
            else -> null
        }

        AnimatedVisibility(
            visible = promptMessage != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = safeTop + vh(8.5f))
                .zIndex(75f)
        ) {
            Surface(
                modifier = Modifier.testTag("targeting_prompt_banner"),
                shape = RoundedCornerShape(GameRadius.control),
                color = SurfaceOverlay,
                border = BorderStroke(1.dp, AccentIce),
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    Text(
                        text = promptMessage ?: "",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = descFontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (activePendingCard?.template?.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                        activePendingCard?.template?.effectType == CardEffectType.DRAIN
                    ) {
                        Button(
                            onClick = {
                                onPlayTargetedCard(activePendingCard.instanceId, "ENEMY_NEXUS")
                                pendingTargetCard = null
                                pendingFirstUnitId = null
                            },
                            modifier = Modifier
                                .height(36.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("target_enemy_nexus_button"),
                            shape = RoundedCornerShape(GameRadius.control),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SemanticDanger,
                                contentColor = TextPrimary
                            )
                        ) {
                            Text(
                                "Nhà Chính địch",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = (descFontSize.value * 0.9f).sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (activePendingCard != null) {
                                pendingTargetCard = null
                                pendingFirstUnitId = null
                            } else {
                                onSelectChallengerAttacker(null)
                            }
                        },
                        modifier = Modifier
                            .height(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_target_button"),
                        shape = RoundedCornerShape(GameRadius.control),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceElevated,
                            contentColor = AccentGold
                        )
                    ) {
                        Text(
                            "Hủy chọn",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = (descFontSize.value * 0.9f).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Drag Warning Toast
        AnimatedVisibility(
            visible = dragWarningText != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = vh(24f))
                .zIndex(80f)
        ) {
            Surface(
                shape = RoundedCornerShape(GameRadius.control),
                color = SurfaceOverlay,
                border = BorderStroke(1.dp, SemanticDanger)
            ) {
                Text(
                    text = dragWarningText ?: "",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = descFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = SemanticDanger,
                    modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.xs)
                )
            }
        }

        // Floating Drag Preview Overlay
        if (currentDrag != null) {
            val localOffset = currentDrag.pointerInRoot - rootOrigin
            when (currentDrag) {
                is ActiveDragState.HandCardDrag -> {
                    val card = currentDrag.card
                    Box(
                        modifier = Modifier
                            .zIndex(95f)
                            .offset {
                                IntOffset(
                                    (localOffset.x - handCardWidth.toPx() / 2f).roundToInt(),
                                    (localOffset.y - handCardHeight.toPx() * 0.7f).roundToInt()
                                )
                            }
                    ) {
                        HandCardView(
                            handCard = card,
                            effectiveCost = combat.effectivePlayCost(card),
                            playable = combat.canPlayCard(card),
                            isFocused = true,
                            cardWidth = handCardWidth,
                            cardHeight = handCardHeight,
                            descFontSize = descFontSize,
                            statFontSize = statFontSize,
                            onClick = {}
                        )
                    }
                }
                is ActiveDragState.BenchUnitDrag -> {
                    val unit = currentDrag.unit
                    Box(
                        modifier = Modifier
                            .zIndex(95f)
                            .offset {
                                IntOffset(
                                    (localOffset.x - unitTileWidth.toPx() / 2f).roundToInt(),
                                    (localOffset.y - unitTileHeight.toPx() * 0.65f).roundToInt()
                                )
                            }
                    ) {
                        UnitToken(
                            unit = unit,
                            isSelected = true,
                            tileWidth = unitTileWidth * 1.08f,
                            tileHeight = unitTileHeight * 1.08f,
                            statFontSize = statFontSize,
                            onClick = {}
                        )
                    }
                }
                is ActiveDragState.CombatUnitDrag -> {
                    val unit = currentDrag.unit
                    Box(
                        modifier = Modifier
                            .zIndex(95f)
                            .offset {
                                IntOffset(
                                    (localOffset.x - unitTileWidth.toPx() / 2f).roundToInt(),
                                    (localOffset.y - unitTileHeight.toPx() * 0.65f).roundToInt()
                                )
                            }
                    ) {
                        UnitToken(
                            unit = unit,
                            isSelected = true,
                            tileWidth = unitTileWidth * 1.05f,
                            tileHeight = unitTileHeight * 1.05f,
                            statFontSize = statFontSize,
                            onClick = {}
                        )
                    }
                }
            }
        }

        // Tactile 6.5% Film Grain Overlay across the battlefield & cards
        FilmGrainOverlay(
            alpha = 0.065f,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(50f)
        )

        // Hand Card Inspect / Quick-Play Overlay
        inspectedHandCard?.let { card ->
            HandCardInspectOverlay(
                card = card,
                canPlay = combat.canPlayCard(card),
                effectiveCost = combat.effectivePlayCost(card),
                detailOnly = inspectHandDetailOnly,
                minWidth = vw(28f),
                maxWidth = vw(40f),
                buttonHeight = maxOf(48.dp, vh(7f)),
                offsetY = -vh(6f),
                padH = vw(1.2f),
                padV = vh(1f),
                descFontSize = descFontSize,
                onPlay = {
                    triggerCardPlayOrTargeting(card, null)
                    inspectedHandCard = null
                    isHandExpanded = false
                },
                onDismiss = { inspectedHandCard = null }
            )
        }

        // Board Unit Inspect Side Popup
        inspectedUnit?.let { unit ->
            UnitInspectOverlay(
                unit = unit,
                sidePadding = vw(16f),
                minWidth = vw(26f),
                maxWidth = vw(36f),
                padH = vw(1.2f),
                padV = vh(0.8f),
                descFontSize = descFontSize,
                onDismiss = { inspectedUnit = null }
            )
        }

        // Emote Picker Overlay
        if (showEmotePicker) {
            EmotePickerOverlay(
                safeStartPadding = safeStart + vw(3f),
                bottomPadding = safeBottom + vh(11f),
                descFontSize = descFontSize,
                onSelectEmote = { emote ->
                    activeEmote = emote
                    showEmotePicker = false
                },
                onDismiss = { showEmotePicker = false }
            )
        }

        // Champion Level-Up (Lv2) Showcase Overlay Animation
        activeLevelUpBanner?.let { leveledChamp ->
            ChampionLevelUpBannerOverlay(
                unit = leveledChamp,
                onDismiss = { activeLevelUpBanner = null }
            )
        }
    }

    if (showPowerInfo) {
        EnemyPowerDialog(combat = combat, onDismiss = { showPowerInfo = false })
    }
    if (showLogDialog) {
        CombatLogDialog(combat = combat, onDismiss = { showLogDialog = false })
    }
    if (showGraveyardDialog) {
        GraveyardDialog(combat = combat, onDismiss = { showGraveyardDialog = false })
    }
    if (showMatchInfoDialog) {
        MatchInfoDialog(combat = combat, onDismiss = { showMatchInfoDialog = false })
    }
    if (showForfeitConfirm) {
        ForfeitConfirmDialog(
            onConfirmForfeit = {
                showForfeitConfirm = false
                onAbandonRun()
            },
            onDismiss = { showForfeitConfirm = false }
        )
    }
    CombatGameOverDialog(
        combat = combat,
        revivesRemaining = revivesRemaining,
        onVictoryContinue = onVictoryContinue,
        onUseRevive = onUseRevive,
        onAcceptDefeat = onAcceptDefeat
    )
}

@Composable
private fun EnemyFannedCardBacks(
    count: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-cardWidth * 0.45f)),
        verticalAlignment = Alignment.Top
    ) {
        val mid = (count - 1) / 2f
        repeat(count) { idx ->
            val angle = ((idx - mid) * 4.5f).coerceIn(-12f, 12f)
            Box(
                modifier = Modifier
                    .size(width = cardWidth, height = cardHeight)
                    .offset(y = (abs(idx - mid) * 1.5f).dp)
                    .graphicsLayer { rotationZ = angle }
                    .clip(RoundedCornerShape(GameRadius.badge))
                    .background(SurfaceBase)
                    .border(1.dp, BorderGoldMuted, RoundedCornerShape(GameRadius.badge)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(GameSpacing.xs)) {
                    if (size.width <= 1f || size.height <= 1f) return@Canvas
                    drawCircle(
                        color = AccentGold.copy(alpha = 0.55f),
                        radius = size.minDimension * 0.25f,
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
        }
    }
}
