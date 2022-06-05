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

import com.lattenes.Core.APU.APU;
import com.lattenes.Core.CPU.MOS6502;
import com.lattenes.Core.Cartridge.Cartridge;
import com.lattenes.Emulator.Emulator;
import com.lattenes.Emulator.EmulatorAudio;
import com.lattenes.Util.SaveStateUtil;

public class System {
    private Memory memoryManagementUnit;
    private MOS6502 CPU;
    private PPU NESPPU;
    private Cartridge cartridge;
    private APU NESAPU;
    private long systemCycleCount = 0;
    private Emulator emulatorObj;

    private double currentNesAudioTime = 0.0f;
    private final double realAudioTimeStep = 1.0 / 44100.0;
    private final double nesAudioTimeStep = 1.0 / 5369318.0;

    private EmulatorAudio audio;

    public System(String cartridgeFile) {
        try {
            cartridge = new Cartridge(cartridgeFile);
        } catch (Exception e) {
            e.printStackTrace();
            java.lang.System.exit(-1);
        }
        NESPPU = new PPU(cartridge);
        NESAPU = new APU();
        memoryManagementUnit = new Memory(cartridge, NESPPU, NESAPU);
        CPU = new MOS6502(memoryManagementUnit, false);
        CPU.reset();
    }

    public boolean frameReady() {
        return NESPPU.frameReady;
    }

    public void clearFrameReady() {
        NESPPU.frameReady = false;
    }

    public void tick() {
        if (memoryManagementUnit.saveStateFlag) {
            memoryManagementUnit.saveStateFlag = false;
            byte[] cpuState = CPU.dumpState();
            byte[] memState = memoryManagementUnit.dumpState();
            byte[] ppuState = NESPPU.dumpState();

            final int size = cpuState.length + memState.length + ppuState.length;
            byte[] state = new byte[size];

            java.lang.System.out.println(cpuState.length);
            java.lang.System.out.println(memState.length);
            java.lang.System.out.println(ppuState.length);

            java.lang.System.arraycopy(cpuState, 0, state, 0, cpuState.length);
            java.lang.System.arraycopy(memState, 0, state, cpuState.length, memState.length);
            java.lang.System.arraycopy(ppuState, 0, state, cpuState.length + memState.length, ppuState.length);

            SaveStateUtil.saveState("sav", state);
            audio.flushSamples(false);
            emulatorObj.keepTicking = true;
        } else if (memoryManagementUnit.loadStateFlag) {
            memoryManagementUnit.loadStateFlag = false;
            byte[] systemState = SaveStateUtil.loadState("sav");
            if (systemState != null) {
                byte[] cpuState = new byte[17];
                byte[] memState = new byte[2086];
                byte[] ppuState = new byte[985497];

                java.lang.System.arraycopy(systemState, 0, cpuState, 0, cpuState.length);
                java.lang.System.arraycopy(systemState, cpuState.length, memState, 0, memState.length);
                java.lang.System.arraycopy(systemState, cpuState.length + memState.length, ppuState, 0, ppuState.length);

                CPU.loadState(cpuState);
                memoryManagementUnit.loadState(memState);
                NESPPU.loadState(ppuState);
                audio.flushSamples(false);
                emulatorObj.keepTicking = true;
            }
        }

        NESPPU.clock();

        NESAPU.clock();

        if (systemCycleCount % 3 == 0) {
            if (memoryManagementUnit.PPUReqDMA && CPU.doneProcessingInstruction()) {
                boolean oddCycle = systemCycleCount % 2 == 1;
                if (memoryManagementUnit.DMAWait) {
                    if(!oddCycle) {
                        memoryManagementUnit.DMAWait = false;
                    }
                } else {
                    if (oddCycle) {
                        memoryManagementUnit.readDMAAddr();
                    } else {
                        memoryManagementUnit.stepDMA();
                    }
                }
            } else {
                CPU.clock();
            }    
        }

        currentNesAudioTime += nesAudioTimeStep;
        if (currentNesAudioTime >= realAudioTimeStep) {
            currentNesAudioTime -= realAudioTimeStep;
            audio.outputSample(NESAPU.getSample());
        }

        systemCycleCount++;
    }

    public long getCycleCount() {
        return systemCycleCount;
    }

    public void endLog() {
        CPU.endLog();
    }

    public float[] getScreen() {
        return NESPPU.screen;
    }

    public Memory getMemory() {
        return memoryManagementUnit;
    }

    public void attachEmuAudioObject(EmulatorAudio emulatorAudio) {
        this.audio = emulatorAudio;
    }

    public void attachEmulatorObject(Emulator emulator) {
        this.emulatorObj = emulator;
    }
}
