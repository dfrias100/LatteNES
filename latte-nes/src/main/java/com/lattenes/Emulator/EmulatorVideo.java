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

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class EmulatorVideo {
    // Window handle
    private long window;

    com.lattenes.Core.System system;

    // Shader programs
    private int vertShaderProgram;
    private int fragShaderProgram;
    private int shaderProgram;

    // IntBuffers to hold the vertex and element objects
    // as well as texture objects
    private IntBuffer textureObj;
    private IntBuffer vertexArrayObj;
    private IntBuffer vertexBufferObj;
    private IntBuffer elementBufferObj;

    // This actually holds the texture data
    private FloatBuffer pixels;

    // Does nothing right now, but could be useful later
    private int windowWidth = 800;
    private int windowHeight = 750;

    // Size of the texture we will generate from OpenGL
    private final int NES_WIDTH = 256;
    private final int NES_HEIGHT = 240;

    // This would have its own file, but we're not ever
    // changing the shader (for now)
    private final String vertexShader = "#version 330 core\n"
                    + "layout (location = 0) in vec3 aPos;\n"
                    + "layout (location = 1) in vec3 aTexCoord;\n"
                    + "out vec2 TexCoord;\n"
                    + "void main()\n"
                    + "{\n"
                    + "   gl_Position = vec4(aPos, 1.0);\n"
                    + "   TexCoord = vec2(aTexCoord.x, aTexCoord.y);\n"
                    + "}\0";

    private final String fragShader = "#version 330 core\n"
                    + "out vec4 FragColor;\n"
                    + "in vec2 TexCoord;\n"
                    + "uniform sampler2D ourTexture;\n" 
                    + "void main()\n"
                    + "{\n"
                    + "   FragColor = texture(ourTexture, TexCoord);\n"
                    + "}\0"; 

    // This is the quad we will draw the texture on
    private final float QUAD_VERTEX_DATA[] = {
        1.0f,  1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
       -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
       -1.0f,  1.0f, 0.0f, 0.0f, 1.0f
    };

    // This is the indices we will use to draw the quad
    private final int QUAD_INDEX_DATA[] = {
        0, 1, 3,
        1, 2, 3,
    };

    private void windowResizeCallback(long window, int width, int height) {
        glViewport(0, 0, width, height);

        windowWidth = width;
        windowHeight = height;
    }

    public double getTime() {
        return glfwGetTime();
    }

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Set window hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, "LatteNES", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // TODO: Change this later for emulator input support   
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }

            if (key == GLFW_KEY_L) {
                system.pressStart();
            }
        });

        // Set our resize call back, this changes the viewport
        glfwSetFramebufferSizeCallback(window, this::windowResizeCallback);

        // Set the window position to the center, for this we need to push a stack frame
        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetFramebufferSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }; // Stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
    
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);

        // Initialize OpenGL
        GL.createCapabilities();

        // Set the clear color
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Something to print to the terminal
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        // Create the pixel buffer
        pixels = BufferUtils.createFloatBuffer(NES_WIDTH * NES_HEIGHT * 4);
    }

    public void createTexture(float[] framebuffer) {
        vertShaderProgram = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertShaderProgram, vertexShader);
        glCompileShader(vertShaderProgram);

        fragShaderProgram = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragShaderProgram, fragShader);
        glCompileShader(fragShaderProgram);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertShaderProgram);
        glAttachShader(shaderProgram, fragShaderProgram);
        glLinkProgram(shaderProgram);

        glDeleteShader(vertShaderProgram);
        glDeleteShader(fragShaderProgram);

        vertexArrayObj = BufferUtils.createIntBuffer(1);
        vertexBufferObj = BufferUtils.createIntBuffer(1);
        elementBufferObj = BufferUtils.createIntBuffer(1);

        glGenVertexArrays(vertexArrayObj);
        glGenBuffers(vertexBufferObj);
        glGenBuffers(elementBufferObj);

        glBindVertexArray(vertexArrayObj.get(0));

        glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObj.get(0));
        glBufferData(GL_ARRAY_BUFFER, QUAD_VERTEX_DATA, GL_STATIC_DRAW);
        
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementBufferObj.get(0));
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, QUAD_INDEX_DATA, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * (Float.SIZE / 8), 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * (Float.SIZE / 8), 3 * (Float.SIZE / 8));
        glEnableVertexAttribArray(1);

        pixels.put(framebuffer);
        pixels.flip();

        textureObj = BufferUtils.createIntBuffer(1);
        glGenTextures(textureObj);
        glBindTexture(GL_TEXTURE_2D, textureObj.get(0));
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, NES_WIDTH, NES_HEIGHT, 0, GL_RGBA, GL_FLOAT, pixels);
    
        glUseProgram(shaderProgram);
        glUniform1i(glGetUniformLocation(shaderProgram, "ourTexture"), 0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void displayTexture() {
        glUseProgram(shaderProgram);
        glBindVertexArray(vertexArrayObj.get(0));        
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    public void updateTexture(float[] framebuffer) {
        pixels.put(framebuffer);
        pixels.flip();
        glBindTexture(GL_TEXTURE_2D, textureObj.get(0));
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, NES_WIDTH, NES_HEIGHT, GL_RGBA, GL_FLOAT, pixels);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void draw() {
        glClear(GL_COLOR_BUFFER_BIT);
        displayTexture();
        glFlush();
        glfwSwapBuffers(window);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void cleanup() {
        glDeleteVertexArrays(vertexArrayObj);
        glDeleteBuffers(vertexBufferObj);
        glDeleteBuffers(elementBufferObj);
        glDeleteTextures(textureObj);
        glDeleteProgram(shaderProgram);

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}
