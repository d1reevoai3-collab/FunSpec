package ru.antigravity.vkspec.ui.modern;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.antigravity.vkspec.FunSpecMod;
import ru.antigravity.vkspec.ui.HistoryEntry;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistoryScreen extends ModernScreenBase {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private double scrollOffset = 0.0;

    public HistoryScreen() {
        super(Text.literal("VK Spec"), 2);
    }

    @Override
    protected String getTabTitle() {
        return "История";
    }

    @Override
    protected void renderContent(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        FunSpecMod mod = FunSpecMod.getInstance();
        List<HistoryEntry> history = mod.getTicketHistory();

        drawCard(ctx, x, y, w, h, "История заявок");

        int cy = y + 22 - (int)scrollOffset;
        int padding = 8;
        
        if (history.isEmpty()) {
            ctx.drawText(this.textRenderer, "История пуста.", x + padding, cy, COL_TEXT_DIM, false);
            return;
        }

        // Рисуем все записи (скроллинг через scrollOffset)
        for (int i = 0; i < history.size(); i++) {
            HistoryEntry e = history.get(i);
            
            // Пропускаем невидимые записи (оптимизация)
            if (cy + 14 < y + 22 || cy > y + h) {
                cy += 14;
                continue;
            }
            
            String time = TIME_FORMAT.format(new java.util.Date(e.timestamp));
            
            // Формат: [Время] Ник (Сервер) - *Сообщение*
            String metaStr = "[" + time + "] " + e.nickname + " (" + e.server + ") - ";
            String msgStr = e.reason;
            
            int maxW = w - padding * 2 - 20; 
            
            String claimedStr = "";
            if (e.claimedBy != null) {
                claimedStr = " [Взял: " + e.claimedBy + "]";
                maxW -= this.textRenderer.getWidth(claimedStr);
            }
            
            // Если слишком длинное - обрезаем сообщение
            int metaW = this.textRenderer.getWidth(metaStr);
            if (metaW + this.textRenderer.getWidth(msgStr) > maxW) {
                msgStr = this.textRenderer.trimToWidth(msgStr, maxW - metaW - this.textRenderer.getWidth("...")) + "...";
            }
            
            int metaColor = COL_TEXT_DIM;
            if (e.claimedBy != null) {
                metaColor = 0xFFAAAAAA;
            }
            ctx.drawText(this.textRenderer, metaStr, x + padding, cy, metaColor, false);
            
            int msgColor = (e.claimedBy != null) ? COL_TEXT_DIM : 0xFFFFFFAA;
            ctx.drawText(this.textRenderer, msgStr, x + padding + metaW, cy, msgColor, false);
            
            if (!claimedStr.isEmpty()) {
                ctx.drawText(this.textRenderer, claimedStr, x + padding + metaW + this.textRenderer.getWidth(msgStr), cy, COL_GREEN, false);
            }
            cy += 14;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= verticalAmount * 20.0;
        if (scrollOffset < 0) scrollOffset = 0;
        // Максимальный скролл: все записи * 14px - видимая область
        FunSpecMod mod = FunSpecMod.getInstance();
        int maxScroll = Math.max(0, mod.getTicketHistory().size() * 14 - 180);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
