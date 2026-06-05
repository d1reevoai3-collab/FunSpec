package ru.antigravity.vkspec.ui.modern;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.antigravity.vkspec.FunSpecMod;
import ru.antigravity.vkspec.config.ModConfig;

public class HomeScreen extends ModernScreenBase {

    public HomeScreen() {
        super(Text.literal("VK Spec"), 0);
    }

    @Override
    protected String getTabTitle() {
        return "Главная";
    }

    @Override
    protected void renderContent(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        FunSpecMod mod = FunSpecMod.getInstance();
        ModConfig config = mod.getConfig();

        int padding = 8;
        int rowH = 14;
        int gap = 8;
        int colW = (w - gap) / 2;

        // ===== LEFT COLUMN =====
        int leftX = x;
        int cy = y;

        // Card 1: Профиль
        int profileH = 46;
        drawCard(ctx, leftX, cy, colW, profileH, "Профиль");
        int py = cy + 22;
        String name = (this.client != null && this.client.getSession() != null) ? this.client.getSession().getUsername() : "Неизвестно";
        drawKeyValue(ctx, leftX + padding, py, colW - padding * 2, "Никнейм", name, COL_ACCENT);
        py += rowH;

        // Статус: зеленый "Активен" если мод включен и на сервере, оранж "Ожидает ключ", красный "Отключен"
        String statusText;
        int statusColor;
        if (!config.modEnabled) {
            statusText = "Отключен";
            statusColor = COL_RED;
        } else if (this.client != null && this.client.getCurrentServerEntry() != null) {
            if (config.autoKey && config.autoKeyValue != null && !config.autoKeyValue.trim().isEmpty()) {
                statusText = "Активен";
                statusColor = COL_GREEN;
            } else {
                statusText = "Ожидает ключ";
                statusColor = 0xFFFFA500; // orange
            }
        } else {
            statusText = "Не на сервере";
            statusColor = COL_YELLOW;
        }
        drawKeyValue(ctx, leftX + padding, py, colW - padding * 2, "Статус", statusText, statusColor);
        cy += profileH + gap;

        // Card 2: IP Сервера
        int ipH = 32;
        drawCard(ctx, leftX, cy, colW, ipH, "Сервер");
        int iy = cy + 20;
        String ip = (this.client != null && this.client.getCurrentServerEntry() != null) ? this.client.getCurrentServerEntry().address : "Одиночная игра";
        drawKeyValue(ctx, leftX + padding, iy, colW - padding * 2, "Айпи", ip, COL_TEXT_DIM);
        cy += ipH + gap;

        // Card 3: Статистика
        int statH = 46;
        drawCard(ctx, leftX, cy, colW, statH, "Статистика");
        int sy = cy + 22;
        drawKeyValue(ctx, leftX + padding, sy, colW - padding * 2, "Всего заявок", String.valueOf(mod.getTotalTickets()), COL_GREEN);
        sy += rowH;
        drawKeyValue(ctx, leftX + padding, sy, colW - padding * 2, "Взято вами", String.valueOf(mod.getMyClaimedTickets()), COL_YELLOW);

        // ===== RIGHT COLUMN =====
        int rightX = x + colW + gap;
        cy = y;

        // Card 4: Текущий спек
        String curSpec = mod.getCurrentlySpectating();
        int specH = curSpec != null ? 60 : 46;
        drawCard(ctx, rightX, cy, colW, specH, "Текущий спек");
        int specY = cy + 22;

        if (curSpec != null) {
            drawKeyValue(ctx, rightX + padding, specY, colW - padding * 2, "Игрок", curSpec, COL_GREEN);
            specY += rowH;
            drawKeyValue(ctx, rightX + padding, specY, colW - padding * 2, "Статус", "В слежке", COL_TEXT);
            specY += rowH;
            String reason = mod.getSpectatingReason();
            if (reason != null && !reason.isEmpty()) {
                String truncReason = reason.length() > 20 ? reason.substring(0, 20) + "..." : reason;
                drawKeyValue(ctx, rightX + padding, specY, colW - padding * 2, "Причина", truncReason, COL_TEXT_DIM);
            }
        } else {
            // Пусто — никого не спекаем
            drawKeyValue(ctx, rightX + padding, specY, colW - padding * 2, "Игрок", "", COL_TEXT_DIM);
            specY += rowH;
            drawKeyValue(ctx, rightX + padding, specY, colW - padding * 2, "Статус", "", COL_TEXT_DIM);
        }
    }
}
