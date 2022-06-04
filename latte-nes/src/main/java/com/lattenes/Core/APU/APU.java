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
    private ChannelLengthCounter pulse1LengthCtr;
    private FrequencySweeper pulse1Sweep;
    private Envelope pulse1Envelope;
    private WaveSampler pulse1Synth;

    private PulseSequencer pulse2Seq;
    private ChannelLengthCounter pulse2LengthCtr;
    private FrequencySweeper pulse2Sweep;
    private Envelope pulse2Envelope;
    private WaveSampler pulse2Synth;

    private NoiseSequcencer noiseSeq;
    private ChannelLengthCounter noiseLengthCtr;
    private Envelope noiseEnvelope;

    private long frameCount = 0;
    private double pulse1Intermediate = 0;
    private double pulse2Intermediate = 0;
    private double pulse1Sample = 0;
    private double pulse2Sample = 0;
    private double noiseSample = 0;
    private double globalTime = 0;
    private boolean pulse1ChanEnabled = true;
    private boolean pulse2ChanEnabled = true;
    private boolean noiseChanEnabled = true;

    private final short lengthTable[] = { 
        10, 254, 20, 2, 40, 4, 80, 6,
        160, 8, 60, 10, 14, 12, 26, 14,
        12, 16, 24, 18, 48, 20, 96, 22,
        192, 24, 72, 26, 16, 28, 32, 30
    };

    public APU() {
        pulse1Seq = new PulseSequencer();
        pulse1LengthCtr = new ChannelLengthCounter();
        pulse1Sweep = new FrequencySweeper();
        pulse1Envelope = new Envelope();
        pulse1Synth = new WaveSampler();

        pulse2Seq = new PulseSequencer();
        pulse2LengthCtr = new ChannelLengthCounter();
        pulse2Sweep = new FrequencySweeper();
        pulse2Envelope = new Envelope();
        pulse2Synth = new WaveSampler();

        noiseSeq = new NoiseSequcencer();
        noiseLengthCtr = new ChannelLengthCounter();
        noiseEnvelope = new Envelope();

        pulse1Sweep.pulseSeq = pulse1Seq;
        pulse2Sweep.pulseSeq = pulse2Seq;

        noiseSeq.sequence = 0xDBDB;
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
                pulse1LengthCtr.halt = (value & 0x20) == 0x20;
                pulse1Envelope.enabled = (value & 0x10) != 0x10;
                pulse1Envelope.volume = (value & 0x0F);
                break;
            case 0x4001:
                pulse1Sweep.enabled = (value & 0x80) == 0x80;
                pulse1Sweep.period = (value & 0x70) >> 4;
                pulse1Sweep.negate = (value & 0x8) == 0x8;
                pulse1Sweep.shift = (value & 0x07);
                pulse1Sweep.reload = true;
                break;
            case 0x4002:
                pulse1Seq.reload = (pulse1Seq.reload & 0xFF00) | (value & 0xFF);
                break;
            case 0x4003:
                pulse1Seq.reload = (pulse1Seq.reload & 0x00FF) | ((value & 0x07) << 8);
                pulse1Seq.timer = pulse1Seq.reload;
                pulse1LengthCtr.counter = lengthTable[(value & 0xF8) >> 3];
                pulse1Envelope.start = true;
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
                pulse2LengthCtr.halt = (value & 0x20) == 0x20;
                pulse2Envelope.enabled = (value & 0x10) != 0x10;
                pulse2Envelope.volume = (value & 0x0F);
                break;
            case 0x4005:
                pulse2Sweep.enabled = (value & 0x80) == 0x80;
                pulse2Sweep.period = (value & 0x70) >> 4;
                pulse2Sweep.negate = (value & 0x8) == 0x8;
                pulse2Sweep.shift = (value & 0x07);
                pulse2Sweep.reload = true;
                break;
            case 0x4006:
                pulse2Seq.reload = (pulse2Seq.reload & 0xFF00) | (value & 0xFF);
                break;
            case 0x4007:
                pulse2Seq.reload = (pulse2Seq.reload & 0x00FF) | ((value & 0x07) << 8);
                pulse2Seq.timer = pulse2Seq.reload;
                pulse2LengthCtr.counter = lengthTable[(value & 0xF8) >> 3];
                pulse2Envelope.start = true;
                break;
            case 0x4008:
                break;
            case 0x400C:
                noiseLengthCtr.halt = (value & 0x20) == 0x20;
                noiseEnvelope.enabled = (value & 0x10) != 0x10;
                noiseEnvelope.volume = (value & 0x0F);
                break;
            case 0x400E:
                switch (value & 0xF) {
                    case 0: noiseSeq.reload = 4; break;
                    case 1: noiseSeq.reload = 8; break;
                    case 2: noiseSeq.reload = 16; break;
                    case 3: noiseSeq.reload = 32; break;
                    case 4: noiseSeq.reload = 64; break;
                    case 5: noiseSeq.reload = 96; break;
                    case 6: noiseSeq.reload = 128; break;
                    case 7: noiseSeq.reload = 160; break;
                    case 8: noiseSeq.reload = 202; break;
                    case 9: noiseSeq.reload = 254; break;
                    case 10: noiseSeq.reload = 380; break;
                    case 11: noiseSeq.reload = 508; break;
                    case 12: noiseSeq.reload = 762; break;
                    case 13: noiseSeq.reload = 1016; break;
                    case 14: noiseSeq.reload = 2034; break;
                    case 15: noiseSeq.reload = 4068; break;
                }
                break;
            case 0x4015:
                pulse1ChanEnabled = (value & 0x01) == 0x01;
                pulse2ChanEnabled = (value & 0x02) == 0x02;
                noiseChanEnabled = (value & 0x04) == 0x04;
                break;
            case 0x400F:
                pulse1Envelope.start = true;
                pulse2Envelope.start = true;
                noiseEnvelope.start = true;
                noiseLengthCtr.counter = lengthTable[(value & 0xF8) >> 3];
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
                pulse1Envelope.clock(pulse1LengthCtr.halt);
                pulse2Envelope.clock(pulse2LengthCtr.halt);
                noiseEnvelope.clock(noiseLengthCtr.halt);
            }

            if (halfFrame) {
                pulse1LengthCtr.clock(pulse1ChanEnabled);
                pulse2LengthCtr.clock(pulse2ChanEnabled);
                noiseLengthCtr.clock(noiseChanEnabled);
                pulse1Sweep.clock(0);
                pulse2Sweep.clock(1);
            }

            pulse1Seq.clock(pulse1ChanEnabled);
            pulse1Synth.freq = 1789773.0 / (16.0 * (double) ((pulse1Seq.reload + 1) & 0xFFFF));
            pulse1Synth.amp = (pulse1Envelope.out + 1) / 16.0;
            pulse1Intermediate = pulse1Synth.sample(globalTime);

            if (pulse1LengthCtr.counter > 0 && pulse1Seq.timer >= 8 && !pulse1Sweep.mute && pulse1Envelope.out > 2) {
                pulse1Sample += (pulse1Intermediate - pulse1Sample) * 0.5;
            } else {
                pulse1Sample = 0;
            }

            pulse2Seq.clock(pulse2ChanEnabled);
            pulse2Synth.freq = 1789773.0 / (16.0 * (double) ((pulse2Seq.reload + 1) & 0xFFFF));
            pulse2Synth.amp = (pulse2Envelope.out + 1) / 16.0;
            pulse2Intermediate = pulse2Synth.sample(globalTime);

            if (pulse2LengthCtr.counter > 0 && pulse2Seq.timer >= 8 && !pulse1Sweep.mute && pulse2Envelope.out > 2) {
                pulse2Sample += (pulse2Intermediate - pulse2Sample) * 0.5;
            } else {
                pulse2Sample = 0;
            }

            noiseSeq.clock(noiseChanEnabled);
            if (noiseLengthCtr.counter > 0 && noiseSeq.timer >= 8) {
                noiseSample = (noiseSeq.output & 0xFF) * (noiseEnvelope.out + 1) / 16.0;
            }
            
            if (!pulse1ChanEnabled) pulse1Sample = 0;
            if (!pulse2ChanEnabled) pulse2Sample = 0;
            if (!noiseChanEnabled) noiseSample = 0;
        }
        pulse1Sweep.track();
        pulse2Sweep.track();
        cycles++;
    }

    public short getSample() {
        return (short) (0.5 * ((pulse1Sample) * 0.1 + (pulse2Sample) * 0.1 + 1.0 * (noiseSample) * 0.1) * Short.MAX_VALUE);
    }
}
