package ru.night.ui.gui.component.mouse;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.ui.gui.GuiScreen;
import ru.night.ui.gui.component.mouse.category.GuiMouseClickedCategory;
import ru.night.ui.gui.component.mouse.colorpicker.GuiMouseClickedColorPicker;
import ru.night.ui.gui.component.mouse.module.GuiMouseClickedModule;
import ru.night.ui.gui.component.mouse.setting.GuiMouseClickedSetting;
import ru.night.ui.gui.component.render.GuiRenderMain;
import ru.night.ui.gui.theme.ThemeScreen;
import ru.night.util.render.core.Renderer2D;
import ru.night.util.render.math.MathHelper;
import ru.night.util.render.math.ScaleHelper;
import ru.night.util.render.math.ScaledResolution;

@Environment(EnvType.CLIENT)
public class GuiMouseClicked extends GuiScreen {
   public static boolean mouseClicked(Renderer2D renderer2D, double pMouseX, double pMouseY, int pButton) {
      int mouseX = (int)ScaleHelper.calc((float)pMouseX, (float)pMouseY)[0];
      int mouseY = (int)ScaleHelper.calc((float)pMouseX, (float)pMouseY)[1];
      ScaledResolution sr = new ScaledResolution(GuiScreen.mc);
      GuiScreen.x = (int)MathHelper.clamp(GuiScreen.x, 0.0F, ScaleHelper.calc(sr.getWidth()) - GuiScreen.width);
      GuiScreen.y = (int)MathHelper.clamp(GuiScreen.y, 0.0F, ScaleHelper.calc(sr.getHeight()) - GuiScreen.height);
      if (!GuiScreen.exit) {
         float searchX = GuiScreen.x + 111.885F;
         float searchY = GuiScreen.y + 6.185F;
         float searchWidth = 124.04F;
         float searchHeight = 21.325F;
         if (pButton == 0 && GuiRenderMain.isHovered(mouseX, mouseY, searchX, searchY, searchWidth, searchHeight)) {
            GuiScreen.activeSearch = true;
            return true;
         }

         GuiMouseClickedCategory.mouseClickedCategory(mouseX, mouseY);
         
         if (ru.night.module.api.Category.Logs.equals(GuiScreen.selectedCategories)) {
             float padX = 8.0F;
             float cx = GuiScreen.x + 104.735F + padX;
             float cy = GuiScreen.y + 34.025F + 26.0F;
             float cardW = 261.5F - padX * 2;
             float fieldH = 24.0F;
             float gap = 5.0F;
             for (int i = 0; i < ru.night.ui.gui.component.render.GuiRenderCustom.logNicks.length; i++) {
                 if (pButton == 0 && GuiRenderMain.isHovered(mouseX, mouseY, cx, cy, cardW, fieldH)) {
                     ru.night.ui.gui.component.render.GuiRenderCustom.activeLogIndex = i;
                     return true;
                 }
                 cy += fieldH + gap;
             }
             // Search button
             float btnH = 26.0F;
             if (pButton == 0 && GuiRenderMain.isHovered(mouseX, mouseY, cx, cy, cardW, btnH)) {
                 ru.night.ui.gui.component.render.GuiRenderCustom.activeLogIndex = -1;
                 ru.night.ui.gui.component.render.GuiRenderCustom.isSearching = true;
                 ru.night.ui.gui.component.render.GuiRenderCustom.logsResult = "Поиск...";
                 // Start search async
                 new Thread(() -> {
                     ru.antigravity.vkspec.FunSpecMod mod = ru.antigravity.vkspec.FunSpecMod.getInstance();
                     java.util.List<String> nicks = new java.util.ArrayList<>(java.util.Arrays.asList(ru.night.ui.gui.component.render.GuiRenderCustom.logNicks));
                     nicks.removeIf(String::isEmpty);
                     String nickStr = String.join(",", nicks);
                     mod.requestLogsCheck(nickStr);
                     
                     long start = System.currentTimeMillis();
                     while (mod.isLogsSearching() && System.currentTimeMillis() - start < 15000) {
                         try { Thread.sleep(100); } catch (InterruptedException e) {}
                     }
                     if (mod.isLogsSearching()) {
                         ru.night.ui.gui.component.render.GuiRenderCustom.logsResult = "Таймаут: бот не ответил за 15 секунд.";
                     } else {
                         ru.night.ui.gui.component.render.GuiRenderCustom.logsResult = mod.getLogsResult();
                     }
                     ru.night.ui.gui.component.render.GuiRenderCustom.isSearching = false;
                 }).start();
                 return true;
             }
             ru.night.ui.gui.component.render.GuiRenderCustom.activeLogIndex = -1;
             return true;
         }
         
         if (ru.night.module.api.Category.Tickets.equals(GuiScreen.selectedCategories) || ru.night.module.api.Category.History.equals(GuiScreen.selectedCategories)) {
             return true; // prevent interacting with modules underneath
         }

         if (GuiMouseClickedColorPicker.mouseClickedColorPicker(mouseX, mouseY, pButton)) {
            return true;
         }

         if (GuiMouseClickedModule.mouseClickedModule(renderer2D, mouseX, mouseY, pButton)) {
            return true;
         }

         float settingsButtonX = GuiScreen.x + 338.555F;
         float settingsButtonY = GuiScreen.y + 6.185F;
         float settingsButtonWidth = 21.325F;
         float settingsButtonHeight = 21.325F;
         if (pButton == 0 && GuiRenderMain.isHovered(mouseX, mouseY, settingsButtonX, settingsButtonY, settingsButtonWidth, settingsButtonHeight)) {
            GuiScreen.showClientSettingsPopup = !GuiScreen.showClientSettingsPopup;
            return true;
         }

         if (GuiScreen.showClientSettingsPopup && pButton == 0) {
            float popupWidth = 100.0F;
            float popupHeight = 60.0F;
            float popupX = GuiScreen.x + 450.0F + 21.325F - popupWidth;
            float popupY = GuiScreen.y - 15.0F + 21.325F + 5.0F;
            if (GuiRenderMain.isHovered(mouseX, mouseY, popupX, popupY, popupWidth, popupHeight)) {
               float settingY = popupY + 10.0F;
               float settingX = popupX + 10.0F;
               float settingWidth = popupWidth - 20.0F;
               if (GuiMouseClickedSetting.handleSettingClick(renderer2D, GuiScreen.clientBlurSetting, settingX, settingY, settingWidth, mouseX, mouseY, pButton)
                  )
                {
                  return true;
               }
            } else {
               GuiScreen.showClientSettingsPopup = false;
            }
         }

         ThemeScreen.mouseClickedTheme(pMouseX, pMouseY, pButton);
      }

      if (GuiScreen.activeBindSetting != null && pButton >= 0 && pButton <= 2) {
         int mouseKey = -100 - pButton;
         GuiScreen.activeBindSetting.key = mouseKey;
         GuiScreen.activeBindSetting.active = false;
         GuiScreen.activeBindSetting = null;
         return true;
      } else {
         return false;
      }
   }
}
