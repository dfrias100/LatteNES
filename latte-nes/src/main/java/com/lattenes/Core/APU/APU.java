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

package com.lattenes.Core.APU;

import com.lattenes.Emulator.EmulatorAudio;

public class APU {
    private long cycles = 0;

    private PulseSequencer pulse1Seq;
    private WaveSampler pulse1Synth;

    private PulseSequencer pulse2Seq;
    private WaveSampler pulse2Synth;

    private long frameCount = 0;
    private double pulse1Output = 0;
    private double pulse2Output = 0;
    private double globalTime = 0;
    private boolean pulse1ChanEnabled = true;
    private boolean pulse2ChanEnabled = true;

    public APU() {
        pulse1Seq = new PulseSequencer();
        pulse1Synth = new WaveSampler();

        pulse2Seq = new PulseSequencer();
        pulse2Synth = new WaveSampler();
    }

    public void writeToAPUFromCPU(int address, byte value) {
        switch (address) {
            case 0x4000:
                switch ((value & 0xC0) >> 6) {
                    case 0x00:
                        pulse1Seq.sequence = 0b00000001;
                        pulse1Synth.duty = 0.125;
                        break;
                    case 0x01:
                        pulse1Seq.sequence = 0b00000011;
                        pulse1Synth.duty = 0.250;
                        break;
                    case 0x02:
                        pulse1Seq.sequence = 0b00001111;
                        pulse1Synth.duty = 0.500;
                        break;
                    case 0x03:
                        pulse1Seq.sequence = 0b11111100;
                        pulse1Synth.duty = 0.750;
                        break;
                }
                break;
            case 0x4001:
                break;
            case 0x4002:
                pulse1Seq.reload = (pulse1Seq.reload & 0xFF00) | (value & 0xFF);
                break;
            case 0x4003:
                pulse1Seq.reload = (pulse1Seq.reload & 0x00FF) | ((value & 0x07) << 8);
                pulse1Seq.timer = pulse1Seq.reload;
                break;
            case 0x4004:
                switch ((value & 0xC0) >> 6) {
                    case 0x00:
                        pulse2Seq.sequence = 0b00000001;
                        pulse2Synth.duty = 0.125;
                        break;
                    case 0x01:
                        pulse2Seq.sequence = 0b00000011;
                        pulse2Synth.duty = 0.250;
                        break;
                    case 0x02:
                        pulse2Seq.sequence = 0b00001111;
                        pulse2Synth.duty = 0.500;
                        break;
                    case 0x03:
                        pulse2Seq.sequence = 0b11111100;
                        pulse2Synth.duty = 0.750;
                        break;
                }
                break;
            case 0x4005:
                break;
            case 0x4006:
                pulse2Seq.reload = (pulse2Seq.reload & 0xFF00) | (value & 0xFF);
                break;
            case 0x4007:
                pulse2Seq.reload = (pulse2Seq.reload & 0x00FF) | ((value & 0x07) << 8);
                pulse2Seq.timer = pulse2Seq.reload;
                break;
            case 0x4008:
                break;
            case 0x400C:
                break;
            case 0x400E:
                break;
            case 0x4015:
                pulse1ChanEnabled = (value & 0x01) == 0x01;
                pulse2ChanEnabled = (value & 0x02) == 0x02;
                break;
            case 0x400F:
                break;
        }
    }

    public void clock() {
        boolean quarterFrame = false;
        boolean halfFrame = false;
        globalTime += 0.33333333 / 1789773.0;

        if (cycles % 6 == 0) {
            frameCount++;

            if (frameCount == 3729) {
                quarterFrame = true;
            }

            if (frameCount == 7457) {
                halfFrame = true;
                quarterFrame = true;
            }

            if (frameCount == 11186) {
                quarterFrame = true;
            }

            if (frameCount == 14916) {
                halfFrame = true;
                quarterFrame = true;
                frameCount = 0;
            }

            if (quarterFrame) {

            }

            if (halfFrame) {

            }

            //pulse1Seq.clock(pulse1ChanEnabled);
            pulse1Synth.freq = 1789773.0 / (16.0 * (double) ((pulse1Seq.reload + 1) & 0xFFFF));
            pulse1Output = pulse1Synth.sample(globalTime);

            pulse2Synth.freq = 1789773.0 / (16.0 * (double) ((pulse2Seq.reload + 1) & 0xFFFF));
            pulse2Output = pulse2Synth.sample(globalTime);
        }
        cycles++;
    }

    public short getSample() {
        return (short) (0.5 * ((pulse1Output) * 0.5 + (pulse2Output) * 0.5) * Short.MAX_VALUE);
    }
}
