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
