package com.agris.gui;

import com.agris.Agris;

/**
 * Obsidian + Violet/Emerald Prism color system shared by {@link AgrisShaderPackScreen}
 * and {@link AgrisShaderOptionsScreen}, automatically honoring Aetherium's
 * {@code darkMode} setting when Aetherium is installed.
 */
public final class AgrisTheme {
    public final int backdropTop;
    public final int backdropBottom;
    public final int panelBg;
    public final int panelBorder;
    public final int rowBg;
    public final int rowHoverBg;
    public final int rowSelectedBg;
    public final int accentPrimary;
    public final int accentSecondary;
    public final int textPrimary;
    public final int textSecondary;
    public final int textMuted;
    public final int badgeBuiltin;
    public final int badgeExternal;
    public final int statusOk;
    public final int statusError;

    private AgrisTheme(final boolean dark) {
        if (dark) {
            this.backdropTop = 0xEE0B0E17;
            this.backdropBottom = 0xF4121828;
            this.panelBg = 0xE6151B2B;
            this.panelBorder = 0xFF2B3550;
            this.rowBg = 0xCC1A2236;
            this.rowHoverBg = 0xE6232E48;
            this.rowSelectedBg = 0xEE28365A;
            this.accentPrimary = 0xFF8B5CF6;   // Aetherium violet
            this.accentSecondary = 0xFF10B981; // Agris emerald prism
            this.textPrimary = 0xFFF3F6FC;
            this.textSecondary = 0xFFB8C4DC;
            this.textMuted = 0xFF7D8CA8;
            this.badgeBuiltin = 0xFF6D28D9;
            this.badgeExternal = 0xFF047857;
            this.statusOk = 0xFF34D399;
            this.statusError = 0xFFF87171;
        } else {
            this.backdropTop = 0xEEF1F5FB;
            this.backdropBottom = 0xF4E2E8F4;
            this.panelBg = 0xF2FFFFFF;
            this.panelBorder = 0xFFCBD5E1;
            this.rowBg = 0xE6F8FAFC;
            this.rowHoverBg = 0xFFEFF4FB;
            this.rowSelectedBg = 0xFFE0E7FF;
            this.accentPrimary = 0xFF6D28D9;
            this.accentSecondary = 0xFF059669;
            this.textPrimary = 0xFF0F172A;
            this.textSecondary = 0xFF334155;
            this.textMuted = 0xFF64748B;
            this.badgeBuiltin = 0xFF7C3AED;
            this.badgeExternal = 0xFF059669;
            this.statusOk = 0xFF059669;
            this.statusError = 0xFFDC2626;
        }
    }

    public static AgrisTheme current() {
        return new AgrisTheme(Agris.aetherium().prefersDarkMode());
    }
}
