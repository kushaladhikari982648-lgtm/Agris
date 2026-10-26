package com.aetherium.gui;

public final class PixelArt {
    public static final class Bitmap {
        public final String[] rows;

        public Bitmap(final String[] rows) {
            this.rows = rows;
        }
    }

    public static final Bitmap SPARKLES = new Bitmap(new String[]{"################"});

    private PixelArt() {
    }
}
