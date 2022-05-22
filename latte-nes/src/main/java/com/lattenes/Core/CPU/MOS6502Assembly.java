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

public enum MOS6502Assembly {
    // The MOS 6502 has 255 opcodes, 56 official instructions, and 23 unofficial instructions
    // This enum is used to define which opcodes are official and which are unofficial
    // along with their assembly mnemonics

    // Official instructions
    ADC("ADC", MOS6502Assembly.Official),
    AND("AND", MOS6502Assembly.Official),
    ASL("ASL", MOS6502Assembly.Official),
    BCC("BCC", MOS6502Assembly.Official),
    BCS("BCS", MOS6502Assembly.Official),
    BEQ("BEQ", MOS6502Assembly.Official),
    BIT("BIT", MOS6502Assembly.Official),
    BMI("BMI", MOS6502Assembly.Official),
    BNE("BNE", MOS6502Assembly.Official),
    BPL("BPL", MOS6502Assembly.Official),
    BRK("BRK", MOS6502Assembly.Official),
    BVC("BVC", MOS6502Assembly.Official),
    BVS("BVS", MOS6502Assembly.Official),
    CLC("CLC", MOS6502Assembly.Official),
    CLD("CLD", MOS6502Assembly.Official),
    CLI("CLI", MOS6502Assembly.Official),
    CLV("CLV", MOS6502Assembly.Official),
    CMP("CMP", MOS6502Assembly.Official),
    CPX("CPX", MOS6502Assembly.Official),
    CPY("CPY", MOS6502Assembly.Official),
    DEC("DEC", MOS6502Assembly.Official),
    DEX("DEX", MOS6502Assembly.Official),
    DEY("DEY", MOS6502Assembly.Official),
    EOR("EOR", MOS6502Assembly.Official),
    INC("INC", MOS6502Assembly.Official),
    INX("INX", MOS6502Assembly.Official),
    INY("INY", MOS6502Assembly.Official),
    JMP("JMP", MOS6502Assembly.Official),
    JSR("JSR", MOS6502Assembly.Official),
    LDA("LDA", MOS6502Assembly.Official),
    LDX("LDX", MOS6502Assembly.Official),
    LDY("LDY", MOS6502Assembly.Official),
    LSR("LSR", MOS6502Assembly.Official),
    NOP("NOP", MOS6502Assembly.Official),
    ORA("ORA", MOS6502Assembly.Official),
    PHA("PHA", MOS6502Assembly.Official),
    PHP("PHP", MOS6502Assembly.Official),
    PLA("PLA", MOS6502Assembly.Official),
    PLP("PLP", MOS6502Assembly.Official),
    ROL("ROL", MOS6502Assembly.Official),
    ROR("ROR", MOS6502Assembly.Official),
    RTI("RTI", MOS6502Assembly.Official),
    RTS("RTS", MOS6502Assembly.Official),
    SBC("SBC", MOS6502Assembly.Official),
    SEC("SEC", MOS6502Assembly.Official),
    SED("SED", MOS6502Assembly.Official),
    SEI("SEI", MOS6502Assembly.Official),
    STA("STA", MOS6502Assembly.Official),
    STX("STX", MOS6502Assembly.Official),
    STY("STY", MOS6502Assembly.Official),
    TAX("TAX", MOS6502Assembly.Official),
    TAY("TAY", MOS6502Assembly.Official),
    TSX("TSX", MOS6502Assembly.Official),
    TXA("TXA", MOS6502Assembly.Official),
    TXS("TXS", MOS6502Assembly.Official),
    TYA("TYA", MOS6502Assembly.Official),

    // For now all unofficial instructions are considered the same
    // This may change in the future
    XXX("XXX", MOS6502Assembly.Unofficial);

    // More information about unofficial instructions can be found at:
    // https://www.masswerk.at/6502/6502_instruction_set.html#illegals

    // Fields
    public final String mnemonic;
    public final static int Official = 0;
    public final static int Unofficial = 1;
    public final int type;

    // Constructor
    MOS6502Assembly(String mnemonic, int type) {
        this.mnemonic = mnemonic;
        this.type = type;
    }
}
