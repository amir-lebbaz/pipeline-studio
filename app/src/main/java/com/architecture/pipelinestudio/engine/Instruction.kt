package com.architecture.pipelinestudio.engine

/**
 * Representation of a decoded instruction with its data dependencies.
 */
data class Instruction(
    val id: Int,
    val rawText: String,
    val mnemonic: String,
    val writes: Set<String>,
    val reads: Set<String>,
    val isBranch: Boolean = false,
    val isMemoryLoad: Boolean = false,
    val isMemoryStore: Boolean = false,
    val isNop: Boolean = false
) {
    val usesDataMemory: Boolean get() = isMemoryLoad || isMemoryStore

    /**
     * Checks if this instruction has a RAW (Read-After-Write) data dependency
     * on an earlier instruction [producer].
     */
    fun hasRawHazardOn(producer: Instruction): Boolean {
        if (producer.writes.isEmpty() || this.reads.isEmpty()) return false
        val overlap = this.reads.intersect(producer.writes)
        return overlap.isNotEmpty()
    }

    /**
     * Returns the overlapping register family written by [producer] and read by this instruction.
     */
    fun getRawConflictingFamilies(producer: Instruction): Set<String> {
        return this.reads.intersect(producer.writes)
    }

    /**
     * Checks if this instruction has a WAR (Write-After-Read) anti-dependency on [earlier].
     */
    fun hasWarDependencyOn(earlier: Instruction): Boolean {
        if (this.writes.isEmpty() || earlier.reads.isEmpty()) return false
        return this.writes.intersect(earlier.reads).isNotEmpty()
    }

    /**
     * Returns the overlapping register family read by [earlier] and written by this instruction.
     */
    fun getWarConflictingFamilies(earlier: Instruction): Set<String> {
        return this.writes.intersect(earlier.reads)
    }

    /**
     * Checks if this instruction has a WAW (Write-After-Write) output dependency on [earlier].
     */
    fun hasWawDependencyOn(earlier: Instruction): Boolean {
        if (this.writes.isEmpty() || earlier.writes.isEmpty()) return false
        return this.writes.intersect(earlier.writes).isNotEmpty()
    }

    /**
     * Returns the overlapping register family written by both [earlier] and this instruction.
     */
    fun getWawConflictingFamilies(earlier: Instruction): Set<String> {
        return this.writes.intersect(earlier.writes)
    }

    /**
     * Checks if this instruction is completely independent of [other].
     */
    fun isCompletelyIndependentOf(other: Instruction): Boolean {
        if (this.isBranch || other.isBranch) return false
        val writesReads = this.writes.intersect(other.reads).isNotEmpty()
        val readsWrites = this.reads.intersect(other.writes).isNotEmpty()
        val writesWrites = this.writes.intersect(other.writes).isNotEmpty()
        return !writesReads && !readsWrites && !writesWrites
    }
}
