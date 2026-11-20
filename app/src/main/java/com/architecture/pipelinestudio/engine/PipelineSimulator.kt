package com.architecture.pipelinestudio.engine

data class CellExecution(
    val cycle: Int,
    val stage: PipelineStage,
    val isStall: Boolean = false
)

data class InstructionSchedule(
    val instruction: Instruction,
    val stages: Map<Int, CellExecution> // Cycle -> CellExecution
)

data class SimulationResult(
    val model: PipelineModel,
    val hazardMode: HazardMode,
    val totalCycles: Int,
    val unpipelinedCycles: Int,
    val speedup: Double,
    val totalStalls: Int,
    val hazards: List<Hazard>,
    val schedules: List<InstructionSchedule>,
    val reorderedInstructions: List<Instruction>? = null,
    val cyclesSavedByReordering: Int = 0,
    val reorderingExplanationEn: String = "",
    val reorderingExplanationAr: String = ""
)

object PipelineSimulator {

    fun simulate(
        rawInstructions: List<Instruction>,
        model: PipelineModel,
        hazardMode: HazardMode
    ): SimulationResult {
        if (rawInstructions.isEmpty()) {
            return SimulationResult(
                model = model,
                hazardMode = hazardMode,
                totalCycles = 0,
                unpipelinedCycles = 0,
                speedup = 1.0,
                totalStalls = 0,
                hazards = emptyList(),
                schedules = emptyList()
            )
        }

        var activeInstructions = rawInstructions
        var reorderedList: List<Instruction>? = null
        var reorderCyclesSaved = 0
        var reorderNoteEn = ""
        var reorderNoteAr = ""

        if (hazardMode == HazardMode.REORDERING) {
            val reorderResult = ReorderingEngine.reorder(rawInstructions, model)
            activeInstructions = reorderResult.reorderedInstructions
            reorderedList = reorderResult.reorderedInstructions
            reorderCyclesSaved = reorderResult.cyclesSaved
            reorderNoteEn = reorderResult.swapExplanationEn
            reorderNoteAr = reorderResult.swapExplanationAr
        }

        return if (model == PipelineModel.FOUR_STAGE) {
            simulate4Stage(activeInstructions, model, hazardMode, reorderedList, reorderCyclesSaved, reorderNoteEn, reorderNoteAr)
        } else {
            simulate5Stage(activeInstructions, model, hazardMode, reorderedList, reorderCyclesSaved, reorderNoteEn, reorderNoteAr)
        }
    }

    /**
     * 4-Stage Model (Chapter 2 Lecture Slides): F -> D -> E -> WB
     * Register write occurs at WB (cycle 4).
     * Operands are needed for Execute (E, cycle 3).
     * With Forwarding: Result from E is forwarded to next instruction's E directly (0 stalls for ALU ops).
     * Without Forwarding: Stalls until WB finishes (1 or 2 stalls).
     */
    private fun simulate4Stage(
        instructions: List<Instruction>,
        model: PipelineModel,
        hazardMode: HazardMode,
        reorderedList: List<Instruction>?,
        reorderCyclesSaved: Int,
        reorderNoteEn: String,
        reorderNoteAr: String
    ): SimulationResult {
        val schedules = mutableListOf<InstructionSchedule>()
        val hazards = mutableListOf<Hazard>()

        var currentCycle = 1
        var totalStalls = 0

        // Maps instruction index -> cycle where its WB finished
        val writebackCycleMap = mutableMapOf<Int, Int>()
        // Maps instruction index -> cycle where its E finished (for forwarding)
        val executeCycleMap = mutableMapOf<Int, Int>()

        for (i in instructions.indices) {
            val inst = instructions[i]
            val stageMap = mutableMapOf<Int, CellExecution>()

            // Default fetch cycle is currentCycle (staggered by 1 cycle per instruction)
            var fetchCycle = if (i == 0) 1 else (schedules[i - 1].stages.values.first { it.stage == PipelineStage.FETCH_4 || it.stage == PipelineStage.STALL }.cycle + 1)

            // Check if prior instruction was a branch (Control Hazard / Rupture)
            if (i > 0 && instructions[i - 1].isBranch) {
                val branchPenalty = 2
                hazards.add(
                    Hazard(
                        type = HazardType.CONTROL_BRANCH,
                        sourceInstruction = instructions[i - 1],
                        targetInstruction = inst,
                        registerFamily = "IP/PC",
                        stallCycles = branchPenalty,
                        descriptionEn = "Control Hazard (Branch Rupture): Pipeline flush with 2 penalty cycles",
                        descriptionAr = "خطر تحكم (انقطاع تسلسل القفز): تفريغ الأنبوب ودورتي تأخير (2 cycles de pénalité)"
                    )
                )
                for (b in 0 until branchPenalty) {
                    stageMap[fetchCycle] = CellExecution(fetchCycle, PipelineStage.STALL, isStall = true)
                    fetchCycle++
                    totalStalls++
                }
            }

            // Fetch stage
            stageMap[fetchCycle] = CellExecution(fetchCycle, PipelineStage.FETCH_4)

            // Decode stage
            var decodeCycle = fetchCycle + 1

            // Check RAW Data Hazard on prior instructions
            var stallsNeeded = 0
            var conflictingFamily = ""
            var conflictingProducer: Instruction? = null

            for (p in 0 until i) {
                val producer = instructions[p]
                if (inst.hasRawHazardOn(producer)) {
                    val families = inst.getRawConflictingFamilies(producer)
                    conflictingFamily = RegisterAlias.familyDisplayName(families.first())
                    conflictingProducer = producer

                    if (hazardMode == HazardMode.FORWARDING) {
                        val producerExecCycle = executeCycleMap[p] ?: 0
                        val normalExecCycle = decodeCycle + 1
                        val diff = producerExecCycle - normalExecCycle + 1
                        if (diff > 0) {
                            stallsNeeded = maxOf(stallsNeeded, diff)
                        }
                    } else {
                        val producerWbCycle = writebackCycleMap[p] ?: 0
                        val normalExecCycle = decodeCycle + 1
                        val diff = producerWbCycle - normalExecCycle + 1
                        if (diff > 0) {
                            stallsNeeded = maxOf(stallsNeeded, diff)
                        }
                    }
                }
            }

            if (stallsNeeded > 0 && conflictingProducer != null) {
                val forwardingSaved = if (hazardMode == HazardMode.FORWARDING) 0 else 1
                hazards.add(
                    Hazard(
                        type = HazardType.RAW_DATA,
                        sourceInstruction = conflictingProducer,
                        targetInstruction = inst,
                        registerFamily = conflictingFamily,
                        stallCycles = stallsNeeded,
                        forwardingSaves = forwardingSaved,
                        descriptionEn = "RAW Data Hazard on register $conflictingFamily between I${conflictingProducer.id} and I${inst.id}",
                        descriptionAr = "خطر بيانات حقيقي (RAW) على السجل $conflictingFamily بين التعليمة I${conflictingProducer.id} و I${inst.id}"
                    )
                )

                // Insert Stalls (Bulles)
                for (s in 0 until stallsNeeded) {
                    stageMap[decodeCycle] = CellExecution(decodeCycle, PipelineStage.STALL, isStall = true)
                    decodeCycle++
                    totalStalls++
                }
            }

            // Check WAR and WAW dependencies (Informational / Reordering Constraints)
            for (p in 0 until i) {
                val earlier = instructions[p]
                if (inst.hasWarDependencyOn(earlier)) {
                    val families = inst.getWarConflictingFamilies(earlier)
                    val familyName = RegisterAlias.familyDisplayName(families.first())
                    hazards.add(
                        Hazard(
                            type = HazardType.WAR_DATA,
                            sourceInstruction = earlier,
                            targetInstruction = inst,
                            registerFamily = familyName,
                            stallCycles = 0,
                            descriptionEn = "WAR Anti-dependency on $familyName (I${earlier.id} reads, I${inst.id} writes). In-order pipeline is naturally immune (0 stalls), but restricts reordering.",
                            descriptionAr = "شبه تعارض (Anti-dépendance / WAR) على السجل $familyName: I${earlier.id} تقرأ و I${inst.id} تكتب. محمي طبيعياً في الأنبوب المتسلسل (بدون تجميد)، لكنه يقيد إعادة الترتيب."
                        )
                    )
                }
                if (inst.hasWawDependencyOn(earlier)) {
                    val families = inst.getWawConflictingFamilies(earlier)
                    val familyName = RegisterAlias.familyDisplayName(families.first())
                    hazards.add(
                        Hazard(
                            type = HazardType.WAW_DATA,
                            sourceInstruction = earlier,
                            targetInstruction = inst,
                            registerFamily = familyName,
                            stallCycles = 0,
                            descriptionEn = "WAW Output dependency on $familyName (both write). Write order preserved in-order at WB (0 stalls), but restricts reordering.",
                            descriptionAr = "تعارض ناتج (Dépendance de sortie / WAW) على السجل $familyName: كلاهما يكتب. ترتيب الكتابة محفوظ بالتسلسل في مرحلة WB (بدون تجميد)، لكنه يقيد إعادة الترتيب."
                        )
                    )
                }
            }

            stageMap[decodeCycle] = CellExecution(decodeCycle, PipelineStage.DECODE_4)

            // Execute stage
            val executeCycle = decodeCycle + 1
            stageMap[executeCycle] = CellExecution(executeCycle, PipelineStage.EXECUTE_4)
            executeCycleMap[i] = executeCycle

            // Write Back stage
            val writebackCycle = executeCycle + 1
            stageMap[writebackCycle] = CellExecution(writebackCycle, PipelineStage.WRITEBACK_4)
            writebackCycleMap[i] = writebackCycle

            schedules.add(InstructionSchedule(inst, stageMap))
            currentCycle = maxOf(currentCycle, writebackCycle)
        }

        val totalCycles = schedules.maxOfOrNull { it.stages.keys.maxOrNull() ?: 0 } ?: 0
        val unpipelined = instructions.size * model.depth
        val speedup = if (totalCycles > 0) unpipelined.toDouble() / totalCycles.toDouble() else 1.0

        return SimulationResult(
            model = model,
            hazardMode = hazardMode,
            totalCycles = totalCycles,
            unpipelinedCycles = unpipelined,
            speedup = Math.round(speedup * 100.0) / 100.0,
            totalStalls = totalStalls,
            hazards = hazards,
            schedules = schedules,
            reorderedInstructions = reorderedList,
            cyclesSavedByReordering = reorderCyclesSaved,
            reorderingExplanationEn = reorderNoteEn,
            reorderingExplanationAr = reorderNoteAr
        )
    }

    /**
     * 5-Stage Model (TD 4 – Exercice 6): IF -> ID -> EX -> MEM -> WB
     * Memory load (MOV RAX, [RBX]) produces value at end of MEM (cycle 4).
     * ALU instruction (ADD RAX, RCX) produces value at end of EX (cycle 3).
     * Operands consumed at start of EX (cycle 3).
     *
     * Without Forwarding: Data available after WB (cycle 5).
     * With Forwarding:
     * - ALU to ALU: Forward from EX to EX -> 0 stalls.
     * - Load to ALU (Load-Use): Forward from MEM to EX -> 1 stall (TD 4 Ex 6).
     */
    private fun simulate5Stage(
        instructions: List<Instruction>,
        model: PipelineModel,
        hazardMode: HazardMode,
        reorderedList: List<Instruction>?,
        reorderCyclesSaved: Int,
        reorderNoteEn: String,
        reorderNoteAr: String
    ): SimulationResult {
        val schedules = mutableListOf<InstructionSchedule>()
        val hazards = mutableListOf<Hazard>()

        var currentCycle = 1
        var totalStalls = 0

        // Maps instruction index -> cycle where its EX, MEM, WB completed
        val exCycleMap = mutableMapOf<Int, Int>()
        val memCycleMap = mutableMapOf<Int, Int>()
        val wbCycleMap = mutableMapOf<Int, Int>()

        for (i in instructions.indices) {
            val inst = instructions[i]
            val stageMap = mutableMapOf<Int, CellExecution>()

            var fetchCycle = if (i == 0) 1 else (schedules[i - 1].stages.values.first { it.stage == PipelineStage.FETCH_5 || it.stage == PipelineStage.STALL }.cycle + 1)

            // Branch Penalty in 5-stage is 3 cycles (per TD 4 Ex 6)
            if (i > 0 && instructions[i - 1].isBranch) {
                val branchPenalty = 3
                hazards.add(
                    Hazard(
                        type = HazardType.CONTROL_BRANCH,
                        sourceInstruction = instructions[i - 1],
                        targetInstruction = inst,
                        registerFamily = "RIP/EIP",
                        stallCycles = branchPenalty,
                        descriptionEn = "Branch penalty: 3 penalty cycles per TD 4 Exercise 6",
                        descriptionAr = "عقوبة تفرع القفز: 3 دورات تأخير حسب تمرين TD 4 رقم 6"
                    )
                )
                for (b in 0 until branchPenalty) {
                    stageMap[fetchCycle] = CellExecution(fetchCycle, PipelineStage.STALL, isStall = true)
                    fetchCycle++
                    totalStalls++
                }
            }

            // IF Stage
            stageMap[fetchCycle] = CellExecution(fetchCycle, PipelineStage.FETCH_5)

            // ID Stage
            var idCycle = fetchCycle + 1

            // Hazard check
            var stallsNeeded = 0
            var conflictingFamily = ""
            var conflictingProducer: Instruction? = null
            var wasLoadUse = false

            for (p in 0 until i) {
                val producer = instructions[p]
                if (inst.hasRawHazardOn(producer)) {
                    val families = inst.getRawConflictingFamilies(producer)
                    conflictingFamily = RegisterAlias.familyDisplayName(families.first())
                    conflictingProducer = producer
                    wasLoadUse = producer.isMemoryLoad

                    if (hazardMode == HazardMode.FORWARDING) {
                        if (producer.isMemoryLoad) {
                            val producerMemCycle = memCycleMap[p] ?: (idCycle + 2)
                            val targetExCycle = idCycle + 1
                            val diff = producerMemCycle - targetExCycle + 1
                            if (diff > 0) stallsNeeded = maxOf(stallsNeeded, diff)
                        } else {
                            val producerExCycle = exCycleMap[p] ?: idCycle
                            val targetExCycle = idCycle + 1
                            val diff = producerExCycle - targetExCycle + 1
                            if (diff > 0) stallsNeeded = maxOf(stallsNeeded, diff)
                        }
                    } else {
                        val producerWbCycle = wbCycleMap[p] ?: (idCycle + 3)
                        val targetExCycle = idCycle + 1
                        val diff = producerWbCycle - targetExCycle + 1
                        if (diff > 0) stallsNeeded = maxOf(stallsNeeded, diff)
                    }
                }
            }

            if (stallsNeeded > 0 && conflictingProducer != null) {
                val saves = if (hazardMode == HazardMode.FORWARDING) 0 else (if (wasLoadUse) 1 else 2)
                hazards.add(
                    Hazard(
                        type = HazardType.RAW_DATA,
                        sourceInstruction = conflictingProducer,
                        targetInstruction = inst,
                        registerFamily = conflictingFamily,
                        stallCycles = stallsNeeded,
                        forwardingSaves = saves,
                        descriptionEn = if (wasLoadUse) {
                            "Load-Use RAW Hazard on $conflictingFamily: $stallsNeeded stall(s) (TD4 Ex6)"
                        } else {
                            "ALU RAW Hazard on $conflictingFamily: $stallsNeeded stall(s)"
                        },
                        descriptionAr = if (wasLoadUse) {
                            "خطر Load-Use (RAW) على السجل $conflictingFamily: $stallsNeeded فقاعة (حسب تمرين TD4)"
                        } else {
                            "خطر بيانات (RAW) على السجل $conflictingFamily: $stallsNeeded فقاعة"
                        }
                    )
                )

                for (s in 0 until stallsNeeded) {
                    stageMap[idCycle] = CellExecution(idCycle, PipelineStage.STALL, isStall = true)
                    idCycle++
                    totalStalls++
                }
            }

            // Check WAR and WAW dependencies
            for (p in 0 until i) {
                val earlier = instructions[p]
                if (inst.hasWarDependencyOn(earlier)) {
                    val families = inst.getWarConflictingFamilies(earlier)
                    val familyName = RegisterAlias.familyDisplayName(families.first())
                    hazards.add(
                        Hazard(
                            type = HazardType.WAR_DATA,
                            sourceInstruction = earlier,
                            targetInstruction = inst,
                            registerFamily = familyName,
                            stallCycles = 0,
                            descriptionEn = "WAR Anti-dependency on $familyName (I${earlier.id} reads, I${inst.id} writes). In-order pipeline is naturally immune (0 stalls), but restricts reordering.",
                            descriptionAr = "شبه تعارض (Anti-dépendance / WAR) على السجل $familyName: I${earlier.id} تقرأ و I${inst.id} تكتب. محمي طبيعياً في الأنبوب المتسلسل (بدون تجميد)، لكنه يقيد إعادة الترتيب."
                        )
                    )
                }
                if (inst.hasWawDependencyOn(earlier)) {
                    val families = inst.getWawConflictingFamilies(earlier)
                    val familyName = RegisterAlias.familyDisplayName(families.first())
                    hazards.add(
                        Hazard(
                            type = HazardType.WAW_DATA,
                            sourceInstruction = earlier,
                            targetInstruction = inst,
                            registerFamily = familyName,
                            stallCycles = 0,
                            descriptionEn = "WAW Output dependency on $familyName (both write). Write order preserved in-order at WB (0 stalls), but restricts reordering.",
                            descriptionAr = "تعارض ناتج (Dépendance de sortie / WAW) على السجل $familyName: كلاهما يكتب. تسلسل الكتابة محفوظ في مرحلة WB (بدون تجميد)، لكنه يقيد إعادة الترتيب."
                        )
                    )
                }
            }

            stageMap[idCycle] = CellExecution(idCycle, PipelineStage.DECODE_5)

            // EX Stage
            val exCycle = idCycle + 1
            stageMap[exCycle] = CellExecution(exCycle, PipelineStage.EXECUTE_5)
            exCycleMap[i] = exCycle

            // MEM Stage
            val memCycle = exCycle + 1
            stageMap[memCycle] = CellExecution(memCycle, PipelineStage.MEMORY_5)
            memCycleMap[i] = memCycle

            // WB Stage
            val wbCycle = memCycle + 1
            stageMap[wbCycle] = CellExecution(wbCycle, PipelineStage.WRITEBACK_5)
            wbCycleMap[i] = wbCycle

            schedules.add(InstructionSchedule(inst, stageMap))
            currentCycle = maxOf(currentCycle, wbCycle)
        }

        val totalCycles = schedules.maxOfOrNull { it.stages.keys.maxOrNull() ?: 0 } ?: 0
        val unpipelined = instructions.size * model.depth
        val speedup = if (totalCycles > 0) unpipelined.toDouble() / totalCycles.toDouble() else 1.0

        return SimulationResult(
            model = model,
            hazardMode = hazardMode,
            totalCycles = totalCycles,
            unpipelinedCycles = unpipelined,
            speedup = Math.round(speedup * 100.0) / 100.0,
            totalStalls = totalStalls,
            hazards = hazards,
            schedules = schedules,
            reorderedInstructions = reorderedList,
            cyclesSavedByReordering = reorderCyclesSaved,
            reorderingExplanationEn = reorderNoteEn,
            reorderingExplanationAr = reorderNoteAr
        )
    }
}
