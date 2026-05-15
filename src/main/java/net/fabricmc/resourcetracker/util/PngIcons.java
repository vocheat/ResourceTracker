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

/**
 * Custom GUI icons packed into one 64x64 texture atlas.
 */
public final class PngIcons {
    public static final int ICON_SIZE = 15;
    public static final int ATLAS_SIZE = 64;
    public static final String ATLAS_NAMESPACE = "resourcetracker";
    public static final String ATLAS_PATH = "textures/gui/icons_atlas.png";

    private PngIcons() {}

    public enum Icon {
        PLUS(0, 0),
        RELOAD(16, 0),
        SEARCH(32, 0),
        SETTINGS(48, 0),
        FOLDER(0, 16),
        EYE(16, 16),
        EYE_CROSSED(32, 16),
        TRASH(48, 16),
        CROSS(0, 32);

        private final int u;
        private final int v;
        private final int width;
        private final int height;

        Icon(int u, int v) {
            this(u, v, ICON_SIZE, ICON_SIZE);
        }

        Icon(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public int u() {
            return u;
        }

        public int v() {
            return v;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }
}
