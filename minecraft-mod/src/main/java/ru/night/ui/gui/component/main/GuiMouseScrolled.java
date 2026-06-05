package ru.night.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.ui.gui.GuiScreen;
import ru.night.ui.gui.component.render.GuiRenderMain;
import ru.night.util.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiMouseScrolled extends GuiScreen {
   public static boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
      float[] mouseCoords = ScaleHelper.calc((float)pMouseX, (float)pMouseY);
      float mouseX = mouseCoords[0];
      float mouseY = mouseCoords[1];
      float x1 = GuiScreen.x + 104.735F + 5.0F;
      float y1 = GuiScreen.y + 34.025F + 5.0F;
      float rectWidth = 251.5F;
      float rectHeight = 199.5F;
      if (!GuiScreen.exit && GuiRenderMain.isHovered(mouseX, mouseY, x1, y1, rectWidth, rectHeight)) {
         if (ru.night.module.api.Category.Tickets.equals(GuiScreen.selectedCategories)) {
             ru.night.ui.gui.component.render.GuiRenderCustom.scrollTickets += pScrollY * 20.0;
             if (ru.night.ui.gui.component.render.GuiRenderCustom.scrollTickets < 0) ru.night.ui.gui.component.render.GuiRenderCustom.scrollTickets = 0;
             return true;
         }
         if (ru.night.module.api.Category.History.equals(GuiScreen.selectedCategories)) {
             ru.night.ui.gui.component.render.GuiRenderCustom.scrollHistory += pScrollY * 20.0;
             if (ru.night.ui.gui.component.render.GuiRenderCustom.scrollHistory < 0) ru.night.ui.gui.component.render.GuiRenderCustom.scrollHistory = 0;
             return true;
         }
         if (ru.night.module.api.Category.Logs.equals(GuiScreen.selectedCategories)) {
             ru.night.ui.gui.component.render.GuiRenderCustom.scrollLogs += pScrollY * 20.0;
             if (ru.night.ui.gui.component.render.GuiRenderCustom.scrollLogs < 0) ru.night.ui.gui.component.render.GuiRenderCustom.scrollLogs = 0;
             return true;
         }
         GuiScreen.getScrollUtil().handleScroll(pScrollY);
         return true;
      } else {
         return false;
      }
   }
}
