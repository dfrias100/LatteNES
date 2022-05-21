package com.lattenes.CPU;

public enum MOS6502AddressMode {
    ACC, // Accumulator
    ABS, // Absolute
    ABX, // Absolute with X offset
    ABY, // Absolute with Y offset
    IMM, // Immediate
    IMP, // Implied
    IND, // Indirect
    INX, // Indirect with X offset
    INY, // Indirect with Y offset
    REL, // Relative
    ZPG, // Zero Page
    ZPX, // Zero Page with X offset
    ZPY  // Zero Page with Y offset

    // More details can be found within the MOS6502.java file
}
