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

import javax.print.attribute.standard.MediaSize.NA;
import javax.xml.crypto.Data;

import com.lattenes.Core.Cartridge.Cartridge;
import com.lattenes.Core.Cartridge.Mirror;
import com.lattenes.Core.Cartridge.Tuple;

public class PPU {
    private byte[] palletteTable;
    private float[][] paletteColors;
    private byte[] vRAM;
    private byte[] OAMData;
    private int cycles;
    private int scanline;
    float[] screen;
    boolean frameReady = false;

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
        CoarseXScroll (0b0000000000011111),
        CoarseYScroll (0b0000001111100000),
        NametableSel  (0b0000110000000000),
        FineYScroll   (0b0111000000000000),
        Unused        (0b1000000000000000);

        private short value;

        VRAMAddressEnum(int value) {
            this.value = (short) value;
        }
    }

    private byte controlRegister = 0x00;
    private byte statusRegister = 0x00;
    private byte maskRegister = 0x00;

    private int VRAMAddress = 0x00;
    private int TRAMAddress = 0x00;

    private short fineXScroll = 0;
    private short OAMAddress = 0;
    private boolean reqNMI = false;
    private boolean inVblank = false;
    private boolean addressLatch = false;

    private byte dataBuffer;

    public PPU(Cartridge cartridge) {
        this.cartridge = cartridge;
        this.palletteTable = new byte[0x20];
        this.vRAM = new byte[0x800];
        this.OAMData = new byte[0x100];
        this.paletteColors = new float[0x40][3];
        this.screen = new float[256 * 240 * 4];
        this.cycles = 0;
        this.scanline = 0;

        this.fineXScroll = 0;
        this.OAMAddress = 0;
        this.dataBuffer = 0;
        this.VRAMAddress = 0;
        this.TRAMAddress = 0;

        this.reqNMI = false;
        this.inVblank = false;
        this.addressLatch = false;

        // Initialize palette colors
        // Using the 2C02 layout
        // https://www.nesdev.org/wiki/PPU_palettes#2C02

        // Colors 0x0 - 0xF
        paletteColors[0x00][0] = 84.0f;
        paletteColors[0x00][1] = 84.0f;
        paletteColors[0x00][2] = 84.0f;

        paletteColors[0x01][0] = 0.0f;
        paletteColors[0x01][1] = 30.0f;
        paletteColors[0x01][2] = 116.0f;

        paletteColors[0x02][0] = 8.0f;
        paletteColors[0x02][1] = 16.0f;
        paletteColors[0x02][2] = 144.0f;

        paletteColors[0x03][0] = 48.0f;
        paletteColors[0x03][1] = 0.0f;
        paletteColors[0x03][2] = 136.0f;

        paletteColors[0x04][0] = 68.0f;
        paletteColors[0x04][1] = 0.0f;
        paletteColors[0x04][2] = 100.0f;

        paletteColors[0x05][0] = 92.0f;
        paletteColors[0x05][1] = 0.0f;
        paletteColors[0x05][2] = 48.0f;

        paletteColors[0x06][0] = 84.0f;
        paletteColors[0x06][1] = 4.0f;
        paletteColors[0x06][2] = 0.0f;

        paletteColors[0x07][0] = 60.0f;
        paletteColors[0x07][1] = 24.0f;
        paletteColors[0x07][2] = 0.0f;

        paletteColors[0x08][0] = 32.0f;
        paletteColors[0x08][1] = 42.0f;
        paletteColors[0x08][2] = 0.0f;

        paletteColors[0x09][0] = 8.0f;
        paletteColors[0x09][1] = 58.0f;
        paletteColors[0x09][2] = 0.0f;

        paletteColors[0x0a][0] = 0.0f;
        paletteColors[0x0a][1] = 64.0f;
        paletteColors[0x0a][2] = 0.0f;

        paletteColors[0x0b][0] = 0.0f;
        paletteColors[0x0b][1] = 60.0f;
        paletteColors[0x0b][2] = 0.0f;

        paletteColors[0x0c][0] = 0.0f;
        paletteColors[0x0c][1] = 50.0f;
        paletteColors[0x0c][2] = 60.0f;

        paletteColors[0x0d][0] = 0.0f;
        paletteColors[0x0d][1] = 0.0f;
        paletteColors[0x0d][2] = 0.0f;

        paletteColors[0x0e][0] = 0.0f;
        paletteColors[0x0e][1] = 0.0f;
        paletteColors[0x0e][2] = 0.0f;

        paletteColors[0x0f][0] = 0.0f;
        paletteColors[0x0f][1] = 0.0f;
        paletteColors[0x0f][2] = 0.0f;

        // Colors 0x10 - 0x1F
        paletteColors[0x10][0] = 152.0f;
        paletteColors[0x10][1] = 150.0f;
        paletteColors[0x10][2] = 152.0f;

        paletteColors[0x11][0] = 8.0f;
        paletteColors[0x11][1] = 76.0f;
        paletteColors[0x11][2] = 196.0f;

        paletteColors[0x12][0] = 48.0f;
        paletteColors[0x12][1] = 50.0f;
        paletteColors[0x12][2] = 236.0f;

        paletteColors[0x13][0] = 92.0f;
        paletteColors[0x13][1] = 30.0f;
        paletteColors[0x13][2] = 228.0f;

        paletteColors[0x14][0] = 136.0f;
        paletteColors[0x14][1] = 20.0f;
        paletteColors[0x14][2] = 176.0f;

        paletteColors[0x15][0] = 160.0f;
        paletteColors[0x15][1] = 20.0f;
        paletteColors[0x15][2] = 100.0f;

        paletteColors[0x16][0] = 152.0f;
        paletteColors[0x16][1] = 34.0f;
        paletteColors[0x16][2] = 32.0f;

        paletteColors[0x17][0] = 120.0f;
        paletteColors[0x17][1] = 60.0f;
        paletteColors[0x17][2] = 0.0f;

        paletteColors[0x18][0] = 84.0f;
        paletteColors[0x18][1] = 90.0f;
        paletteColors[0x18][2] = 0.0f;

        paletteColors[0x19][0] = 40.0f;
        paletteColors[0x19][1] = 114.0f;
        paletteColors[0x19][2] = 0.0f;

        paletteColors[0x1a][0] = 8.0f;
        paletteColors[0x1a][1] = 124.0f;
        paletteColors[0x1a][2] = 0.0f;

        paletteColors[0x1b][0] = 0.0f;
        paletteColors[0x1b][1] = 118.0f;
        paletteColors[0x1b][2] = 40.0f;

        paletteColors[0x1c][0] = 0.0f;
        paletteColors[0x1c][1] = 102.0f;
        paletteColors[0x1c][2] = 120.0f;

        paletteColors[0x1d][0] = 0.0f;
        paletteColors[0x1d][1] = 0.0f;
        paletteColors[0x1d][2] = 0.0f;

        paletteColors[0x1e][0] = 0.0f;
        paletteColors[0x1e][1] = 0.0f;
        paletteColors[0x1e][2] = 0.0f;

        paletteColors[0x1f][0] = 0.0f;
        paletteColors[0x1f][1] = 0.0f;
        paletteColors[0x1f][2] = 0.0f;

        // Colors 0x20 - 0x2F
        paletteColors[0x20][0] = 236.0f;
        paletteColors[0x20][1] = 238.0f;
        paletteColors[0x20][2] = 236.0f;

        paletteColors[0x21][0] = 76.0f;
        paletteColors[0x21][1] = 154.0f;
        paletteColors[0x21][2] = 236.0f;

        paletteColors[0x22][0] = 120.0f;
        paletteColors[0x22][1] = 124.0f;
        paletteColors[0x22][2] = 236.0f;

        paletteColors[0x23][0] = 176.0f;
        paletteColors[0x23][1] = 98.0f;
        paletteColors[0x23][2] = 236.0f;

        paletteColors[0x24][0] = 228.0f;
        paletteColors[0x24][1] = 84.0f;
        paletteColors[0x24][2] = 236.0f;

        paletteColors[0x25][0] = 236.0f;
        paletteColors[0x25][1] = 88.0f;
        paletteColors[0x25][2] = 180.0f;

        paletteColors[0x26][0] = 236.0f;
        paletteColors[0x26][1] = 106.0f;
        paletteColors[0x26][2] = 100.0f;

        paletteColors[0x27][0] = 212.0f;
        paletteColors[0x27][1] = 136.0f;
        paletteColors[0x27][2] = 32.0f;

        paletteColors[0x28][0] = 160.0f;
        paletteColors[0x28][1] = 170.0f;
        paletteColors[0x28][2] = 0.0f;

        paletteColors[0x29][0] = 116.0f;
        paletteColors[0x29][1] = 196.0f;
        paletteColors[0x29][2] = 0.0f;

        paletteColors[0x2a][0] = 76.0f;
        paletteColors[0x2a][1] = 208.0f;
        paletteColors[0x2a][2] = 32.0f;

        paletteColors[0x2b][0] = 56.0f;
        paletteColors[0x2b][1] = 204.0f;
        paletteColors[0x2b][2] = 108.0f;

        paletteColors[0x2c][0] = 56.0f;
        paletteColors[0x2c][1] = 180.0f;
        paletteColors[0x2c][2] = 204.0f;

        paletteColors[0x2d][0] = 60.0f;
        paletteColors[0x2d][1] = 60.0f;
        paletteColors[0x2d][2] = 60.0f;

        paletteColors[0x2e][0] = 0.0f;
        paletteColors[0x2e][1] = 0.0f;
        paletteColors[0x2e][2] = 0.0f;

        paletteColors[0x2f][0] = 0.0f;
        paletteColors[0x2f][1] = 0.0f;
        paletteColors[0x2f][2] = 0.0f;

        // Colors 0x30 - 0x3F
        paletteColors[0x30][0] = 236.0f;
        paletteColors[0x30][1] = 238.0f;
        paletteColors[0x30][2] = 236.0f;

        paletteColors[0x31][0] = 168.0f;
        paletteColors[0x31][1] = 204.0f;
        paletteColors[0x31][2] = 236.0f;

        paletteColors[0x32][0] = 188.0f;
        paletteColors[0x32][1] = 188.0f;
        paletteColors[0x32][2] = 236.0f;

        paletteColors[0x33][0] = 212.0f;
        paletteColors[0x33][1] = 178.0f;
        paletteColors[0x33][2] = 236.0f;

        paletteColors[0x34][0] = 236.0f;
        paletteColors[0x34][1] = 174.0f;
        paletteColors[0x34][2] = 236.0f;

        paletteColors[0x35][0] = 236.0f;
        paletteColors[0x35][1] = 174.0f;
        paletteColors[0x35][2] = 212.0f;

        paletteColors[0x36][0] = 236.0f;
        paletteColors[0x36][1] = 180.0f;
        paletteColors[0x36][2] = 176.0f;

        paletteColors[0x37][0] = 228.0f;
        paletteColors[0x37][1] = 196.0f;
        paletteColors[0x37][2] = 144.0f;

        paletteColors[0x38][0] = 204.0f;
        paletteColors[0x38][1] = 210.0f;
        paletteColors[0x38][2] = 120.0f;

        paletteColors[0x39][0] = 180.0f;
        paletteColors[0x39][1] = 222.0f;
        paletteColors[0x39][2] = 120.0f;

        paletteColors[0x3a][0] = 168.0f;
        paletteColors[0x3a][1] = 226.0f;
        paletteColors[0x3a][2] = 144.0f;

        paletteColors[0x3b][0] = 152.0f;
        paletteColors[0x3b][1] = 226.0f;
        paletteColors[0x3b][2] = 180.0f;

        paletteColors[0x3c][0] = 160.0f;
        paletteColors[0x3c][1] = 214.0f;
        paletteColors[0x3c][2] = 228.0f;

        paletteColors[0x3d][0] = 160.0f;
        paletteColors[0x3d][1] = 162.0f;
        paletteColors[0x3d][2] = 160.0f;

        paletteColors[0x3e][0] = 0.0f;
        paletteColors[0x3e][1] = 0.0f;
        paletteColors[0x3e][2] = 0.0f;

        paletteColors[0x3f][0] = 0.0f;
        paletteColors[0x3f][1] = 0.0f;
        paletteColors[0x3f][2] = 0.0f;

        for (int i = 0; i < 0x40; i++) {
            paletteColors[i][0] /= 255.0f;
            paletteColors[i][1] /= 255.0f;
            paletteColors[i][2] /= 255.0f;
        }
    }

    private short getIncrement() {
        if ((controlRegister & ControlRegisterEnum.Increment.value) == 0) {
            return 1;
        } else {
            return 32;
        }
    }

    private void setLatchedVramAddr(byte value) {
        if (!addressLatch) {
            // Hi byte first
            TRAMAddress = (value & 0x3F) << 8 | TRAMAddress & 0x00FF;
        } else {
            // Lo byte next
            TRAMAddress = (TRAMAddress & 0xFF00) | (value & 0x00FF);
            VRAMAddress = TRAMAddress;
        }
        addressLatch = !addressLatch;
    }

    private void setLatchedScroll(byte value) {
        if (!addressLatch) {
            fineXScroll = (short) (value & 0x07);
            int coarseXScroll = value & 0xFF;
            coarseXScroll >>= 3;
            coarseXScroll &= VRAMAddressEnum.CoarseXScroll.value;
            TRAMAddress = (TRAMAddress  & (
                        VRAMAddressEnum.Unused.value        | 
                        VRAMAddressEnum.FineYScroll.value   |
                        VRAMAddressEnum.NametableSel.value  |
                        VRAMAddressEnum.CoarseYScroll.value 
                        // Coarse X Scroll
                    )) | coarseXScroll;
        } else {
            int fineYScroll = value & 0x07;
            fineYScroll <<= 12;
            TRAMAddress = (TRAMAddress & (
                        VRAMAddressEnum.Unused.value        | 
                        // Fine Y Scroll
                        VRAMAddressEnum.NametableSel.value  |
                        VRAMAddressEnum.CoarseYScroll.value |
                        VRAMAddressEnum.CoarseXScroll.value 
                    )) | fineYScroll;

            int coarseYScroll = value & 0xFF;
            coarseYScroll <<= 2;
            coarseYScroll &= VRAMAddressEnum.CoarseYScroll.value;
            TRAMAddress = (TRAMAddress & (
                        VRAMAddressEnum.Unused.value        | 
                        VRAMAddressEnum.FineYScroll.value   |
                        VRAMAddressEnum.NametableSel.value  |
                        // Coarse Y Scroll
                        VRAMAddressEnum.CoarseXScroll.value 
                    )) | coarseYScroll;
        }

        addressLatch = !addressLatch;
    }
 
    public byte readPPUFromCPU(int address) {
        byte data = 0x00;
        address &= 0x7;
        switch (address) {
            case 0:
                // Control Register - cannot be read 
                break;
            case 1:
                // Mask Register - cannot be read 
                break;
            case 2:
                // Status register
                data = statusRegister;
                statusRegister &= ~PPUStatusEnum.VerticalBlank.value;
                addressLatch = false;
                break;
            case 3:
                // OAM Address Register - cannot be read 
                break;
            case 4: 
                // OAM Data
                data = OAMData[OAMAddress];
                break;
            case 5:
                // Scroll Register - cannot be read 
                break;
            case 6:
                // PPU Data - cannot be read 
                break;
            case 7:
                // Data register
                data = dataBuffer;
                dataBuffer = readFromPPUBus(VRAMAddress);

                if (address >= 0x3F00) {
                    data = dataBuffer;
                }

                VRAMAddress += getIncrement();
                VRAMAddress &= 0xFFFF;
                break;
        }
        return data;
    }

    public void writeToPPUFromCPU(int address, byte value) {
        address &= 0x7;
        switch (address) {
            case 0:
                // Control register
                byte nmiBefore = (byte) ((controlRegister & ControlRegisterEnum.NMI.value) >> 7);
                controlRegister = value; 
                byte nmiAfter = (byte) ((controlRegister & ControlRegisterEnum.NMI.value) >> 7);
                if (nmiBefore == 0 && nmiAfter == 1 && inVblank) {
                    reqNMI = true;
                }
                // "Equivalently, bits 1 and 0 are the most significant bit of the scrolling 
                //  coordinates (see Nametables and PPUSCROLL)"
                // NESDEV says that bit 0 controls the x scroll, bit 1 controls the y scroll
                int NametableSel = controlRegister & 0b0011;
                NametableSel <<= 10;
                TRAMAddress = (TRAMAddress & (
                        VRAMAddressEnum.Unused.value        | 
                        VRAMAddressEnum.FineYScroll.value   |
                        // NametableSel
                        VRAMAddressEnum.CoarseYScroll.value |
                        VRAMAddressEnum.CoarseXScroll.value 
                    )) | NametableSel;
                break;
            case 1:
                // Mask register
                maskRegister = value;
                break;
            case 2:
                // Status register - can't write to this register
                break;
            case 3: 
                // OAM address
                OAMAddress = (short) (value & 0xFF);
                break;
            case 4:
                // OAM data 
                OAMData[OAMAddress] = value;
                break;
            case 5:
                // Scroll
                setLatchedScroll(value);
                break;
            case 6:
                // PPU Address 
                setLatchedVramAddr(value);
                break;
            case 7:
                // Data register
                writeToPPUBus(VRAMAddress, value);
                VRAMAddress += getIncrement();
                VRAMAddress &= 0xFFFF;
                break;
        }
    }

    private void writeToPPUBus(int address, byte value) {
        address &= 0x3FFF;
        if (cartridge.writeWordFromPPU(address, value)) {
            // CHR ROM write or internal pattern table
        } else if (address >= 0x2000 && address <= 0x3EFF) {
            // RAM write
            address &= 0x2FFF;
            address -= 0x2000;
            int nametable = (address & VRAMAddressEnum.NametableSel.value) >> 10;
            if (cartridge.getCartMirror() == Mirror.HORIZONTAL) {
                address &= 0x03FF;
                switch (nametable) {
                    case 0:
                    case 1:
                        vRAM[address] = value; 
                        break;
                    case 2:
                    case 3:
                        vRAM[address + 0x400] = value;
                        break;
                }
            } else if (cartridge.getCartMirror() == Mirror.VERTICAL) {
                address &= 0x03FF;
                switch (nametable) {
                    case 0:
                    case 2:
                        vRAM[address] = value; 
                        break;
                    case 1:
                    case 3:
                        vRAM[address + 0x400] = value;
                        break;
                }
            }
        } else if (address >= 0x3F00 && address <= 0x3FFF) {
            // Palette write
            
            // "Addresses $3F10/$3F14/$3F18/$3F1C are mirrors of $3F00/$3F04/$3F08/$3F0C."
            address &= 0x001F;

            switch (address) {
                case 0x10:
                case 0x14:
                case 0x18:
                case 0x1C:
                    address &= 0x000F;
                    break;
            }

            palletteTable[address] = value;
        }
    }

    private byte readFromPPUBus(int address) {
        byte data = 0x00;
        address &= 0x3FFF;
        Tuple<Boolean, Byte> cartridgeRead = cartridge.readWordFromPPU(address);
        if (cartridgeRead.first) {
            // CHR ROM read or internal pattern table
            data = cartridgeRead.second;
        } else if (address >= 0x2000 && address <= 0x3EFF) {
            // RAM read
            address &= 0x2FFF;
            address -= 0x2000;
            int nametable = (address & VRAMAddressEnum.NametableSel.value) >> 10;
            if (cartridge.getCartMirror() == Mirror.HORIZONTAL) {
                address &= 0x03FF;
                switch (nametable) {
                    case 0:
                    case 1:
                        data = vRAM[address];
                        break;
                    case 2:
                    case 3:
                        data = vRAM[address + 0x400];
                        break;
                }
            } else if (cartridge.getCartMirror() == Mirror.VERTICAL) {
                address &= 0x03FF;
                switch (nametable) {
                    case 0:
                    case 2:
                        data = vRAM[address];
                        break;
                    case 1:
                    case 3:
                        data = vRAM[address + 0x400];
                        break;
                }
            }
        } if (address >= 0x3F00 && address <= 0x3FFF) {
            // Palette read

            // "Addresses $3F10/$3F14/$3F18/$3F1C are mirrors of $3F00/$3F04/$3F08/$3F0C."
            address &= 0x001F;

            switch (address) {
                case 0x10:
                case 0x14:
                case 0x18:
                case 0x1C:
                    address &= 0x000F;
                    break;
            }

            data = palletteTable[address];
        }
        return data;
    }

    public void clock() {
        cycles++;
        if (cycles >= 341) {
            scanline++;

            if (scanline == 241 && (controlRegister & ControlRegisterEnum.NMI.value) == 1) {
                inVblank = true;
                reqNMI = true;
            }

            if (scanline == 262) {
                scanline = 0;
                inVblank = false;
            }
        }
    }

    public boolean requestingNMI() {
        return reqNMI;
    }

    public void clearNMI() {
        reqNMI = false;
    }
}
