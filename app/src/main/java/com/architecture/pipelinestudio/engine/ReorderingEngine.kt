package com.architecture.pipelinestudio.engine

/**
 * Reordering Engine implementing compiler-level instruction scheduling (Réordonnancement)
 * as described in Chapter 2, Slide 29 of the course materials.
 *
 * Strictly preserves program semantics:
 * - Never reorders instructions with True Data Dependencies (RAW)
 * - Never reorders instructions with Anti-Dependencies (WAR)
 * - Never reorders instructions with Output Dependencies (WAW)
 * - Never reorders across branch / jump control boundaries
 */
object ReorderingEngine {

    data class ReorderResult(
        val reorderedInstructions: List<Instruction>,
        val cyclesSaved: Int,
        val swapExplanationEn: String,
        val swapExplanationAr: String
    )

    /**
     * Attempts to reorder instructions to minimize RAW stalls.
     */
    fun reorder(
        instructions: List<Instruction>,
        model: PipelineModel
    ): ReorderResult {
        if (instructions.size <= 2) {
            return ReorderResult(instructions, 0, "No reordering needed.", "لا حاجة لإعادة الترتيب.")
        }

        // Split into basic blocks by branches
        val basicBlocks = mutableListOf<MutableList<Instruction>>()
        var currentBlock = mutableListOf<Instruction>()

        for (inst in instructions) {
            if (inst.isBranch) {
                if (currentBlock.isNotEmpty()) {
                    basicBlocks.add(currentBlock)
                    currentBlock = mutableListOf()
                }
                basicBlocks.add(mutableListOf(inst)) // branch alone
            } else {
                currentBlock.add(inst)
            }
        }
        if (currentBlock.isNotEmpty()) {
            basicBlocks.add(currentBlock)
        }

        val fullReordered = mutableListOf<Instruction>()
        var totalSwaps = 0
        val explanationsEn = mutableListOf<String>()
        val explanationsAr = mutableListOf<String>()

        for (block in basicBlocks) {
            if (block.size <= 2 || (block.size == 1 && block[0].isBranch)) {
                fullReordered.addAll(block)
                continue
            }

            // Schedule the basic block using dependency DAG
            val (scheduledBlock, swapsCount, noteEn, noteAr) = scheduleBlock(block, model)
            fullReordered.addAll(scheduledBlock)
            totalSwaps += swapsCount
            if (noteEn.isNotEmpty()) explanationsEn.add(noteEn)
            if (noteAr.isNotEmpty()) explanationsAr.add(noteAr)
        }

        // Re-index instructions to maintain clean 1..N order
        val indexedReordered = fullReordered.mapIndexed { idx, inst ->
            inst.copy(id = idx + 1)
        }

        val explanationEn = if (totalSwaps > 0) {
            explanationsEn.joinToString("; ")
        } else {
            "No independent instructions available to eliminate stalls without altering semantics."
        }

        val explanationAr = if (totalSwaps > 0) {
            explanationsAr.joinToString("؛ ")
        } else {
            "لا توجد تعليمات مستقلة متاحة لإعادة الترتيب دون المساس بصحة البرنامج."
        }

        return ReorderResult(
            reorderedInstructions = indexedReordered,
            cyclesSaved = totalSwaps,
            swapExplanationEn = explanationEn,
            swapExplanationAr = explanationAr
        )
    }

    private data class BlockScheduleResult(
        val block: List<Instruction>,
        val swaps: Int,
        val noteEn: String,
        val noteAr: String
    )

    private fun scheduleBlock(
        block: List<Instruction>,
        model: PipelineModel
    ): BlockScheduleResult {
        val workingList = block.toMutableList()
        var swaps = 0
        var noteEn = ""
        var noteAr = ""

        // Check for adjacent RAW stalls and search for an independent instruction to insert
        var i = 0
        while (i < workingList.size - 1) {
            val producer = workingList[i]
            val consumer = workingList[i + 1]

            if (consumer.hasRawHazardOn(producer)) {
                // Find an independent candidate instruction after consumer that can be moved between producer and consumer
                var candidateIdx = -1
                for (k in (i + 2) until workingList.size) {
                    val candidate = workingList[k]

                    // Candidate must be independent of producer
                    val depProducer = candidate.hasRawHazardOn(producer) ||
                            candidate.hasWarDependencyOn(producer) ||
                            candidate.hasWawDependencyOn(producer)

                    // Candidate must be independent of consumer
                    val depConsumer = candidate.hasRawHazardOn(consumer) ||
                            candidate.hasWarDependencyOn(consumer) ||
                            candidate.hasWawDependencyOn(consumer)

                    // Candidate must be independent of any instructions between consumer and k
                    var depIntervening = false
                    for (m in (i + 1) until k) {
                        val mid = workingList[m]
                        if (candidate.hasRawHazardOn(mid) || candidate.hasWarDependencyOn(mid) || candidate.hasWawDependencyOn(mid)) {
                            depIntervening = true
                            break
                        }
                    }

                    if (!depProducer && !depConsumer && !depIntervening) {
                        candidateIdx = k
                        break
                    }
                }

                if (candidateIdx != -1) {
                    val candidate = workingList.removeAt(candidateIdx)
                    workingList.add(i + 1, candidate)
                    swaps++
                    noteEn = "Moved instruction '${candidate.rawText}' before '${consumer.rawText}' to hide RAW latency"
                    noteAr = "تم تقديم التعليمة '${candidate.rawText}' قبل '${consumer.rawText}' لتغطية وقت انتظار البيانات"
                }
            }
            i++
        }

        return BlockScheduleResult(workingList, swaps, noteEn, noteAr)
    }
}
