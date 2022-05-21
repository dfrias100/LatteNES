package com.lattenes.CPU;

import com.lattenes.Memory.Memory;

import java.util.ArrayList;

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

    public enum ProcessorStatusWordFlag {
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

    public MOS6502(Memory memory) {
        this.memory = memory;
        opcodes = new ArrayList<MOS6502Instr>();

        PC = 0x0000;
        SP = (byte) 0xFD;
        processorStatusWord = 0x34;
        A = 0x00;
        X = 0x00;
        Y = 0x00;
    }

    public void clock() {
        // TODO: Implement
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
        
        @return cycles: 0
    */
    int IMP() {
        fetchedVal = A;
        return 0;
    }

    /*
        Addressing Mode: Immediate
        The operand is the next byte in memory.
        
        @return cycles: 0
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
        absoluteAddress = (int) memory.readWord(PC++);
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
    int INX() {
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
    int INY() {
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
}