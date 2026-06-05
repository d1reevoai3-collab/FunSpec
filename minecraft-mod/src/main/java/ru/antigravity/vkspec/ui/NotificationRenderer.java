package ru.antigravity.vkspec.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import ru.antigravity.vkspec.FunSpecMod;
import ru.antigravity.vkspec.config.ModConfig;

import net.minecraft.client.render.RenderTickCounter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class NotificationRenderer {
    private static final int CARD_WIDTH = 196;
    private static final int FREE_CARD_HEIGHT = 68;
    private static final int CLAIMED_CARD_HEIGHT = 38;
    private static final int CARD_GAP = 8;
    private static final int CARD_RIGHT_MARGIN = 14;
    private static final int CARD_TOP_MARGIN = 14;
    private static final int DOCK_MARGIN = 8;
    
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    
    // Notification HUD state
    private static boolean isDraggingNotif = false;
    private static boolean isResizingNotif = false;
    private static int notifDragOffsetX = 0;
    private static int notifDragOffsetY = 0;
    private static int notifResizeStartX = 0;
    private static int notifResizeStartY = 0;
    private static float notifResizeStartScale = 1.0f;
    private static boolean didDragOrResizeNotif = false;

    // Dock HUD state
    private static boolean isDraggingDock = false;
    private static boolean isResizingDock = false;
    private static int dockDragOffsetX = 0;
    private static int dockDragOffsetY = 0;
    private static int dockResizeStartX = 0;
    private static int dockResizeStartY = 0;
    private static float dockResizeStartScale = 1.0f;
    private static boolean didDragOrResizeDock = false;
    
    private static boolean wasMouseDown = false;

    public static void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.options.hudHidden || client.world == null) return;

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod == null || !mod.getConfig().modEnabled) return;

        ModConfig config = mod.getConfig();
        boolean inScreen = client.currentScreen != null;
        
        // Скрытие по Tab (только вне экранов/GUI)
        if (!inScreen) {
            long handle = client.getWindow().getHandle();
            boolean tabPressed = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (tabPressed) return; // Полностью скрываем HUD при зажатом Tab
        }
        
        double mouseX = client.mouse.getX() * (double) screenWidth / (double) client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double) screenHeight / (double) client.getWindow().getHeight();
        boolean mouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        // Reset flags on fresh click
        if (mouseDown && !wasMouseDown) {
            didDragOrResizeNotif = false;
            didDragOrResizeDock = false;
            notifResizeStartX = (int) mouseX;
            notifResizeStartY = (int) mouseY;
        }

        // Поднимаем z-порядок чтобы рендерить ПОВЕРХ ванильного HUD
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(0, 0, 200);

        // Render Status Dock
        if (config.showStatusDock || inScreen) {
            renderStatusDock(drawContext, textRenderer, screenHeight, mod, config, inScreen, mouseX, mouseY, mouseDown);
        }
        
        // Render Notifications
        if (config.showNotificationsHUD || inScreen) {
            renderNotifications(drawContext, textRenderer, screenWidth, mod, config, inScreen, mouseX, mouseY, mouseDown);
        }

        drawContext.getMatrices().pop();

        wasMouseDown = mouseDown;
    }

    private static void renderStatusDock(DrawContext drawContext, TextRenderer textRenderer, int screenHeight, FunSpecMod mod, ModConfig config, boolean inScreen, double mouseX, double mouseY, boolean mouseDown) {
        int total = mod.getTotalTickets();
        int mine = mod.getMyClaimedTickets();
        String spectating = mod.getCurrentlySpectating();

        int dockWidth = 176;
        int dockHeight = 28;
        
        // Подсчёт высоты дока с учётом включенных элементов
        int lineCount = 1; // Базовая строка: заявки
        if (config.showTimeHUD) lineCount++;
        if (config.showSpecHUD && spectating != null) lineCount++;
        
        java.util.List<net.minecraft.text.OrderedText> reasonLines = null;
        if (spectating != null && config.showReasonInDock && mod.getSpectatingReason() != null) {
            String reason = mod.getSpectatingReason();
            reasonLines = textRenderer.wrapLines(net.minecraft.text.Text.literal(reason), dockWidth - 20);
        }
        
        dockHeight = 16 + lineCount * 13;
        if (reasonLines != null) {
            dockHeight += (reasonLines.size() * 10) + 2;
        }

        if (inScreen && total <= 0 && spectating == null) {
            dockHeight = 28; // fallback size for empty dock in chat
        }

        int defaultX = DOCK_MARGIN;
        int defaultY = screenHeight - 54;
        int currentX = config.dockX < 0 ? defaultX : (int) config.dockX;
        int currentY = config.dockY < 0 ? defaultY : (int) config.dockY;
        float scale = config.dockScale > 0.1f ? config.dockScale : 1.0f;

        if (inScreen) {
            int scaledW = (int)(dockWidth * scale);
            int scaledH = (int)(dockHeight * scale);
            
            int b = 10; // 10px invisible border
            boolean hoverOuter = mouseX >= currentX - b && mouseX <= currentX + scaledW + b && mouseY >= currentY - b && mouseY <= currentY + scaledH + b;
            boolean hoverInner = mouseX >= currentX + b && mouseX <= currentX + scaledW - b && mouseY >= currentY + b && mouseY <= currentY + scaledH - b;
            
            boolean hoverResize = hoverOuter && !hoverInner;
            boolean hoverDrag = hoverInner;

            // Тонкая рамка для зоны перетаскивания (без белых рамок)
            drawContext.fill(currentX - 1, currentY - 1, currentX + scaledW + 1, currentY + scaledH + 1, 0x22FFFFFF);
            drawContext.drawText(textRenderer, "Status Dock", currentX, currentY - 10, 0xFFFFFF, true);

            if (mouseDown) {
                if (hoverResize && !isDraggingDock && !isResizingDock && !wasMouseDown) {
                    isResizingDock = true;
                    dockResizeStartX = (int) mouseX;
                    dockResizeStartY = (int) mouseY;
                    dockResizeStartScale = scale;
                } else if (hoverDrag && !isResizingDock && !isDraggingDock && !isResizingNotif && !isDraggingNotif && !wasMouseDown) {
                    isDraggingDock = true;
                    dockDragOffsetX = (int) (mouseX - currentX);
                    dockDragOffsetY = (int) (mouseY - currentY);
                    dockResizeStartX = (int) mouseX;
                    dockResizeStartY = (int) mouseY;
                }
            } else {
                if (isDraggingDock || isResizingDock) config.save();
                isDraggingDock = false;
                isResizingDock = false;
            }

            if (isDraggingDock) {
                config.dockX = (int) (mouseX - dockDragOffsetX);
                config.dockY = (int) (mouseY - dockDragOffsetY);
                currentX = (int) config.dockX;
                currentY = (int) config.dockY;
                if (Math.abs(mouseX - dockResizeStartX) > 2 || Math.abs(mouseY - dockResizeStartY) > 2) {
                    didDragOrResizeDock = true;
                }
            }
            if (isResizingDock) {
                float diffX = (float) (mouseX - dockResizeStartX);
                float diffY = (float) (mouseY - dockResizeStartY);
                float newScale = dockResizeStartScale + (diffX + diffY) / 150.0f;
                if (newScale < 0.2f) newScale = 0.2f;
                if (newScale > 3.0f) newScale = 3.0f;
                config.dockScale = newScale;
                scale = newScale;
                if (Math.abs(diffX) > 2 || Math.abs(diffY) > 2) didDragOrResizeDock = true;
            }
        } else {
            isDraggingDock = false;
            isResizingDock = false;
        }

        if (total <= 0 && spectating == null && inScreen) {
            // Пример-карточка (mock) для перетаскивания в чате
            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(currentX, currentY, 0);
            drawContext.getMatrices().scale(scale, scale, 1.0f);
            drawPanel(drawContext, 0, 0, dockWidth, dockHeight, 0xD20C111A, 0xE2475666);
            drawContext.drawText(textRenderer, "VK SPEC (пример)", 10, 6, 0xFF8DA1B9, false);
            drawContext.drawText(textRenderer, "Перетащите для настройки", 10, 17, 0xFF64748B, false);
            drawContext.getMatrices().pop();
            return;
        }

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(currentX, currentY, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);

        drawPanel(drawContext, 0, 0, dockWidth, dockHeight, 0xD20C111A, 0xE2475666);
        drawAccent(drawContext, 0, 0, 3, dockHeight, 0xFF53E0B5);

        int textY = 6;
        
        // Заголовок с временем (если включено)
        if (config.showTimeHUD) {
            String timeStr = TIME_FORMAT.format(new Date());
            drawContext.drawText(textRenderer, Text.literal("VK SPEC"), 10, textY, 0xFF8DA1B9, false);
            int timeWidth = textRenderer.getWidth(timeStr);
            drawContext.drawText(textRenderer, Text.literal(timeStr), dockWidth - 10 - timeWidth, textY, 0xFF7DD3FC, false);
        } else {
            drawContext.drawText(textRenderer, Text.literal("VK SPEC"), 10, textY, 0xFF8DA1B9, false);
        }
        textY += 13;
        
        drawContext.drawText(textRenderer, Text.literal("Заявки " + total + "   Взял " + mine), 10, textY, 0xFFF1F5F9, false);
        textY += 13;

        if (config.showSpecHUD && spectating != null) {
            drawContext.drawText(textRenderer, Text.literal("Спекаю: " + spectating), 10, textY, 0xFF7DD3FC, false);
            textY += 13;
            
            if (reasonLines != null) {
                for (net.minecraft.text.OrderedText line : reasonLines) {
                    drawContext.drawText(textRenderer, line, 10, textY, 0xFFA0B2C6, false);
                    textY += 10;
                }
            }
        }

        drawContext.getMatrices().pop();
    }

    private static void renderNotifications(DrawContext drawContext, TextRenderer textRenderer, int screenWidth, FunSpecMod mod, ModConfig config, boolean inScreen, double mouseX, double mouseY, boolean mouseDown) {
        List<SpecNotification> activeNotifications = mod.getActiveNotifications();
        
        int defaultX = screenWidth - CARD_WIDTH - CARD_RIGHT_MARGIN;
        int defaultY = CARD_TOP_MARGIN;
        int currentX = config.hudX < 0 ? defaultX : (int) config.hudX;
        int currentY = config.hudY < 0 ? defaultY : (int) config.hudY;
        float scale = config.hudScale > 0.1f ? config.hudScale : 1.0f;

        int maxOnScreen = config.maxNotificationsOnScreen;
        
        int totalHeight = 0;
        int count = 0;
        for (SpecNotification notification : activeNotifications) {
            if (count++ >= maxOnScreen) break;
            totalHeight += (notification.claimed ? CLAIMED_CARD_HEIGHT : FREE_CARD_HEIGHT) + CARD_GAP;
        }
        if (totalHeight > 0) totalHeight -= CARD_GAP;

        if (inScreen) {
            if (totalHeight == 0) totalHeight = FREE_CARD_HEIGHT;
            
            int scaledW = (int)(CARD_WIDTH * scale);
            int scaledH = (int)(totalHeight * scale);
            
            int b = 10; // 10px invisible border
            boolean hoverOuter = mouseX >= currentX - b && mouseX <= currentX + scaledW + b && mouseY >= currentY - b && mouseY <= currentY + scaledH + b;
            boolean hoverInner = mouseX >= currentX + b && mouseX <= currentX + scaledW - b && mouseY >= currentY + b && mouseY <= currentY + scaledH - b;
            
            boolean hoverResize = hoverOuter && !hoverInner;
            boolean hoverDrag = hoverInner;

            // Тонкая рамка вместо белых полос
            drawContext.fill(currentX - 1, currentY - 1, currentX + scaledW + 1, currentY + scaledH + 1, 0x22FFFFFF);

            if (mouseDown) {
                if (hoverResize && !isDraggingNotif && !isResizingNotif && !wasMouseDown) {
                    isResizingNotif = true;
                    notifResizeStartX = (int) mouseX;
                    notifResizeStartY = (int) mouseY;
                    notifResizeStartScale = scale;
                } else if (hoverDrag && !isResizingNotif && !isDraggingNotif && !isResizingDock && !isDraggingDock && !wasMouseDown) {
                    isDraggingNotif = true;
                    notifDragOffsetX = (int) (mouseX - currentX);
                    notifDragOffsetY = (int) (mouseY - currentY);
                    notifResizeStartX = (int) mouseX;
                    notifResizeStartY = (int) mouseY;
                }
            } else {
                if (isDraggingNotif || isResizingNotif) config.save();
                
                // CLICK DETECTION
                if (!isDraggingNotif && !isResizingNotif && !didDragOrResizeNotif && wasMouseDown && hoverOuter) {
                    // Check which notification was clicked!
                    int relativeY = (int) ((mouseY - currentY) / scale);
                    int drawYCheck = 0;
                    int checkCount = 0;
                    for (SpecNotification notification : activeNotifications) {
                        if (checkCount++ >= maxOnScreen) break;
                        int cardH = notification.claimed ? CLAIMED_CARD_HEIGHT : FREE_CARD_HEIGHT;
                        if (relativeY >= drawYCheck && relativeY <= drawYCheck + cardH) {
                            if (!notification.claimed) {
                                // CLICK TRIGGERED
                                MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("§a[FunSpec] Клик по уведомлению: беру " + notification.nickname), true);
                                mod.claimTicket(MinecraftClient.getInstance(), notification);
                            }
                            break;
                        }
                        drawYCheck += cardH + CARD_GAP;
                    }
                }
                
                isDraggingNotif = false;
                isResizingNotif = false;
            }

            if (isDraggingNotif) {
                config.hudX = (int) (mouseX - notifDragOffsetX);
                config.hudY = (int) (mouseY - notifDragOffsetY);
                currentX = (int) config.hudX;
                currentY = (int) config.hudY;
                if (Math.abs(mouseX - notifResizeStartX) > 5 || Math.abs(mouseY - notifResizeStartY) > 5) {
                    didDragOrResizeNotif = true;
                }
            }
            if (isResizingNotif) {
                float diffX = (float) (mouseX - notifResizeStartX);
                float diffY = (float) (mouseY - notifResizeStartY);
                float newScale = notifResizeStartScale + (diffX + diffY) / 200.0f;
                if (newScale < 0.2f) newScale = 0.2f;
                if (newScale > 3.0f) newScale = 3.0f;
                config.hudScale = newScale;
                scale = newScale;
                if (Math.abs(diffX) > 2 || Math.abs(diffY) > 2) didDragOrResizeNotif = true;
            }
        } else {
            isDraggingNotif = false;
            isResizingNotif = false;
        }

        // Mock-карточки (пример) когда нет заявок и мы в GUI
        if (activeNotifications.isEmpty() && inScreen) {
            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(currentX, currentY, 0);
            drawContext.getMatrices().scale(scale, scale, 1.0f);
            
            // Фейковая карточка-пример
            drawPanel(drawContext, 0, 0, CARD_WIDTH, FREE_CARD_HEIGHT, 0xFF101722, 0xFF2A3A4A);
            drawAccent(drawContext, 0, 0, 4, FREE_CARD_HEIGHT, 0xFF3A5A3A);
            drawContext.drawText(textRenderer, Text.literal("ПРИМЕР"), 12, 7, 0xFF6E7D8E, false);
            drawContext.drawText(textRenderer, Text.literal("PlayerName"), 12, 20, 0xFF9FB0C3, false);
            drawContext.drawText(textRenderer, Text.literal("ANARCHY305"), 12, 33, 0xFF8A8520, false);
            drawContext.drawText(textRenderer, Text.literal("Перетащите для настройки"), 12, 45, 0xFF6E7D8E, false);
            
            drawContext.getMatrices().pop();
            return;
        }
        
        if (activeNotifications.isEmpty()) return;

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(currentX, currentY, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        
        int drawY = 0;
        count = 0;
        for (SpecNotification notification : activeNotifications) {
            if (count++ >= maxOnScreen) break;
            
            // Hover effect check
            int cardH = notification.claimed ? CLAIMED_CARD_HEIGHT : FREE_CARD_HEIGHT;
            boolean isHovered = false;
            if (inScreen) {
                int absY = currentY + (int)(drawY * scale);
                if (mouseX >= currentX && mouseX <= currentX + CARD_WIDTH * scale && mouseY >= absY && mouseY <= absY + cardH * scale) {
                    isHovered = true;
                }
            }
            
            drawY += renderNotificationCard(drawContext, textRenderer, MinecraftClient.getInstance(), notification, 0, drawY, config, isHovered) + CARD_GAP;
        }
        drawContext.getMatrices().pop();
    }

    private static int renderNotificationCard(
        DrawContext drawContext,
        TextRenderer textRenderer,
        MinecraftClient client,
        SpecNotification notification,
        int x,
        int y,
        ModConfig config,
        boolean isHovered
    ) {
        float alpha = notification.ticksLeft < 20 ? notification.ticksLeft / 20.0f : 1.0f;
        int height = notification.claimed ? CLAIMED_CARD_HEIGHT : FREE_CARD_HEIGHT;

        int background = applyAlpha(isHovered ? 0xFF1C2836 : 0xFF101722, alpha);
        int border = applyAlpha(notification.claimed ? 0xFF5B6676 : (isHovered ? 0xFF34D399 : 0xFF22C55E), alpha);
        int textPrimary = applyAlpha(0xFFF8FAFC, alpha);
        int textSecondary = applyAlpha(0xFF9FB0C3, alpha);
        int textMuted = applyAlpha(0xFF6E7D8E, alpha);

        drawPanel(drawContext, x, y, CARD_WIDTH, height, background, border);
        drawAccent(drawContext, x, y, 4, height, notification.claimed ? applyAlpha(0xFF94A3B8, alpha) : applyAlpha(0xFF22C55E, alpha));

        if (notification.claimed) {
            drawContext.drawText(textRenderer, Text.literal("CLAIMED"), x + 12, y + 7, textMuted, false);
            drawContext.drawText(textRenderer, Text.literal(notification.nickname), x + 12, y + 18, textPrimary, false);
            drawContext.drawText(
                textRenderer,
                Text.literal("Забрал: " + notification.claimedBy),
                x + 12,
                y + 29,
                textSecondary,
                false
            );
            return height;
        }

        drawContext.drawText(textRenderer, Text.literal("NEW SPEC"), x + 12, y + 7, textMuted, false);
        drawContext.drawText(textRenderer, Text.literal(notification.nickname), x + 12, y + 20, textPrimary, false);
        drawContext.drawText(textRenderer, Text.literal(notification.server.toUpperCase()), x + 12, y + 33, applyAlpha(0xFFFACC15, alpha), false);
        drawContext.drawText(textRenderer, Text.literal(trim(notification.reason, 28)), x + 12, y + 45, textSecondary, false);
        
        // Полоска времени (если включена в настройках)
        if (config.showProgressBar) {
            float progress = notification.durationTicks <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, notification.ticksLeft / (float) notification.durationTicks));
            int progressWidth = Math.max(8, (int) ((CARD_WIDTH - 18) * progress));
            drawContext.fill(x + 9, y + height - 8, x + CARD_WIDTH - 9, y + height - 6, applyAlpha(0xFF223041, alpha));
            drawContext.fill(x + 9, y + height - 8, x + 9 + progressWidth, y + height - 6, applyAlpha(0xFF22C55E, alpha));
        }

        return height;
    }

    private static void drawPanel(DrawContext drawContext, int x, int y, int width, int height, int backgroundColor, int borderColor) {
        drawContext.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x55000000); // shadow
        drawContext.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor); // border
        drawContext.fill(x, y, x + width, y + height, backgroundColor); // background
    }

    private static void drawAccent(DrawContext drawContext, int x, int y, int width, int height, int color) {
        drawContext.fill(x, y, x + width, y + height, color);
    }

    private static int applyAlpha(int color, float alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alpha)));
        return (clampedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
