package com.architecture.pipelinestudio

import com.architecture.pipelinestudio.engine.*
import org.junit.Assert.*
import org.junit.Test

class PipelineEngineTest {

    @Test
    fun testTD4Exercise6_RAWAndForwarding() {
        // From TD 4 - Exercice 6:
        // MOV RAX,[RBX]
        // ADD RAX,RCX
        // JMP L
        val code = """
            MOV RAX, [RBX]
            ADD RAX, RCX
            JMP L
        """.trimIndent()

        val instructions = Parser.parse(code)
        assertEquals(3, instructions.size)
        assertTrue("First instruction should be a memory load", instructions[0].isMemoryLoad)
        assertTrue("Third instruction should be a branch", instructions[2].isBranch)

        // 1. Simulate in Stalls Only mode (5-Stage model)
        val stallsResult = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY
        )

        // Verify RAW hazard on RAX detected between I1 and I2
        assertEquals(1, stallsResult.hazards.filter { it.type == HazardType.RAW_DATA }.size)
        val rawHazard = stallsResult.hazards.first { it.type == HazardType.RAW_DATA }
        assertEquals("RAX/EAX/AX/AL", rawHazard.registerFamily)
        assertEquals(1, rawHazard.sourceInstruction.id)
        assertEquals(2, rawHazard.targetInstruction.id)
        assertEquals(2, rawHazard.stallCycles) // 2 stalls without forwarding until WB

        // 2. Simulate in Forwarding mode (5-Stage model)
        val forwardingResult = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.FORWARDING
        )

        // As noted in TD 4 Ex 6 Corrigé: Load-Use forwarding from MEM to EX leaves 1 stall cycle
        val fwdHazard = forwardingResult.hazards.first { it.type == HazardType.RAW_DATA }
        assertEquals(1, fwdHazard.stallCycles)
        assertTrue(
            "Forwarding result should require fewer total cycles than stalls-only",
            forwardingResult.totalCycles < stallsResult.totalCycles
        )
    }

    @Test
    fun testSlide29_InstructionReordering() {
        // From Chapter 2, Slide 29:
        // 1. A = B + C
        // 2. D = A + C
        // 3. E = F + B
        val code = """
            A = B + C
            D = A + C
            E = F + B
        """.trimIndent()

        val instructions = Parser.parse(code)
        assertEquals(3, instructions.size)

        // In Stalls Only mode, D = A + C has a RAW hazard on A
        val stallsResult = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FOUR_STAGE,
            HazardMode.STALLS_ONLY
        )
        assertTrue(stallsResult.totalStalls > 0)

        // In Reordering mode, E = F + B should be moved before D = A + C
        val reorderResult = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FOUR_STAGE,
            HazardMode.REORDERING
        )

        assertNotNull(reorderResult.reorderedInstructions)
        val reordered = reorderResult.reorderedInstructions!!
        assertEquals(3, reordered.size)

        // Verify reordered sequence matches Slide 29 exactly:
        // 1. A = B + C
        // 2. E = F + B
        // 3. D = A + C
        assertEquals("A = B + C", reordered[0].rawText)
        assertEquals("E = F + B", reordered[1].rawText)
        assertEquals("D = A + C", reordered[2].rawText)

        assertTrue("Cycles should be saved by reordering", reorderResult.cyclesSavedByReordering > 0)
        assertTrue(
            "Reordered execution should take fewer or equal cycles compared to stalls-only",
            reorderResult.totalCycles <= stallsResult.totalCycles
        )
        assertTrue(
            "Reordering eliminates stall bubbles",
            reorderResult.totalStalls < stallsResult.totalStalls
        )
    }

    @Test
    fun testRegisterAliasing_SubRegistersTriggerRAW() {
        // Writing AX, then reading AL or EAX must trigger RAW
        val code = """
            MOV AX, 1
            MOV BL, AL
        """.trimIndent()

        val instructions = Parser.parse(code)
        assertEquals(2, instructions.size)

        val result = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FOUR_STAGE,
            HazardMode.STALLS_ONLY
        )

        val rawHazards = result.hazards.filter { it.type == HazardType.RAW_DATA }
        assertEquals(1, rawHazards.size)
        assertEquals("RAX/EAX/AX/AL", rawHazards[0].registerFamily)
    }

    @Test
    fun testTD4Exercise6_SpeedupCalculationFormula() {
        // In TD 4 Ex 6: 100 instructions, 5 stages
        // Without pipeline: 100 * 5 = 500 cycles
        // With ideal pipeline: 5 + 99 = 104 cycles
        // Speedup: 500 / 104 ≈ 4.8
        val n = 100
        val k = 5
        val unpipelined = n * k
        val idealPipelined = k + (n - 1)
        val speedup = unpipelined.toDouble() / idealPipelined.toDouble()

        assertEquals(500, unpipelined)
        assertEquals(104, idealPipelined)
        assertEquals(4.81, Math.round(speedup * 100.0) / 100.0, 0.05)
    }

    @Test
    fun testHazardDetection_WARAntiDependency() {
        // I1 reads AX, I2 writes AX -> WAR (Write After Read)
        val code = """
            MOV BX, AX
            MOV AX, 5
        """.trimIndent()

        val instructions = Parser.parse(code)
        assertEquals(2, instructions.size)

        val result = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY
        )

        val warHazards = result.hazards.filter { it.type == HazardType.WAR_DATA }
        assertEquals(1, warHazards.size)
        assertEquals("RAX/EAX/AX/AL", warHazards[0].registerFamily)
        assertEquals(0, warHazards[0].stallCycles) // 0 stalls in an in-order pipeline
    }

    @Test
    fun testHazardDetection_WAWOutputDependency() {
        // Both I1 and I2 write RAX -> WAW (Write After Write)
        val code = """
            MOV RAX, 10
            MOV RAX, 20
        """.trimIndent()

        val instructions = Parser.parse(code)
        assertEquals(2, instructions.size)

        val result = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY
        )

        val wawHazards = result.hazards.filter { it.type == HazardType.WAW_DATA }
        assertEquals(1, wawHazards.size)
        assertEquals("RAX/EAX/AX/AL", wawHazards[0].registerFamily)
        assertEquals(0, wawHazards[0].stallCycles) // 0 stalls in an in-order pipeline
    }

    @Test
    fun testStructuralHazard_UnifiedVsHarvard() {
        // I1 performs a memory load; I4 fetches at cycle 4.
        val code = """
            MOV RAX, [RBX]
            MOV R8, 1
            MOV R9, 2
            MOV R10, 3
        """.trimIndent()

        val instructions = Parser.parse(code)
        assertEquals(4, instructions.size)
        assertTrue(instructions[0].usesDataMemory)

        // 1. Unified Memory (Von Neumann single-port) -> Structural hazard
        val unifiedResult = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY,
            unifiedMemory = true
        )
        val structuralHazards = unifiedResult.hazards.filter { it.type == HazardType.STRUCTURAL }
        assertEquals(1, structuralHazards.size)
        assertEquals(1, structuralHazards[0].stallCycles)
        assertEquals(1, structuralHazards[0].sourceInstruction.id)
        assertEquals(4, structuralHazards[0].targetInstruction.id)
        assertTrue(unifiedResult.totalStalls >= 1)

        // 2. Harvard Architecture (Separate I/D Caches) -> 0 structural stalls
        val harvardResult = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY,
            unifiedMemory = false
        )
        val harvardStructural = harvardResult.hazards.filter { it.type == HazardType.STRUCTURAL }
        assertEquals(0, harvardStructural.size)
        assertEquals(0, harvardResult.totalStalls)
    }

    @Test
    fun testControlHazard_TerminalBranch() {
        val code = """
            MOV AX, 1
            JMP L
        """.trimIndent()

        val instructions = Parser.parse(code)
        val result = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY
        )

        val branchHazards = result.hazards.filter { it.type == HazardType.CONTROL_BRANCH }
        assertEquals(1, branchHazards.size)
        assertEquals(0, branchHazards[0].stallCycles) // Advisory terminal branch
        assertTrue(branchHazards[0].solutionEn.isNotEmpty())
        assertTrue(branchHazards[0].solutionAr.isNotEmpty())
    }

    @Test
    fun testAllHazardsDemo_DetectsAllFiveHazards() {
        val code = """
            MOV RAX, [RBX]
            ADD RAX, RCX
            MOV RDX, RAX
            MOV RCX, 5
            MOV RAX, 10
            JMP L
        """.trimIndent()

        val instructions = Parser.parse(code)
        val result = PipelineSimulator.simulate(
            instructions,
            PipelineModel.FIVE_STAGE,
            HazardMode.STALLS_ONLY,
            unifiedMemory = true
        )

        val types = result.hazards.map { it.type }.toSet()
        assertTrue("RAW must be detected", types.contains(HazardType.RAW_DATA))
        assertTrue("WAR must be detected", types.contains(HazardType.WAR_DATA))
        assertTrue("WAW must be detected", types.contains(HazardType.WAW_DATA))
        assertTrue("CONTROL must be detected", types.contains(HazardType.CONTROL_BRANCH))
        assertTrue("STRUCTURAL must be detected", types.contains(HazardType.STRUCTURAL))
        assertEquals(5, types.size)

        // Ensure all hazards have academic solutions provided in both languages
        for (hazard in result.hazards) {
            assertTrue("solutionEn must not be blank for ${hazard.type}", hazard.solutionEn.isNotBlank())
            assertTrue("solutionAr must not be blank for ${hazard.type}", hazard.solutionAr.isNotBlank())
        }
    }
}
