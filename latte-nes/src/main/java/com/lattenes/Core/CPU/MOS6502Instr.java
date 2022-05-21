package com.lattenes.Core.CPU;

import java.util.function.Supplier;

public class MOS6502Instr {
    public Supplier<Integer> instrFunc;
    public Supplier<Integer> instrAddressingMode;
    public short opcode;
    public short cycles;
    public MOS6502AddressMode addressingMode;
    public MOS6502Assembly assembly;
    
    public MOS6502Instr(MOS6502 CPU, short opcode, short cycles, MOS6502AddressMode addressingMode, MOS6502Assembly assembly) {
        this.opcode = opcode;
        this.cycles = cycles;
        this.addressingMode = addressingMode;
        this.assembly = assembly;

        switch (addressingMode) {
            // TODO: Implement all addressing modes
        }

        switch (assembly) {
            // TODO: Implement all instructions
        }
    }

    public int execute() {
        return instrFunc.get();
    }

    public int processAddressingMode() {
        return instrAddressingMode.get();
    }
}
