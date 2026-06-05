package ru.night.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.Night;
import ru.night.ui.gui.GuiScreen;

@Environment(EnvType.CLIENT)
public class GuiCharTyped extends GuiScreen {
   public static boolean charTyped(char codePoint, int modifiers) {
      if (ru.night.module.api.Category.Logs.equals(GuiScreen.selectedCategories)) {
          int idx = ru.night.ui.gui.component.render.GuiRenderCustom.activeLogIndex;
          if (idx >= 0 && idx < ru.night.ui.gui.component.render.GuiRenderCustom.logNicks.length) {
              if (codePoint >= ' ' && codePoint != 127) {
                  if (ru.night.ui.gui.component.render.GuiRenderCustom.logNicks[idx].length() < 16) {
                      ru.night.ui.gui.component.render.GuiRenderCustom.logNicks[idx] += codePoint;
                  }
                  return true;
              }
          }
      }

      if (GuiScreen.activeStringSetting != null) {
         if (codePoint == '\b') {
            if (!GuiScreen.activeStringSetting.input.isEmpty()) {
               GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input.substring(0, GuiScreen.activeStringSetting.input.length() - 1);
               if (Night.get.configManager != null) {
                  Night.get.configManager.autoSave();
               }
            }

            return true;
         }

         if (codePoint >= ' ' && codePoint != 127) {
            if (GuiScreen.activeStringSetting.input.length() < 16) {
               GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input + codePoint;
               if (Night.get.configManager != null) {
                  Night.get.configManager.autoSave();
               }
            }

            return true;
         }
      }

      if (GuiScreen.activeSearch) {
         if (codePoint == '\b') {
            return true;
         }

         if (codePoint >= ' '
            && codePoint != 127
            && (codePoint >= 'a' && codePoint <= 'z' || codePoint >= 'A' && codePoint <= 'Z' || codePoint >= '0' && codePoint <= '9' || codePoint == ' ')) {
            if (GuiScreen.searchText.length() < 50) {
               GuiScreen.searchText = GuiScreen.searchText + codePoint;
            }

            return true;
         }
      }

      return false;
   }
}
