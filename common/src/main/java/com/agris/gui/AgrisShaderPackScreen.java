package com.agris.gui;

import com.agris.Agris;
import com.agris.client.AgrisClientHooks;
import com.agris.config.AgrisConfig;
import com.agris.mobile.MobilePreset;
import com.agris.pack.ShaderPack;
import com.agris.pack.ShaderPackDiscovery;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animated Agris Shader Pack Manager screen crafted in Aetherium's crisp obsidian
 * &amp; violet/emerald aesthetic.
 *
 * <p>Supports live shader toggling, built-in &amp; external {@code .zip}/folder pack
 * browsing, instant search filtering, opening {@code shaderpacks/}, launching
 * {@link AgrisShaderOptionsScreen}, hot-reloading ({@code R}), and clear GLSL
 * compilation diagnostics.</p>
 */
public final class AgrisShaderPackScreen extends Screen {
    private static final int ROW_HEIGHT = 28;

    private record ActionBtn(int x, int y, int w, int h, String label, boolean primary, boolean enabled, Runnable action) {
        boolean contains(final double mx, final double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private final Screen parent;
    private List<ShaderPackDiscovery.PackEntry> allPacks = List.of();
    private List<ShaderPackDiscovery.PackEntry> filteredPacks = List.of();
    private final List<ActionBtn> buttons = new ArrayList<>();

    private String searchQuery = "";
    private boolean searchFocused;
    private String stagedPackId = "";
    private boolean stagedEnabled;
    private int activePresetIndex = -1;
    private double scrollOffset;
    private double targetScrollOffset;
    private boolean touchPressInList;
    private double touchDraggedDistance;

    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private int searchBoxX;
    private int searchBoxY;
    private int searchBoxW;
    private int searchBoxH;

    public AgrisShaderPackScreen(final Screen parent) {
        super(Component.literal("Agris Shaders"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final AgrisConfig cfg = Agris.config();
        this.stagedEnabled = cfg.enabled.get();
        this.stagedPackId = cfg.shaderPack.get();
        refreshPackList();
        rebuildLayout();
    }

    private void refreshPackList() {
        this.allPacks = Agris.packs().discoverPacks();
        applyFilter();
    }

    private void applyFilter() {
        if (this.searchQuery.isBlank()) {
            this.filteredPacks = this.allPacks;
            return;
        }
        final String needle = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        final List<ShaderPackDiscovery.PackEntry> out = new ArrayList<>();
        for (final ShaderPackDiscovery.PackEntry entry : this.allPacks) {
            if (entry.displayName().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.id().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(entry);
            }
        }
        this.filteredPacks = List.copyOf(out);
    }

    private void rebuildLayout() {
        this.buttons.clear();
        final int pad = Math.max(16, this.width / 24);
        this.listLeft = pad;
        this.listRight = this.width - pad;
        this.listTop = 58;
        this.listBottom = this.height - 62;

        this.searchBoxW = Math.min(200, (this.listRight - this.listLeft) / 3);
        this.searchBoxH = 20;
        this.searchBoxX = this.listRight - this.searchBoxW;
        this.searchBoxY = 30;

        // Top-left master switch + Reload + One-Tap Mobile Preset
        final String masterLabel = this.stagedEnabled ? "Shaders: ON" : "Shaders: OFF";
        this.buttons.add(new ActionBtn(this.listLeft, 30, 100, 20, masterLabel, this.stagedEnabled, true, () -> {
            this.stagedEnabled = !this.stagedEnabled;
            commitSelection();
            rebuildLayout();
        }));
        this.buttons.add(new ActionBtn(this.listLeft + 104, 30, 74, 20, "Reload (R)", false, true, () -> {
            commitSelection();
            AgrisClientHooks.reloadShadersAndChunks();
        }));

        final MobilePreset[] presets = MobilePreset.values();
        final String presetLabel = this.activePresetIndex >= 0 && this.activePresetIndex < presets.length
                ? "Preset: " + presets[this.activePresetIndex].label()
                : "Mobile Preset \u25be";
        final int presetBtnW = Math.max(96, Math.min(138, this.searchBoxX - (this.listLeft + 184) - 6));
        if (presetBtnW >= 80) {
            this.buttons.add(new ActionBtn(this.listLeft + 182, 30, presetBtnW, 20, presetLabel, false, true, () -> {
                this.activePresetIndex = (this.activePresetIndex + 1) % presets.length;
                final MobilePreset chosen = presets[this.activePresetIndex];
                final AgrisConfig cfg = Agris.config();
                chosen.applyTo(cfg, Agris.pipeline().glLimits().mobileArchitecture());
                this.stagedEnabled = cfg.enabled.get();
                this.stagedPackId = cfg.shaderPack.get();
                Agris.saveConfigAndReload(AgrisClientHooks::reloadShadersAndChunks);
                rebuildLayout();
            }));
        }

        // Bottom action bar
        final int btnY = this.height - 34;
        final int totalW = this.listRight - this.listLeft;
        final int gap = 8;
        final int btnW = (totalW - gap * 3) / 4;

        this.buttons.add(new ActionBtn(this.listLeft, btnY, btnW, 22, "Open Pack Folder", false, true,
                () -> Util.getPlatform().openFile(Agris.packs().shaderpacksDirectory().toFile())));

        final boolean canConfigure = this.stagedEnabled && !this.stagedPackId.isBlank();
        this.buttons.add(new ActionBtn(this.listLeft + (btnW + gap), btnY, btnW, 22, "Shader Options...", false,
                canConfigure, this::openOptionsForSelection));

        this.buttons.add(new ActionBtn(this.listLeft + (btnW + gap) * 2, btnY, btnW, 22, "Apply", true, true,
                this::commitSelection));

        this.buttons.add(new ActionBtn(this.listLeft + (btnW + gap) * 3, btnY,
                totalW - (btnW + gap) * 3, 22, "Done", false, true, this::onClose));
    }

    private void commitSelection() {
        final AgrisConfig cfg = Agris.config();
        cfg.enabled.set(this.stagedEnabled);
        if (!this.stagedPackId.isBlank()) {
            cfg.shaderPack.set(this.stagedPackId);
        }
        Agris.saveConfigAndReload(AgrisClientHooks::reloadShadersAndChunks);
    }

    private void openOptionsForSelection() {
        commitSelection();
        if (this.minecraft == null) {
            return;
        }
        try {
            final String lang = this.minecraft.options != null ? this.minecraft.options.languageCode : "en_us";
            final ShaderPack loaded = Agris.packs().loadPack(this.stagedPackId, lang);
            if (loaded != null) {
                this.minecraft.setScreen(new AgrisShaderOptionsScreen(this, loaded));
            }
        } catch (final Throwable ignored) {
            // Error banner will display any load failure.
        }
    }

    @Override
    public void renderBackground(final GuiGraphics graphics) {
        // Painted directly in render() to avoid unwanted full-screen dim over live preview.
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final AgrisTheme t = AgrisTheme.current();
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.32;

        g.fillGradient(0, 0, this.width, this.height, t.backdropTop, t.backdropBottom);

        // Header bar + accent prism line + GPU / Mobile TBDR badge
        g.drawString(this.font, "AGRIS SHADERS", this.listLeft, 12, t.textPrimary, false);
        final var arch = Agris.pipeline().glLimits().mobileArchitecture();
        final String bridgeText = arch.isMobileOrTbdr()
                ? arch.summary() + "  \u00b7  " + Agris.aetherium().describeStatus()
                : Agris.aetherium().describeStatus();
        final int bridgeW = this.font.width(bridgeText);
        g.drawString(this.font, bridgeText, this.listRight - bridgeW, 13, t.accentSecondary, false);
        g.fill(this.listLeft, 25, this.listRight, 26, t.accentPrimary);

        // Search input box
        g.fill(this.searchBoxX, this.searchBoxY, this.searchBoxX + this.searchBoxW,
                this.searchBoxY + this.searchBoxH, t.panelBg);
        g.renderOutline(this.searchBoxX, this.searchBoxY, this.searchBoxW, this.searchBoxH,
                this.searchFocused ? t.accentPrimary : t.panelBorder);
        final String searchDisplay = this.searchQuery.isEmpty() && !this.searchFocused
                ? "Filter shader packs..."
                : this.searchQuery + (this.searchFocused ? "_" : "");
        g.drawString(this.font, searchDisplay, this.searchBoxX + 6, this.searchBoxY + 6,
                this.searchQuery.isEmpty() && !this.searchFocused ? t.textMuted : t.textPrimary, false);

        // Pack list container
        g.fill(this.listLeft, this.listTop, this.listRight, this.listBottom, t.panelBg);
        g.renderOutline(this.listLeft, this.listTop, this.listRight - this.listLeft,
                this.listBottom - this.listTop, t.panelBorder);

        g.enableScissor(this.listLeft + 1, this.listTop + 1, this.listRight - 1, this.listBottom - 1);
        int y = (int) Math.round(this.listTop + 4 - this.scrollOffset);

        // First row: "(Off) — Vanilla / Aetherium Direct"
        renderPackRow(g, t, "", "Off (Aetherium Direct Renderer)", "DIRECT", true,
                y, mouseX, mouseY, !this.stagedEnabled);
        y += ROW_HEIGHT + 3;

        for (final ShaderPackDiscovery.PackEntry entry : this.filteredPacks) {
            final boolean selected = this.stagedEnabled && entry.id().equalsIgnoreCase(this.stagedPackId);
            renderPackRow(g, t, entry.id(), entry.displayName(), entry.badge(),
                    entry.kind() == ShaderPackDiscovery.PackKind.BUILTIN,
                    y, mouseX, mouseY, selected);
            y += ROW_HEIGHT + 3;
        }
        g.disableScissor();

        // Status / error diagnostic banner between list and bottom buttons
        final String err = Agris.pipeline().lastErrorMessage();
        final int statusY = this.listBottom + 7;
        if (err != null && !err.isEmpty()) {
            g.drawString(this.font, "Fallback active: " + err, this.listLeft, statusY, t.statusError, false);
        } else {
            g.drawString(this.font, Agris.describePipelineShort() + "  \u00b7  Shortcuts: [O] Packs  [R] Reload  [K] Toggle",
                    this.listLeft, statusY, t.textSecondary, false);
        }

        // Render buttons
        for (final ActionBtn btn : this.buttons) {
            final boolean hovered = btn.enabled() && btn.contains(mouseX, mouseY);
            final int bg = !btn.enabled() ? t.rowBg
                    : (btn.primary() ? t.accentPrimary : (hovered ? t.rowHoverBg : t.rowBg));
            final int border = btn.primary() ? t.accentPrimary : (hovered ? t.accentSecondary : t.panelBorder);
            g.fill(btn.x(), btn.y(), btn.x() + btn.w(), btn.y() + btn.h(), bg);
            g.renderOutline(btn.x(), btn.y(), btn.w(), btn.h(), border);
            final int tw = this.font.width(btn.label());
            final int fg = !btn.enabled() ? t.textMuted : (btn.primary() ? 0xFFFFFFFF : t.textPrimary);
            g.drawString(this.font, btn.label(), btn.x() + (btn.w() - tw) / 2, btn.y() + (btn.h() - 8) / 2, fg, false);
        }
    }

    private void renderPackRow(final GuiGraphics g, final AgrisTheme t,
                               final String id, final String title, final String badge,
                               final boolean builtinStyle, final int rowY,
                               final int mouseX, final int mouseY, final boolean selected) {
        if (rowY + ROW_HEIGHT < this.listTop || rowY > this.listBottom) {
            return;
        }
        final int rowX = this.listLeft + 6;
        final int rowW = (this.listRight - this.listLeft) - 12;
        final boolean hovered = mouseX >= rowX && mouseX < rowX + rowW
                && mouseY >= Math.max(rowY, this.listTop) && mouseY < Math.min(rowY + ROW_HEIGHT, this.listBottom);

        final int bg = selected ? t.rowSelectedBg : (hovered ? t.rowHoverBg : t.rowBg);
        g.fill(rowX, rowY, rowX + rowW, rowY + ROW_HEIGHT, bg);
        if (selected) {
            g.fill(rowX, rowY, rowX + 3, rowY + ROW_HEIGHT, t.accentSecondary);
            g.renderOutline(rowX, rowY, rowW, ROW_HEIGHT, t.accentPrimary);
        }

        // Badge pill on right
        final int badgeW = this.font.width(badge) + 10;
        final int badgeX = rowX + rowW - badgeW - 8;
        final int badgeY = rowY + 6;
        g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 16, builtinStyle ? t.badgeBuiltin : t.badgeExternal);
        g.drawString(this.font, badge, badgeX + 5, badgeY + 4, 0xFFFFFFFF, false);

        g.drawString(this.font, title, rowX + 10, rowY + 6, t.textPrimary, false);
        final String subtitle = id.isEmpty() ? "Zero shader overhead; native Aetherium terrain & culling" : id;
        g.drawString(this.font, subtitle, rowX + 10, rowY + 16, t.textMuted, false);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        this.touchPressInList = false;
        this.touchDraggedDistance = 0.0;
        this.searchFocused = mouseX >= this.searchBoxX && mouseX < this.searchBoxX + this.searchBoxW
                && mouseY >= this.searchBoxY && mouseY < this.searchBoxY + this.searchBoxH;

        for (final ActionBtn btn : this.buttons) {
            if (btn.enabled() && btn.contains(mouseX, mouseY)) {
                btn.action().run();
                return true;
            }
        }

        if (mouseX >= this.listLeft + 6 && mouseX < this.listRight - 6
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            this.touchPressInList = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
                                final double dragX, final double dragY) {
        if (button == 0 && this.touchPressInList) {
            this.touchDraggedDistance += Math.abs(dragX) + Math.abs(dragY);
            final double maxScroll = computeMaxScroll();
            this.targetScrollOffset = Math.max(0.0, Math.min(maxScroll, this.targetScrollOffset - dragY));
            this.scrollOffset = this.targetScrollOffset;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (button == 0 && this.touchPressInList) {
            this.touchPressInList = false;
            if (this.touchDraggedDistance < 6.0
                    && mouseX >= this.listLeft + 6 && mouseX < this.listRight - 6
                    && mouseY >= this.listTop && mouseY < this.listBottom) {
                final int relY = (int) Math.round(mouseY - (this.listTop + 4 - this.scrollOffset));
                final int slot = relY / (ROW_HEIGHT + 3);
                if (slot == 0) {
                    this.stagedEnabled = false;
                    commitSelection();
                    rebuildLayout();
                    return true;
                }
                final int packIdx = slot - 1;
                if (packIdx >= 0 && packIdx < this.filteredPacks.size()) {
                    this.stagedPackId = this.filteredPacks.get(packIdx).id();
                    this.stagedEnabled = true;
                    commitSelection();
                    rebuildLayout();
                    return true;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double amount) {
        final double maxScroll = computeMaxScroll();
        this.targetScrollOffset = Math.max(0.0, Math.min(maxScroll, this.targetScrollOffset - amount * 22.0));
        return true;
    }

    private double computeMaxScroll() {
        final int totalRows = 1 + this.filteredPacks.size();
        final int contentH = totalRows * (ROW_HEIGHT + 3) + 8;
        final int viewH = Math.max(1, this.listBottom - this.listTop);
        return Math.max(0.0, contentH - viewH);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.searchQuery.isEmpty()) {
                this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                applyFilter();
                this.targetScrollOffset = 0.0;
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            commitSelection();
            AgrisClientHooks.reloadShadersAndChunks();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        if (this.searchFocused && codePoint >= 32 && codePoint != 127 && this.searchQuery.length() < 48) {
            this.searchQuery += codePoint;
            applyFilter();
            this.targetScrollOffset = 0.0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        commitSelection();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
