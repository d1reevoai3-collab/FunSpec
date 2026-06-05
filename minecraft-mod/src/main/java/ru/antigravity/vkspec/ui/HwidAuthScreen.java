package ru.antigravity.vkspec.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.antigravity.vkspec.net.HwidManager;

public class HwidAuthScreen extends Screen {

    private final String hwid;
    private final String reason;

    public HwidAuthScreen(String hwid) {
        super(Text.literal("HWID Authorization"));
        this.hwid = hwid;
        this.reason = HwidManager.getBlockReason();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Кнопка "Скопировать HWID"
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Скопировать HWID"), button -> {
            if (this.client != null && this.client.keyboard != null) {
                this.client.keyboard.setClipboard(this.hwid);
            }
            button.setMessage(Text.literal("§aСкопировано!"));
        }).dimensions(centerX - 100, centerY + 30, 200, 20).build());

        // Кнопка "Выйти из игры"
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Выйти из игры"), button -> {
            if (this.client != null) {
                this.client.scheduleStop();
            }
        }).dimensions(centerX - 100, centerY + 55, 200, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Сплошной чёрный фон
        context.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Заголовок
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("ДОСТУП ЗАПРЕЩЕН").formatted(Formatting.RED, Formatting.BOLD),
            centerX, centerY - 70, 0xFFFFFF);

        // Причина блокировки
        String reasonText;
        int reasonColor;
        if ("banned".equals(reason)) {
            reasonText = "Ваш HWID заблокирован администратором.";
            reasonColor = 0xFF5555; // Красный
        } else if ("network_error".equals(reason)) {
            reasonText = "Ошибка подключения к серверу проверки. Проверьте интернет.";
            reasonColor = 0xFFAA00; // Оранжевый
        } else if ("tamper_detected".equals(reason)) {
            reasonText = "Обнаружена подмена данных. Отключите прокси/VPN.";
            reasonColor = 0xFF5555; // Красный
        } else {
            reasonText = "Ваш HWID не зарегистрирован в базе.";
            reasonColor = 0xFFFFFF; // Белый
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal(reasonText),
            centerX, centerY - 50, reasonColor);

        // Инструкции
        if (!"banned".equals(reason) && !"tamper_detected".equals(reason)) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Отправьте его боту ВКонтакте для получения доступа.").formatted(Formatting.GRAY),
                centerX, centerY - 35, 0xFFFFFF);
        }

        // HWID
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Ваш HWID: " + this.hwid).formatted(Formatting.YELLOW, Formatting.BOLD),
            centerX, centerY - 10, 0xFFFFFF);

        // Подсказка
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Нажмите кнопку ниже чтобы скопировать HWID").formatted(Formatting.DARK_GRAY),
            centerX, centerY + 15, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Нельзя закрыть через ESC
    }
}
