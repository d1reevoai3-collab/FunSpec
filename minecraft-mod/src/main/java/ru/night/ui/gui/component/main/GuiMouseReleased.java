package ru.night.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.Night;
import ru.night.ui.gui.GuiScreen;

@Environment(EnvType.CLIENT)
public class GuiMouseReleased extends GuiScreen {
   public static void mouseReleased() {
      if ((GuiScreen.activeSliderSetting != null || GuiScreen.pickingSaturationBrightness || GuiScreen.pickingHue) && Night.get.configManager != null) {
         Night.get.configManager.autoSave();
      }

      GuiScreen.pickingSaturationBrightness = false;
      GuiScreen.pickingHue = false;
      GuiScreen.pickingAlpha = false;
      GuiScreen.activeSliderSetting = null;
      GuiScreen.sliderX = 0.0F;
      GuiScreen.sliderY = 0.0F;
      GuiScreen.sliderWidth = 0.0F;
   }
}
