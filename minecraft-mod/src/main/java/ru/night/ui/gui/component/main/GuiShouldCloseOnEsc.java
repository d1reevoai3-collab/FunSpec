package ru.night.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.ui.gui.GuiScreen;
import ru.night.util.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class GuiShouldCloseOnEsc extends GuiScreen {
   /**
    * Вызывается Minecraft при нажатии Esc.
    * Начинает анимацию закрытия GUI вместо мгновенного закрытия.
    * Возвращает false, чтобы Minecraft НЕ вызывал close() напрямую —
    * мы закроем экран сами в tick() после завершения анимации.
    */
   public static boolean shouldCloseOnEsc() {
      // Защита: не закрываем GUI в первые 300мс после открытия,
      // чтобы предотвратить конфликт с Fabric keybinding event'ами
      long timeSinceOpen = System.currentTimeMillis() - GuiScreen.openTimestamp;
      if (timeSinceOpen < 300L) {
         System.out.println("[GuiClient] ESC ignored — GUI just opened " + timeSinceOpen + "ms ago");
         return false;
      }

      if (!GuiScreen.exit && GuiScreen.alphaPC.getValue() > 0.0) {
         System.out.println("[GuiClient] ESC accepted — starting close animation (alpha=" + GuiScreen.alphaPC.getValue() + ")");
         GuiScreen.alphaPC.run(0.0, 0.4F, Easings.CIRC_OUT);
         GuiScreen.exit = true;
      }

      return false;
   }
}
