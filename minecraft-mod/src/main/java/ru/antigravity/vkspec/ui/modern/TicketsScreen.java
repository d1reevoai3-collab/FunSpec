package ru.antigravity.vkspec.ui.modern;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.antigravity.vkspec.FunSpecMod;
import ru.antigravity.vkspec.ui.SpecNotification;
import java.util.List;

public class TicketsScreen extends ModernScreenBase {

    public TicketsScreen() {
        super(Text.literal("VK Spec"), 1);
    }

    @Override
    protected String getTabTitle() {
        return "Заявки";
    }

    @Override
    protected void renderContent(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        FunSpecMod mod = FunSpecMod.getInstance();
        List<SpecNotification> tickets = mod.getActiveNotifications();

        drawCard(ctx, x, y, w, h, "Очередь заявок (" + tickets.size() + ")");
        
        int cy = y + 22;
        int padding = 8;
        
        if (tickets.isEmpty()) {
            ctx.drawText(this.textRenderer, "Нет активных заявок.", x + padding, cy, COL_TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < Math.min(tickets.size(), 10); i++) {
            SpecNotification t = tickets.get(i);
            String label = "🔹 " + t.nickname + " [" + t.server + "] - " + t.reason;
            int maxW = w - padding * 2 - 20;
            if (this.textRenderer.getWidth(label) > maxW) {
                label = this.textRenderer.trimToWidth(label, maxW - this.textRenderer.getWidth("...")) + "...";
            }
            ctx.drawText(this.textRenderer, label, x + padding, cy, COL_TEXT, false);
            cy += 14;
        }
    }
}
