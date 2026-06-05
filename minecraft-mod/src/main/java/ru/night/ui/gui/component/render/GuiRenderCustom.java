package ru.night.ui.gui.component.render;

import java.text.SimpleDateFormat;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import ru.antigravity.vkspec.FunSpecMod;
import ru.antigravity.vkspec.ui.HistoryEntry;
import ru.antigravity.vkspec.ui.SpecNotification;
import ru.night.ui.gui.GuiScreen;
import ru.night.util.color.ColorUtil;
import ru.night.util.render.core.Renderer2D;
import ru.night.util.render.text.FontRegistry;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class GuiRenderCustom {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    public static double scrollHistory = 0;
    public static double scrollTickets = 0;
    public static double scrollLogs = 0;
    
    // Logs fields
    public static String[] logNicks = new String[]{"", ""};
    public static int activeLogIndex = -1;
    public static String logsResult = null;
    public static boolean isSearching = false;

    public static void renderTickets(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, float mainAlpha) {
        float x = GuiScreen.x + 104.735F;
        float y = GuiScreen.y + 34.025F;
        float w = 261.5F;
        float h = 209.5F;

        int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
        int textDim = ColorUtil.multAlpha(textColor, 0.85f);
        int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
        int mainColor20 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(170.0F * mainAlpha));
        int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
        int backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(60.0F * mainAlpha));

        // Header with icon
        renderer2D.text(FontRegistry.INTER_MEDIUM, x + 8, y + 6 + 10, 13, "Очередь заявок", textColor);

        List<SpecNotification> tickets = FunSpecMod.getInstance().getActiveNotifications();
        if (tickets.isEmpty()) {
            // Empty state — center in the panel
            float emptyY = y + h / 2 - 10;
            renderer2D.text(FontRegistry.INTER_MEDIUM, x + w / 2 - renderer2D.measureText(FontRegistry.INTER_MEDIUM, "Нет активных заявок.", 12).width / 2, emptyY, 12, "Нет активных заявок.", textDim);
            return;
        }

        float cy = y + 24 - (float)scrollTickets;
        float blockHeight = 28F;
        float gap = 4F;
        float padX = 8F;
        float cardW = w - padX * 2;

        for (int i = 0; i < tickets.size(); i++) {
            float cardTop = cy;
            if (cardTop + blockHeight > y + 20 && cardTop < y + h - 5) {
                // Card background
                renderer2D.rect(x + padX, cardTop, cardW, blockHeight, 5.0F, backGroundThreeColor);
                renderer2D.rectOutline(x + padX, cardTop, cardW, blockHeight, 5.0F, outlineColor, 0.1F);
                
                // Accent dot left side
                renderer2D.rect(x + padX + 4, cardTop + blockHeight / 2 - 2.5F, 5, 5, 2.5F, mainColor);

                SpecNotification t = tickets.get(i);
                // Nickname bold
                String nick = t.nickname;
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14, cardTop + 4 + 10, 11, nick, textColor);
                float nickW = renderer2D.measureText(FontRegistry.INTER_MEDIUM, nick, 11).width;

                // Server tag
                String serverTag = " [" + t.server + "]";
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14 + nickW, cardTop + 4 + 10, 11, serverTag, textDim);

                // Reason on second line
                String reason = t.reason;
                if (reason.length() > 40) reason = reason.substring(0, 38) + "..";
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14, cardTop + 16 + 10, 10, reason, mainColor20);
            }
            cy += blockHeight + gap;
        }
    }

    public static void renderHistory(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, float mainAlpha) {
        float x = GuiScreen.x + 104.735F;
        float y = GuiScreen.y + 34.025F;
        float w = 261.5F;
        float h = 209.5F;

        int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
        int textDim = ColorUtil.multAlpha(textColor, 0.85f);
        int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
        int mainColor20 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(170.0F * mainAlpha));
        int colorGreen = ColorUtil.getColor(100, 255, 130, (int)(200.0F * mainAlpha));
        int colorYellow = ColorUtil.getColor(255, 230, 120, (int)(220.0F * mainAlpha));
        int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
        int backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(60.0F * mainAlpha));

        // Header
        renderer2D.text(FontRegistry.INTER_MEDIUM, x + 8, y + 6 + 10, 13, "История заявок", textColor);

        List<HistoryEntry> history = FunSpecMod.getInstance().getTicketHistory();
        if (history.isEmpty()) {
            float emptyY = y + h / 2 - 10;
            renderer2D.text(FontRegistry.INTER_MEDIUM, x + w / 2 - renderer2D.measureText(FontRegistry.INTER_MEDIUM, "История пуста.", 12).width / 2, emptyY, 12, "История пуста.", textDim);
            return;
        }

        float cy = y + 24 - (float)scrollHistory;
        float blockHeight = 32F;
        float gap = 4F;
        float padX = 8F;
        float cardW = w - padX * 2;

        for (int i = 0; i < history.size(); i++) {
            float cardTop = cy;
            if (cardTop + blockHeight > y + 20 && cardTop < y + h - 5) {
                HistoryEntry e = history.get(i);
                boolean claimed = e.claimedBy != null;

                // Card
                renderer2D.rect(x + padX, cardTop, cardW, blockHeight, 5.0F, backGroundThreeColor);
                renderer2D.rectOutline(x + padX, cardTop, cardW, blockHeight, 5.0F, outlineColor, 0.1F);
                
                // Status dot — green if claimed, yellow if pending
                int dotColor = claimed ? colorGreen : colorYellow;
                renderer2D.rect(x + padX + 4, cardTop + 5, 5, 5, 2.5F, dotColor);

                // Time
                String time = TIME_FORMAT.format(new java.util.Date(e.timestamp));
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14, cardTop + 4 + 10, 10, time, textDim);
                float timeW = renderer2D.measureText(FontRegistry.INTER_MEDIUM, time, 10).width;

                // Nickname
                String nick = e.nickname;
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14 + timeW + 6, cardTop + 4 + 10, 10, nick, textColor);
                float nickW = renderer2D.measureText(FontRegistry.INTER_MEDIUM, nick, 10).width;

                // Server
                String srv = "(" + e.server + ")";
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14 + timeW + 6 + nickW + 4, cardTop + 4 + 10, 10, srv, textDim);

                // Reason — second line
                String reason = e.reason;
                if (reason.length() > 45) reason = reason.substring(0, 43) + "..";
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 14, cardTop + 17 + 10, 10, reason, mainColor20);

                // Claimed by — right side badge
                if (claimed) {
                    String badge = e.claimedBy;
                    float badgeW = renderer2D.measureText(FontRegistry.INTER_MEDIUM, badge, 9).width + 8;
                    float badgeX = x + padX + cardW - badgeW - 5;
                    float badgeY = cardTop + blockHeight / 2 - 5;
                    renderer2D.rect(badgeX, badgeY, badgeW, 11, 3.0F, ColorUtil.multAlpha(colorGreen, 0.2f));
                    renderer2D.text(FontRegistry.INTER_MEDIUM, badgeX + 4, badgeY + 2 + 10, 9, badge, colorGreen);
                }
            }
            cy += blockHeight + gap;
        }
    }

    public static void renderLogs(Renderer2D renderer2D, MatrixStack pose, int mouseX, int mouseY, float mainAlpha) {
        float x = GuiScreen.x + 104.735F;
        float y = GuiScreen.y + 34.025F;
        float w = 261.5F;
        float h = 209.5F;

        int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
        int textDim = ColorUtil.multAlpha(textColor, 0.85f);
        int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
        int mainColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(100.0F * mainAlpha));
        int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
        int backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(60.0F * mainAlpha));

        float padX = 8F;
        float cardW = w - padX * 2;

        // Header
        renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX, y + 6 + 10, 13, "Поиск по логам", textColor);

        float cy = y + 26;
        
        // Input fields with nice labels
        for (int i = 0; i < logNicks.length; i++) {
            boolean active = (activeLogIndex == i);
            float fieldH = 24;
            
            // Field card
            renderer2D.rect(x + padX, cy, cardW, fieldH, 5.0F, backGroundThreeColor);
            renderer2D.rectOutline(x + padX, cy, cardW, fieldH, 5.0F, active ? mainColor : outlineColor, active ? 0.3f : 0.1f);

            String text = logNicks[i];
            if (active && (System.currentTimeMillis() % 1000 < 500)) {
                text += "|";
            }
            if (text.isEmpty() && !active) {
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 10, cy + 7 + 10, 11, "Никнейм " + (i + 1), textDim);
            } else {
                renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 10, cy + 7 + 10, 11, text, textColor);
            }
            cy += fieldH + 5;
        }

        // Search button — accent gradient style
        float btnW = cardW;
        float btnH = 26;
        boolean hovered = GuiRenderMain.isHovered(mouseX, mouseY, x + padX, cy, btnW, btnH);
        int btnColor = hovered ? mainColor : mainColor40;
        renderer2D.rect(x + padX, cy, btnW, btnH, 5.0F, btnColor);
        
        String btnText = isSearching ? "Поиск..." : "Найти";
        float btnTextW = renderer2D.measureText(FontRegistry.INTER_MEDIUM, btnText, 12).width;
        renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + btnW / 2 - btnTextW / 2, cy + 8 + 10, 12, btnText, -1);
        cy += btnH + 8;

        // Results section
        if (logsResult != null) {
            float ry = cy - (float)scrollLogs;
            String[] lines = logsResult.split("\n");
            
            // Results header
            renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX, ry + 10, 11, "Результаты:", textDim);
            ry += 16;

            for (String line : lines) {
                if (ry > y + 20 && ry < y + h - 5) {
                    // Each result line as a mini card
                    float lineH = 16;
                    renderer2D.rect(x + padX, ry, cardW, lineH, 3.0F, backGroundThreeColor);
                    
                    String trimLine = line.trim();
                    if (trimLine.length() > 50) trimLine = trimLine.substring(0, 48) + "..";
                    renderer2D.text(FontRegistry.INTER_MEDIUM, x + padX + 6, ry + 3 + 10, 10, trimLine, textColor);
                }
                ry += 18;
            }
        }
    }
}
