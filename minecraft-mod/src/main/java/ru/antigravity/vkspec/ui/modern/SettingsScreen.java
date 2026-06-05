package ru.antigravity.vkspec.ui.modern;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.antigravity.vkspec.FunSpecMod;
import ru.antigravity.vkspec.config.ModConfig;

public class SettingsScreen extends ModernScreenBase {

    private int toggleAutoX, toggleAutoY;
    private int toggleModX, toggleModY;
    private int toggleNotifX, toggleNotifY;
    private int toggleDockX, toggleDockY;
    private int toggleSmartSpecX, toggleSmartSpecY;
    private int toggleReasonX, toggleReasonY;
    private int themeBtnX, themeBtnY, themeBtnW, themeBtnH;
    private int maxNotifBtnX, maxNotifBtnY, maxNotifBtnW, maxNotifBtnH;
    private int soundBtnX, soundBtnY, soundBtnW, soundBtnH;
    private int toggleTimeHudX, toggleTimeHudY;
    private int toggleSpecHudX, toggleSpecHudY;
    private int toggleProgressBarX, toggleProgressBarY;
    private int notifDurationBtnX, notifDurationBtnY, notifDurationBtnW, notifDurationBtnH;
    
    private boolean soundDropdownOpen = false;
    private double scrollOffset = 0.0;
    
    private boolean keyFieldFocused = false;
    private int keyFieldX, keyFieldY, keyFieldW, keyFieldH;
    private int toggleAutoKeyX, toggleAutoKeyY;
    
    private static final String[] SOUND_IDS = {
        "none",
        "entity.experience_orb.pickup",
        "entity.player.levelup",
        "ui.button.click",
        "block.anvil.place",
        "block.note_block.bell"
    };
    private static final String[] SOUND_NAMES = {
        "Ничего",
        "Дзинь (Опыт)",
        "Level Up",
        "Щелчок",
        "Наковальня",
        "Колокольчик"
    };

    public SettingsScreen() {
        super(Text.literal("VK Spec"), 3);
    }

    @Override
    protected String getTabTitle() {
        return "Настройки";
    }

    @Override
    protected void renderContent(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        FunSpecMod mod = FunSpecMod.getInstance();
        ModConfig config = mod.getConfig();

        // Card 1: Основные настройки
        int card1Y = y - (int)scrollOffset;
        int card1H = 200;
        drawCard(ctx, x, card1Y, w, card1H, "Основные");
        
        int cy = card1Y + 22;
        int padding = 8;
        
        ctx.drawText(this.textRenderer, "Авто-спектейт", x + padding, cy + 2, COL_TEXT, false);
        toggleAutoX = x + w - padding - 28;
        toggleAutoY = cy;
        drawToggle(ctx, toggleAutoX, toggleAutoY, config.autoSpec, mouseX, mouseY);
        
        cy += 20;
        ctx.drawText(this.textRenderer, "Включить мод", x + padding, cy + 2, COL_TEXT, false);
        toggleModX = x + w - padding - 28;
        toggleModY = cy;
        drawToggle(ctx, toggleModX, toggleModY, config.modEnabled, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Показывать заявки", x + padding, cy + 2, COL_TEXT, false);
        toggleNotifX = x + w - padding - 28;
        toggleNotifY = cy;
        drawToggle(ctx, toggleNotifX, toggleNotifY, config.showNotificationsHUD, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Показывать статус", x + padding, cy + 2, COL_TEXT, false);
        toggleDockX = x + w - padding - 28;
        toggleDockY = cy;
        drawToggle(ctx, toggleDockX, toggleDockY, config.showStatusDock, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Умный спек (!spec Ник)", x + padding, cy + 2, COL_TEXT, false);
        toggleSmartSpecX = x + w - padding - 28;
        toggleSmartSpecY = cy;
        drawToggle(ctx, toggleSmartSpecX, toggleSmartSpecY, config.smartSpec, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Сообщение в статусе", x + padding, cy + 2, COL_TEXT, false);
        toggleReasonX = x + w - padding - 28;
        toggleReasonY = cy;
        drawToggle(ctx, toggleReasonX, toggleReasonY, config.showReasonInDock, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Цветовая Тема", x + padding, cy + 2, COL_TEXT, false);
        String[] themes = {"Синяя", "Красная", "Зеленая", "Фиол."};
        String curTheme = themes[config.themeIndex % themes.length];
        themeBtnW = 40; themeBtnH = 14;
        themeBtnX = x + w - padding - themeBtnW; themeBtnY = cy - 1;
        ctx.fill(themeBtnX, themeBtnY, themeBtnX + themeBtnW, themeBtnY + themeBtnH, COL_TAB_HOVER);
        drawBorder(ctx, themeBtnX, themeBtnY, themeBtnW, themeBtnH, COL_BORDER);
        ctx.drawText(this.textRenderer, curTheme, themeBtnX + (themeBtnW - this.textRenderer.getWidth(curTheme))/2, themeBtnY + 3, COL_ACCENT, false);

        cy += 20;
        ctx.drawText(this.textRenderer, "Авто ключ", x + padding, cy + 2, COL_TEXT, false);
        toggleAutoKeyX = x + w - padding - 28;
        toggleAutoKeyY = cy;
        drawToggle(ctx, toggleAutoKeyX, toggleAutoKeyY, config.autoKey, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Ключ", x + padding, cy + 2, COL_TEXT, false);
        keyFieldW = 80; keyFieldH = 14;
        keyFieldX = x + w - padding - keyFieldW; keyFieldY = cy - 1;
        
        int boxBg = keyFieldFocused ? 0xFF0A0A10 : 0xFF1A1A26;
        int boxBorder = keyFieldFocused ? COL_ACCENT : COL_BORDER;
        ctx.fill(keyFieldX, keyFieldY, keyFieldX + keyFieldW, keyFieldY + keyFieldH, boxBg);
        drawBorder(ctx, keyFieldX, keyFieldY, keyFieldW, keyFieldH, boxBorder);
        
        String displayedText = config.autoKeyValue != null ? config.autoKeyValue : "";
        if (keyFieldFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            displayedText += "_";
        }
        
        String truncatedText = displayedText;
        while (this.textRenderer.getWidth(truncatedText) > keyFieldW - 8 && truncatedText.length() > 0) {
            truncatedText = truncatedText.substring(1);
        }
        
        ctx.drawText(this.textRenderer, truncatedText, keyFieldX + 4, keyFieldY + 3, COL_TEXT, false);

        // Card 2: Лимиты и Звуки
        int card2Y = card1Y + card1H + 8;
        int card2H = 70;
        drawCard(ctx, x, card2Y, w, card2H, "Интерфейс и Звук");
        
        cy = card2Y + 22;
        ctx.drawText(this.textRenderer, "Макс. заявок на экране", x + padding, cy + 2, COL_TEXT, false);
        String maxStr = String.valueOf(config.maxNotificationsOnScreen);
        maxNotifBtnW = 20; maxNotifBtnH = 14;
        maxNotifBtnX = x + w - padding - maxNotifBtnW; maxNotifBtnY = cy - 1;
        ctx.fill(maxNotifBtnX, maxNotifBtnY, maxNotifBtnX + maxNotifBtnW, maxNotifBtnY + maxNotifBtnH, COL_TAB_HOVER);
        drawBorder(ctx, maxNotifBtnX, maxNotifBtnY, maxNotifBtnW, maxNotifBtnH, COL_BORDER);
        ctx.drawText(this.textRenderer, maxStr, maxNotifBtnX + (maxNotifBtnW - this.textRenderer.getWidth(maxStr))/2, maxNotifBtnY + 3, COL_ACCENT, false);
        
        cy += 20;
        ctx.drawText(this.textRenderer, "Звук уведомления", x + padding, cy + 2, COL_TEXT, false);
        
        String curSoundName = "Выбрать";
        for (int i = 0; i < SOUND_IDS.length; i++) {
            if (SOUND_IDS[i].equals(config.notificationSound)) {
                curSoundName = SOUND_NAMES[i];
                break;
            }
        }
        
        soundBtnW = 80; soundBtnH = 14;
        soundBtnX = x + w - padding - soundBtnW; soundBtnY = cy - 1;
        ctx.fill(soundBtnX, soundBtnY, soundBtnX + soundBtnW, soundBtnY + soundBtnH, COL_TAB_HOVER);
        drawBorder(ctx, soundBtnX, soundBtnY, soundBtnW, soundBtnH, COL_BORDER);
        ctx.drawText(this.textRenderer, curSoundName, soundBtnX + (soundBtnW - this.textRenderer.getWidth(curSoundName))/2, soundBtnY + 3, COL_ACCENT, false);
        
        // Card 3: HUD функции
        int card3Y = card2Y + card2H + 8;
        int card3H = 100;
        drawCard(ctx, x, card3Y, w, card3H, "HUD функции");
        
        cy = card3Y + 22;
        ctx.drawText(this.textRenderer, "Time (время ПК)", x + padding, cy + 2, COL_TEXT, false);
        toggleTimeHudX = x + w - padding - 28;
        toggleTimeHudY = cy;
        drawToggle(ctx, toggleTimeHudX, toggleTimeHudY, config.showTimeHUD, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Spec (текущий спек)", x + padding, cy + 2, COL_TEXT, false);
        toggleSpecHudX = x + w - padding - 28;
        toggleSpecHudY = cy;
        drawToggle(ctx, toggleSpecHudX, toggleSpecHudY, config.showSpecHUD, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Полоска времени", x + padding, cy + 2, COL_TEXT, false);
        toggleProgressBarX = x + w - padding - 28;
        toggleProgressBarY = cy;
        drawToggle(ctx, toggleProgressBarX, toggleProgressBarY, config.showProgressBar, mouseX, mouseY);

        cy += 20;
        ctx.drawText(this.textRenderer, "Время уведомления", x + padding, cy + 2, COL_TEXT, false);
        String durationStr = config.notificationDurationSeconds + "с";
        notifDurationBtnW = 30; notifDurationBtnH = 14;
        notifDurationBtnX = x + w - padding - notifDurationBtnW; notifDurationBtnY = cy - 1;
        ctx.fill(notifDurationBtnX, notifDurationBtnY, notifDurationBtnX + notifDurationBtnW, notifDurationBtnY + notifDurationBtnH, COL_TAB_HOVER);
        drawBorder(ctx, notifDurationBtnX, notifDurationBtnY, notifDurationBtnW, notifDurationBtnH, COL_BORDER);
        ctx.drawText(this.textRenderer, durationStr, notifDurationBtnX + (notifDurationBtnW - this.textRenderer.getWidth(durationStr))/2, notifDurationBtnY + 3, COL_ACCENT, false);

        // Draw dropdown last (on top of everything)
        if (soundDropdownOpen) {
            int dropY = soundBtnY + soundBtnH;
            ctx.fill(soundBtnX, dropY, soundBtnX + soundBtnW, dropY + SOUND_NAMES.length * 14, COL_BG_DARKER);
            drawBorder(ctx, soundBtnX, dropY, soundBtnW, SOUND_NAMES.length * 14, COL_BORDER);
            for (int i = 0; i < SOUND_NAMES.length; i++) {
                int itemY = dropY + i * 14;
                if (mouseX >= soundBtnX && mouseX <= soundBtnX + soundBtnW && mouseY >= itemY && mouseY <= itemY + 14) {
                    ctx.fill(soundBtnX + 1, itemY + 1, soundBtnX + soundBtnW - 1, itemY + 13, COL_TAB_HOVER);
                }
                ctx.drawText(this.textRenderer, SOUND_NAMES[i], soundBtnX + 4, itemY + 3, COL_TEXT, false);
            }
        }
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= verticalAmount * 20.0;
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > 400) scrollOffset = 400; // max scroll
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            FunSpecMod mod = FunSpecMod.getInstance();
            ModConfig config = mod.getConfig();
            boolean clicked = false;
            
            if (soundDropdownOpen) {
                int dropY = soundBtnY + soundBtnH;
                for (int i = 0; i < SOUND_NAMES.length; i++) {
                    int itemY = dropY + i * 14;
                    if (mouseX >= soundBtnX && mouseX <= soundBtnX + soundBtnW && mouseY >= itemY && mouseY <= itemY + 14) {
                        config.notificationSound = SOUND_IDS[i];
                        soundDropdownOpen = false;
                        config.save();
                        playPreviewSound(config.notificationSound);
                        keyFieldFocused = false;
                        return true;
                    }
                }
                soundDropdownOpen = false;
                keyFieldFocused = false;
                return true;
            }
            
            if (mouseX >= keyFieldX && mouseX <= keyFieldX + keyFieldW && mouseY >= keyFieldY && mouseY <= keyFieldY + keyFieldH) {
                keyFieldFocused = !keyFieldFocused;
                soundDropdownOpen = false;
                return true;
            } else {
                keyFieldFocused = false;
            }
            
            if (isInToggle(toggleAutoX, toggleAutoY, (int)mouseX, (int)mouseY)) {
                config.autoSpec = !config.autoSpec;
                clicked = true;
            }
            if (isInToggle(toggleModX, toggleModY, (int)mouseX, (int)mouseY)) {
                config.modEnabled = !config.modEnabled;
                clicked = true;
            }
            if (isInToggle(toggleNotifX, toggleNotifY, (int)mouseX, (int)mouseY)) {
                config.showNotificationsHUD = !config.showNotificationsHUD;
                clicked = true;
            }
            if (isInToggle(toggleDockX, toggleDockY, (int)mouseX, (int)mouseY)) {
                config.showStatusDock = !config.showStatusDock;
                clicked = true;
            }
            if (isInToggle(toggleSmartSpecX, toggleSmartSpecY, (int)mouseX, (int)mouseY)) {
                config.smartSpec = !config.smartSpec;
                clicked = true;
            }
            if (isInToggle(toggleReasonX, toggleReasonY, (int)mouseX, (int)mouseY)) {
                config.showReasonInDock = !config.showReasonInDock;
                clicked = true;
            }
            if (mouseX >= themeBtnX && mouseX <= themeBtnX + themeBtnW && mouseY >= themeBtnY && mouseY <= themeBtnY + themeBtnH) {
                config.themeIndex = (config.themeIndex + 1) % 4;
                updateTheme(); // apply immediately
                clicked = true;
            }
            if (isInToggle(toggleAutoKeyX, toggleAutoKeyY, (int)mouseX, (int)mouseY)) {
                config.autoKey = !config.autoKey;
                clicked = true;
            }
            if (mouseX >= maxNotifBtnX && mouseX <= maxNotifBtnX + maxNotifBtnW && mouseY >= maxNotifBtnY && mouseY <= maxNotifBtnY + maxNotifBtnH) {
                config.maxNotificationsOnScreen++;
                if (config.maxNotificationsOnScreen > 5) config.maxNotificationsOnScreen = 1;
                clicked = true;
            }
            if (isInToggle(toggleTimeHudX, toggleTimeHudY, (int)mouseX, (int)mouseY)) {
                config.showTimeHUD = !config.showTimeHUD;
                clicked = true;
            }
            if (isInToggle(toggleSpecHudX, toggleSpecHudY, (int)mouseX, (int)mouseY)) {
                config.showSpecHUD = !config.showSpecHUD;
                clicked = true;
            }
            if (isInToggle(toggleProgressBarX, toggleProgressBarY, (int)mouseX, (int)mouseY)) {
                config.showProgressBar = !config.showProgressBar;
                clicked = true;
            }
            if (mouseX >= notifDurationBtnX && mouseX <= notifDurationBtnX + notifDurationBtnW && mouseY >= notifDurationBtnY && mouseY <= notifDurationBtnY + notifDurationBtnH) {
                int[] durations = {5, 10, 15, 20, 30};
                int idx = 0;
                for (int i = 0; i < durations.length; i++) {
                    if (durations[i] == config.notificationDurationSeconds) { idx = i; break; }
                }
                config.notificationDurationSeconds = durations[(idx + 1) % durations.length];
                clicked = true;
            }
            if (mouseX >= soundBtnX && mouseX <= soundBtnX + soundBtnW && mouseY >= soundBtnY && mouseY <= soundBtnY + soundBtnH) {
                soundDropdownOpen = !soundDropdownOpen;
                return true;
            }

            if (clicked) {
                config.save();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (keyFieldFocused) {
            FunSpecMod mod = FunSpecMod.getInstance();
            ModConfig config = mod.getConfig();
            if (config.autoKeyValue.length() < 32) {
                config.autoKeyValue += chr;
                config.save();
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyFieldFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                FunSpecMod mod = FunSpecMod.getInstance();
                ModConfig config = mod.getConfig();
                if (config.autoKeyValue.length() > 0) {
                    config.autoKeyValue = config.autoKeyValue.substring(0, config.autoKeyValue.length() - 1);
                    config.save();
                }
                return true;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                keyFieldFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private void playPreviewSound(String soundId) {
        if ("none".equals(soundId)) return;
        if (this.client != null && this.client.getSoundManager() != null) {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(soundId);
            if (id != null) {
                net.minecraft.sound.SoundEvent event = net.minecraft.registry.Registries.SOUND_EVENT.get(id);
                if (event != null) {
                    this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(event, 1.0F));
                }
            }
        }
    }
}
