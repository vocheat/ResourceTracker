/*
 * MIT License
 *
 * Copyright (c) 2026 vocheat
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.resourcetracker.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Runtime icon masks loaded from the PNG assets bundled with the mod.
 */
public final class PngIcons {
    public static final int ICON_SIZE = 15;

    private static final String BASE_PATH = "/assets/resourcetracker/textures/gui/icons/";

    private PngIcons() {}

    public enum Icon {
        PLUS("plus.png"),
        RELOAD("reload.png"),
        SEARCH("search.png"),
        SETTINGS("settings.png"),
        FOLDER("folder.png"),
        EYE("eye.png"),
        EYE_CROSSED("eye_crossed.png"),
        TRASH("trash.png"),
        CROSS("cross.png");

        private final Mask mask;

        Icon(String fileName) {
            this.mask = loadMask(fileName);
        }

        Mask mask() {
            return mask;
        }
    }

    static final class Mask {
        final int width;
        final int height;
        final int[] pixels;

        Mask(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private static Mask loadMask(String fileName) {
        String resourcePath = BASE_PATH + fileName;
        try (InputStream stream = PngIcons.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing icon resource: " + resourcePath);
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IllegalStateException("Unreadable icon resource: " + resourcePath);
            }
            return toMask(resourcePath, image);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load icon resource: " + resourcePath, e);
        }
    }

    private static Mask toMask(String resourcePath, BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != ICON_SIZE || height != ICON_SIZE) {
            throw new IllegalStateException(resourcePath + " must be " + ICON_SIZE + "x" + ICON_SIZE + " pixels");
        }

        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0) {
                    count++;
                }
            }
        }

        int[] pixels = new int[count];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha != 0) {
                    pixels[index++] = (x << 16) | (y << 8) | alpha;
                }
            }
        }
        return new Mask(width, height, pixels);
    }
}
