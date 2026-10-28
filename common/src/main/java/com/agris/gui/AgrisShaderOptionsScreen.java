package com.agris.gui;

import com.agris.Agris;
import com.agris.client.AgrisClientHooks;
import com.agris.pack.LanguageMap;
import com.agris.pack.ShaderPack;
import com.agris.pack.ShaderPackOption;
import com.agris.pack.ShaderPackOptionStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchical Shader Pack Options screen supporting OptiFine / Iris profiles
 * ({@code Low}, {@code Medium}, {@code High}, {@code Ultra}, {@code Android_Fast}),
 * sub-screen navigation ({@code [LIGHTING]}, {@code [ATMOSPHERE]}, {@code [POST]}),
 * sliders, boolean toggles, multi-value {@code #define}/{@code const} selectors,
 * localized {@code .lang} tooltips, and live {@code .txt} persistence.
 */
public final class AgrisShaderOptionsScreen extends Screen {
    private static final int ROW_HEIGHT = 24;

    private sealed interface RowItem {
        record SubScreenRow(String screenKey, String title) implements RowItem {
        }

        record OptionRow(ShaderPackOption option) implements RowItem {
        }
    }

    private final Screen parent;
    private final ShaderPack pack;
    private final Map<String, String> workingValues = new LinkedHashMap<>();
    private final List<String> profileNames = new ArrayList<>();
    private final Deque<String> screenStack = new ArrayDeque<>();
    private List<RowItem> visibleRows = List.of();

    private double scrollOffset;
    private double targetScrollOffset;
    private boolean touchPressInList;
    private double touchDraggedDistance;
    private int touchPressButton;
    private ShaderPackOption activeSliderDrag;
    private int listLeft;
    private int listTop;
    private int listRight;
    private int listBottom;
    private String hoveredTooltip = "";

    public AgrisShaderOptionsScreen(final Screen parent, final ShaderPack pack) {
        super(Component.literal("Shader Pack Options"));
        this.parent = parent;
        this.pack = pack;
        this.workingValues.putAll(pack.activeOptionValues());
        this.profileNames.addAll(pack.directives().profiles().keySet());
        this.screenStack.push("main");
    }

    @Override
    protected void init() {
        final int pad = Math.max(16, this.width / 20);
        this.listLeft = pad;
        this.listRight = this.width - pad;
        this.listTop = 58;
        this.listBottom = this.height - 56;
        rebuildRows();
    }

    private void rebuildRows() {
        final String currentScreen = this.screenStack.peek() == null ? "main" : this.screenStack.peek();
        final List<String> layout = this.pack.directives().screens().get(currentScreen);
        final LanguageMap lang = this.pack.languageMap();
        final List<RowItem> rows = new ArrayList<>();

        if (layout != null && !layout.isEmpty()) {
            for (final String token : layout) {
                if ("*".equals(token)) {
                    for (final ShaderPackOption opt : this.pack.options().values()) {
                        if (!containsOption(rows, opt.name())) {
                            rows.add(new RowItem.OptionRow(opt));
                        }
                    }
                } else if ("<empty>".equalsIgnoreCase(token) || "-".equals(token)) {
                    // Spacer token skipped in vertical list layout.
                } else if (this.pack.directives().screens().containsKey(token)) {
                    rows.add(new RowItem.SubScreenRow(token, lang.screenLabel(token)));
                } else {
                    final ShaderPackOption opt = this.pack.options().get(token);
                    if (opt != null) {
                        rows.add(new RowItem.OptionRow(opt));
                    }
                }
            }
        } else {
            for (final ShaderPackOption opt : this.pack.options().values()) {
                rows.add(new RowItem.OptionRow(opt));
            }
        }
        this.visibleRows = List.copyOf(rows);
    }

    private static boolean containsOption(final List<RowItem> rows, final String name) {
        for (final RowItem item : rows) {
            if (item instanceof RowItem.OptionRow o && o.option().name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String currentProfileDisplay() {
        final LanguageMap lang = this.pack.languageMap();
        for (final Map.Entry<String, Map<String, String>> entry : this.pack.directives().profiles().entrySet()) {
            boolean matches = !entry.getValue().isEmpty();
            for (final Map.Entry<String, String> req : entry.getValue().entrySet()) {
                final String cur = this.workingValues.get(req.getKey());
                if (cur != null && !cur.equals(req.getValue())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return lang.profileLabel(entry.getKey());
            }
        }
        return "Custom";
    }

    private void cycleProfile() {
        if (this.profileNames.isEmpty()) {
            return;
        }
        String matchedKey = null;
        for (final String key : this.profileNames) {
            final Map<String, String> reqs = this.pack.directives().profiles().get(key);
            boolean all = reqs != null && !reqs.isEmpty();
            if (all) {
                for (final Map.Entry<String, String> r : reqs.entrySet()) {
                    final String cur = this.workingValues.get(r.getKey());
                    if (cur != null && !cur.equals(r.getValue())) {
                        all = false;
                        break;
                    }
                }
            }
            if (all) {
                matchedKey = key;
                break;
            }
        }
        final int idx = matchedKey == null ? -1 : this.profileNames.indexOf(matchedKey);
        final String nextKey = this.profileNames.get((idx + 1) % this.profileNames.size());
        final Map<String, String> values = this.pack.directives().profiles().get(nextKey);
        if (values != null) {
            for (final Map.Entry<String, String> e : values.entrySet()) {
                if (this.pack.options().containsKey(e.getKey())) {
                    this.workingValues.put(e.getKey(), e.getValue());
                }
            }
        }
    }

    private void resetAllToDefaults() {
        for (final ShaderPackOption opt : this.pack.options().values()) {
            this.workingValues.put(opt.name(), opt.defaultValue());
        }
    }

    private void saveAndApply() {
        ShaderPackOptionStore.save(
                Agris.packs().shaderpacksDirectory(),
                this.pack.id(),
                this.pack.options(),
                this.workingValues);
        AgrisClientHooks.reloadShadersAndChunks();
    }

    @Override
    public void renderBackground(final GuiGraphics graphics) {
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final AgrisTheme t = AgrisTheme.current();
        final LanguageMap lang = this.pack.languageMap();
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.32;
        this.hoveredTooltip = "";

        g.fillGradient(0, 0, this.width, this.height, t.backdropTop, t.backdropBottom);

        // Header & breadcrumbs
        final String crumb = "main".equals(this.screenStack.peek())
                ? this.pack.displayName()
                : this.pack.displayName() + "  \u203a  " + lang.screenLabel(this.screenStack.peek());
        g.drawString(this.font, crumb, this.listLeft, 12, t.textPrimary, false);
        g.fill(this.listLeft, 25, this.listRight, 26, t.accentPrimary);

        // Top bar: Back (if nested) + Profile selector + Reset Defaults
        if (this.screenStack.size() > 1) {
            drawPillButton(g, t, this.listLeft, 31, 74, 20, "\u2190 Back", false, mouseX, mouseY);
        }
        if (!this.profileNames.isEmpty()) {
            final int profX = this.screenStack.size() > 1 ? this.listLeft + 80 : this.listLeft;
            drawPillButton(g, t, profX, 31, 190, 20, "Profile: " + currentProfileDisplay(), true, mouseX, mouseY);
        }
        drawPillButton(g, t, this.listRight - 116, 31, 116, 20, "Reset Defaults", false, mouseX, mouseY);

        // Options list panel
        g.fill(this.listLeft, this.listTop, this.listRight, this.listBottom, t.panelBg);
        g.renderOutline(this.listLeft, this.listTop, this.listRight - this.listLeft,
                this.listBottom - this.listTop, t.panelBorder);

        g.enableScissor(this.listLeft + 1, this.listTop + 1, this.listRight - 1, this.listBottom - 1);
        int y = (int) Math.round(this.listTop + 5 - this.scrollOffset);

        if (this.visibleRows.isEmpty()) {
            g.drawString(this.font, "This shader pack does not expose configurable #define options.",
                    this.listLeft + 12, this.listTop + 16, t.textMuted, false);
        }

        for (final RowItem item : this.visibleRows) {
            if (y + ROW_HEIGHT >= this.listTop && y <= this.listBottom) {
                final int rx = this.listLeft + 6;
                final int rw = (this.listRight - this.listLeft) - 12;
                final boolean hov = mouseX >= rx && mouseX < rx + rw
                        && mouseY >= Math.max(y, this.listTop) && mouseY < Math.min(y + ROW_HEIGHT, this.listBottom);
                g.fill(rx, y, rx + rw, y + ROW_HEIGHT, hov ? t.rowHoverBg : t.rowBg);

                if (item instanceof RowItem.SubScreenRow sub) {
                    g.fill(rx, y, rx + 3, y + ROW_HEIGHT, t.accentPrimary);
                    g.drawString(this.font, sub.title(), rx + 10, y + 8, t.textPrimary, false);
                    g.drawString(this.font, "Open \u203a", rx + rw - 44, y + 8, t.accentSecondary, false);
                } else if (item instanceof RowItem.OptionRow optRow) {
                    final ShaderPackOption opt = optRow.option();
                    final String val = this.workingValues.getOrDefault(opt.name(), opt.defaultValue());
                    final boolean modified = !val.equals(opt.defaultValue());
                    g.drawString(this.font, lang.optionLabel(opt.name()), rx + 10, y + 8,
                            modified ? t.accentSecondary : t.textPrimary, false);

                    final String valText = lang.valueLabel(opt.name(), val);
                    if (opt.isSlider()) {
                        final int trackW = 110;
                        final int trackX = rx + rw - trackW - 8;
                        final int trackY = y + 10;
                        g.fill(trackX, trackY, trackX + trackW, trackY + 4, t.panelBorder);
                        final int idx = Math.max(0, opt.allowedValues().indexOf(val));
                        final float frac = opt.allowedValues().size() <= 1
                                ? 1.0f
                                : idx / (float) (opt.allowedValues().size() - 1);
                        final int fillW = Math.round(trackW * frac);
                        g.fill(trackX, trackY, trackX + fillW, trackY + 4, t.accentPrimary);
                        final int vw = this.font.width(valText);
                        g.drawString(this.font, valText, trackX - vw - 8, y + 8, t.textSecondary, false);
                    } else {
                        final int vw = this.font.width(valText);
                        final int valColor = opt.isBoolean()
                                ? (Boolean.parseBoolean(val) ? t.statusOk : t.textMuted)
                                : t.accentPrimary;
                        g.drawString(this.font, valText, rx + rw - vw - 10, y + 8, valColor, false);
                    }

                    if (hov) {
                        this.hoveredTooltip = lang.optionTooltip(opt.name(), opt.comment());
                    }
                }
            }
            y += ROW_HEIGHT + 3;
        }
        g.disableScissor();

        // Tooltip line + bottom Apply / Done buttons
        if (!this.hoveredTooltip.isBlank()) {
            g.drawString(this.font, this.hoveredTooltip, this.listLeft, this.listBottom + 6, t.textSecondary, false);
        }

        final int btnY = this.height - 30;
        final int btnW = 130;
        drawPillButton(g, t, this.width / 2 - btnW - 6, btnY, btnW, 22, "Apply & Reload", true, mouseX, mouseY);
        drawPillButton(g, t, this.width / 2 + 6, btnY, btnW, 22, "Done", false, mouseX, mouseY);
    }

    private void drawPillButton(final GuiGraphics g, final AgrisTheme t,
                                final int x, final int y, final int w, final int h,
                                final String label, final boolean accent,
                                final int mx, final int my) {
        final boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        final int bg = accent ? t.accentPrimary : (hov ? t.rowHoverBg : t.rowBg);
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, accent ? t.accentPrimary : (hov ? t.accentSecondary : t.panelBorder));
        final int tw = this.font.width(label);
        g.drawString(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2,
                accent ? 0xFFFFFFFF : t.textPrimary, false);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        this.touchPressInList = false;
        this.touchDraggedDistance = 0.0;
        this.activeSliderDrag = null;
        this.touchPressButton = button;

        // Top bar clicks
        if (this.screenStack.size() > 1 && inside(mouseX, mouseY, this.listLeft, 31, 74, 20)) {
            this.screenStack.pop();
            this.targetScrollOffset = 0.0;
            rebuildRows();
            return true;
        }
        if (!this.profileNames.isEmpty()) {
            final int profX = this.screenStack.size() > 1 ? this.listLeft + 80 : this.listLeft;
            if (inside(mouseX, mouseY, profX, 31, 190, 20)) {
                cycleProfile();
                return true;
            }
        }
        if (inside(mouseX, mouseY, this.listRight - 116, 31, 116, 20)) {
            resetAllToDefaults();
            return true;
        }

        // Bottom buttons
        final int btnY = this.height - 30;
        final int btnW = 130;
        if (inside(mouseX, mouseY, this.width / 2 - btnW - 6, btnY, btnW, 22)) {
            saveAndApply();
            return true;
        }
        if (inside(mouseX, mouseY, this.width / 2 + 6, btnY, btnW, 22)) {
            onClose();
            return true;
        }

        // Row press / touch slider scrub
        if (mouseX >= this.listLeft + 6 && mouseX < this.listRight - 6
                && mouseY >= this.listTop && mouseY < this.listBottom) {
            final int relY = (int) Math.round(mouseY - (this.listTop + 5 - this.scrollOffset));
            final int slot = relY / (ROW_HEIGHT + 3);
            if (slot >= 0 && slot < this.visibleRows.size()
                    && this.visibleRows.get(slot) instanceof RowItem.OptionRow optRow
                    && optRow.option().isSlider()) {
                final int rx = this.listLeft + 6;
                final int rw = (this.listRight - this.listLeft) - 12;
                final int trackW = 110;
                final int trackX = rx + rw - trackW - 8;
                if (mouseX >= trackX - 6 && mouseX <= trackX + trackW + 6) {
                    this.activeSliderDrag = optRow.option();
                    applySliderScrub(this.activeSliderDrag, mouseX, trackX, trackW);
                    return true;
                }
            }
            this.touchPressInList = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
                                final double dragX, final double dragY) {
        if (this.activeSliderDrag != null) {
            final int rx = this.listLeft + 6;
            final int rw = (this.listRight - this.listLeft) - 12;
            final int trackW = 110;
            final int trackX = rx + rw - trackW - 8;
            applySliderScrub(this.activeSliderDrag, mouseX, trackX, trackW);
            return true;
        }
        if (this.touchPressInList) {
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
        if (this.activeSliderDrag != null) {
            this.activeSliderDrag = null;
            return true;
        }
        if (this.touchPressInList) {
            this.touchPressInList = false;
            if (this.touchDraggedDistance < 6.0
                    && mouseX >= this.listLeft + 6 && mouseX < this.listRight - 6
                    && mouseY >= this.listTop && mouseY < this.listBottom) {
                final int relY = (int) Math.round(mouseY - (this.listTop + 5 - this.scrollOffset));
                final int slot = relY / (ROW_HEIGHT + 3);
                if (slot >= 0 && slot < this.visibleRows.size()) {
                    final RowItem row = this.visibleRows.get(slot);
                    if (row instanceof RowItem.SubScreenRow sub) {
                        this.screenStack.push(sub.screenKey());
                        this.targetScrollOffset = 0.0;
                        rebuildRows();
                        return true;
                    } else if (row instanceof RowItem.OptionRow optRow) {
                        final ShaderPackOption opt = optRow.option();
                        final String cur = this.workingValues.getOrDefault(opt.name(), opt.defaultValue());
                        final String next = this.touchPressButton == 1 ? opt.previousValue(cur) : opt.nextValue(cur);
                        this.workingValues.put(opt.name(), next);
                        return true;
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySliderScrub(final ShaderPackOption opt, final double mouseX,
                                  final int trackX, final int trackW) {
        final List<String> vals = opt.allowedValues();
        if (vals.isEmpty()) {
            return;
        }
        final double frac = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / Math.max(1.0, trackW)));
        final int idx = (int) Math.round(frac * (vals.size() - 1));
        this.workingValues.put(opt.name(), vals.get(Math.max(0, Math.min(vals.size() - 1, idx))));
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double amount) {
        final double maxScroll = computeMaxScroll();
        this.targetScrollOffset = Math.max(0.0, Math.min(maxScroll, this.targetScrollOffset - amount * 22.0));
        return true;
    }

    private double computeMaxScroll() {
        final int contentH = this.visibleRows.size() * (ROW_HEIGHT + 3) + 10;
        final int viewH = Math.max(1, this.listBottom - this.listTop);
        return Math.max(0.0, contentH - viewH);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.screenStack.size() > 1) {
                this.screenStack.pop();
                this.targetScrollOffset = 0.0;
                rebuildRows();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        saveAndApply();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static boolean inside(final double mx, final double my,
                                  final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
