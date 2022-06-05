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

 // This PPU is heavily based on OLC's PPU implementation, which is available at:
 // https://github.com/OneLoneCoder/olcNES/blob/master/Part%20%237%20-%20Mappers%20%26%20Basic%20Sounds/olc2C02.cpp
 // and is licensed under the OLC-3 license.

 /*
    License (OLC-3)
	~~~~~~~~~~~~~~~
	Copyright 2018-2022 OneLoneCoder.com
	Redistribution and use in source and binary forms, with or without
	modification, are permitted provided that the following conditions
	are met:
	1. Redistributions or derivations of source code must retain the above
	copyright notice, this list of conditions and the following disclaimer.
	2. Redistributions or derivative works in binary form must reproduce
	the above copyright notice. This list of conditions and the following
	disclaimer must be reproduced in the documentation and/or other
	materials provided with the distribution.
	3. Neither the name of the copyright holder nor the names of its
	contributors may be used to endorse or promote products derived
	from this software without specific prior written permission.
	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
	"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
	LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
	A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
	HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
	SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
	LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
	DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
	THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
	(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
	OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.lattenes.Core;

import com.lattenes.Core.Cartridge.Cartridge;
import com.lattenes.Core.Cartridge.Mirror;
import com.lattenes.Util.Tuple;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;

public class PPU {
    private byte[] palletteTable;
    private float[][] paletteColors;
    private byte[] vRAM;
    private byte[] SecondaryOAMData;
    private int cycles;
    private int scanline;
    private byte spritesOnScanline;
    byte[] OAMData;
    float[] screen;
    boolean frameReady = false;

    private Cartridge cartridge;

    private enum ControlRegisterEnum {
        Nametable1      (0b00000001), // This is the base address of the first nametable 
        Nametable2      (0b00000010), // 0b00 = $2000, 0b01 = $2400, 0b10 = $2800, 0b11 = $2C00
        Increment       (0b00000100), // VRAM address increment, 0: add 1, going across 1: add 32, going down        SpriteTable     (0b00001000),
        SpriteTable     (0b00001000), // Sprite patern 0b0: $0000, 0b1: $1000, ignored in 8x16 mode
        BackgroundTable (0b00010000), // Background pattern 0b0: $0000, 0b1: $1000
        SpriteSize      (0b00100000), // Sprite size 0b0: 8x8, 0b1: 8x16
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

    private EnumSet<PPUStatusEnum> statusRegister = EnumSet.noneOf(PPUStatusEnum.class);
    private EnumSet<PPUMaskEnum> maskRegister = EnumSet.noneOf(PPUMaskEnum.class);
    private EnumSet<ControlRegisterEnum> controlRegister = EnumSet.noneOf(ControlRegisterEnum.class);
    
    private void byteToStatusEnumSet(byte value) {
        statusRegister.clear();
        for (PPUStatusEnum e : PPUStatusEnum.values()) {
            if ((value & e.value) == e.value) {
                statusRegister.add(e);
            }
        }
    }
    
    
    private void byteToMaskEnumSet(byte value) {
        maskRegister.clear();
        for (PPUMaskEnum maskEnum : PPUMaskEnum.values()) {
            if ((value & maskEnum.value) == maskEnum.value) {
                maskRegister.add(maskEnum);
            }
        }
    }

    private void byteToControlEnumSet(byte value) {
        controlRegister.clear();
        for (ControlRegisterEnum controlEnum : ControlRegisterEnum.values()) {
            if ((value & controlEnum.value) == controlEnum.value) {
                controlRegister.add(controlEnum);
            }
        }
    }

    private byte statusEnumSetToByte() {
        byte result = 0x00;
        for (PPUStatusEnum statusEnum : statusRegister) {
            result |= statusEnum.value;
        }
        return result;
    }

    private byte maskEnumSetToByte() {
        byte result = 0x00;
        for (PPUMaskEnum maskEnum : maskRegister) {
            result |= maskEnum.value;
        }
        return result;
    }

    private byte controlEnumSetToByte() {
        byte result = 0x00;
        for (ControlRegisterEnum controlEnum : controlRegister) {
            result |= controlEnum.value;
        }
        return result;
    }


    private int VRAMAddress = 0x00;
    private int TRAMAddress = 0x00;

    private short fineXScroll = 0;
    short OAMAddress = 0;
    private boolean reqNMI = false;
    private boolean addressLatch = false;

    private boolean sprite0HitPossible = false;
    private boolean sprite0Rendering = false;

    private byte dataBuffer;

    // Data shifters
    private int backgroundShiftPatternLoByte = 0;
    private int backgroundShiftPatternHiByte = 0;

    private short[] spriteShiftPatternLoByte;
    private short[] spriteShiftPatternHiByte;

    private int backgroundShiftAttributeLoByte = 0;
    private int backgroundShiftAttributeHiByte = 0;

    private int bgNextTile = 0;
    private int bgNextTileID = 0;
    private int bgNextTileAttributes = 0;

    public byte[] dumpState() {
        ArrayList<byte[]> fieldArray = new ArrayList<byte[]>();
        fieldArray.add(palletteTable);
        fieldArray.add(vRAM);
        fieldArray.add(SecondaryOAMData);
        fieldArray.add(OAMData);

        byte[] screenBytes = new byte[screen.length * (Float.SIZE / 8)];
        ByteBuffer.wrap(screenBytes).asFloatBuffer().put(screen);
        fieldArray.add(screenBytes);

        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(cycles).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(scanline).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(spritesOnScanline).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(sprite0HitPossible ? (byte) 1 : (byte) 0).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(sprite0Rendering ? (byte) 1 : (byte) 0).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(addressLatch ? (byte) 1 : (byte) 0).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(reqNMI ? (byte) 1 : (byte) 0).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(frameReady ? (byte) 1 : (byte) 0).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(VRAMAddress).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(TRAMAddress).array());
        fieldArray.add(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(fineXScroll).array());
        fieldArray.add(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(OAMAddress).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(backgroundShiftPatternLoByte).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(backgroundShiftPatternHiByte).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(backgroundShiftAttributeLoByte).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(backgroundShiftAttributeHiByte).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bgNextTile).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bgNextTileID).array());
        fieldArray.add(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bgNextTileAttributes).array());

        byte[] spriteShiftersLoAsBytes = new byte[spriteShiftPatternLoByte.length * (Short.SIZE / 8)];
        ByteBuffer.wrap(spriteShiftersLoAsBytes).asShortBuffer().put(spriteShiftPatternLoByte);
        fieldArray.add(spriteShiftersLoAsBytes);

        byte[] spriteShiftersHiAsBytes = new byte[spriteShiftPatternHiByte.length * (Short.SIZE / 8)];
        ByteBuffer.wrap(spriteShiftersHiAsBytes).asShortBuffer().put(spriteShiftPatternHiByte);
        fieldArray.add(spriteShiftersHiAsBytes);

        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(statusEnumSetToByte()).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(maskEnumSetToByte()).array());
        fieldArray.add(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(controlEnumSetToByte()).array());

        int size = 0;

        for (byte[] field : fieldArray) {
            size += field.length;
        }

        byte[] state = new byte[size];
        int k = 0;

        for (byte[] field : fieldArray) {
            for (int i = 0; i < field.length; i++) {
                state[k++] = field[i];
            }
        }

        return state;
    }

    public void loadState(byte[] state) {
        int k = 0;
        for (int i = 0; i < palletteTable.length; i++) {
            palletteTable[i] = state[k++];
        }
        for (int i = 0; i < vRAM.length; i++) {
            vRAM[i] = state[k++];
        }
        for (int i = 0; i < SecondaryOAMData.length; i++) {
            SecondaryOAMData[i] = state[k++];
        }
        for (int i = 0; i < OAMData.length; i++) {
            OAMData[i] = state[k++];
        }
        byte[] screenBytes = new byte[screen.length * (Float.SIZE / 8)];
        for (int i = 0; i < screenBytes.length; i++) {
            screenBytes[i] = state[k++];
        }
        ByteBuffer.wrap(screenBytes).asFloatBuffer().get(screen);
        cycles = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        scanline = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        spritesOnScanline = state[k++];
        sprite0HitPossible = state[k++] == 1;
        sprite0Rendering = state[k++] == 1;
        addressLatch = state[k++] == 1;
        reqNMI = state[k++] == 1;
        frameReady = state[k++] == 1;
        VRAMAddress = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        TRAMAddress = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        fineXScroll = ByteBuffer.wrap(state, k, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        k += 2;
        OAMAddress = ByteBuffer.wrap(state, k, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        k += 2;
        backgroundShiftPatternLoByte = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        backgroundShiftPatternHiByte = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        backgroundShiftAttributeLoByte = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        backgroundShiftAttributeHiByte = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        bgNextTile = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        bgNextTileID = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        bgNextTileAttributes = ByteBuffer.wrap(state, k, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        k += 4;
        byte[] spriteShiftersLoAsBytes = new byte[spriteShiftPatternLoByte.length * (Short.SIZE / 8)];
        for (int i = 0; i < spriteShiftersLoAsBytes.length; i++) {
            spriteShiftersLoAsBytes[i] = state[k++];
        }
        ByteBuffer.wrap(spriteShiftersLoAsBytes).asShortBuffer().get(spriteShiftPatternLoByte);
        byte[] spriteShiftersHiAsBytes = new byte[spriteShiftPatternHiByte.length * (Short.SIZE / 8)];
        for (int i = 0; i < spriteShiftersHiAsBytes.length; i++) {
            spriteShiftersHiAsBytes[i] = state[k++];
        }
        ByteBuffer.wrap(spriteShiftersHiAsBytes).asShortBuffer().get(spriteShiftPatternHiByte);
        byteToStatusEnumSet(state[k++]);
        byteToMaskEnumSet(state[k++]);
        byteToControlEnumSet(state[k++]);
    }

    public PPU(Cartridge cartridge) {
        this.cartridge = cartridge;
        this.palletteTable = new byte[0x20];
        this.vRAM = new byte[0x800];
        this.OAMData = new byte[0x100];
        this.SecondaryOAMData = new byte[0x8 * 4];
        this.paletteColors = new float[0x40][3];
        this.screen = new float[256 * 240 * 4];
        this.spriteShiftPatternLoByte = new short[8];
        this.spriteShiftPatternHiByte = new short[8];
        this.cycles = 0;
        this.scanline = 0;

        this.fineXScroll = 0;
        this.OAMAddress = 0;
        this.dataBuffer = 0;
        this.VRAMAddress = 0;
        this.TRAMAddress = 0;

        this.reqNMI = false;
        this.addressLatch = false;

        // Initialize palette colors
        // Using the 2C02 layout
        // https://www.nesdev.org/wiki/PPU_palettes#2C02

        { 
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
        }

        for (int i = 0; i < 0x40; i++) {
            paletteColors[i][0] /= 255.0f;
            paletteColors[i][1] /= 255.0f;
            paletteColors[i][2] /= 255.0f;
        }
    }

    private short getIncrement() {
        if (!controlRegister.contains(ControlRegisterEnum.Increment)) {
            return 1;
        } else {
            return 32;
        }
    }

    private void setPixel(int x, int y, float r, float g, float b) {
        if (x < 0 || x > 255 || y < 0 || y > 239) {
            return;
        }

        int index = (239 - y) * 256 * 4 + x * 4;
        screen[index + 0] = r;
        screen[index + 1] = g;
        screen[index + 2] = b;
        screen[index + 3] = 1.0f;
    }

    private void setLatchedVramAddr(byte value) {
        if (!addressLatch) {
            // Hi byte first
            TRAMAddress = ((value & 0x3F) << 8) | TRAMAddress & 0x00FF;
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

            TRAMAddress &= ~VRAMAddressEnum.CoarseXScroll.value;
            TRAMAddress |= coarseXScroll;
        } else {
            int fineYScroll = value & 0x07;
            fineYScroll <<= 12;

            TRAMAddress &= ~VRAMAddressEnum.FineYScroll.value;
            TRAMAddress |= fineYScroll;

            int coarseYScroll = value & 0xFF;
            coarseYScroll <<= 2;
            coarseYScroll &= VRAMAddressEnum.CoarseYScroll.value;

            TRAMAddress &= ~VRAMAddressEnum.CoarseYScroll.value;
            TRAMAddress |= coarseYScroll;
        }

        addressLatch = !addressLatch;
    }

    private int flipBits(int dataByte) {
        dataByte = (dataByte & 0xF0) >> 4 | (dataByte & 0x0F) << 4;
        dataByte = (dataByte & 0xCC) >> 2 | (dataByte & 0x33) << 2;
        dataByte = (dataByte & 0xAA) >> 1 | (dataByte & 0x55) << 1;
        return dataByte;
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
                data = statusEnumSetToByte();
                statusRegister.remove(PPUStatusEnum.VerticalBlank);
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

                if (VRAMAddress >= 0x3F00) {
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
                boolean nmiBefore = controlRegister.contains(ControlRegisterEnum.NMI);
                byteToControlEnumSet(value);
                boolean nmiAfter = controlRegister.contains(ControlRegisterEnum.NMI);
                if (!nmiBefore && nmiAfter && statusRegister.contains(PPUStatusEnum.VerticalBlank)) {
                    reqNMI = true;
                }
                // "Equivalently, bits 1 and 0 are the most significant bit of the scrolling 
                //  coordinates (see Nametables and PPUSCROLL)"
                // NESDEV says that bit 0 controls the x scroll, bit 1 controls the y scroll
                int nametableSel = value & 0b0011;
                nametableSel <<= 10;
                TRAMAddress &= ~VRAMAddressEnum.NametableSel.value;
                TRAMAddress |= nametableSel;
                break;
            case 1:
                // Mask register
                byteToMaskEnumSet(value);
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
                OAMAddress++;
                OAMAddress &= 0xFF;
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
            int nametable = (address & VRAMAddressEnum.NametableSel.value) >> 10;
            address &= 0x03FF;
            if (cartridge.getCartMirror() == Mirror.HORIZONTAL) {
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
            int nametable = (address & VRAMAddressEnum.NametableSel.value) >> 10;
            address &= 0x03FF;
            if (cartridge.getCartMirror() == Mirror.HORIZONTAL) {
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
        } else if (address >= 0x3F00 && address <= 0x3FFF) {
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

    private void backgroundShift() {
        if (maskRegister.contains(PPUMaskEnum.BGEnable)) {
            backgroundShiftPatternLoByte <<= 1;
            backgroundShiftPatternHiByte <<= 1;

            backgroundShiftAttributeLoByte <<= 1;
            backgroundShiftAttributeHiByte <<= 1;

            backgroundShiftPatternLoByte &= 0xFFFF;
            backgroundShiftPatternHiByte &= 0xFFFF;
            backgroundShiftAttributeLoByte &= 0xFFFF;
            backgroundShiftAttributeHiByte &= 0xFFFF;
        }
    }

    private void spriteShift() {
        if (maskRegister.contains(PPUMaskEnum.SpriteEnable) && cycles >= 1 && cycles < 258) {
            for (int i = 0; i < spritesOnScanline; i++) {
                int spriteX = SecondaryOAMData[i * 4 + 3] & 0xFF;
                if (spriteX > 0) {
                    spriteX--;
                    SecondaryOAMData[i * 4 + 3] = (byte) spriteX;
                } else {
                    spriteShiftPatternLoByte[i] <<= 1;
                    spriteShiftPatternHiByte[i] <<= 1;

                    spriteShiftPatternLoByte[i] &= 0xFF;
                    spriteShiftPatternHiByte[i] &= 0xFF;
                }
            }
        }
    }

    private void loadBG() {
        backgroundShiftPatternLoByte = (backgroundShiftPatternLoByte & 0xFF00) | (bgNextTile & 0x00FF);
        backgroundShiftPatternHiByte = (backgroundShiftPatternHiByte & 0xFF00) | ((bgNextTile >> 8) & 0x00FF); 
        
        backgroundShiftAttributeLoByte = (backgroundShiftAttributeLoByte & 0xFF00) | ((bgNextTileAttributes & 0b01) != 0 ? 0xFF : 0x00);
        backgroundShiftAttributeHiByte = (backgroundShiftAttributeHiByte & 0xFF00) | ((bgNextTileAttributes & 0b10) != 0 ? 0xFF : 0x00);
    }

    private void incrementXScroll() {
        if (maskRegister.contains(PPUMaskEnum.BGEnable) || maskRegister.contains(PPUMaskEnum.SpriteEnable)) {
            int coarseX = (VRAMAddress & VRAMAddressEnum.CoarseXScroll.value);
            if (coarseX == 31) {
                VRAMAddress &= ~VRAMAddressEnum.CoarseXScroll.value;
                int nametableX = (VRAMAddress & VRAMAddressEnum.NametableSel.value) >> 10;
                VRAMAddress &= ~(0b010000000000);
                nametableX = ~nametableX;
                nametableX &= 0x01;
                VRAMAddress |= nametableX << 10;
            } else {
                coarseX += 1;
                coarseX &= 0b11111;
                VRAMAddress &= ~VRAMAddressEnum.CoarseXScroll.value;
                VRAMAddress |= coarseX;
            }
        }
    }

    private void incrementYScroll() {
        if (maskRegister.contains(PPUMaskEnum.BGEnable) || maskRegister.contains(PPUMaskEnum.SpriteEnable)) {
            int fineY = (VRAMAddress & VRAMAddressEnum.FineYScroll.value) >> 12;
            if (fineY < 7) {
                fineY++;
                fineY &= 0b111;
                VRAMAddress &= ~VRAMAddressEnum.FineYScroll.value;
                VRAMAddress |= fineY << 12;
            } else {
                VRAMAddress &= ~VRAMAddressEnum.FineYScroll.value;
                int coarseY = (VRAMAddress & VRAMAddressEnum.CoarseYScroll.value) >> 5;
                if (coarseY == 29) {
                    VRAMAddress &= ~VRAMAddressEnum.CoarseYScroll.value;
                    int nametableY = (VRAMAddress & VRAMAddressEnum.NametableSel.value) >> 11;
                    VRAMAddress &= ~(0b100000000000);
                    nametableY = ~nametableY;
                    nametableY &= 0x01;
                    VRAMAddress |= nametableY << 11;
                } else if (coarseY == 31) {
                    VRAMAddress &= ~VRAMAddressEnum.CoarseYScroll.value;
                } else {
                    coarseY += 1;
                    coarseY &= 0b11111;
                    VRAMAddress &= ~VRAMAddressEnum.CoarseYScroll.value;
                    VRAMAddress |= coarseY << 5;
                }
            }
        }
    }

    public void clock() {

        if (scanline >= -1 && scanline < 240) {
            if (scanline == -1 && cycles == 1) {
                statusRegister.remove(PPUStatusEnum.Sprite0Hit);
                statusRegister.remove(PPUStatusEnum.SpriteOverflow);
                statusRegister.remove(PPUStatusEnum.VerticalBlank);

                for (int i = 0; i < 8; i++) {
                    spriteShiftPatternLoByte[i] = 0;
                    spriteShiftPatternHiByte[i] = 0;
                }
            }

            if ((cycles >= 2 && cycles < 258) || (cycles >= 321 && cycles < 338)) {
                backgroundShift();
                spriteShift();

                int readAddress, fineY;
                switch ((cycles - 1) % 8) {
                    case 0:
                        loadBG();
                        bgNextTileID = readFromPPUBus(0x2000 | (VRAMAddress & 0xFFF)) & 0xFF;
                        break;
                    case 2:
                        // Starting address is at 0b0010001111000000
                        readAddress = 0x23C0;

                        // Get nametable, this is already at the correct bit position
                        int nametable = (VRAMAddress & VRAMAddressEnum.NametableSel.value);
                        // Take coarse Y and coarse X from VRAM address, shift coarseY by 5 to test their bits
                        int coarseY = (VRAMAddress & VRAMAddressEnum.CoarseYScroll.value) >> 5;
                        int coarseX = VRAMAddress & VRAMAddressEnum.CoarseXScroll.value;

                        // Check the bits to get the appropriate attribute
                        boolean coarseYBit1 = (coarseY & 0x2) != 0;
                        boolean coarseXBit1 = (coarseX & 0x2) != 0;

                        // Now we shift the bits to get the correct address,
                        // we take the most significant 3 bits of the coarseX and coarseY

                        // Say coarseY is 0b11111
                        // shift by 1 is 0b111110
                        coarseY <<= 1;

                        // Take the 3 most significant bits of coarseY and now its
                        // 0b111000 and in the correct position of the final address
                        coarseY &= 0b111000;

                        // Shift right by 2 to get it down to a 3 bit number
                        coarseX >>= 2;

                        // ORing these variables together we get 0b0010NM1111YYYXXX as
                        // the final address to read from the PPU bus
                        readAddress = readAddress | nametable | coarseY | coarseX;
                        bgNextTileAttributes = readFromPPUBus(readAddress) & 0xFF;

                        // Depending on the coarseX and Y bits we need to shift the
                        // attribute bits to the correct attribute
                        if (coarseYBit1) bgNextTileAttributes >>= 4;
                        if (coarseXBit1) bgNextTileAttributes >>= 2;
                        bgNextTileAttributes &= 0x03;
                        break;
                    case 4:
                        readAddress = controlRegister.contains(ControlRegisterEnum.BackgroundTable) ? 0x1000 : 0x0000;
                        fineY = (VRAMAddress & VRAMAddressEnum.FineYScroll.value) >> 12;
                        readAddress = readAddress + bgNextTileID * 16 + fineY;
                        bgNextTile &= ~0x00FF;
                        bgNextTile |= (readFromPPUBus(readAddress) & 0xFF);
                        break;
                    case 6:
                        readAddress = controlRegister.contains(ControlRegisterEnum.BackgroundTable) ? 0x1000 : 0x0000;
                        fineY = (VRAMAddress & VRAMAddressEnum.FineYScroll.value) >> 12;
                        readAddress = readAddress + bgNextTileID * 16 + fineY + 8;
                        bgNextTile &= ~0xFF00;
                        bgNextTile |= (readFromPPUBus(readAddress) & 0xFF) << 8;
                        break;
                    case 7:
                        incrementXScroll();
                        break;
                }
            }

            if (cycles == 256) {
                incrementYScroll();
            }

            if (cycles == 257) {
                loadBG();
                
                if (maskRegister.contains(PPUMaskEnum.BGEnable) || maskRegister.contains(PPUMaskEnum.SpriteEnable)) {
                    int tempAddrInfo = TRAMAddress & (VRAMAddressEnum.CoarseXScroll.value 
                                                    | 0x400);
                    VRAMAddress &= ~(VRAMAddressEnum.CoarseXScroll.value | 0x400);
                    VRAMAddress |= tempAddrInfo;
                }
            }

            if (cycles == 338 || cycles == 340) {
                bgNextTileID = readFromPPUBus(0x2000 | (VRAMAddress & 0xFFF)) & 0xFF;
            }

            if (scanline == -1 && cycles >= 280 && cycles < 305) {
                if (maskRegister.contains(PPUMaskEnum.BGEnable) || maskRegister.contains(PPUMaskEnum.SpriteEnable)) {
                    int tempAddrInfo = TRAMAddress & (VRAMAddressEnum.CoarseYScroll.value 
                                                    | VRAMAddressEnum.FineYScroll.value
                                                    | 0x800);
                    VRAMAddress &= ~(VRAMAddressEnum.FineYScroll.value | VRAMAddressEnum.CoarseYScroll.value | 0x800);
                    VRAMAddress |= tempAddrInfo;
                }
            }

            // Sprite Rendering
            if (cycles == 257 && scanline >= 0) {
                for (int i = 0; i < 32; i++)
                    SecondaryOAMData[i] = (byte) 0xFF;
                spritesOnScanline = 0;

                int spriteEntry = 0;
                sprite0HitPossible = false;
                while (spriteEntry < 64 && spritesOnScanline < 9) {
                    int spriteY = OAMData[spriteEntry * 4] & 0xFF;
                    int diff = scanline - spriteY;
                    int spriteSize = controlRegister.contains(ControlRegisterEnum.SpriteSize) ? 16 : 8;

                    if (diff >= 0 && diff < spriteSize) {
                        if (spritesOnScanline < 8) {
                            if (spriteEntry == 0) {
                                sprite0HitPossible = true;
                            }
                            SecondaryOAMData[spritesOnScanline * 4] = OAMData[spriteEntry * 4];
                            SecondaryOAMData[spritesOnScanline * 4 + 1] = OAMData[spriteEntry * 4 + 1];
                            SecondaryOAMData[spritesOnScanline * 4 + 2] = OAMData[spriteEntry * 4 + 2];
                            SecondaryOAMData[spritesOnScanline * 4 + 3] = OAMData[spriteEntry * 4 + 3];
                        }
                        spritesOnScanline++;
                    }
                    spriteEntry++;
                }
                statusRegister.remove(PPUStatusEnum.SpriteOverflow);
                if (spritesOnScanline >= 8) {
                    statusRegister.add(PPUStatusEnum.SpriteOverflow);
                    spritesOnScanline = 8;
                }
            }

            if (cycles == 340) {
                for (int i = 0; i < spritesOnScanline; i++) {
                    int spritePatternBitLo, spritePatternBitHi;
                    int spritePatternAddressLo, spritePatternAddressHi;

                    byte spriteAttribute = SecondaryOAMData[i * 4 + 2];
                    byte spriteID = SecondaryOAMData[i * 4 + 1];
                    byte spriteY = SecondaryOAMData[i * 4];

                    
                    if (!controlRegister.contains(ControlRegisterEnum.SpriteSize)) {
                        spritePatternAddressLo = controlRegister.contains(ControlRegisterEnum.SpriteTable) ? 0x1000 : 0x0000;
                        spritePatternAddressLo |= (spriteID & 0xFF) << 4;
                        if ((spriteAttribute & 0x80) == 0) {
                            spritePatternAddressLo |= (scanline - (spriteY & 0xFF));
                        } else { 
                            spritePatternAddressLo |= (7 - (scanline - (spriteY & 0xFF)));
                        }
                    } else {
                        spritePatternAddressLo = (spriteID & 0x01) << 12;
                        if ((spriteAttribute & 0x80) == 0) {
                            spritePatternAddressLo |= ((scanline - (spriteY & 0xFF)) & 0x07);
                            if ((scanline - (spriteY & 0xFF)) < 8) {
                                spritePatternAddressLo |= (spriteID & 0xFE) << 4;
                            } else {
                                spritePatternAddressLo |= ((spriteID & 0xFE) + 1) << 4;
                            }
                        } else { 
                            spritePatternAddressLo |= (7 - ((scanline - (spriteY & 0xFF)) & 0x07));
                            if ((scanline - (spriteY & 0xFF)) < 8) {    
                                spritePatternAddressLo |= ((spriteID & 0xFE) + 1) << 4;
                            } else {
                                spritePatternAddressLo |= ((spriteID & 0xFE)) << 4;
                            }
                        }
                    }

                    spritePatternAddressHi = spritePatternAddressLo + 8;
                    spritePatternAddressHi &= 0xFFFF;
                    spritePatternBitLo = readFromPPUBus(spritePatternAddressLo) & 0xFF;
                    spritePatternBitHi = readFromPPUBus(spritePatternAddressHi) & 0xFF;

                    if ((spriteAttribute & 0x40) != 0) {
                        spritePatternBitLo = flipBits(spritePatternBitLo);
                        spritePatternBitHi = flipBits(spritePatternBitHi);
                    }

                    spriteShiftPatternLoByte[i] = (short) spritePatternBitLo;
                    spriteShiftPatternHiByte[i] = (short) spritePatternBitHi;
                }
            }

        }

        if (scanline >= 241 && scanline < 261) {
            if (scanline == 241 && cycles == 1) {
                statusRegister.add(PPUStatusEnum.VerticalBlank);
                if (controlRegister.contains(ControlRegisterEnum.NMI)) {
                    reqNMI = true;
                }
            }
        }

        int backgroundPixel = 0;
        int backgroundPalette = 0;

        if (maskRegister.contains(PPUMaskEnum.BGEnable)) {
            if (maskRegister.contains(PPUMaskEnum.BGLeftColEnable) || cycles >= 9) {
                int bitMux = (0x8000 >> fineXScroll) & 0xFFFF;
                int plane0Pixel = (backgroundShiftPatternLoByte & bitMux) != 0 ? 1 : 0;
                int plane1Pixel = (backgroundShiftPatternHiByte & bitMux) != 0 ? 1 : 0;
                backgroundPixel = plane0Pixel | (plane1Pixel << 1);

                int bgPaletteBit0 = (backgroundShiftAttributeLoByte & bitMux) != 0 ? 1 : 0;
                int bgPaletteBit1 = (backgroundShiftAttributeHiByte & bitMux) != 0 ? 1 : 0;
                backgroundPalette = bgPaletteBit0 | (bgPaletteBit1 << 1);
            } 
        }

        int spritePixel = 0;
        int spritePalette = 0;
        int spritePriority = 0;

        if (maskRegister.contains(PPUMaskEnum.SpriteEnable)) {
            sprite0Rendering = false;
            for (int i = 0; i < spritesOnScanline; i++) {
                if (SecondaryOAMData[i * 4 + 3] == 0) {
                    int spritePixelLo = (spriteShiftPatternLoByte[i] & 0x80) != 0 ? 1 : 0;
                    int spritePixelHi = (spriteShiftPatternHiByte[i] & 0x80) != 0 ? 1 : 0;
                    spritePixel = spritePixelLo | (spritePixelHi << 1);

                    spritePalette = (SecondaryOAMData[i * 4 + 2] & 0x03) + 4;
                    spritePriority = (SecondaryOAMData[i * 4 + 2] & 0x20) == 0 ? 1 : 0;
                    
                    if (spritePixel != 0) {
                        if (i == 0) {
                            sprite0Rendering = true;
                        }
                        break;
                    }
                }
            }
        }

        int finalPixel = 0;
        int finalPalette = 0;

        if (backgroundPixel == 0 && spritePixel == 0) {
            // do nothing
        } else if (backgroundPixel == 0 && spritePixel > 0) {
            finalPixel = spritePixel;
            finalPalette = spritePalette;
        } else if (backgroundPixel > 0 && spritePixel == 0) {
            finalPixel = backgroundPixel;
            finalPalette = backgroundPalette;
        } else {
            if (spritePriority != 0) {
                finalPixel = spritePixel;
                finalPalette = spritePalette;
            } else {
                finalPixel = backgroundPixel;
                finalPalette = backgroundPalette;
            }

            if (sprite0HitPossible && sprite0Rendering) {
                boolean renderBackgroundEnabled = maskRegister.contains(PPUMaskEnum.BGEnable);
                boolean renderSpritesEnabled = maskRegister.contains(PPUMaskEnum.SpriteEnable);
                boolean backgroundLeft = maskRegister.contains(PPUMaskEnum.BGLeftColEnable);
                boolean spriteLeft = maskRegister.contains(PPUMaskEnum.SpriteLeftColEnable);

                if (renderBackgroundEnabled && renderSpritesEnabled) {
                    if (!(backgroundLeft || spriteLeft)) {
                        if (cycles >= 9 && cycles < 258) {
                            statusRegister.add(PPUStatusEnum.Sprite0Hit);
                        }
                    } else {
                        if (cycles >= 1 && cycles < 258) {
                            statusRegister.add(PPUStatusEnum.Sprite0Hit);
                        }
                    }
                }
            }
        }

        float r, g, b;

        int colorIndex = readFromPPUBus(0x3F00 + (finalPalette << 2) + finalPixel) & 0x3F;
        float[] palette = paletteColors[colorIndex];
        r = palette[0];
        g = palette[1];
        b = palette[2];

        setPixel(cycles - 1, scanline, r, g, b);

        cycles++;
        if (cycles >= 341) {
            cycles = 0;
            scanline++;

            if (scanline >= 261) {
                scanline = -1;
                frameReady = true;
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
