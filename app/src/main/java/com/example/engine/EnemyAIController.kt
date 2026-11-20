package com.example.engine

import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.Keyword
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard

/**
 * Section 9.4 EnemyAIController:
 * Executes one AI decision step whenever priorityHolder == PriorityHolder.ENEMY.
 * Handles Case A (SPELL_STACK_PHASE), Case B (MAIN_ACTION_PHASE), and Case C (COMBAT_DECLARE_BLOCK_PHASE).
 */
object EnemyAIController {

    fun executeTurn(state: CombatState): CombatState {
        if (state.phase == CombatPhase.GAME_OVER || state.priorityHolder != PriorityHolder.ENEMY) {
            return state
        }
        val logs = state.combatLog.toMutableList()

        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && state.playerInitiatedCombat && !state.blockCommitted) {
            return handleBlockDeclaration(state, logs)
        }

        if (state.spellStack.isNotEmpty()) {
            return handleSpellStackResponse(state, logs)
        }

        return when (state.phase) {
            CombatPhase.SPELL_STACK_PHASE -> handleSpellStackResponse(state, logs)
            CombatPhase.MAIN_ACTION_PHASE -> handleMainActionPhase(state, logs)
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                if (state.playerInitiatedCombat) {
                    if (state.blockCommitted) {
                        CombatEngine.finishPlayerAttackCombat(state)
                    } else {
                        handleBlockDeclaration(state, logs)
                    }
                } else if (state.blockCommitted) {
                    val reactiveSpell = pickReactiveCombatSpell(state, playerIsAttacking = false)
                    if (reactiveSpell != null) {
                        castEnemySpell(state, reactiveSpell.first, logs, targetUnitId = reactiveSpell.second)
                    } else if (state.enemyCastBurstInBlock) {
                        logs.add("Địch chuyển quyền phản hồi sau phép Siêu Tốc.")
                        state.copy(
                            enemyCastBurstInBlock = false,
                            priorityHolder = PriorityHolder.PLAYER,
                            consecutivePasses = 0,
                            combatLog = logs.takeLast(16)
                        )
                    } else {
                        CombatEngine.finishEnemyAttackCombat(state)
                    }
                } else {
                    state.copy(priorityHolder = PriorityHolder.PLAYER)
                }
            }
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            CombatPhase.COMBAT_RESOLUTION_PHASE,
            CombatPhase.GAME_OVER -> state
        }
    }

    // Case A: Responding to SPELL_STACK_PHASE or combat stack
    private fun handleSpellStackResponse(state: CombatState, logs: MutableList<String>): CombatState {
        val totalMana = state.enemyMana + state.enemySpellMana
        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val responseSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                it.cost <= totalMana &&
                (it.spellSpeed == SpellSpeed.BURST ||
                    (it.spellSpeed == SpellSpeed.FAST && (!isInCombat || !state.enemyFastSpellUsedInCombat)))
        }

        val playerEntries = state.spellStack.filter { it.sourceIsPlayer }
        val playerStackThreatDmg = playerEntries.sumOf { it.card.effectValue + it.damageBonus }

        val targetedEnemyIds = playerEntries.mapNotNull { it.targetUnitId }.toSet()
        val threatenedEnemy = state.enemyBoard.firstOrNull { targetedEnemyIds.contains(it.instanceId) }
            ?: if (playerStackThreatDmg > 0) {
                state.enemyBoard
                    .filter { e -> state.blockPairings.values.contains(e.instanceId) || e.health <= playerStackThreatDmg }
                    .minByOrNull { it.health }
                    ?: state.enemyBoard.minByOrNull { it.health }
            } else null

        var chosenTargetId: String? = null
        val reactiveSpell = responseSpells.firstOrNull { spell ->
            when (spell.effectType) {
                CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                    val candidate = threatenedEnemy?.takeIf { !it.hasBarrier && !it.hasSpellShield && playerStackThreatDmg > 0 }
                    if (candidate != null) {
                        chosenTargetId = candidate.instanceId
                        true
                    } else false
                }
                CardEffectType.STUN_TARGET,
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO -> {
                    val candidate = state.playerBoard
                        .filter { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                        .maxByOrNull { if (state.attackingUnitIds.contains(it.instanceId)) it.effectivePower + 100 else it.effectivePower }
                    if (candidate != null) {
                        chosenTargetId = candidate.instanceId
                        true
                    } else false
                }
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DRAIN -> {
                    val killable = state.playerBoard
                        .filter { !it.hasBarrier && !it.hasSpellShield && it.health <= spell.effectValue }
                        .maxByOrNull { it.effectivePower }
                    if (killable != null) {
                        chosenTargetId = killable.instanceId
                        true
                    } else if (state.playerNexusHp <= spell.effectValue) {
                        chosenTargetId = "PLAYER_NEXUS"
                        true
                    } else false
                }
                CardEffectType.SINGLE_COMBAT_STRIKE -> {
                    val pair = state.enemyBoard.firstNotNullOfOrNull { e ->
                        state.playerBoard.firstOrNull { p ->
                            !p.hasSpellShield && e.strikePower >= p.health && e.health > p.strikePower
                        }?.let { p -> e to p }
                    }
                    if (pair != null) {
                        chosenTargetId = "${pair.first.instanceId}|${pair.second.instanceId}"
                        true
                    } else false
                }
                else -> false
            }
        }

        if (reactiveSpell != null) {
            return castEnemySpell(state, reactiveSpell, logs, targetUnitId = chosenTargetId)
        }

        // Địch bỏ qua để thực thi hàng chờ phép LIFO
        logs.add("Địch bỏ qua — đang thực thi hàng chờ phép.")
        return CombatEngine.resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
    }

    // Case B: Acting in MAIN_ACTION_PHASE
    private fun handleMainActionPhase(state: CombatState, logs: MutableList<String>): CombatState {
        // 1. Play Units / Champions / Landmarks (highest cost playable <= enemyMana while board < 6)
        if (state.enemyBoard.size < 6) {
            val playableUnits = state.enemyHand.filter {
                (it.type == CardType.UNIT || it.type == CardType.CHAMPION || it.type == CardType.LANDMARK) &&
                    it.cost <= state.enemyMana
            }
            val bestUnit = playableUnits.sortedWith(
                compareByDescending<CardTemplate> { it.isChampion }
                    .thenByDescending { it.cost }
                    .thenByDescending { it.keywords.size }
            ).firstOrNull()

            if (bestUnit != null) {
                val summoned = CombatEngine.summonEnemyUnitFromPool(state, bestUnit, bestUnit.cost, logs)
                return CombatEngine.checkGameOver(
                    summoned.copy(
                        consecutivePasses = 0,
                        priorityHolder = PriorityHolder.PLAYER,
                        combatLog = logs.takeLast(16)
                    )
                )
            }
        }

        // 2. Cast Proactive Spells (Slow, Focus, Burst, or Fast with valid targets)
        val totalMana = state.enemyMana + state.enemySpellMana
        val proactiveSpell = state.enemyHand.filter {
            it.type == CardType.SPELL && it.cost <= totalMana && isSpellWorthCasting(state, it)
        }.maxByOrNull { it.cost }

        if (proactiveSpell != null) {
            return castEnemySpell(state, proactiveSpell, logs)
        }

        // 3. Declare Attack if AI holds Attack Token and has profitable attackers
        if (state.enemyHasAttackToken) {
            val eligibleAttackers = state.enemyBoard.filter { it.canAttack }
            val chosenAttackers = eligibleAttackers.filter { atk ->
                isProfitableAiAttacker(atk, state.playerBoard)
            }.take(6)

            if (chosenAttackers.isNotEmpty()) {
                val chosenIds = chosenAttackers.map { it.instanceId }
                var s = state

                // 1. Hallowed leftmost buff (round-scoped, runs before Support)
                if (s.enemyHallowedStacks > 0) {
                    val firstId = chosenIds.first()
                    CombatEngine.recordRoundHallowedBuff(s.encounterNode.id, s.round, firstId, s.enemyHallowedStacks)
                    s = s.copy(enemyBoard = s.enemyBoard.map {
                        if (it.instanceId == firstId) it.copy(power = it.power + s.enemyHallowedStacks) else it
                    })
                    logs.add("Thiêng Liêng ban cho ${chosenAttackers.first().template.name} +${s.enemyHallowedStacks}|+0!")
                }

                // 2. AI Lurk (once per round if a Lurker attacks and top of enemyDrawPool has Lurk)
                val hasLurkAttacker = s.enemyBoard.any { chosenIds.contains(it.instanceId) && !it.isSilenced && it.keywords.contains(Keyword.LURK) }
                val topEnemyIsLurk = s.enemyDrawPool.firstOrNull()?.keywords?.contains(Keyword.LURK) == true
                if (!s.enemyLurkTriggeredThisRound && hasLurkAttacker && topEnemyIsLurk) {
                    s = s.copy(
                        enemyLurkTriggeredThisRound = true,
                        enemyLurkBonus = s.enemyLurkBonus + 1,
                        enemyBoard = s.enemyBoard.map {
                            if (!it.isSilenced && it.keywords.contains(Keyword.LURK)) it.copy(power = it.power + 1) else it
                        }
                    )
                    logs.add("Ẩn Nấp của địch kích hoạt! Quân Ẩn Nấp nhận +1|+0.")
                }

                // 3. Support left-to-right (after Hallowed & Lurk)
                s = CombatEngine.applyAttackSupportBuffs(s, chosenIds, playerIsAttacking = false, logs = logs)

                if (CombatEngine.canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "READY_RANKS") {
                    s = s.copy(enemyBoard = s.enemyBoard.map {
                        if (chosenIds.contains(it.instanceId)) it.copy(power = it.power + 1) else it
                    })
                    logs.add("Sẵn Sàng Đội Hình ban cho quân tấn công của địch +1|+0!")
                }
                // Áp dụng Sức Mạnh Chòm Sao khi kẻ địch tấn công (Anivia 6★ chỉ đóng băng kẻ địch mạnh nhất)
                s = CombatEngine.applyEnemyAttackConstellationPowers(s, chosenIds, logs)

                // 4. AI Challenger & Vulnerable forced blocks
                val aiChallenges = mutableMapOf<String, String>()
                val availablePlayerTargets = s.playerBoard
                    .filter { it.template.type != CardType.LANDMARK && !it.isStunned && !it.isShackled }
                    .toMutableList()
                for (atkId in chosenIds) {
                    val atk = s.enemyBoard.find { it.instanceId == atkId } ?: continue
                    val target = if (!atk.isSilenced && atk.keywords.contains(Keyword.CHALLENGER)) {
                        availablePlayerTargets
                            .filter { atk.strikePower >= it.health && (atk.health > it.strikePower || it.template.isChampion || it.effectivePower >= 3) }
                            .maxByOrNull { it.effectivePower + if (it.template.isChampion) 3 else 0 }
                            ?: availablePlayerTargets
                                .filter { it.template.isChampion && atk.strikePower >= it.health }
                                .maxByOrNull { it.effectivePower }
                    } else {
                        availablePlayerTargets
                            .filter { it.keywords.contains(Keyword.VULNERABLE) && atk.strikePower >= it.health }
                            .maxByOrNull { it.effectivePower + if (it.template.isChampion) 3 else 0 }
                    }
                    if (target != null) {
                        aiChallenges[atkId] = target.instanceId
                        availablePlayerTargets.remove(target)
                        logs.add("${atk.template.name} kéo ${target.template.name} vào chặn!")
                    }
                }

                logs.add("Địch tấn công với ${chosenIds.size} quân! Hãy chọn quân chặn.")
                var attackState = s.copy(
                    enemyHasAttackToken = false,
                    playerInitiatedCombat = false,
                    blockCommitted = false,
                    playerCastSpellInBlock = false,
                    enemyCastBurstInBlock = false,
                    playerFastSpellUsedInCombat = false,
                    enemyFastSpellUsedInCombat = false,
                    phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
                    priorityHolder = PriorityHolder.PLAYER,
                    consecutivePasses = 0,
                    attackingUnitIds = chosenIds,
                    blockPairings = aiChallenges,
                    confirmedBlockedAttackerIds = aiChallenges.keys.toSet(),
                    challengedPairings = emptyMap(),
                    lockedBlockAttackerIds = aiChallenges.keys.toSet(),
                    selectedAttackerForChallenger = null,
                    selectedEnemyAttackerForBlock = null,
                    combatLog = logs.takeLast(16)
                )
                // Optionally queue up to 1 Fast combat spell alongside the attack declaration
                val attackFastSpell = pickReactiveCombatSpell(attackState, playerIsAttacking = false, fastOnly = true)
                if (attackFastSpell != null) {
                    attackState = castEnemySpell(attackState, attackFastSpell.first, logs, targetUnitId = attackFastSpell.second)
                }
                return attackState
            }
        }

        // 4. Pass Priority
        val nextPasses = state.consecutivePasses + 1
        if (nextPasses >= 2) {
            logs.add("Địch bỏ qua. Kết thúc Vòng ${state.round}.")
            return CombatEngine.advanceToNextRound(state, logs)
        }
        logs.add("Địch đã chuyển lượt.")
        return state.copy(
            consecutivePasses = nextPasses,
            priorityHolder = PriorityHolder.PLAYER,
            combatLog = logs.takeLast(16)
        )
    }

    // Case C: Defending in COMBAT_DECLARE_BLOCK_PHASE (Player declared Attackers)
    private fun handleBlockDeclaration(state: CombatState, logs: MutableList<String>): CombatState {
        val attackers = state.attackingUnitIds.mapNotNull { id ->
            state.playerBoard.find { it.instanceId == id }
        }
        // Preserve Challenger forced blocks already locked in state.blockPairings
        val pairings = state.blockPairings.toMutableMap()
        val lockedBlockerIds = pairings.values.toSet()
        val availableBlockers = state.enemyBoard
            .filter { it.canBlock && !lockedBlockerIds.contains(it.instanceId) }
            .toMutableList()

        // Estimate unblocked damage including Impact & Overwhelm threat
        val totalPotentialDamage = attackers.sumOf { it.strikePower + it.impactStacks }
        val isLethalThreat = totalPotentialDamage >= state.enemyNexusHp
        val lowHealthThreshold = state.enemyNexusHp <= 8

        val leveledAsheActive = state.playerBoard.any { it.template.id == "card_ashe" && it.isLeveledUp }

        for (atk in attackers) {
            if (pairings.containsKey(atk.instanceId)) continue
            val eligible = availableBlockers.filter { blk ->
                blk.canBlockAttacker(atk) && (!leveledAsheActive || blk.effectivePower > 0)
            }
            if (eligible.isEmpty()) continue

            val chosenBlocker = if (isLethalThreat) {
                eligible.maxByOrNull { blk ->
                    val absorbed = if (atk.keywords.contains(Keyword.OVERWHELM)) minOf(atk.strikePower, blk.health) else atk.strikePower
                    absorbed * 100 + blk.health
                }
            } else {
                val cleanWin = eligible.filter { blk ->
                    val atkHasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
                    val blkKills = blk.strikePower >= atk.health
                    val blkSurvives = if (atkHasFirstStrike && atk.strikePower >= blk.health && !blk.hasBarrier && !blk.hasDeathless) {
                        false
                    } else {
                        blk.hasBarrier || blk.hasDeathless || atk.strikePower < blk.health
                    }
                    blkKills && blkSurvives
                }.minByOrNull { it.template.cost }

                cleanWin ?: run {
                    val isHighThreat = atk.template.isChampion || atk.effectivePower >= 4 || atk.keywords.contains(Keyword.OVERWHELM)
                    val favorableOrEvenTrade = eligible.filter { blk ->
                        val blkKills = blk.strikePower >= atk.health
                        (blkKills && (atk.template.cost >= blk.template.cost || isHighThreat)) ||
                            (lowHealthThreshold && isHighThreat) ||
                            (blk.hasDeathless && isHighThreat)
                    }.minByOrNull { it.template.cost }

                    favorableOrEvenTrade ?: if (lowHealthThreshold) {
                        eligible.minByOrNull { it.effectivePower }
                    } else null
                }
            }

            if (chosenBlocker != null) {
                pairings[atk.instanceId] = chosenBlocker.instanceId
                availableBlockers.remove(chosenBlocker)
            }
        }

        val confirmedIds = pairings.keys.toSet()
        val blockedState = state.copy(
            blockPairings = pairings,
            confirmedBlockedAttackerIds = confirmedIds,
            blockCommitted = true,
            combatLog = logs.takeLast(16)
        )

        val reactiveSpell = pickReactiveCombatSpell(blockedState, playerIsAttacking = true)
        if (reactiveSpell != null) {
            logs.add("Địch đã gán ${pairings.size} quân chặn và dùng ${reactiveSpell.first.name}.")
            var afterSpell = castEnemySpell(blockedState, reactiveSpell.first, logs, targetUnitId = reactiveSpell.second)
            if (reactiveSpell.first.spellSpeed == SpellSpeed.BURST && !afterSpell.enemyFastSpellUsedInCombat) {
                val followUpFast = pickReactiveCombatSpell(afterSpell, playerIsAttacking = true, fastOnly = true)
                if (followUpFast != null) {
                    afterSpell = castEnemySpell(afterSpell, followUpFast.first, logs, targetUnitId = followUpFast.second)
                }
            }
            return afterSpell.copy(
                priorityHolder = PriorityHolder.PLAYER,
                blockCommitted = true,
                confirmedBlockedAttackerIds = confirmedIds,
                enemyCastBurstInBlock = false
            )
        }

        // Step 4: Always yield response window to Attacker (PLAYER) after block confirmation
        logs.add("Địch đã xác nhận ${pairings.size} quân chặn — đến lượt bạn phản hồi trước khi giao tranh.")
        return blockedState.copy(
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0,
            combatLog = logs.takeLast(16)
        )
    }

    private fun pickReactiveCombatSpell(
        state: CombatState,
        playerIsAttacking: Boolean,
        fastOnly: Boolean = false
    ): Pair<CardTemplate, String?>? {
        val totalMana = state.enemyMana + state.enemySpellMana
        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val combatSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                ((it.spellSpeed == SpellSpeed.FAST && (!isInCombat || !state.enemyFastSpellUsedInCombat)) ||
                    (!fastOnly && it.spellSpeed == SpellSpeed.BURST)) &&
                it.cost <= totalMana
        }
        for (spell in combatSpells) {
            when (spell.effectType) {
                CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                    val candidate = state.enemyBoard
                        .filter { !it.hasBarrier && (state.blockPairings.values.contains(it.instanceId) || state.attackingUnitIds.contains(it.instanceId)) }
                        .maxByOrNull { it.effectivePower }
                        ?: state.enemyBoard.firstOrNull { !it.hasBarrier }
                    if (candidate != null) return spell to candidate.instanceId
                }
                CardEffectType.STUN_TARGET,
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO -> {
                    val targets = if (playerIsAttacking) {
                        state.attackingUnitIds.mapNotNull { id -> state.playerBoard.find { it.instanceId == id } }
                    } else {
                        state.playerBoard
                    }
                    val chosen = targets
                        .filter { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                        .maxByOrNull { it.effectivePower }
                    if (chosen != null) return spell to chosen.instanceId
                }
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DRAIN -> {
                    val killable = state.playerBoard
                        .filter { !it.hasBarrier && !it.hasSpellShield && it.health <= spell.effectValue }
                        .maxByOrNull { it.effectivePower }
                    if (killable != null) return spell to killable.instanceId
                    if (state.playerNexusHp <= spell.effectValue) return spell to "PLAYER_NEXUS"
                    if (!playerIsAttacking && state.blockCommitted) {
                        val barrierBlocker = state.blockPairings.values.firstOrNull { blkId ->
                            state.playerBoard.any { p -> p.instanceId == blkId && p.hasBarrier }
                        }
                        if (barrierBlocker != null) return spell to barrierBlocker
                    }
                }
                CardEffectType.SINGLE_COMBAT_STRIKE -> {
                    val pair = state.enemyBoard.firstNotNullOfOrNull { e ->
                        state.playerBoard.firstOrNull { p ->
                            !p.hasSpellShield && e.strikePower >= p.health && e.health > p.strikePower
                        }?.let { p -> e to p }
                    }
                    if (pair != null) return spell to "${pair.first.instanceId}|${pair.second.instanceId}"
                }
                else -> {}
            }
        }
        return null
    }

    private fun castEnemySpell(
        state: CombatState,
        spell: CardTemplate,
        logs: MutableList<String>,
        targetUnitId: String? = null
    ): CombatState {
        val spellUsed = minOf(maxOf(0, state.enemySpellMana), spell.cost)
        val unitUsed = spell.cost - spellUsed
        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val usedFastInCombat = spell.spellSpeed == SpellSpeed.FAST && isInCombat
        val castBurstInBlock = spell.spellSpeed == SpellSpeed.BURST &&
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE

        var s = state.copy(
            enemySpellMana = maxOf(0, state.enemySpellMana - spellUsed),
            enemyMana = maxOf(0, state.enemyMana - unitUsed),
            enemyHand = CombatEngine.removeFirstTemplate(state.enemyHand, spell.id),
            enemyFastSpellUsedInCombat = state.enemyFastSpellUsedInCombat || usedFastInCombat,
            enemyCastBurstInBlock = state.enemyCastBurstInBlock || castBurstInBlock
        )
        if (CombatEngine.canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "ENLIGHTENED_HARMONY") {
            val healed = minOf(s.enemyMaxHp, s.enemyNexusHp + 1)
            if (healed > s.enemyNexusHp) {
                s = s.copy(enemyNexusHp = healed)
                logs.add("Hòa Hợp Khai Sáng hồi +1 Máu cho Nhà Chính địch.")
            }
        }

        return if (spell.spellSpeed == SpellSpeed.BURST || spell.spellSpeed == SpellSpeed.FOCUS) {
            s = CombatEngine.resolveSingleSpellEffect(
                state = s,
                card = spell,
                sourceIsPlayer = false,
                logs = logs,
                targetUnitId = targetUnitId
            )
            val nextPriority = if (s.isFreeAttack && s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
                s.priorityHolder
            } else {
                PriorityHolder.ENEMY
            }
            CombatEngine.checkGameOver(
                s.copy(
                    consecutivePasses = 0,
                    priorityHolder = nextPriority,
                    combatLog = logs.takeLast(16)
                )
            )
        } else {
            val entry = SpellStackEntry(
                id = "st_${CombatEngine.generateId()}",
                sourceIsPlayer = false,
                card = spell,
                targetUnitId = targetUnitId
            )
            val nextPhase = if (s.phase == CombatPhase.MAIN_ACTION_PHASE) CombatPhase.SPELL_STACK_PHASE else s.phase
            logs.add("Địch đặt ${spell.name} (${spell.spellSpeed.displayName}) lên hàng chờ phép.")
            s.copy(
                spellStack = s.spellStack + entry,
                phase = nextPhase,
                lastStackCaster = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                priorityHolder = PriorityHolder.PLAYER,
                combatLog = logs.takeLast(16)
            )
        }
    }

    private fun isSpellWorthCasting(state: CombatState, spell: CardTemplate): Boolean {
        return when (spell.effectType) {
            CardEffectType.DEAL_DAMAGE_ANY,
            CardEffectType.DRAIN ->
                state.playerBoard.any { it.health <= spell.effectValue } || state.playerNexusHp <= spell.effectValue
            CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS,
            CardEffectType.DEAL_DAMAGE_NEXUS -> true
            CardEffectType.STUN_TARGET,
            CardEffectType.FROSTBITE_TARGET -> state.playerBoard.isNotEmpty()
            CardEffectType.GIVE_BARRIER_AND_DRAW,
            CardEffectType.RALLY_AND_SHIELD -> state.enemyBoard.isNotEmpty()
            CardEffectType.RALLY -> !state.enemyHasAttackToken
            CardEffectType.FREE_ATTACK -> state.enemyBoard.any { it.canAttack }
            CardEffectType.SINGLE_COMBAT_STRIKE ->
                state.enemyBoard.any { e -> state.playerBoard.any { p -> e.strikePower >= p.health && e.health > p.strikePower } }
            CardEffectType.PREDICT_DRAW,
            CardEffectType.PLANT_PUFFCAPS -> true
            else -> false
        }
    }

    private fun isProfitableAiAttacker(atk: UnitOnBoard, playerBoard: List<UnitOnBoard>): Boolean {
        val eligibleBlockers = playerBoard.filter { it.canBlockAttacker(atk) }
        // Unblocked Nexus damage or empty player board
        if (eligibleBlockers.isEmpty()) return atk.strikePower > 0 || atk.impactStacks > 0

        // Quick Attack / Double Attack can safely attack if it kills weakest/strongest blockers without dying,
        // or unit survives biggest eligible blocker, or trades evenly
        val strongestBlocker = eligibleBlockers.maxByOrNull { it.strikePower } ?: return true
        val hasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
        if (hasFirstStrike && eligibleBlockers.all { atk.strikePower >= it.health }) return true
        if (atk.health > strongestBlocker.strikePower || atk.hasBarrier || atk.hasDeathless) return true
        return atk.strikePower >= strongestBlocker.health && atk.template.cost <= strongestBlocker.template.cost
    }
}
