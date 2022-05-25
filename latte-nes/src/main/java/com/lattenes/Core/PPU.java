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

package com.lattenes.Core;

import com.lattenes.Core.Cartridge.Cartridge;
import com.lattenes.Core.Cartridge.Mirror;

public class PPU {
    private byte[] palletteTable;
    private float[][] paletteColors;
    private byte[] vRAM;
    private byte[] OAMData;
    float[] screen;
    boolean frameReady = false;

    private Mirror mirroring;

    private Cartridge cartridge;

    private enum ControlRegisterEnum {
        Nametable1      (0b00000001), // This is the base address of the first nametable 
        Nametable2      (0b00000010), // 0b00 = $2000, 0b01 = $2400, 0b10 = $2800, 0b11 = $2C00
        Increment       (0b00000100), // VRAM address increment, 0: add 1, going across 1: add 32, going down        SpriteTable     (0b00001000),
        BackgroundTable (0b00010000), // Sprite pattern 0b0: $0000, 0b1: $1000, ignored in 8x16 mode
        SpriteSize      (0b00100000), // Background pattern 0b0: $0000, 0b1: $1000
        MasterSlave     (0b01000000), // Master/Slave select, 0: read backdrop from EXT pins, 1: output color on EXT pins
        NMI             (0b10000000); // Generate an NMI at the start of the vertical blanking interval

        private byte value;

        ControlRegisterEnum(int value) {
            this.value = (byte) value;
        }
    }

    private enum PPUStatusEnum {
        Unused          (0b00011111),
        SpriteOverflow  (0b00100000),
        Sprite0Hit      (0b01000000),
        VerticalBlank   (0b10000000);

        private byte value;

        PPUStatusEnum(int value) {
            this.value = (byte) value;
        }
    }

    private enum PPUMaskEnum {
        Grayscale           (0b00000001),
        BGLeftColEnable     (0b00000010),
        SpriteLeftColEnable (0b00000100),
        BGEnable            (0b00001000),
        SpriteEnable        (0b00010000),
        RedEnable           (0b00100000),
        GreenEnable         (0b01000000),
        BlueEnable          (0b10000000);

        private byte value;

        PPUMaskEnum(int value) {
            this.value = (byte) value;
        }
    }

    private enum VRAMAddressEnum {
        CoarseXScroll (0b000000000011111),
        CoarseYScroll (0b000001111100000),
        NametableSel  (0b000110000000000),
        FineYScroll   (0b111000000000000);

        private short value;

        VRAMAddressEnum(int value) {
            this.value = (short) value;
        }
    }

    private byte controlRegister = 0x00;
    private byte statusRegister = 0x00;
    private byte maskRegister = 0x00;

    private short VRAMAddress;
    private short TRAMAddress;

    private short fineXScroll;
    private boolean reqNMI;

    public PPU(Cartridge cartridge) {
        this.cartridge = cartridge;
        this.palletteTable = new byte[0x20];
        this.vRAM = new byte[0x800];
        this.OAMData = new byte[0x100];
        this.paletteColors = new float[0x40][3];
        this.screen = new float[256 * 240 * 4];
    }

    private short getIncrement() {
        if ((controlRegister & ControlRegisterEnum.Increment.value) == 0) {
            return 1;
        } else {
            return 32;
        }
    }

    public byte readPPUFromCPU(int address) {
        byte data = 0x00;
        address &= 0x7;
        switch (address) {
            case 0: break;
            case 1: break;
            case 2:
                // Status register
                break;
            case 3: break;
            case 4: break;
            case 5: break;
            case 6: break;
            case 7:
                // Data register
                break;
        }
        return data;
    }

    public void writeToPPUFromCPU(int address, byte value) {
        address &= 0x7;
        switch (address) {
            case 0:
                // Control register 
                break;
            case 1:
                // Mask register
                break;
            case 2: break;
            case 3: 
                // OAM address
                break;
            case 4:
                // OAM data 
                break;
            case 5:
                // Scroll 
                break;
            case 6:
                // PPU Address 
                break;
            case 7:
                // Data register
                break;
        }
    }

    private void writeToPPUBus(int address, byte value) {

    }

    private byte readFromPPUBus(int address) {
        return 0x00;
    }

    public void clock() {

    }

    public boolean requestingNMI() {
        return reqNMI;
    }

    public void clearNMI() {
        reqNMI = false;
    }
}
