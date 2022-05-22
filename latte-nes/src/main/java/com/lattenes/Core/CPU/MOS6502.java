/*
 * LatteNES: Nintendo Entertainment System (NES) Emulator written in Java
 * Copyright (C) 2022 Daniel Frias
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lattenes.Core.CPU;

import java.util.ArrayList;

import com.lattenes.Core.Memory.Memory;

public class MOS6502 {
    private int PC;
    private short SP;
    private byte processorStatusWord;
    private short A;
    private short X;
    private short Y;

    private int fetchedVal = 0;
    private int temp = 0;
    private int absoluteAddress = 0;
    private int relativeAddress = 0;
    private int opcode = 0;
    private int cycles = 0;
    private long cyclesCount = 0;

    private Memory memory;

    private ArrayList<MOS6502Instr> opcodes;

    private enum ProcessorStatusWordFlag {
        C(0x01), // Bit 0: Carry Flag
        Z(0x02), // Bit 1: Zero Flag
        I(0x04), // Bit 2: Interrupt Disable
        D(0x08), // Bit 3: Decimal Mode (no effect on the NES)
        B(0x10), // Bit 4: Break Command (only used on the stack)
        U(0x20), // Bit 5: Unused
        V(0x40), // Bit 6: Overflow Flag
        N(0x80); // Bit 7: Negative Flag

        public final int value;

        private ProcessorStatusWordFlag(int value) {
            this.value = value;
        }
    }

    void setFlag(ProcessorStatusWordFlag flag, boolean value) {
        if (value) {
            processorStatusWord |= flag.value;
        } else {
            processorStatusWord &= ~flag.value;
        }
    }

    boolean getFlag(ProcessorStatusWordFlag flag) {
        return (processorStatusWord & flag.value) != 0;
    }

    public MOS6502(Memory memory) {
        this.memory = memory;
        opcodes = new ArrayList<MOS6502Instr>();

        PC = 0x0000;
        SP = (byte) 0xFD;
        processorStatusWord = (byte) (0x00 | (byte) ProcessorStatusWordFlag.U.value);
        A = 0x00;
        X = 0x00;
        Y = 0x00;
    }

    public void clock() {
        // TODO: Implement
    } 

    private int fetch() {
        if (opcodes.get(opcode).addressingMode != MOS6502AddressMode.IMP) {
            fetchedVal = memory.readWord(absoluteAddress);
        }
        return fetchedVal;
    }

    /*
        Interrupts
    */

    // Maskable Interrupts
    // Pushes the PC and Processor Status Word to the stack
    // Reads the new PC from 0xFFFE
    public void IRQ() {
        if (!getFlag(ProcessorStatusWordFlag.I)) {
            memory.writeWord(0x0100 + SP, (byte) (PC >> 8));
            SP--;
            SP &= 0xFF;
            memory.writeWord(0x0100 + SP, (byte) (PC & 0xFF));
            SP--;
            SP &= 0xFF;

            setFlag(ProcessorStatusWordFlag.B, false);
            setFlag(ProcessorStatusWordFlag.U, true);
            setFlag(ProcessorStatusWordFlag.I, true);
            memory.writeWord(0x0100 + SP, processorStatusWord);
            SP--;
            SP &= 0xFF;

            absoluteAddress = 0xFFFE;
            PC = memory.readWord(absoluteAddress);
            PC |= (memory.readWord(absoluteAddress + 1) << 8);

            cycles = 7;
        }
    }

    // Non-maskable Interrupts
    // Same thing, but the interrupt is not masked, and the interrupt vector is
    // at 0xFFFA
    public void NMI() {
        memory.writeWord(0x0100 + SP, (byte) (PC >> 8));
        SP--;
        SP &= 0xFF;
        memory.writeWord(0x0100 + SP, (byte) (PC & 0xFF));
        SP--;
        SP &= 0xFF;

        setFlag(ProcessorStatusWordFlag.B, false);
        setFlag(ProcessorStatusWordFlag.U, true);
        setFlag(ProcessorStatusWordFlag.I, true);
        memory.writeWord(0x0100 + SP, processorStatusWord);
        SP--;
        SP &= 0xFF;

        absoluteAddress = 0xFFFA;
        PC = memory.readWord(absoluteAddress);
        PC |= (memory.readWord(absoluteAddress + 1) << 8);

        cycles = 8;
    }

    // Reset
    // Reads the new PC from 0xFFFC and reset the CPU state to a fresh state
    public void reset() {
        PC = memory.readWord(0xFFFC);
        PC |= (memory.readWord(0xFFFD) << 8);

        SP = (byte) 0xFD;
        processorStatusWord = (byte) (0x00 | (byte) ProcessorStatusWordFlag.U.value);
        A = 0x00;
        X = 0x00;
        Y = 0x00;

        absoluteAddress = 0x0000;
        relativeAddress = 0x0000;
        fetchedVal = 0;

        cycles = 8;
    }

    /*       
                         MOS 6502
                A d d r e s s i n g  M o d e s
                ==============================
    
        The NES CPU has 13 addressing modes, all of which
        are described and implemented below.
    */

    /*    
        Addressing Mode: Implied & Accumulator
        The simplest of them all. This means that the
        instruction does not take any parameters. The
        operand is implied and is not stored anywhere.
         
        @return 0
    */
    int IMP() {
        fetchedVal = A;
        return 0;
    }

    /*
        Addressing Mode: Immediate
        The operand is the next byte in memory.
        
        @return 0
    */
    int IMM() {
        absoluteAddress = PC++;
        PC = PC & 0xFFFF;
        return 0;
    }

    /*
        Addressing Mode: Zero Page
        A single byte address is used specifies an address in the first
        page of memory.
        
        @return cycles: 0
    */
    int ZPG() {
        absoluteAddress = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        absoluteAddress &= 0x00FF;
        return 0;
    }

    /*
        Addressing Mode: Zero Page, X
        A single byte address is used specifies an address in the first
        page of memory. The X register is added to the address.
        
        @return cycles: 0
    */
    int ZPX() {
        absoluteAddress = (memory.readWord(PC++) + X);
        PC = PC & 0xFFFF;
        absoluteAddress &= 0x00FF;
        return 0;
    }

    /*
        Addressing Mode: Zero Page, Y
        A single byte address is used specifies an address in the first
        page of memory. The Y register is added to the address.
        
        @return cycles: 0
    */
    int ZPY() {
        absoluteAddress = (memory.readWord(PC++) + Y);
        PC = PC & 0xFFFF;
        absoluteAddress &= 0x00FF;
        return 0;
    }

    /*
        Addressing Mode: Absolute
        Two bytes are used to specify an address in memory.
        
        @return cycles: 0
    */
    int ABS() {
        int loByte = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        int hiByte = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        absoluteAddress = ((hiByte << 8) | loByte) & 0xFFFF;
        return 0;
    }

    /*
        Addressing Mode: Absolute, X
        Two bytes are used to specify an address in memory. The X register
        is added to the address.
        
        @return cycles: 0 if page boundary is not crossed, 1 otherwise
    */
    int ABX() {
        int loByte = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        int hiByte = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        absoluteAddress = ((hiByte << 8) | loByte) + X;
        absoluteAddress &= 0xFFFF;
        
        if ((absoluteAddress & 0xFF00) != (hiByte << 8)) {
            return 1;
        } else {
            return 0;
        }
    }

    /*
        Addressing Mode: Absolute, Y
        Two bytes are used to specify an address in memory. The Y register
        is added to the address.
        
        @return cycles: 0 if page boundary is not crossed, 1 otherwise
    */
    int ABY() {
        int loByte = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        int hiByte = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        absoluteAddress = ((hiByte << 8) | loByte) + Y;
        absoluteAddress &= 0xFFFF;
        
        if ((absoluteAddress & 0xFF00) != (hiByte << 8)) {
            return 1;
        } else {
            return 0;
        }
    }

    /*
        Addressing Mode: Indirect
        A full 16-bit address is used to specify an address in memory.
        The contents of that address are read and used as the operand.
        
        @return cycles: 0
    */
    int IND() {
        int loPtr = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        int hiPtr = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        int ptr = ((hiPtr << 8) | loPtr) & 0xFFFF;

        if ((ptr & 0xFF00) == 0xFF00) {
            // LSB is at the boundary of the page
            absoluteAddress = (memory.readWord(ptr & 0xFF00) << 8) | memory.readWord(ptr);
            absoluteAddress &= 0xFFFF;
        } else {
            absoluteAddress = (memory.readWord(ptr + 1) << 8) | memory.readWord(ptr);
            absoluteAddress &= 0xFFFF;
        }

        return 0;
    }

    /*
        Addressing Mode: X, Indirect
        A 8-bit address is offset by the X register. This is used to
        read a 16-bit address from the first page of memory.
        
        @return cycles: 0
    */
    int IZX() {
        int helper = memory.readWord(PC++);
        PC = PC & 0xFFFF;

        int lo = memory.readWord((helper + X) & 0x00FF);
        int hi = memory.readWord((helper + X + 1) & 0x00FF);

        absoluteAddress = (hi << 8) | lo;

        return 0;
    }

    /*
        Addressing Mode: Indirect, Y
        A 8-bit address is used to read a 16-bit address from the first
        page of memory. The contents of the Y register is added to offset
        the address.
        
        @return cycles: 0 if page boundary is not crossed, 1 otherwise
    */
    int IZY() {
        int helper = memory.readWord(PC++);
        PC = PC & 0xFFFF;

        int lo = memory.readWord(helper & 0x00FF);
        int hi = memory.readWord((helper + 1) & 0x00FF);

        absoluteAddress = (hi << 8) | lo;
        absoluteAddress += Y;
        absoluteAddress &= 0xFFFF;

        if ((absoluteAddress & 0xFF00) != (hi << 8)) {
            return 1;
        } else {
            return 0;
        }
    }

    /*
        Addressing Mode: Relative
        The operand is a signed byte, used in branching instructions.

        @return cycles: 0
    */
    int REL() {
        relativeAddress = memory.readWord(PC++);
        PC = PC & 0xFFFF;
        if ((relativeAddress & 0x80) > 0) {
            relativeAddress |= 0xFFFFFF00;
        }
        return 0;
    }

    /*       
                                 MOS 6502
                        I n s t r u c t i o n  S e t
                        ============================
    
        The NES CPU has 56 official instructions, and 23 unofficial.
        Here, the official instructions are implemented in alphabetical
        order.
    */

    /*
        Instruction: ADC
        Add memory to accumulator with carry.
        A + M + C -> A, C

        Flags affected: N, Z, C, v

        Adds the a byte located at the specified address to the accumulator.
        @return 1 since it could take an additional cycle to complete
    */
    int ADC() {
        // Get our operand from memory
        fetch();

                    // This mask here might not be necessary but it's better to be safe :)
        temp = A + (fetchedVal & 0xFF) + (getFlag(ProcessorStatusWordFlag.C) ? 1 : 0);

        // Set flags
        setFlag(ProcessorStatusWordFlag.C, temp > 255);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        // The overflow flag is a bit of a doozy, but essentially
        // Positive + Positive = Negative is an overflow
        // so is Negative + Negative = Positive
        // Everything else is not an overflow
        setFlag(ProcessorStatusWordFlag.V, ((~(A ^ fetchedVal) & (A ^ temp)) & 0x80) > 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) > 0);

        // Store the result
        A = (short) (temp & 0xFF);

        return 1;
    }

    /*
        Instruction: AND
        Bitwise AND memory with accumulator.
        A and M -> A

        Flags affected: N, Z

        ANDs the contents of the accumulator with the byte
        located at the specified address.
        @return 1 since it could take an additional cycle to complete
    */
    int AND() {
        fetch();
        A &= fetchedVal;
        A &= 0xFF;
        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);
        return 1;
    }

    /*
        Instruction: ASL
        Shift left one bit, memory or accumulator.
        C <- [76543210] <- 0

        Flags affected: N, Z, C

        Shifts the byte in memory or accumulator one bit to the left. The
        least significant bit is set to 0, and the carry flag is set to the
        most significant bit before the shift.

        @return 0
    */
    int ASL() {
        fetch();

        setFlag(ProcessorStatusWordFlag.C, (fetchedVal & 0x80) > 0);
        fetchedVal <<= 1;
        fetchedVal &= 0xFF;
        setFlag(ProcessorStatusWordFlag.Z, fetchedVal == 0);
        setFlag(ProcessorStatusWordFlag.N, (fetchedVal & 0x80) > 0);

        if (opcodes.get(opcode).addressingMode == MOS6502AddressMode.ACC) {
            A = (short) fetchedVal;
        } else {
            memory.writeWord(absoluteAddress, (byte) fetchedVal);
        }

        return 0;
    }

    /*
        Instruction: BCC
        Branch on carry clear.
        Branch on C = 0

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    // Luckily, all of the branch instructions look the same, just
    // the condition is different
    int BCC() {
        if (!getFlag(ProcessorStatusWordFlag.C)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BCS
        Branch on carry set.
        Branch on C = 1

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BCS() {
        if (getFlag(ProcessorStatusWordFlag.C)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BEQ
        Branch on result zero.
        Branch on Z = 1

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BEQ() {
        if (getFlag(ProcessorStatusWordFlag.Z)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BIT
        Test bits in memory with accumulator.
        A and M, then M7 -> N, M6 -> V

        Flags affected: N, V, Z

        The bits 7 and 6 of the value in memory with the accumulator are
        tested. If the bit is set, the corresponding flag in the status
        register is set.

        @return 0
    */
    int BIT() {
        fetch();

        setFlag(ProcessorStatusWordFlag.Z, (fetchedVal & A) == 0);
        setFlag(ProcessorStatusWordFlag.N, (fetchedVal & 0x80) > 0);
        setFlag(ProcessorStatusWordFlag.V, (fetchedVal & 0x40) > 0);

        return 0;
    }

    // More branches

    /*
        Instruction: BMI
        Branch on result minus.
        Branch on N = 1

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BMI() {
        if (getFlag(ProcessorStatusWordFlag.N)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BNE
        Branch on result not zero.
        Branch on Z = 0

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BNE() {
        if (!getFlag(ProcessorStatusWordFlag.Z)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BPL
        Branch on result plus.
        Branch on N = 0

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BPL() {
        if (!getFlag(ProcessorStatusWordFlag.N)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BRK
        Force interrupt, push PC+1, push PC+2, push P, set I, set B, set
        PC to contents of vector at 0xFFFE

        @return 0
    */
    int BRK() {
        PC++;
        PC &= 0xFFFF;

        setFlag(ProcessorStatusWordFlag.I, true);
        memory.writeWord(0x0100 + SP, (byte) (PC >> 8));
        SP--;
        SP &= 0xFF;
        memory.writeWord(0x0100 + SP, (byte) (PC & 0xFF));
        SP--;
        SP &= 0xFF;

        setFlag(ProcessorStatusWordFlag.B, true);
        memory.writeWord(0x0100 + SP, processorStatusWord);
        SP--;
        SP &= 0xFF;
        setFlag(ProcessorStatusWordFlag.B, false);

        PC = memory.readWord(0xFFFE) | (memory.readWord(0xFFFF) << 8);
        PC &= 0xFFFF;

        return 0;
    }

    // Last couple branches
    /*
        Instruction: BVC
        Branch on overflow clear.
        Branch on V = 0

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BVC() {
        if (!getFlag(ProcessorStatusWordFlag.V)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: BVS
        Branch on overflow set.
        Branch on V = 1

        If branch is taken, add 1 to the cycle count. If
        the branch is taken and the page boundary is crossed,
        add 1 more cycle.

        @return 0
    */
    int BVS() {
        if (getFlag(ProcessorStatusWordFlag.V)) {
            cycles++;
            absoluteAddress = (absoluteAddress + relativeAddress) & 0xFFFF;

            if ((absoluteAddress & 0xFF00) != (PC & 0xFF00)) {
                cycles++;
            }

            PC = absoluteAddress;
        }
        
        return 0;
    }

    /*
        Instruction: CLC
        Clear carry flag.
        C = 0

        @return 0
    */
    int CLC() {
        setFlag(ProcessorStatusWordFlag.C, false);
        
        return 0;
    }

    /*
        Instruction: CLD
        Clear decimal mode.
        D = 0

        @return 0
    */
    int CLD() {
        setFlag(ProcessorStatusWordFlag.D, false);
        
        return 0;
    }

    /*
        Instruction: CLI
        Clear interrupt disable.
        I = 0

        @return 0
    */
    int CLI() {
        setFlag(ProcessorStatusWordFlag.I, false);
        
        return 0;
    }

    /*
        Instruction: CLV
        Clear overflow flag.
        V = 0

        @return 0
    */ 
    int CLV() {
        setFlag(ProcessorStatusWordFlag.V, false);
        
        return 0;
    }

    /*
        Instruction: CMP
        Compare memory and accumulator.
        R = A - M, then R7 -> N

        Flags affected: N, Z, C
        Compare the accumulator with the value in memory. If the
        result is negative, set the negative flag. If the result is
        zero, set the zero flag. Set the carry flag if the result is
        zero or A was greater than the value stored in memory.

        @return 1 since it could take an extra cycle
    */
    int CMP() {
        fetch();

        int result = (A - fetchedVal) & 0xFF;
        setFlag(ProcessorStatusWordFlag.Z, result == 0);
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) > 0);
        setFlag(ProcessorStatusWordFlag.C, A >= fetchedVal);

        return 1;
    }

    /*
        Instruction: CPX
        Compare memory and X.
        R = X - M, then R7 -> N

        Flags affected: N, Z, C
        Compare the X register with the value in memory. If the
        result is negative, set the negative flag. If the result is
        zero, set the zero flag. Set the carry flag if the result is
        zero or X was greater than the value stored in memory.

        @return 0
    */
    int CPX() {
        fetch();

        int result = (X - fetchedVal) & 0xFF;; 
        setFlag(ProcessorStatusWordFlag.Z, result == 0);
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) > 0);
        setFlag(ProcessorStatusWordFlag.C, X >= fetchedVal);

        return 0;
    }

    /*
        Instruction: CPY
        Compare memory and Y.
        R = Y - M, then R7 -> N

        Flags affected: N, Z, C
        Compare the Y register with the value in memory. If the
        result is negative, set the negative flag. If the result is
        zero, set the zero flag. Set the carry flag if the result is
        zero or Y was greater than the value stored in memory.

        @return 0
    */
    int CPY() {
        fetch();

        int result = (Y - fetchedVal) & 0xFF;; 
        setFlag(ProcessorStatusWordFlag.Z, result == 0);
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) > 0);
        setFlag(ProcessorStatusWordFlag.C, Y >= fetchedVal);

        return 0;
    }

    /*
        Instruction: DEC
        Decrement memory by one.
        M - 1 -> M

        Flags affected: N, Z
        Decrement the value in memory by one. If the result is
        negative, set the negative flag. If the result is zero,
        set the zero flag.

        @return 0
    */
    int DEC() {
        fetch();
        
        int result = (fetchedVal - 1) & 0xFF;
        setFlag(ProcessorStatusWordFlag.Z, result == 0);
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) > 0);
        memory.writeWord(absoluteAddress, (byte) result);
        
        return 0;
    }

    /*
        Instruction: DEX
        Decrement X by one.
        X - 1 -> M

        Flags affected: N, Z
        Decrement the X register by one. If the result is
        negative, set the negative flag. If the result is zero,
        set the zero flag.

        @return 0
    */
    int DEX() {
        X = (short) ((X - 1) & 0xFF);
        setFlag(ProcessorStatusWordFlag.Z, X == 0);
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) > 0);
        
        return 0;
    }

    /*
        Instruction: DEY
        Decrement Y by one.
        Y - 1 -> Y

        Flags affected: N, Z
        Decrement the Y register by one. If the result is
        negative, set the negative flag. If the result is zero,
        set the zero flag.

        @return 0
    */
    int DEY() {
        Y = (short) ((Y - 1) & 0xFF);
        setFlag(ProcessorStatusWordFlag.Z, Y == 0);
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) > 0);
        
        return 0;
    }

    /*
        Instruction: EOR
        Exclusive OR memory with accumulator.
        A ^ M -> A

        Flags affected: N, Z
        Exclusive OR the accumulator with the value in memory. If
        the result is negative, set the negative flag. If the result
        is zero, set the zero flag.

        @return 1 since it could take an extra cycle
    */
    int EOR() {
        fetch();

        A = (short) (A ^ fetchedVal);
        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);
        
        return 1;
    }
    
    /*
        Instruction: INC
        Increment memory by one.
        M + 1 -> M

        Flags affected: N, Z
        Increment the value in memory by one. If the result is
        negative, set the negative flag. If the result is zero,
        set the zero flag.

        @return 0
    */
    int INC() {
        fetch();
        
        int result = (fetchedVal + 1) & 0xFF;
        setFlag(ProcessorStatusWordFlag.Z, result == 0);
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) > 0);
        memory.writeWord(absoluteAddress, (byte) result);
        
        return 0;
    }

    /*
        Instruction: INX
        Increment index X by one.
        X + 1 -> X

        Flags affected: N, Z
        Increment the X register by one. If the result is
        negative, set the negative flag. If the result is zero,
        set the zero flag.

        @return 0
    */
    int INX() {
        X = (short) ((X + 1) & 0xFF);
        setFlag(ProcessorStatusWordFlag.Z, X == 0);
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) > 0);
        
        return 0;
    }

    /*
        Instruction: INY
        Increment index Y by one.
        Y + 1 -> Y

        Flags affected: N, Z
        Increment the Y register by one. If the result is
        negative, set the negative flag. If the result is zero,
        set the zero flag.

        @return 0
    */
    int INY() {
        Y = (short) ((Y + 1) & 0xFF);
        setFlag(ProcessorStatusWordFlag.Z, Y == 0);
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) > 0);
        
        return 0;
    }

    /*
        Instruction: JMP
        Jump to new location.
        PC = M

        Flags affected: none
        Set the program counter to the value in memory.

        @return 0
    */
    int JMP() {
        PC = absoluteAddress;
        return 0;
    }

    /*
        Instruction: JSR
        Jump to new location saving return address.
        Push PC + 2 to stack, PC = M

        Flags affected: none
        Push the program counter to the stack, then set the program
        counter to the value in memory.

        @return 0
    */
    int JSR() {
        PC--;

        memory.writeWord(0x0100 + SP, (byte) (PC >> 8));
        SP--;
        SP &= 0xFF;
        memory.writeWord(0x0100 + SP, (byte) (PC & 0xFF));
        SP--;
        SP &= 0xFF;

        PC = absoluteAddress;
        return 0;
    }

    /*
        Instruction: LDA
        Load accumulator with memory.
        M -> A

        Flags affected: N, Z
        Load the accumulator with the value in memory. If the
        result is negative, set the negative flag. If the result is
        zero, set the zero flag.

        @return 1 since it could take an extra cycle
    */
    int LDA() {
        fetch();

        A = (short) fetchedVal;
        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);

        return 1;
    }

    /*
        Instruction: LDX
        Load index X with memory.
        M -> X

        Flags affected: N, Z
        Load the X register with the value in memory. If the
        result is negative, set the negative flag. If the result is
        zero, set the zero flag.

        @return 1 since it could take an extra cycle
    */
    int LDX() {
        fetch();

        X = (short) fetchedVal;
        setFlag(ProcessorStatusWordFlag.Z, X == 0);
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) > 0);

        return 1;
    }

    /*
        Instruction: LDY
        Load index Y with memory.
        M -> Y

        Flags affected: N, Z
        Load the Y register with the value in memory. If the
        result is negative, set the negative flag. If the result is
        zero, set the zero flag.

        @return 1 since it could take an extra cycle
    */
    int LDY() {
        fetch();

        Y = (short) fetchedVal;
        setFlag(ProcessorStatusWordFlag.Z, Y == 0);
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) > 0);

        return 1;
    }

    /*
        Instruction: LSR, memory or accumulator
        Shift right one bit.
        0 -> [76543210] -> C, 0 -> N

        Flags affected: N, Z, C
        Shift the value in memory right one bit. Clear the negative flag.
        If the result is zero, set the zero flag. Set the carry flag to the 
        value in the least significant bit before the shift.

        @return 0
    */
    int LSR() {
        fetch();

        setFlag(ProcessorStatusWordFlag.C, (fetchedVal & 0x01) > 0);
        fetchedVal >>= 1;
        setFlag(ProcessorStatusWordFlag.Z, fetchedVal == 0);
        setFlag(ProcessorStatusWordFlag.N, false);

        if (opcodes.get(opcode).addressingMode == MOS6502AddressMode.ACC) {
            A = (short) fetchedVal;
        } else {
            memory.writeWord(absoluteAddress, (byte) fetchedVal);
        }
        
        
        return 0;
    }


    /*
        Instruction: NOP
        No operation.

        The hardest instruction to implement.

        @return 0
    */
    int NOP() {
        return 0;
    }

    /*
        Instruction: ORA
        Logical inclusive OR.
        A | M -> A

        Flags affected: N, Z
        Perform a logical inclusive OR on the accumulator and the
        value in memory. If the result is negative, set the negative
        flag. If the result is zero, set the zero flag.

        @return 1 since it could take an extra cycle
    */
    int ORA() {
        fetch();

        A |= fetchedVal;
        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);

        return 1;
    }

    /*
        Instruction: PHA
        Push accumulator to stack.
        A -> stack

        Flags affected: none
        Push the accumulator to the stack.

        @return 0
    */
    int PHA() {
        memory.writeWord(0x0100 + SP, (byte) A);
        SP--;
        SP &= 0xFF;

        return 0;
    }

    /*
        Instruction: PHP
        Push processor status to stack.
        P -> stack

        Flags affected: none
        Push the processor status to the stack.

        @return 0
    */
    int PHP() {
        setFlag(ProcessorStatusWordFlag.B, true);
        setFlag(ProcessorStatusWordFlag.U, true);
        memory.writeWord(0x0100 + SP, (byte) processorStatusWord);
        SP--;
        SP &= 0xFF;
        setFlag(ProcessorStatusWordFlag.B, false);
        setFlag(ProcessorStatusWordFlag.U, false);
        
        return 0;
    }

    /*
        Instruction: PLA
        Pull accumulator from stack.
        stack -> A

        Flags affected: N, Z
        Pull the accumulator from the stack. If the result is negative,
        set the negative flag. If the result is zero, set the zero flag.

        @return 0
    */
    int PLA() {
        SP++;
        SP &= 0xFF;
        A = (short) memory.readWord(0x0100 + SP);

        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);

        return 0;
    }

    /*
        Instruction: PLP
        Pull processor status from stack.
        stack -> P

        Flags affected: none
        Pull the processor status from the stack.

        @return 0
    */
    int PLP() {
        SP++;
        SP &= 0xFF;
        processorStatusWord = (byte) memory.readWord(0x0100 + SP);
        setFlag(ProcessorStatusWordFlag.U, true);

        return 0;
    }

    /*
        Instruction: ROL
        Rotate one bit left.
        C <- [76543210] <- C

        Flags affected: N, Z, C
        Rotate the value in memory left one bit. Set the carry flag to the
        value in the most significant bit before the rotation.

        @return 0
    */
    int ROL() {
        fetch();

        temp = (fetchedVal << 1) | (getFlag(ProcessorStatusWordFlag.C) ? 1 : 0);
        setFlag(ProcessorStatusWordFlag.C, (temp & 0x100) > 0);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) > 0);
        if (opcodes.get(opcode).addressingMode == MOS6502AddressMode.ACC) {
            A = (short) (temp & 0xFF);
        } else {
            memory.writeWord(absoluteAddress, (byte) (temp & 0xFF));
        }

        return 0;
    }

    /*
        Instruction: ROR
        Rotate one bit right.
        C -> [76543210] -> C 

        Flags affected: N, Z, C
        Rotate the value in memory right one bit. Set the carry flag to the
        value in the least significant bit before the rotation.

        @return 0
    */
    int ROR() {
        fetch();

        temp = (getFlag(ProcessorStatusWordFlag.C) ? (1 << 7) : 0) | (fetchedVal >> 1);
        setFlag(ProcessorStatusWordFlag.C, (fetchedVal & 0x1) > 0);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) > 0);
        if (opcodes.get(opcode).addressingMode == MOS6502AddressMode.ACC) {
            A = (short) (temp & 0xFF);
        } else {
            memory.writeWord(absoluteAddress, (byte) (temp & 0xFF));
        }

        return 0;
    }

    /*
        Instruction: RTI
        Return from interrupt.
        P <- stack, PC <- stack

        Flags affected: none
        Pull the processor status from the stack. Pull the program counter
        from the stack.

        @return 0
    */
    int RTI() {
        SP++;
        SP &= 0xFF;
        processorStatusWord = (byte) memory.readWord(0x0100 + SP);
        setFlag(ProcessorStatusWordFlag.B, false);
        setFlag(ProcessorStatusWordFlag.U, false);

        SP++;
        SP &= 0xFF;
        PC = memory.readWord(0x0100 + SP);
        SP++;
        SP &= 0xFF;
        PC |= (memory.readWord(0x0100 + SP) << 8);

        return 0;
    }

    /*
        Instruction: RTS
        Return from subroutine.
        PC <- stack

        Flags affected: none
        Pull the program counter from the stack.

        @return 0
    */
    int RTS() {
        SP++;
        SP &= 0xFF;
        PC = memory.readWord(0x0100 + SP);
        SP++;
        SP &= 0xFF;
        PC |= (memory.readWord(0x0100 + SP) << 8);

        PC++;
        PC &= 0xFFFF;
        return 0;
    }

    /*
        Instruction: SBC
        Subtract memory with borrow in.
        A - M - ~C -> A
        
        Flags affected: N, Z, C, V
        subtracts the a byte located at the specified address from the accumulator.
        @return 1 since it could take an additional cycle to complete
    */
    int SBC() {
        fetch();

        // Flip the lower 8 bits of the fetched value
        int val = (fetchedVal & 0xFF) ^ 0xFF;

        /*
            Here is the derivation of the formula for the SBC instruction.
            Let -M = ~M + 1 (by two's complement)
            Let ~C = (1 - C) (C can be 0 or 1)

            A - M - ~C = A - M - (1 - C)
                       = A - M - 1 + C
                       = A + (~M + 1) - 1 + C
                       = A + ~M + 1 - 1 + C
                       = A + ~M + C

            So, all we need to do is add val to A and C, same as ADC 
        */
        temp = A + val + (getFlag(ProcessorStatusWordFlag.C) ? 0 : 1);
        setFlag(ProcessorStatusWordFlag.C, temp > 255);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        setFlag(ProcessorStatusWordFlag.V, ((~(A ^ val) & (A ^ temp)) & 0x80) > 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) > 0);

        A = (short) (temp & 0xFF);

        return 1;
    }

    /*
        Instruction: SEC
        Set carry flag.
        C <- 1

        Flags affected: none
        Set the carry flag.

        @return 0
    */
    int SEC() {
        setFlag(ProcessorStatusWordFlag.C, true);
        return 0;
    }

    /*
        Instruction: SED
        Set decimal mode.
        D <- 1

        Flags affected: none
        Set the decimal mode flag.

        @return 0
    */
    int SED() {
        setFlag(ProcessorStatusWordFlag.D, true);
        return 0;
    }

    /*
        Instruction: SEI
        Set interrupt disable status.
        I <- 1

        Flags affected: none
        Set the interrupt disable flag.

        @return 0
    */
    int SEI() {
        setFlag(ProcessorStatusWordFlag.I, true);
        return 0;
    }

    /*
        Instruction: STA
        Store accumulator.
        A -> M

        Flags affected: none
        Store the accumulator at the specified address.

        @return 0
    */
    int STA() {
        memory.writeWord(absoluteAddress, (byte) A);
        return 0;
    }

    /*
        Instruction: STX
        Store X register.
        X -> M

        Flags affected: none
        Store the X register at the specified address.

        @return 0
    */
    int STX() {
        memory.writeWord(absoluteAddress, (byte) X);
        return 0;
    }

    /*
        Instruction: STY
        Store Y register.
        Y -> M

        Flags affected: none
        Store the Y register at the specified address.

        @return 0
    */
    int STY() {
        memory.writeWord(absoluteAddress, (byte) Y);
        return 0;
    }

    /*
        Instruction: TAX
        Transfer accumulator to X.
        A -> X

        Flags affected: N, Z
        Transfer the accumulator to the X register.

        @return 0
    */
    int TAX() {
        X = A;
        setFlag(ProcessorStatusWordFlag.Z, X == 0);
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) > 0);
        return 0;
    }

    /*
        Instruction: TAY
        Transfer accumulator to Y.
        A -> Y

        Flags affected: N, Z
        Transfer the accumulator to the Y register.

        @return 0
    */
    int TAY() {
        Y = A;
        setFlag(ProcessorStatusWordFlag.Z, Y == 0);
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) > 0);
        return 0;
    }

    /*
        Instruction: TSX
        Transfer stack pointer to X.
        SP -> X

        Flags affected: N, Z
        Transfer the stack pointer to the X register.

        @return 0
    */
    int TSX() {
        X = SP;
        setFlag(ProcessorStatusWordFlag.Z, X == 0);
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) > 0);
        return 0;
    }

    /*
        Instruction: TXA
        Transfer X to accumulator.
        X -> A

        Flags affected: N, Z
        Transfer the X register to the accumulator.

        @return 0
    */
    int TXA() {
        A = X;
        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);
        return 0;
    }

    /*
        Instruction: TXS
        Transfer X to stack pointer.
        X -> SP

        Flags affected: none
        Transfer the X register to the stack pointer.

        @return 0
    */
    int TXS() {
        SP = X;
        return 0;
    }

    /*
        Instruction: TYA
        Transfer Y to accumulator.
        Y -> A

        Flags affected: N, Z
        Transfer the Y register to the accumulator.

        @return 0
    */
    int TYA() {
        A = Y;
        setFlag(ProcessorStatusWordFlag.Z, A == 0);
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) > 0);
        return 0;
    }

    /*
        Instruction: XXX
        Undocumented, unknown, or unofficial instruction.
        XXX -> A

        Flags affected: none
        Undocumented, unknown, or unofficial instruction.

        @return 0
    */
    int XXX() {
        return 0;
    }
}   