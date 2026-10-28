package com.aetherium.gui;

import java.util.ArrayList;
import java.util.List;

public final class Page {
    public final String name;
    public final String title;
    public final String subtitle;
    public final PixelArt.Bitmap icon;
    public final List<Setting> settings = new ArrayList<>();

    public Page(final String name, final String title, final String subtitle, final PixelArt.Bitmap icon) {
        this.name = name;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    public Page add(final Setting setting) {
        this.settings.add(setting);
        return this;
    }
}
