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

import com.lattenes.Core.Memory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MOS6502 {
    private int PC;
    private short SP;
    private byte processorStatusWord;
    private short A;
    private short X;
    private short Y;

    private boolean logging = false;

    private int fetchedVal = 0;
    private int temp = 0;
    private int absoluteAddress = 0;
    private int relativeAddress = 0;
    private int opcode = 0;
    private int cycles = 0;
    private long cyclesCount = 0;
    private File logFile;
    private FileWriter logFileWriter;

    private final String logName = "MOS6502execlog.txt";

    MOS6502Instr instruction;

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

    public byte[] dumpState() {
        ArrayList<byte[]> state = new ArrayList<byte[]>();
        state.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(PC).array());
        state.add(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(SP).array());
        state.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(processorStatusWord).array());
        state.add(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(A).array());
        state.add(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(X).array());
        state.add(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(Y).array());
        state.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(cycles).array());

        byte[] stateArray = new byte[17];
        int k = 0;
        for (byte[] array : state) {
            for (int i = 0; i < array.length; i++) {
                stateArray[k++] = array[i];
            }
        }
        
        return stateArray;
    }

    public void loadState(byte[] state) {
        int k = 0;
        PC = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        SP = ByteBuffer.wrap(state, k, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        k += 2;
        processorStatusWord = state[k++];
        A = ByteBuffer.wrap(state, k, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        k += 2;
        X = ByteBuffer.wrap(state, k, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        k += 2;
        Y = ByteBuffer.wrap(state, k, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        k += 2;
        cycles = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public boolean doneProcessingInstruction() {
        return cycles == 0;
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

    private boolean systemRequestingNMI() {
        return memory.pollNMI();
    }

    public MOS6502(Memory memory, boolean logging) {
        this.memory = memory;
        opcodes = new ArrayList<MOS6502Instr>();
        this.logging = logging;

        PC = 0xC000;
        SP = 0xFD;
        processorStatusWord = 0x24;
        A = 0x00;
        X = 0x00;
        Y = 0x00;

        if (logging) {
            try {
                logFile = new File(logName);
                if (!logFile.exists()) {
                    logFile.createNewFile();
                }
                logFileWriter = new FileWriter(logFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        opcodes.add(new MOS6502Instr(this, 0x00, 7, MOS6502AddressMode.IMP, MOS6502Assembly.BRK));
        opcodes.add(new MOS6502Instr(this, 0x01, 6, MOS6502AddressMode.IZX, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x02, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x03, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x04, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x05, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x06, 5, MOS6502AddressMode.ZPG, MOS6502Assembly.ASL));
        opcodes.add(new MOS6502Instr(this, 0x07, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x08, 3, MOS6502AddressMode.IMP, MOS6502Assembly.PHP));
        opcodes.add(new MOS6502Instr(this, 0x09, 2, MOS6502AddressMode.IMM, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x0A, 2, MOS6502AddressMode.ACC, MOS6502Assembly.ASL));
        opcodes.add(new MOS6502Instr(this, 0x0B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x0C, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x0D, 4, MOS6502AddressMode.ABS, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x0E, 6, MOS6502AddressMode.ABS, MOS6502Assembly.ASL));
        opcodes.add(new MOS6502Instr(this, 0x0F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x10, 2, MOS6502AddressMode.REL, MOS6502Assembly.BPL));
        opcodes.add(new MOS6502Instr(this, 0x11, 5, MOS6502AddressMode.IZY, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x12, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x13, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x14, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x15, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x16, 6, MOS6502AddressMode.ZPX, MOS6502Assembly.ASL));
        opcodes.add(new MOS6502Instr(this, 0x17, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x18, 2, MOS6502AddressMode.IMP, MOS6502Assembly.CLC));
        opcodes.add(new MOS6502Instr(this, 0x19, 4, MOS6502AddressMode.ABY, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x1A, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x1B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x1C, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x1D, 4, MOS6502AddressMode.ABX, MOS6502Assembly.ORA));
        opcodes.add(new MOS6502Instr(this, 0x1E, 7, MOS6502AddressMode.ABX, MOS6502Assembly.ASL));
        opcodes.add(new MOS6502Instr(this, 0x1F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x20, 6, MOS6502AddressMode.ABS, MOS6502Assembly.JSR));
        opcodes.add(new MOS6502Instr(this, 0x21, 6, MOS6502AddressMode.IZX, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x22, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x23, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x24, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.BIT));
        opcodes.add(new MOS6502Instr(this, 0x25, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x26, 5, MOS6502AddressMode.ZPG, MOS6502Assembly.ROL));
        opcodes.add(new MOS6502Instr(this, 0x27, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x28, 4, MOS6502AddressMode.IMP, MOS6502Assembly.PLP));
        opcodes.add(new MOS6502Instr(this, 0x29, 2, MOS6502AddressMode.IMM, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x2A, 2, MOS6502AddressMode.ACC, MOS6502Assembly.ROL));
        opcodes.add(new MOS6502Instr(this, 0x2B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x2C, 4, MOS6502AddressMode.ABS, MOS6502Assembly.BIT));
        opcodes.add(new MOS6502Instr(this, 0x2D, 4, MOS6502AddressMode.ABS, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x2E, 6, MOS6502AddressMode.ABS, MOS6502Assembly.ROL));
        opcodes.add(new MOS6502Instr(this, 0x2F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x30, 2, MOS6502AddressMode.REL, MOS6502Assembly.BMI));
        opcodes.add(new MOS6502Instr(this, 0x31, 5, MOS6502AddressMode.IZY, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x32, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x33, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x34, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x35, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x36, 6, MOS6502AddressMode.ZPX, MOS6502Assembly.ROL));
        opcodes.add(new MOS6502Instr(this, 0x37, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x38, 2, MOS6502AddressMode.IMP, MOS6502Assembly.SEC));
        opcodes.add(new MOS6502Instr(this, 0x39, 4, MOS6502AddressMode.ABY, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x3A, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x3B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x3C, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x3D, 4, MOS6502AddressMode.ABX, MOS6502Assembly.AND));
        opcodes.add(new MOS6502Instr(this, 0x3E, 7, MOS6502AddressMode.ABX, MOS6502Assembly.ROL));
        opcodes.add(new MOS6502Instr(this, 0x3F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x40, 6, MOS6502AddressMode.IMP, MOS6502Assembly.RTI));
        opcodes.add(new MOS6502Instr(this, 0x41, 6, MOS6502AddressMode.IZX, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x42, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x43, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));    
        opcodes.add(new MOS6502Instr(this, 0x44, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x45, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x46, 5, MOS6502AddressMode.ZPG, MOS6502Assembly.LSR));
        opcodes.add(new MOS6502Instr(this, 0x47, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x48, 3, MOS6502AddressMode.IMP, MOS6502Assembly.PHA));
        opcodes.add(new MOS6502Instr(this, 0x49, 2, MOS6502AddressMode.IMM, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x4A, 2, MOS6502AddressMode.ACC, MOS6502Assembly.LSR));
        opcodes.add(new MOS6502Instr(this, 0x4B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x4C, 3, MOS6502AddressMode.ABS, MOS6502Assembly.JMP));
        opcodes.add(new MOS6502Instr(this, 0x4D, 4, MOS6502AddressMode.ABS, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x4E, 6, MOS6502AddressMode.ABS, MOS6502Assembly.LSR));
        opcodes.add(new MOS6502Instr(this, 0x4F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x50, 2, MOS6502AddressMode.REL, MOS6502Assembly.BVC));
        opcodes.add(new MOS6502Instr(this, 0x51, 5, MOS6502AddressMode.IZY, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x52, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x53, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x54, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x55, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x56, 6, MOS6502AddressMode.ZPX, MOS6502Assembly.LSR));
        opcodes.add(new MOS6502Instr(this, 0x57, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x58, 2, MOS6502AddressMode.IMP, MOS6502Assembly.CLI));
        opcodes.add(new MOS6502Instr(this, 0x59, 4, MOS6502AddressMode.ABY, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x5A, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x5B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x5C, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x5D, 4, MOS6502AddressMode.ABX, MOS6502Assembly.EOR));
        opcodes.add(new MOS6502Instr(this, 0x5E, 7, MOS6502AddressMode.ABX, MOS6502Assembly.LSR));
        opcodes.add(new MOS6502Instr(this, 0x5F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x60, 6, MOS6502AddressMode.IMP, MOS6502Assembly.RTS));
        opcodes.add(new MOS6502Instr(this, 0x61, 6, MOS6502AddressMode.IZX, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x62, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x63, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x64, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x65, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x66, 5, MOS6502AddressMode.ZPG, MOS6502Assembly.ROR));
        opcodes.add(new MOS6502Instr(this, 0x67, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x68, 4, MOS6502AddressMode.IMP, MOS6502Assembly.PLA));
        opcodes.add(new MOS6502Instr(this, 0x69, 2, MOS6502AddressMode.IMM, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x6A, 2, MOS6502AddressMode.ACC, MOS6502Assembly.ROR));
        opcodes.add(new MOS6502Instr(this, 0x6B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));  
        opcodes.add(new MOS6502Instr(this, 0x6C, 5, MOS6502AddressMode.IND, MOS6502Assembly.JMP));
        opcodes.add(new MOS6502Instr(this, 0x6D, 4, MOS6502AddressMode.ABS, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x6E, 6, MOS6502AddressMode.ABS, MOS6502Assembly.ROR));
        opcodes.add(new MOS6502Instr(this, 0x6F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
    
        opcodes.add(new MOS6502Instr(this, 0x70, 2, MOS6502AddressMode.REL, MOS6502Assembly.BVS));
        opcodes.add(new MOS6502Instr(this, 0x71, 5, MOS6502AddressMode.IZY, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x72, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x73, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x74, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x75, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x76, 6, MOS6502AddressMode.ZPX, MOS6502Assembly.ROR));
        opcodes.add(new MOS6502Instr(this, 0x77, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x78, 2, MOS6502AddressMode.IMP, MOS6502Assembly.SEI));
        opcodes.add(new MOS6502Instr(this, 0x79, 4, MOS6502AddressMode.ABY, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x7A, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x7B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x7C, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x7D, 4, MOS6502AddressMode.ABX, MOS6502Assembly.ADC));
        opcodes.add(new MOS6502Instr(this, 0x7E, 7, MOS6502AddressMode.ABX, MOS6502Assembly.ROR));
        opcodes.add(new MOS6502Instr(this, 0x7F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x80, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x81, 6, MOS6502AddressMode.IZX, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x82, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x83, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x84, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.STY));
        opcodes.add(new MOS6502Instr(this, 0x85, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x86, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.STX));
        opcodes.add(new MOS6502Instr(this, 0x87, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x88, 2, MOS6502AddressMode.IMP, MOS6502Assembly.DEY));
        opcodes.add(new MOS6502Instr(this, 0x89, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x8A, 2, MOS6502AddressMode.IMP, MOS6502Assembly.TXA));
        opcodes.add(new MOS6502Instr(this, 0x8B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x8C, 4, MOS6502AddressMode.ABS, MOS6502Assembly.STY));
        opcodes.add(new MOS6502Instr(this, 0x8D, 4, MOS6502AddressMode.ABS, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x8E, 4, MOS6502AddressMode.ABS, MOS6502Assembly.STX));
        opcodes.add(new MOS6502Instr(this, 0x8F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0x90, 2, MOS6502AddressMode.REL, MOS6502Assembly.BCC));
        opcodes.add(new MOS6502Instr(this, 0x91, 6, MOS6502AddressMode.IZY, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x92, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x93, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x94, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.STY));
        opcodes.add(new MOS6502Instr(this, 0x95, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x96, 4, MOS6502AddressMode.ZPY, MOS6502Assembly.STX));
        opcodes.add(new MOS6502Instr(this, 0x97, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x98, 2, MOS6502AddressMode.IMP, MOS6502Assembly.TYA));
        opcodes.add(new MOS6502Instr(this, 0x99, 5, MOS6502AddressMode.ABY, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x9A, 2, MOS6502AddressMode.IMP, MOS6502Assembly.TXS));
        opcodes.add(new MOS6502Instr(this, 0x9B, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x9C, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x9D, 5, MOS6502AddressMode.ABX, MOS6502Assembly.STA));
        opcodes.add(new MOS6502Instr(this, 0x9E, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0x9F, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0xA0, 2, MOS6502AddressMode.IMM, MOS6502Assembly.LDY));
        opcodes.add(new MOS6502Instr(this, 0xA1, 6, MOS6502AddressMode.IZX, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xA2, 2, MOS6502AddressMode.IMM, MOS6502Assembly.LDX));
        opcodes.add(new MOS6502Instr(this, 0xA3, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xA4, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.LDY));
        opcodes.add(new MOS6502Instr(this, 0xA5, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xA6, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.LDX));
        opcodes.add(new MOS6502Instr(this, 0xA7, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xA8, 2, MOS6502AddressMode.IMP, MOS6502Assembly.TAY));
        opcodes.add(new MOS6502Instr(this, 0xA9, 2, MOS6502AddressMode.IMM, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xAA, 2, MOS6502AddressMode.IMP, MOS6502Assembly.TAX));
        opcodes.add(new MOS6502Instr(this, 0xAB, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xAC, 4, MOS6502AddressMode.ABS, MOS6502Assembly.LDY));
        opcodes.add(new MOS6502Instr(this, 0xAD, 4, MOS6502AddressMode.ABS, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xAE, 4, MOS6502AddressMode.ABS, MOS6502Assembly.LDX));
        opcodes.add(new MOS6502Instr(this, 0xAF, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0xB0, 2, MOS6502AddressMode.REL, MOS6502Assembly.BCS));
        opcodes.add(new MOS6502Instr(this, 0xB1, 5, MOS6502AddressMode.IZY, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xB2, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xB3, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xB4, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.LDY));
        opcodes.add(new MOS6502Instr(this, 0xB5, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xB6, 4, MOS6502AddressMode.ZPY, MOS6502Assembly.LDX));
        opcodes.add(new MOS6502Instr(this, 0xB7, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xB8, 2, MOS6502AddressMode.IMP, MOS6502Assembly.CLV));
        opcodes.add(new MOS6502Instr(this, 0xB9, 4, MOS6502AddressMode.ABY, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xBA, 2, MOS6502AddressMode.IMP, MOS6502Assembly.TSX));
        opcodes.add(new MOS6502Instr(this, 0xBB, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xBC, 4, MOS6502AddressMode.ABX, MOS6502Assembly.LDY));
        opcodes.add(new MOS6502Instr(this, 0xBD, 4, MOS6502AddressMode.ABX, MOS6502Assembly.LDA));
        opcodes.add(new MOS6502Instr(this, 0xBE, 4, MOS6502AddressMode.ABY, MOS6502Assembly.LDX));
        opcodes.add(new MOS6502Instr(this, 0xBF, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0xC0, 2, MOS6502AddressMode.IMM, MOS6502Assembly.CPY));
        opcodes.add(new MOS6502Instr(this, 0xC1, 6, MOS6502AddressMode.IZX, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xC2, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xC3, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xC4, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.CPY));
        opcodes.add(new MOS6502Instr(this, 0xC5, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xC6, 5, MOS6502AddressMode.ZPG, MOS6502Assembly.DEC));
        opcodes.add(new MOS6502Instr(this, 0xC7, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xC8, 2, MOS6502AddressMode.IMP, MOS6502Assembly.INY));
        opcodes.add(new MOS6502Instr(this, 0xC9, 2, MOS6502AddressMode.IMM, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xCA, 2, MOS6502AddressMode.IMP, MOS6502Assembly.DEX));
        opcodes.add(new MOS6502Instr(this, 0xCB, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xCC, 4, MOS6502AddressMode.ABS, MOS6502Assembly.CPY));
        opcodes.add(new MOS6502Instr(this, 0xCD, 4, MOS6502AddressMode.ABS, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xCE, 6, MOS6502AddressMode.ABS, MOS6502Assembly.DEC));
        opcodes.add(new MOS6502Instr(this, 0xCF, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0xD0, 2, MOS6502AddressMode.REL, MOS6502Assembly.BNE));
        opcodes.add(new MOS6502Instr(this, 0xD1, 5, MOS6502AddressMode.IZY, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xD2, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xD3, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xD4, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xD5, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xD6, 6, MOS6502AddressMode.ZPX, MOS6502Assembly.DEC));
        opcodes.add(new MOS6502Instr(this, 0xD7, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xD8, 2, MOS6502AddressMode.IMP, MOS6502Assembly.CLD));
        opcodes.add(new MOS6502Instr(this, 0xD9, 4, MOS6502AddressMode.ABY, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xDA, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xDB, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xDC, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xDD, 4, MOS6502AddressMode.ABX, MOS6502Assembly.CMP));
        opcodes.add(new MOS6502Instr(this, 0xDE, 7, MOS6502AddressMode.ABX, MOS6502Assembly.DEC));
        opcodes.add(new MOS6502Instr(this, 0xDF, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0xE0, 2, MOS6502AddressMode.IMM, MOS6502Assembly.CPX));
        opcodes.add(new MOS6502Instr(this, 0xE1, 6, MOS6502AddressMode.IZX, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xE2, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xE3, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xE4, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.CPX));
        opcodes.add(new MOS6502Instr(this, 0xE5, 3, MOS6502AddressMode.ZPG, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xE6, 5, MOS6502AddressMode.ZPG, MOS6502Assembly.INC));
        opcodes.add(new MOS6502Instr(this, 0xE7, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xE8, 2, MOS6502AddressMode.IMP, MOS6502Assembly.INX));
        opcodes.add(new MOS6502Instr(this, 0xE9, 2, MOS6502AddressMode.IMM, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xEA, 2, MOS6502AddressMode.IMP, MOS6502Assembly.NOP));
        opcodes.add(new MOS6502Instr(this, 0xEB, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xEC, 4, MOS6502AddressMode.ABS, MOS6502Assembly.CPX));
        opcodes.add(new MOS6502Instr(this, 0xED, 4, MOS6502AddressMode.ABS, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xEE, 6, MOS6502AddressMode.ABS, MOS6502Assembly.INC));
        opcodes.add(new MOS6502Instr(this, 0xEF, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));

        opcodes.add(new MOS6502Instr(this, 0xF0, 2, MOS6502AddressMode.REL, MOS6502Assembly.BEQ));
        opcodes.add(new MOS6502Instr(this, 0xF1, 5, MOS6502AddressMode.IZY, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xF2, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xF3, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xF4, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xF5, 4, MOS6502AddressMode.ZPX, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xF6, 6, MOS6502AddressMode.ZPX, MOS6502Assembly.INC));
        opcodes.add(new MOS6502Instr(this, 0xF7, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xF8, 2, MOS6502AddressMode.IMP, MOS6502Assembly.SED));
        opcodes.add(new MOS6502Instr(this, 0xF9, 4, MOS6502AddressMode.ABY, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xFA, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xFB, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xFC, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
        opcodes.add(new MOS6502Instr(this, 0xFD, 4, MOS6502AddressMode.ABX, MOS6502Assembly.SBC));
        opcodes.add(new MOS6502Instr(this, 0xFE, 7, MOS6502AddressMode.ABX, MOS6502Assembly.INC));
        opcodes.add(new MOS6502Instr(this, 0xFF, 2, MOS6502AddressMode.IMP, MOS6502Assembly.XXX));
    }

    public void endLog() {
        if (logFileWriter != null) {
            try {
                logFileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String disassembly() {
        int debugPC = (PC - 1) & 0xFFFF;
        int debugOpcode = opcode;
        MOS6502Instr debugInstr = opcodes.get(opcode);
        MOS6502AddressMode debugAddressMode = debugInstr.addressingMode;
        MOS6502Assembly debugAssembly = debugInstr.assembly;
        String mnemonic = debugAssembly.getMnemonic();

        String byteString = String.format("%02X", debugOpcode) + "       ";
        int operand1 = 0;
        int operand2 = 0;

        switch (debugAddressMode) {
            case IMP:
            case ACC:
                break;
            case REL:
            case IZY:
            case IZX:
            case ZPG:
            case ZPX:
            case ZPY:
            case IMM:
                operand1 = memory.readWord(debugPC + 1);
                byteString = String.format("%02X", debugOpcode) + " " + String.format("%02X", operand1);
                byteString += "    ";
                break;
            default:
                operand1 = memory.readWord(debugPC + 1);
                operand2 = memory.readWord(debugPC + 2);
                byteString = String.format("%02X", debugOpcode) + " " + String.format("%02X", operand1) 
                             + " " + String.format("%02X", operand2);
                byteString += " ";
                break;
        }
        int addr;
        switch (debugAddressMode) {
            case IMP:
                break;
            case ACC:
                mnemonic += " A";
                break;
            case REL:
                debugPC += 2;
                if ((operand1 & 0x80) != 0) {
                    operand1 |= 0xFFFFFF00;
                }
                debugPC += operand1;
                debugPC &= 0xFFFF;
                mnemonic += " " + String.format("$%04X", debugPC);
                break;
            case IZY:
                addr = (memory.readWord((operand1 + 1) & 0x00FF) << 8) | memory.readWord(operand1 & 0x00FF);
                mnemonic += " " + String.format("($%02X),Y", operand1) 
                        + " = " + String.format("%04X", addr & 0xFFFF)
                        + " @ " + String.format("%04X", (addr + Y) & 0xFFFF)
                        + " = " + String.format("%02X", memory.readWord((addr + Y) & 0xFFFF ));
                break;
            case IZX:
                addr = (memory.readWord((operand1 + X + 1) & 0x00FF) << 8) | memory.readWord((operand1 + X) & 0x00FF);
                mnemonic += " " + String.format("($%02X,X)", operand1) 
                        + " @ " + String.format("%02X", (operand1 + X) & 0x00FF)
                        + " = " + String.format("%04X", addr)
                        + " = " + String.format("%02X", memory.readWord(addr));
                break;
            case ZPG:
                mnemonic += " " + String.format("$%02X", operand1) + " = " 
                        + String.format("%02X", memory.readWord(operand1));
                break;
            case ZPX:
                mnemonic += " " + String.format("$%02X,X", operand1) + " @ " 
                        + String.format("%02X", (operand1 + X) & 0xFF) + " = "
                        + String.format("%02X", memory.readWord((operand1 + X) & 0xFF));
                break;
            case ZPY:
                mnemonic += " " + String.format("$%02X,Y", operand1) + " @ " 
                        + String.format("%02X", (operand1 + Y) & 0xFF) + " = "
                        + String.format("%02X", memory.readWord((operand1 + Y) & 0xFF));
                break;
            case IMM:
                mnemonic += " " + String.format("#$%02X", operand1);
                break;
            case ABS:
                mnemonic += " " + String.format("$%04X", ((operand2 << 8) | operand1));
                if (debugAssembly != MOS6502Assembly.JMP && debugAssembly != MOS6502Assembly.JSR) {
                    mnemonic += " = " + String.format("%02X", memory.readWord((operand2 << 8) | operand1));
                }
                break;
            case ABX:
                addr = (operand2 << 8) | operand1;
                mnemonic += " " + String.format("$%04X,X", addr)
                        + " @ " + String.format("%04X", (addr + X) & 0xFFFF) 
                        + " = " + String.format("%02X", memory.readWord((addr + X) & 0xFFFF));
                break;
            case ABY:
                addr = (operand2 << 8) | operand1;
                mnemonic += " " + String.format("$%04X,Y", addr)
                        + " @ " + String.format("%04X", (addr + Y) & 0xFFFF) 
                        + " = " + String.format("%02X", memory.readWord((addr + Y) & 0xFFFF));
                break;
            case IND:
                addr = (operand2 << 8) | operand1;
                int hiByte;
                if (operand1 == 0xFF) {
                    hiByte = memory.readWord(addr & 0xFF00);
                } else {
                    hiByte = memory.readWord((addr + 1) & 0xFFFF);
                }
                mnemonic += " " + String.format("($%04X)", addr) + " = " 
                        + String.format("%02X", hiByte) 
                        + String.format("%02X", memory.readWord(addr));
                break;
        }

        return byteString + " " + mnemonic;
    }

    private String registers() {
        StringBuilder sb = new StringBuilder();
        sb.append("A:" + String.format("%02X", A));
        sb.append(" X:" + String.format("%02X", X));
        sb.append(" Y:" + String.format("%02X", Y));
        sb.append(" P:" + String.format("%02X", processorStatusWord));
        sb.append(" SP:" + String.format("%02X", SP));
        return sb.toString();
    }

    public void clock() {
        if (cycles == 0) {
            if (systemRequestingNMI()) {
                memory.clearNMI();
                NMI();
                return;
            }

            setFlag(ProcessorStatusWordFlag.U, true);

            // Get the next opcode
            opcode = memory.readWord(PC++);

            // Log this execution
            if (logging) {
                try {
                    final int spaceLength = 29;
                    logFileWriter.write(String.format("%04X", (PC - 1) & 0xFFFF));
                    logFileWriter.write("  ");
                    String disasm = disassembly();
                    logFileWriter.write(disasm);
                    int spaces = spaceLength - (disasm.length() - 13);
                    for (int i = 0; i < spaces; i++) {
                        logFileWriter.write(" ");
                    }
                    logFileWriter.write(registers());
                    logFileWriter.write("\n");
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }

            // PC is always 2 bytes, but in this class
            // we have it as an int
            PC &= 0xFFFF;

            instruction = opcodes.get(opcode);
            cycles = instruction.cycles;

            // Execute the instruction
            int result1 = instruction.processAddressingMode();
            int result2 = instruction.execute();

            // Determine if we need an additional clock cycle.
            // This isn't applicable in branch instructions
            // those instructions directly add to the
            // cycles variable based on the conditions
            // of the branch.
            cycles += (result1 & result2) != 0 ? 1 : 0;
        }
        cyclesCount++;
        cycles--;
    } 

    private int fetch() {
        MOS6502AddressMode mode = opcodes.get(opcode).addressingMode;
        if (mode != MOS6502AddressMode.IMP && mode != MOS6502AddressMode.ACC) {
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
    public boolean IRQ() {
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
            return true;
        }
        return false;
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

        SP = 0xFD;
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

        if ((ptr & 0x00FF) == 0x00FF) {
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
        if ((relativeAddress & 0x80) != 0) {
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
        setFlag(ProcessorStatusWordFlag.V, ((~(A ^ fetchedVal) & (A ^ temp)) & 0x80) != 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) != 0);

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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);
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

        setFlag(ProcessorStatusWordFlag.C, (fetchedVal & 0x80) != 0);
        fetchedVal <<= 1;
        fetchedVal &= 0xFF;
        setFlag(ProcessorStatusWordFlag.Z, fetchedVal == 0);
        setFlag(ProcessorStatusWordFlag.N, (fetchedVal & 0x80) != 0);

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
        setFlag(ProcessorStatusWordFlag.N, (fetchedVal & 0x80) != 0);
        setFlag(ProcessorStatusWordFlag.V, (fetchedVal & 0x40) != 0);

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
            absoluteAddress = (PC + relativeAddress) & 0xFFFF;

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
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) != 0);
        
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
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) != 0);
        
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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);
        
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
        setFlag(ProcessorStatusWordFlag.N, (result & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) != 0);
        
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
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) != 0);
        
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
        PC &= 0xFFFF;

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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);

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
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) != 0);

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
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) != 0);

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

        setFlag(ProcessorStatusWordFlag.C, (fetchedVal & 0x01) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);

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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);

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
        setFlag(ProcessorStatusWordFlag.B, false);
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
        setFlag(ProcessorStatusWordFlag.C, (temp & 0x100) != 0);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.C, (fetchedVal & 0x1) != 0);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) != 0);
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
        temp = A + val + (getFlag(ProcessorStatusWordFlag.C) ? 1 : 0);
        setFlag(ProcessorStatusWordFlag.C, temp > 255);
        setFlag(ProcessorStatusWordFlag.Z, (temp & 0xFF) == 0);
        setFlag(ProcessorStatusWordFlag.V, ((~(A ^ val) & (A ^ temp)) & 0x80) != 0);
        setFlag(ProcessorStatusWordFlag.N, (temp & 0x80) != 0);

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
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (Y & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (X & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);
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
        setFlag(ProcessorStatusWordFlag.N, (A & 0x80) != 0);
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