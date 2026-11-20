package com.architecture.pipelinestudio.engine

import java.util.Locale
import java.util.regex.Pattern

/**
 * Handles x86/x86-64 register aliasing and canonical family mapping.
 * Ensures that sub-registers (e.g., RAX, EAX, AX, AH, AL) are treated as the same
 * physical register entity during RAW hazard detection.
 */
object RegisterAlias {

    // Register families for x86 / x86-64
    private val FAMILY_MAP = mapOf(
        // A Register Family
        "RAX" to "REG_A", "EAX" to "REG_A", "AX" to "REG_A", "AH" to "REG_A", "AL" to "REG_A",
        // B Register Family
        "RBX" to "REG_B", "EBX" to "REG_B", "BX" to "REG_B", "BH" to "REG_B", "BL" to "REG_B",
        // C Register Family
        "RCX" to "REG_C", "ECX" to "REG_C", "CX" to "REG_C", "CH" to "REG_C", "CL" to "REG_C",
        // D Register Family
        "RDX" to "REG_D", "EDX" to "REG_D", "DX" to "REG_D", "DH" to "REG_D", "DL" to "REG_D",
        // Index & Pointer Registers
        "RSI" to "REG_SI", "ESI" to "REG_SI", "SI" to "REG_SI", "SIL" to "REG_SI",
        "RDI" to "REG_DI", "EDI" to "REG_DI", "DI" to "REG_DI", "DIL" to "REG_DI",
        "RBP" to "REG_BP", "EBP" to "REG_BP", "BP" to "REG_BP", "BPL" to "REG_BP",
        "RSP" to "REG_SP", "ESP" to "REG_SP", "SP" to "REG_SP", "SPL" to "REG_SP",
        // 64-bit Extended Registers R8 - R15
        "R8" to "REG_R8", "R8D" to "REG_R8", "R8W" to "REG_R8", "R8B" to "REG_R8",
        "R9" to "REG_R9", "R9D" to "REG_R9", "R9W" to "REG_R9", "R9B" to "REG_R9",
        "R10" to "REG_R10", "R10D" to "REG_R10", "R10W" to "REG_R10", "R10B" to "REG_R10",
        "R11" to "REG_R11", "R11D" to "REG_R11", "R11W" to "REG_R11", "R11B" to "REG_R11",
        "R12" to "REG_R12", "R12D" to "REG_R12", "R12W" to "REG_R12", "R12B" to "REG_R12",
        "R13" to "REG_R13", "R13D" to "REG_R13", "R13W" to "REG_R13", "R13B" to "REG_R13",
        "R14" to "REG_R14", "R14D" to "REG_R14", "R14W" to "REG_R14", "R14B" to "REG_R14",
        "R15" to "REG_R15", "R15D" to "REG_R15", "R15W" to "REG_R15", "R15B" to "REG_R15",
        // Segment Registers
        "CS" to "REG_CS", "DS" to "REG_DS", "SS" to "REG_SS", "ES" to "REG_ES", "FS" to "REG_FS", "GS" to "REG_GS"
    )

    private val MEMORY_PATTERN = Pattern.compile("\\[([^\\]]+)\\]")
    private val TOKEN_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*")

    /**
     * Returns the canonical family name for a register or variable.
     * E.g. "EAX" -> "REG_A", "al" -> "REG_A", "varX" -> "VAR_VARX".
     */
    fun canonicalFamily(token: String): String {
        val clean = token.trim().uppercase(Locale.ROOT)
        return FAMILY_MAP[clean] ?: "VAR_$clean"
    }

    /**
     * User-facing display name for a register family.
     */
    fun familyDisplayName(family: String): String {
        return when (family) {
            "REG_A" -> "RAX/EAX/AX/AL"
            "REG_B" -> "RBX/EBX/BX/BL"
            "REG_C" -> "RCX/ECX/CX/CL"
            "REG_D" -> "RDX/EDX/DX/DL"
            "REG_SI" -> "RSI/ESI/SI"
            "REG_DI" -> "RDI/EDI/DI"
            "REG_BP" -> "RBP/EBP/BP"
            "REG_SP" -> "RSP/ESP/SP"
            else -> if (family.startsWith("VAR_")) family.removePrefix("VAR_") else family
        }
    }

    /**
     * Checks if an operand is a memory address expression e.g. "[RBX]" or "[BX+SI+4]"
     */
    fun isMemoryOperand(operand: String): Boolean {
        return operand.trim().contains("[") && operand.trim().contains("]")
    }

    /**
     * Extracts all register families that are read to compute the memory address.
     * E.g. "[RBX + RSI*4 + 8]" -> ["REG_B", "REG_SI"]
     */
    fun extractRegistersFromMemory(operand: String): Set<String> {
        val matcher = MEMORY_PATTERN.matcher(operand)
        val result = mutableSetOf<String>()
        if (matcher.find()) {
            val inner = matcher.group(1) ?: ""
            val tokenMatcher = TOKEN_PATTERN.matcher(inner)
            while (tokenMatcher.find()) {
                val token = tokenMatcher.group().uppercase(Locale.ROOT)
                if (FAMILY_MAP.containsKey(token)) {
                    result.add(FAMILY_MAP.getValue(token))
                }
            }
        }
        return result
    }

    /**
     * Checks if a token represents an immediate number (decimal, hex, binary).
     */
    fun isImmediate(token: String): Boolean {
        val clean = token.trim().lowercase(Locale.ROOT)
        if (clean.matches(Regex("^-?[0-9]+$"))) return true
        if (clean.matches(Regex("^0x[0-9a-f]+$"))) return true
        if (clean.matches(Regex("^[0-9a-f]+h$"))) return true
        if (clean.matches(Regex("^[01]+b$"))) return true
        return false
    }
}
