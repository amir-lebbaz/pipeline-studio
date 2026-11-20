package com.architecture.pipelinestudio.engine

import java.util.Locale

object Parser {

    private val BRANCH_MNEMONICS = setOf(
        "JMP", "JZ", "JNZ", "JE", "JNE", "JG", "JL", "JGE", "JLE", "JA", "JB", "JAE", "JBE",
        "JC", "JNC", "JO", "JNO", "JS", "JNS", "JP", "JNP", "LOOP", "LOOPE", "LOOPNE", "LOOPZ", "LOOPNZ",
        "CALL", "RET", "IRET"
    )

    fun parse(codeText: String): List<Instruction> {
        val lines = codeText.lines()
        val instructions = mutableListOf<Instruction>()
        var instIndex = 1

        for (rawLine in lines) {
            // Strip comments
            var line = rawLine.trim()
            val commentIdx = line.indexOf(';')
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx).trim()
            }
            if (line.isEmpty()) continue

            // Strip leading numbered prefixes like "1.", "1-", etc.
            line = line.replaceFirst(Regex("^[0-9]+[.\\-\\)]\\s*"), "")

            // Strip labels e.g. "Boucle:", "Next:", "L:"
            val colonIdx = line.indexOf(':')
            if (colonIdx != -1 && colonIdx < line.length - 1) {
                line = line.substring(colonIdx + 1).trim()
            } else if (colonIdx != -1 && colonIdx == line.length - 1) {
                continue // Standalone label line
            }
            if (line.isEmpty()) continue

            val instruction = parseSingleInstruction(instIndex, line, rawLine.trim())
            instructions.add(instruction)
            instIndex++
        }

        return instructions
    }

    private fun parseSingleInstruction(id: Int, line: String, originalText: String): Instruction {
        // Check for Slide 29 assignment syntax: A = B + C or D = A + C
        if (line.contains("=")) {
            return parseAssignmentSyntax(id, line, originalText)
        }

        // Standard x86 Assembly Syntax: MNEMONIC [OPERANDS]
        val parts = line.split(Regex("\\s+"), limit = 2)
        val mnemonic = parts[0].uppercase(Locale.ROOT)
        val operandsPart = if (parts.size > 1) parts[1].trim() else ""

        if (mnemonic == "NOP") {
            return Instruction(
                id = id,
                rawText = originalText,
                mnemonic = "NOP",
                writes = emptySet(),
                reads = emptySet(),
                isNop = true
            )
        }

        if (BRANCH_MNEMONICS.contains(mnemonic)) {
            val reads = mutableSetOf<String>()
            if (mnemonic.startsWith("LOOP")) {
                reads.add(RegisterAlias.canonicalFamily("CX")) // Implicit read of CX
            }
            return Instruction(
                id = id,
                rawText = originalText,
                mnemonic = mnemonic,
                writes = if (mnemonic.startsWith("LOOP")) setOf(RegisterAlias.canonicalFamily("CX")) else emptySet(),
                reads = reads,
                isBranch = true
            )
        }

        val operands = if (operandsPart.isNotEmpty()) {
            operandsPart.split(',').map { it.trim() }
        } else emptyList()

        val writes = mutableSetOf<String>()
        val reads = mutableSetOf<String>()
        var isLoad = false
        var isStore = false

        when (mnemonic) {
            "MOV" -> {
                if (operands.size >= 2) {
                    val dest = operands[0]
                    val src = operands[1]

                    // Dest
                    if (RegisterAlias.isMemoryOperand(dest)) {
                        isStore = true
                        reads.addAll(RegisterAlias.extractRegistersFromMemory(dest))
                    } else if (!RegisterAlias.isImmediate(dest)) {
                        writes.add(RegisterAlias.canonicalFamily(dest))
                    }

                    // Src
                    if (RegisterAlias.isMemoryOperand(src)) {
                        isLoad = true
                        reads.addAll(RegisterAlias.extractRegistersFromMemory(src))
                    } else if (!RegisterAlias.isImmediate(src)) {
                        reads.add(RegisterAlias.canonicalFamily(src))
                    }
                }
            }
            "ADD", "SUB", "AND", "OR", "XOR" -> {
                if (operands.size >= 2) {
                    val dest = operands[0]
                    val src = operands[1]

                    if (RegisterAlias.isMemoryOperand(dest)) {
                        isStore = true
                        reads.addAll(RegisterAlias.extractRegistersFromMemory(dest))
                    } else if (!RegisterAlias.isImmediate(dest)) {
                        writes.add(RegisterAlias.canonicalFamily(dest))
                        reads.add(RegisterAlias.canonicalFamily(dest)) // Read-Modify-Write
                    }

                    if (RegisterAlias.isMemoryOperand(src)) {
                        isLoad = true
                        reads.addAll(RegisterAlias.extractRegistersFromMemory(src))
                    } else if (!RegisterAlias.isImmediate(src)) {
                        reads.add(RegisterAlias.canonicalFamily(src))
                    }
                }
            }
            "INC", "DEC", "NEG", "NOT" -> {
                if (operands.isNotEmpty()) {
                    val dest = operands[0]
                    if (RegisterAlias.isMemoryOperand(dest)) {
                        reads.addAll(RegisterAlias.extractRegistersFromMemory(dest))
                    } else if (!RegisterAlias.isImmediate(dest)) {
                        writes.add(RegisterAlias.canonicalFamily(dest))
                        reads.add(RegisterAlias.canonicalFamily(dest))
                    }
                }
            }
            "CMP", "TEST" -> {
                if (operands.size >= 2) {
                    val op1 = operands[0]
                    val op2 = operands[1]
                    if (RegisterAlias.isMemoryOperand(op1)) reads.addAll(RegisterAlias.extractRegistersFromMemory(op1))
                    else if (!RegisterAlias.isImmediate(op1)) reads.add(RegisterAlias.canonicalFamily(op1))

                    if (RegisterAlias.isMemoryOperand(op2)) reads.addAll(RegisterAlias.extractRegistersFromMemory(op2))
                    else if (!RegisterAlias.isImmediate(op2)) reads.add(RegisterAlias.canonicalFamily(op2))
                }
            }
            "MUL", "DIV" -> {
                if (operands.isNotEmpty()) {
                    val src = operands[0]
                    writes.add(RegisterAlias.canonicalFamily("AX"))
                    writes.add(RegisterAlias.canonicalFamily("DX"))
                    reads.add(RegisterAlias.canonicalFamily("AX"))
                    reads.add(RegisterAlias.canonicalFamily("DX"))
                    if (!RegisterAlias.isImmediate(src)) {
                        reads.add(RegisterAlias.canonicalFamily(src))
                    }
                }
            }
            else -> {
                // Generic 2-operand or 1-operand assumption
                if (operands.size >= 2) {
                    val dest = operands[0]
                    val src = operands[1]
                    if (!RegisterAlias.isImmediate(dest) && !RegisterAlias.isMemoryOperand(dest)) {
                        writes.add(RegisterAlias.canonicalFamily(dest))
                    }
                    if (!RegisterAlias.isImmediate(src) && !RegisterAlias.isMemoryOperand(src)) {
                        reads.add(RegisterAlias.canonicalFamily(src))
                    }
                } else if (operands.size == 1) {
                    val op = operands[0]
                    if (!RegisterAlias.isImmediate(op)) reads.add(RegisterAlias.canonicalFamily(op))
                }
            }
        }

        return Instruction(
            id = id,
            rawText = originalText,
            mnemonic = mnemonic,
            writes = writes,
            reads = reads,
            isBranch = false,
            isMemoryLoad = isLoad,
            isMemoryStore = isStore
        )
    }

    private fun parseAssignmentSyntax(id: Int, line: String, originalText: String): Instruction {
        val eqParts = line.split("=", limit = 2)
        val left = eqParts[0].trim()
        val right = eqParts[1].trim()

        val writes = setOf(RegisterAlias.canonicalFamily(left))
        val reads = mutableSetOf<String>()

        // Split right hand side by operators: +, -, *, /
        val tokens = right.split(Regex("[+\\-*/(),\\s]+"))
        for (token in tokens) {
            val t = token.trim()
            if (t.isNotEmpty() && !RegisterAlias.isImmediate(t)) {
                reads.add(RegisterAlias.canonicalFamily(t))
            }
        }

        return Instruction(
            id = id,
            rawText = originalText,
            mnemonic = "=",
            writes = writes,
            reads = reads,
            isBranch = false
        )
    }
}
