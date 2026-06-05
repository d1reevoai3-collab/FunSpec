package ru.antigravity.vkspec.ui.modern;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import ru.antigravity.vkspec.FunSpecMod;

import java.util.ArrayList;
import java.util.List;

public class LogsScreen extends ModernScreenBase {

    private final List<TextFieldWidget> nickFields = new ArrayList<>();
    private int fieldCount = 2; // Минимум 2 поля
    private static final int MAX_FIELDS = 6;
    
    // Результаты логов
    private String logsResult = null;
    private boolean isSearching = false;
    private long searchStartTime = 0;
    
    // Скролл для результатов
    private int scrollOffset = 0;
    private List<String> resultLines = new ArrayList<>();

    public LogsScreen() {
        super(Text.literal("VK Spec"), 4); // Tab index 4 = Логи
    }

    @Override
    protected String getTabTitle() {
        return "Логи";
    }

    @Override
    protected void init() {
        super.init();
        nickFields.clear();
        
        int contentX = guiX + SIDEBAR_WIDTH + 1;
        int startY = guiY + HEADER_HEIGHT + 30;
        int fieldW = 120;
        
        for (int i = 0; i < fieldCount; i++) {
            TextFieldWidget field = new TextFieldWidget(
                this.textRenderer, 
                contentX + 55, 
                startY + i * 18, 
                fieldW, 
                14, 
                Text.literal("Ник " + (i + 1))
            );
            field.setMaxLength(16);
            field.setEditableColor(0xFFE2E8F0);
            field.setUneditableColor(0xFF94A3B8);
            nickFields.add(field);
            this.addDrawableChild(field);
        }
        
        // Восстанавливаем результат если был
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod != null) {
            String cached = mod.getLogsResult();
            if (cached != null) {
                logsResult = cached;
                isSearching = false;
                rebuildResultLines();
            }
            if (mod.isLogsSearching()) {
                isSearching = true;
                searchStartTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        
        // Проверяем, не пришел ли результат
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod != null && isSearching) {
            String result = mod.getLogsResult();
            if (result != null) {
                logsResult = result;
                isSearching = false;
                rebuildResultLines();
            }
            // Таймаут 15 секунд
            if (System.currentTimeMillis() - searchStartTime > 15000) {
                isSearching = false;
                logsResult = "⚠ Таймаут: бот не ответил за 15 секунд.";
                rebuildResultLines();
            }
        }
    }

    private void rebuildResultLines() {
        resultLines.clear();
        scrollOffset = 0;
        if (logsResult == null) return;
        
        // Разбиваем текст на строки, с переносом длинных строк
        String[] rawLines = logsResult.split("\n");
        int maxLineWidth = GUI_WIDTH - SIDEBAR_WIDTH - 30;
        
        for (String line : rawLines) {
            if (line.isEmpty()) {
                resultLines.add("");
                continue;
            }
            // Разбиваем длинные строки
            while (this.textRenderer != null && this.textRenderer.getWidth(line) > maxLineWidth && line.length() > 1) {
                int cutAt = line.length();
                while (cutAt > 1 && this.textRenderer.getWidth(line.substring(0, cutAt)) > maxLineWidth) {
                    cutAt--;
                }
                resultLines.add(line.substring(0, cutAt));
                line = line.substring(cutAt);
            }
            resultLines.add(line);
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int padding = 8;
        int cy = y;

        // === Блок ввода ников ===
        int inputCardH = 18 + fieldCount * 18 + 24;
        drawCard(ctx, x, cy, w, inputCardH, "Проверка логов");

        int fieldStartY = cy + 20;
        for (int i = 0; i < nickFields.size(); i++) {
            ctx.drawText(this.textRenderer, "Ник " + (i + 1) + ":", x + padding, fieldStartY + i * 18 + 3, COL_TEXT_DIM, false);
            // Перемещаем поля на правильные позиции (они могут сдвинуться при анимации)
            nickFields.get(i).setX(x + 50);
            nickFields.get(i).setY(fieldStartY + i * 18);
        }

        // Кнопка [+] для добавления поля
        int btnPlusX = x + 50 + 125;
        int btnPlusY = fieldStartY + (fieldCount - 1) * 18;
        boolean plusHover = fieldCount < MAX_FIELDS && mouseX >= btnPlusX && mouseX <= btnPlusX + 14 && mouseY >= btnPlusY && mouseY <= btnPlusY + 14;
        
        if (fieldCount < MAX_FIELDS) {
            ctx.fill(btnPlusX, btnPlusY, btnPlusX + 14, btnPlusY + 14, plusHover ? 0xFF2A3A2A : 0xFF1A2A1A);
            drawBorder(ctx, btnPlusX, btnPlusY, 14, 14, plusHover ? COL_GREEN : 0xFF2E5E3E);
            ctx.drawText(this.textRenderer, "+", btnPlusX + 4, btnPlusY + 3, COL_GREEN, false);
        }

        // Кнопка [-] для удаления поля
        if (fieldCount > 2) {
            int btnMinusX = btnPlusX + 18;
            boolean minusHover = mouseX >= btnMinusX && mouseX <= btnMinusX + 14 && mouseY >= btnPlusY && mouseY <= btnPlusY + 14;
            ctx.fill(btnMinusX, btnPlusY, btnMinusX + 14, btnPlusY + 14, minusHover ? 0xFF3A2A2A : 0xFF2A1A1A);
            drawBorder(ctx, btnMinusX, btnPlusY, 14, 14, minusHover ? COL_RED : 0xFF5E2E2E);
            ctx.drawText(this.textRenderer, "-", btnMinusX + 4, btnPlusY + 3, COL_RED, false);
        }

        // Кнопка "Искать"
        int btnSearchY = fieldStartY + fieldCount * 18 + 4;
        int btnSearchW = 70;
        int btnSearchH = 14;
        int btnSearchX = x + padding;
        boolean searchHover = !isSearching && mouseX >= btnSearchX && mouseX <= btnSearchX + btnSearchW && mouseY >= btnSearchY && mouseY <= btnSearchY + btnSearchH;
        
        int btnBg = isSearching ? 0xFF1A1A2A : (searchHover ? COL_ACCENT_HOVER : COL_ACCENT);
        ctx.fill(btnSearchX, btnSearchY, btnSearchX + btnSearchW, btnSearchY + btnSearchH, btnBg);
        drawBorder(ctx, btnSearchX, btnSearchY, btnSearchW, btnSearchH, isSearching ? COL_TEXT_DARK : COL_ACCENT);
        
        String btnText = isSearching ? "Поиск..." : "⌕ Искать";
        int textW = this.textRenderer.getWidth(btnText);
        ctx.drawText(this.textRenderer, btnText, btnSearchX + (btnSearchW - textW) / 2, btnSearchY + 3, 0xFFFFFFFF, false);
        
        // Кнопка "Очистить"
        int btnClearX = btnSearchX + btnSearchW + 6;
        int btnClearW = 60;
        boolean clearHover = mouseX >= btnClearX && mouseX <= btnClearX + btnClearW && mouseY >= btnSearchY && mouseY <= btnSearchY + btnSearchH;
        ctx.fill(btnClearX, btnSearchY, btnClearX + btnClearW, btnSearchY + btnSearchH, clearHover ? 0xFF2A1A1A : 0xFF1A1212);
        drawBorder(ctx, btnClearX, btnSearchY, btnClearW, btnSearchH, clearHover ? COL_RED : 0xFF5E2E2E);
        String clearText = "Очистить";
        int clearTW = this.textRenderer.getWidth(clearText);
        ctx.drawText(this.textRenderer, clearText, btnClearX + (btnClearW - clearTW) / 2, btnSearchY + 3, clearHover ? COL_RED : COL_TEXT_DIM, false);

        cy += inputCardH + 6;

        // === Блок результатов ===
        int resultH = h - (cy - y);
        if (resultH < 20) resultH = 20;
        drawCard(ctx, x, cy, w, resultH, "Результат");

        int resultY = cy + 18;
        int resultInnerH = resultH - 22;
        int maxVisibleLines = resultInnerH / 10;

        if (isSearching) {
            // Анимация точек
            int dots = (int) ((System.currentTimeMillis() - searchStartTime) / 500) % 4;
            String loadText = "Ищу логи" + ".".repeat(dots);
            ctx.drawText(this.textRenderer, loadText, x + padding, resultY, COL_YELLOW, false);
        } else if (resultLines.isEmpty()) {
            ctx.drawText(this.textRenderer, "Введите ники и нажмите «Искать»", x + padding, resultY, COL_TEXT_DARK, false);
        } else {
            // Отрисовка результатов с учетом скролла
            int startLine = Math.max(0, scrollOffset);
            int endLine = Math.min(resultLines.size(), startLine + maxVisibleLines);
            
            for (int i = startLine; i < endLine; i++) {
                String line = resultLines.get(i);
                int lineColor = COL_TEXT;
                
                // Подсветка ключевых слов
                if (line.contains("✅") || line.contains("Найдено")) lineColor = COL_GREEN;
                else if (line.contains("⚠") || line.contains("ошибка") || line.contains("Ошибка")) lineColor = COL_RED;
                else if (line.contains("Ищу") || line.contains("...")) lineColor = COL_YELLOW;
                else if (line.startsWith("  ")) lineColor = COL_TEXT_DIM; // Подробности чуть темнее
                
                ctx.drawText(this.textRenderer, line, x + padding, resultY + (i - startLine) * 10, lineColor, false);
            }
            
            // Индикатор скролла
            if (resultLines.size() > maxVisibleLines) {
                int scrollBarH = Math.max(8, resultInnerH * maxVisibleLines / resultLines.size());
                int scrollBarY = resultY + (resultInnerH - scrollBarH) * scrollOffset / Math.max(1, resultLines.size() - maxVisibleLines);
                ctx.fill(x + w - 4, scrollBarY, x + w - 2, scrollBarY + scrollBarH, COL_ACCENT);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int contentX = guiX + SIDEBAR_WIDTH + 1;
            int x = contentX + 6;
            int y = guiY + HEADER_HEIGHT + 6;
            int w = GUI_WIDTH - SIDEBAR_WIDTH - 13;
            int inputCardH = 18 + fieldCount * 18 + 24;
            int fieldStartY = y + 20;

            // Кнопка [+]
            int btnPlusX = x + 50 + 125;
            int btnPlusY = fieldStartY + (fieldCount - 1) * 18;
            if (fieldCount < MAX_FIELDS && mouseX >= btnPlusX && mouseX <= btnPlusX + 14 && mouseY >= btnPlusY && mouseY <= btnPlusY + 14) {
                fieldCount++;
                this.rebuildFields();
                return true;
            }

            // Кнопка [-]
            if (fieldCount > 2) {
                int btnMinusX = btnPlusX + 18;
                if (mouseX >= btnMinusX && mouseX <= btnMinusX + 14 && mouseY >= btnPlusY && mouseY <= btnPlusY + 14) {
                    fieldCount--;
                    this.rebuildFields();
                    return true;
                }
            }

            // Кнопка "Искать"
            int btnSearchY = fieldStartY + fieldCount * 18 + 4;
            int btnSearchX = x + 8;
            int btnSearchW = 70;
            int btnSearchH = 14;
            if (!isSearching && mouseX >= btnSearchX && mouseX <= btnSearchX + btnSearchW && mouseY >= btnSearchY && mouseY <= btnSearchY + btnSearchH) {
                performSearch();
                return true;
            }
            
            // Кнопка "Очистить"
            int btnClearX = btnSearchX + btnSearchW + 6;
            int btnClearW = 60;
            if (mouseX >= btnClearX && mouseX <= btnClearX + btnClearW && mouseY >= btnSearchY && mouseY <= btnSearchY + btnSearchH) {
                clearResults();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!resultLines.isEmpty()) {
            int contentX = guiX + SIDEBAR_WIDTH + 1;
            int x = contentX + 6;
            int w = GUI_WIDTH - SIDEBAR_WIDTH - 13;
            int inputCardH = 18 + fieldCount * 18 + 24;
            int resultY = guiY + HEADER_HEIGHT + 6 + inputCardH + 6;
            int resultH = GUI_HEIGHT - HEADER_HEIGHT - 12 - inputCardH - 6;
            int maxVisibleLines = (resultH - 22) / 10;
            
            if (mouseX >= x && mouseX <= x + w && mouseY >= resultY && mouseY <= resultY + resultH) {
                scrollOffset -= (int) verticalAmount * 3;
                scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, resultLines.size() - maxVisibleLines)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void performSearch() {
        List<String> nicks = new ArrayList<>();
        for (TextFieldWidget field : nickFields) {
            String text = field.getText().trim();
            if (!text.isEmpty()) {
                nicks.add(text);
            }
        }
        
        if (nicks.size() < 2) {
            logsResult = "⚠ Введите минимум 2 ника для проверки.";
            rebuildResultLines();
            return;
        }
        
        // Очищаем предыдущий результат
        logsResult = null;
        resultLines.clear();
        scrollOffset = 0;
        isSearching = true;
        searchStartTime = System.currentTimeMillis();
        
        // Отправляем запрос через WebSocket в расширение
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod != null) {
            String nickStr = String.join(",", nicks);
            mod.requestLogsCheck(nickStr);
        }
    }
    
    private void clearResults() {
        logsResult = null;
        resultLines.clear();
        scrollOffset = 0;
        isSearching = false;
        for (TextFieldWidget field : nickFields) {
            field.setText("");
        }
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod != null) {
            mod.clearLogsResult();
        }
    }

    private void rebuildFields() {
        // Сохраняем текст из существующих полей
        List<String> savedTexts = new ArrayList<>();
        for (TextFieldWidget field : nickFields) {
            savedTexts.add(field.getText());
        }
        
        // Убираем виджеты
        for (TextFieldWidget field : nickFields) {
            this.remove(field);
        }
        nickFields.clear();
        
        // Пересоздаём
        int contentX = guiX + SIDEBAR_WIDTH + 1;
        int startY = guiY + HEADER_HEIGHT + 30;
        int fieldW = 120;
        
        for (int i = 0; i < fieldCount; i++) {
            TextFieldWidget field = new TextFieldWidget(
                this.textRenderer,
                contentX + 55,
                startY + i * 18,
                fieldW,
                14,
                Text.literal("Ник " + (i + 1))
            );
            field.setMaxLength(16);
            field.setEditableColor(0xFFE2E8F0);
            field.setUneditableColor(0xFF94A3B8);
            // Восстанавливаем текст
            if (i < savedTexts.size()) {
                field.setText(savedTexts.get(i));
            }
            nickFields.add(field);
            this.addDrawableChild(field);
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab для переключения между полями
        if (keyCode == 258) { // Tab key
            for (int i = 0; i < nickFields.size(); i++) {
                if (nickFields.get(i).isFocused()) {
                    int next = (i + 1) % nickFields.size();
                    nickFields.get(i).setFocused(false);
                    nickFields.get(next).setFocused(true);
                    return true;
                }
            }
        }
        // Enter для поиска
        if (keyCode == 257) { // Enter key
            if (!isSearching) {
                performSearch();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
