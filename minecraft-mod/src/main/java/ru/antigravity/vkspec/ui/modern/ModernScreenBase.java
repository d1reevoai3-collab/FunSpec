package ru.antigravity.vkspec.ui.modern;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class ModernScreenBase extends Screen {

    // --- Layout ---
    protected static final int GUI_WIDTH = 420;
    protected static final int GUI_HEIGHT = 240;
    protected static final int SIDEBAR_WIDTH = 90;
    protected static final int HEADER_HEIGHT = 22;

    // --- Colors ---
    protected static int COL_BG = 0xF2111118;
    protected static int COL_BG_DARKER = 0xF20C0C12;
    protected static int COL_SIDEBAR = 0xF20A0A10;
    protected static int COL_HEADER = 0xFF0E0E16;
    protected static int COL_BORDER = 0xFF2A2A3A;
    protected static int COL_CARD = 0xFF1A1A26;
    protected static int COL_CARD_BORDER = 0xFF2E2E40;
    protected static int COL_ACCENT = 0xFF3B82F6;
    protected static int COL_ACCENT_HOVER = 0xFF60A5FA;
    protected static int COL_GREEN = 0xFF4ADE80;
    protected static int COL_RED = 0xFFF87171;
    protected static int COL_YELLOW = 0xFFFACC15;
    protected static int COL_TEXT = 0xFFE2E8F0;
    protected static int COL_TEXT_DIM = 0xFF94A3B8;
    protected static int COL_TEXT_DARK = 0xFF64748B;
    protected static int COL_TAB_HOVER = 0xFF1E1E2E;
    protected static int COL_TAB_ACTIVE = 0xFF1A1A2E;
    protected static int COL_DIVIDER = 0xFF252538;

    protected int guiX, guiY;
    protected int currentTab;
    protected static long openTime = 0;
    
    public static void resetAnimation() {
        openTime = System.currentTimeMillis();
    }
    
    private static final String[] TAB_NAMES = {"Главная", "Заявки", "История", "Настройки", "Логи"};
    private static final String[] TAB_ICONS = {"⌂", "✉", "☰", "⚙", "⚑"};

    protected ModernScreenBase(Text title, int currentTab) {
        super(title);
        this.currentTab = currentTab;
    }

    @Override
    protected void init() {
        super.init();
        this.guiX = (this.width - GUI_WIDTH) / 2;
        this.guiY = (this.height - GUI_HEIGHT) / 2;
        updateTheme();
    }

    protected void updateTheme() {
        int theme = ru.antigravity.vkspec.FunSpecMod.getInstance().getConfig().themeIndex;
        switch (theme) {
            case 1 -> { // Red
                COL_ACCENT = 0xFFEF4444; COL_ACCENT_HOVER = 0xFFF87171; 
                COL_TAB_ACTIVE = 0xFF2A1616; COL_TAB_HOVER = 0xFF241616;
            }
            case 2 -> { // Green
                COL_ACCENT = 0xFF22C55E; COL_ACCENT_HOVER = 0xFF4ADE80;
                COL_TAB_ACTIVE = 0xFF162A1A; COL_TAB_HOVER = 0xFF162418;
            }
            case 3 -> { // Purple
                COL_ACCENT = 0xFFA855F7; COL_ACCENT_HOVER = 0xFFC084FC;
                COL_TAB_ACTIVE = 0xFF22162A; COL_TAB_HOVER = 0xFF1E1624;
            }
            default -> { // Blue
                COL_ACCENT = 0xFF3B82F6; COL_ACCENT_HOVER = 0xFF60A5FA;
                COL_TAB_ACTIVE = 0xFF1A1A2E; COL_TAB_HOVER = 0xFF1E1E2E;
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long timeSinceInit = System.currentTimeMillis() - openTime;
        float animProgress = Math.min(1.0f, timeSinceInit / 250.0f);
        int animY = (int) ((1.0f - animProgress) * 20); // Slide up by 20px
        
        int currentGuiY = guiY + animY;

        // --- Dim background ---
        ctx.fill(0, 0, this.width, this.height, ((int)(0x88 * animProgress) << 24) | 0x000000);

        // --- Main window background ---
        ctx.fill(guiX, currentGuiY, guiX + GUI_WIDTH, currentGuiY + GUI_HEIGHT, COL_BG);
        drawBorder(ctx, guiX, currentGuiY, GUI_WIDTH, GUI_HEIGHT, COL_BORDER);

        // --- Sidebar ---
        ctx.fill(guiX, currentGuiY, guiX + SIDEBAR_WIDTH, currentGuiY + GUI_HEIGHT, COL_SIDEBAR);
        ctx.fill(guiX + SIDEBAR_WIDTH, currentGuiY, guiX + SIDEBAR_WIDTH + 1, currentGuiY + GUI_HEIGHT, COL_BORDER);

        // --- Sidebar header (logo) ---
        ctx.fill(guiX, currentGuiY, guiX + SIDEBAR_WIDTH, currentGuiY + HEADER_HEIGHT, COL_HEADER);
        ctx.fill(guiX, currentGuiY + HEADER_HEIGHT, guiX + SIDEBAR_WIDTH, currentGuiY + HEADER_HEIGHT + 1, COL_BORDER);
        ctx.drawText(this.textRenderer, "VK Spec", guiX + 18, currentGuiY + 7, COL_ACCENT, false);

        // --- Tab buttons ---
        int tabY = currentGuiY + HEADER_HEIGHT + 8;
        for (int i = 0; i < 5; i++) {
            int ty = tabY + i * 28;
            boolean isActive = (i == currentTab);
            boolean isHover = mouseX >= guiX + 4 && mouseX <= guiX + SIDEBAR_WIDTH - 4
                    && mouseY >= ty && mouseY <= ty + 22;

            // Tab background
            if (isActive) {
                ctx.fill(guiX + 4, ty, guiX + SIDEBAR_WIDTH - 4, ty + 22, COL_TAB_ACTIVE);
                // Accent bar on left
                ctx.fill(guiX + 4, ty, guiX + 7, ty + 22, COL_ACCENT);
            } else if (isHover) {
                ctx.fill(guiX + 4, ty, guiX + SIDEBAR_WIDTH - 4, ty + 22, COL_TAB_HOVER);
            }

            int textColor = isActive ? COL_ACCENT : (isHover ? COL_TEXT : COL_TEXT_DIM);
            ctx.drawText(this.textRenderer, TAB_ICONS[i], guiX + 14, ty + 7, textColor, false);
            ctx.drawText(this.textRenderer, TAB_NAMES[i], guiX + 26, ty + 7, textColor, false);
        }

        // --- Header bar for content area ---
        int contentX = guiX + SIDEBAR_WIDTH + 1;
        int contentW = GUI_WIDTH - SIDEBAR_WIDTH - 1;
        ctx.fill(contentX, currentGuiY, guiX + GUI_WIDTH, currentGuiY + HEADER_HEIGHT, COL_HEADER);
        ctx.fill(contentX, currentGuiY + HEADER_HEIGHT, guiX + GUI_WIDTH, currentGuiY + HEADER_HEIGHT + 1, COL_BORDER);
        ctx.drawText(this.textRenderer, getTabTitle(), contentX + 8, currentGuiY + 7, COL_TEXT, true);

        // --- Content area ---
        ctx.getMatrices().push();
        int contentTop = currentGuiY + HEADER_HEIGHT + 1;
        int contentBottom = currentGuiY + GUI_HEIGHT;
        ctx.enableScissor(contentX, contentTop, guiX + GUI_WIDTH, contentBottom);
        // Fade in alpha trick for content if desired, or just translate
        renderContent(ctx, contentX + 6, currentGuiY + HEADER_HEIGHT + 6, contentW - 12, GUI_HEIGHT - HEADER_HEIGHT - 12, mouseX, mouseY);
        ctx.disableScissor();
        ctx.getMatrices().pop();

        super.render(ctx, mouseX, mouseY, delta);
    }

    protected abstract String getTabTitle();
    protected abstract void renderContent(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY);

    // --- Helpers ---

    protected void drawCard(DrawContext ctx, int x, int y, int w, int h, String title) {
        ctx.fill(x, y, x + w, y + h, COL_CARD);
        drawBorder(ctx, x, y, w, h, COL_CARD_BORDER);
        if (title != null) {
            ctx.drawText(this.textRenderer, title, x + 6, y + 4, COL_TEXT, false);
            ctx.fill(x + 1, y + 14, x + w - 1, y + 15, COL_DIVIDER);
        }
    }

    protected void drawKeyValue(DrawContext ctx, int x, int y, int w, String key, String value, int valueColor) {
        ctx.drawText(this.textRenderer, key, x, y, COL_TEXT_DIM, false);
        int valWidth = this.textRenderer.getWidth(value);
        ctx.drawText(this.textRenderer, value, x + w - valWidth, y, valueColor, false);
    }

    protected void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);           // top
        ctx.fill(x, y + h - 1, x + w, y + h, color);   // bottom
        ctx.fill(x, y, x + 1, y + h, color);            // left
        ctx.fill(x + w - 1, y, x + w, y + h, color);   // right
    }

    protected void drawToggle(DrawContext ctx, int x, int y, boolean on, int mouseX, int mouseY) {
        int tw = 28, th = 12;
        int bgColor = on ? 0xFF1D4E2E : 0xFF3A1A1A;
        int dotColor = on ? COL_GREEN : COL_RED;
        ctx.fill(x, y, x + tw, y + th, bgColor);
        drawBorder(ctx, x, y, tw, th, on ? 0xFF2D6B3E : 0xFF5A2A2A);
        int dotX = on ? x + tw - 13 : x + 3;
        ctx.fill(dotX, y + 2, dotX + 8, y + th - 2, dotColor);
    }

    protected boolean isInToggle(int toggleX, int toggleY, int mouseX, int mouseY) {
        return mouseX >= toggleX && mouseX <= toggleX + 28 && mouseY >= toggleY && mouseY <= toggleY + 12;
    }

    // --- Click handling ---
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabY = guiY + HEADER_HEIGHT + 8;
            for (int i = 0; i < 5; i++) {
                int ty = tabY + i * 28;
                if (mouseX >= guiX + 4 && mouseX <= guiX + SIDEBAR_WIDTH - 4
                        && mouseY >= ty && mouseY <= ty + 22) {
                    if (i != currentTab) {
                        switchTab(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchTab(int index) {
        if (this.client != null) {
            switch (index) {
                case 0 -> this.client.setScreen(new HomeScreen());
                case 1 -> this.client.setScreen(new TicketsScreen());
                case 2 -> this.client.setScreen(new HistoryScreen());
                case 3 -> this.client.setScreen(new SettingsScreen());
                case 4 -> this.client.setScreen(new LogsScreen());
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
