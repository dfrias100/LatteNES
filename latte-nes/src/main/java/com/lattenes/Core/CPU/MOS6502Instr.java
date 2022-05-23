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
    public int opcode;
    public int cycles;
    public MOS6502AddressMode addressingMode;
    public MOS6502Assembly assembly;
    
    public MOS6502Instr(MOS6502 CPU, int opcode, int cycles, MOS6502AddressMode addressingMode, MOS6502Assembly assembly) {
        this.opcode = opcode;
        this.cycles = cycles;
        this.addressingMode = addressingMode;
        this.assembly = assembly;

        switch (addressingMode) {
            case ACC: 
            case IMP:
                instrAddressingMode = CPU::IMP; break;
            case ABS: instrAddressingMode = CPU::ABS; break;
            case ABX: instrAddressingMode = CPU::ABX; break;
            case ABY: instrAddressingMode = CPU::ABY; break;
            case IMM: instrAddressingMode = CPU::IMM; break;
            case IND: instrAddressingMode = CPU::IND; break;
            case IZX: instrAddressingMode = CPU::IZX; break;
            case IZY: instrAddressingMode = CPU::IZY; break;
            case REL: instrAddressingMode = CPU::REL; break;
            case ZPG: instrAddressingMode = CPU::ZPG; break;
            case ZPX: instrAddressingMode = CPU::ZPX; break;
            case ZPY: instrAddressingMode = CPU::ZPY; break;
        }

        switch (assembly) {
            case ADC: instrFunc = CPU::ADC; break;
            case AND: instrFunc = CPU::AND; break;
            case ASL: instrFunc = CPU::ASL; break;
            case BCC: instrFunc = CPU::BCC; break;
            case BCS: instrFunc = CPU::BCS; break;
            case BEQ: instrFunc = CPU::BEQ; break;
            case BIT: instrFunc = CPU::BIT; break;
            case BMI: instrFunc = CPU::BMI; break;
            case BNE: instrFunc = CPU::BNE; break;
            case BPL: instrFunc = CPU::BPL; break;
            case BRK: instrFunc = CPU::BRK; break;
            case BVC: instrFunc = CPU::BVC; break;
            case BVS: instrFunc = CPU::BVS; break;
            case CLC: instrFunc = CPU::CLC; break;
            case CLD: instrFunc = CPU::CLD; break;
            case CLI: instrFunc = CPU::CLI; break;
            case CLV: instrFunc = CPU::CLV; break;
            case CMP: instrFunc = CPU::CMP; break;
            case CPX: instrFunc = CPU::CPX; break;
            case CPY: instrFunc = CPU::CPY; break;
            case DEC: instrFunc = CPU::DEC; break;
            case DEX: instrFunc = CPU::DEX; break;
            case DEY: instrFunc = CPU::DEY; break;
            case EOR: instrFunc = CPU::EOR; break;
            case INC: instrFunc = CPU::INC; break;
            case INX: instrFunc = CPU::INX; break;
            case INY: instrFunc = CPU::INY; break;
            case JMP: instrFunc = CPU::JMP; break;
            case JSR: instrFunc = CPU::JSR; break;
            case LDA: instrFunc = CPU::LDA; break;
            case LDX: instrFunc = CPU::LDX; break;
            case LDY: instrFunc = CPU::LDY; break;
            case LSR: instrFunc = CPU::LSR; break;
            case NOP: instrFunc = CPU::NOP; break;
            case ORA: instrFunc = CPU::ORA; break;
            case PHA: instrFunc = CPU::PHA; break;
            case PHP: instrFunc = CPU::PHP; break;
            case PLA: instrFunc = CPU::PLA; break;
            case PLP: instrFunc = CPU::PLP; break;
            case ROL: instrFunc = CPU::ROL; break;
            case ROR: instrFunc = CPU::ROR; break;
            case RTI: instrFunc = CPU::RTI; break;
            case RTS: instrFunc = CPU::RTS; break;
            case SBC: instrFunc = CPU::SBC; break;
            case SEC: instrFunc = CPU::SEC; break;
            case SED: instrFunc = CPU::SED; break;
            case SEI: instrFunc = CPU::SEI; break;
            case STA: instrFunc = CPU::STA; break;
            case STX: instrFunc = CPU::STX; break;
            case STY: instrFunc = CPU::STY; break;
            case TAX: instrFunc = CPU::TAX; break;
            case TAY: instrFunc = CPU::TAY; break;
            case TSX: instrFunc = CPU::TSX; break;
            case TXA: instrFunc = CPU::TXA; break;
            case TXS: instrFunc = CPU::TXS; break;
            case TYA: instrFunc = CPU::TYA; break;
            case XXX: instrFunc = CPU::XXX; break;
        }
    }

    public int execute() {
        return instrFunc.get();
    }

    public int processAddressingMode() {
        return instrAddressingMode.get();
    }
}
