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

package com.lattenes.Emulator;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class EmulatorAudio {
    private SourceDataLine line;
    private byte[] soundBuf;
    private int bufPos;
    private float outVol;

    public EmulatorAudio(int sampleRate) {
        outVol = 1.0f;
        final int samplesPerFrame = (int) Math.ceil(sampleRate / 60.0988);
        soundBuf = new byte[samplesPerFrame * 2 * 2];
        try {
            AudioFormat AF = new AudioFormat(sampleRate, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(AF);
            line.open(AF, samplesPerFrame * 4 * 2);
            line.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void flushSamples(boolean wait) {
        if (line.available() < bufPos) {
            if (wait) {
                line.write(soundBuf, 0, bufPos);
            }
        } else {
            line.write(soundBuf, 0, bufPos);
        }
        bufPos = 0;
    }

    public void outputSample(int sample) {
        sample *= outVol;
        if (sample < -32768) sample = -32768;
        if (sample > 32767) sample = 32767;

        soundBuf[bufPos] = (byte) (sample & 0xFF);
        soundBuf[bufPos + 1] = (byte) ((sample >> 8) & 0xFF);
        bufPos += 2;
    }

    public void pause() {
        line.flush();
        line.stop();
    }

    public void resume() {
        line.start();
    }

    public void destroy() {
        line.stop();
        line.close();
    }

    public boolean bufHasLT(int samples) {
        return (line.getBufferSize() - line.available()) <= samples;
    }
}