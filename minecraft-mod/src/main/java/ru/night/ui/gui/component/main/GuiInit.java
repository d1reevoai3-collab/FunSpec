package ru.night.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.ui.gui.GuiScreen;
import ru.night.util.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class GuiInit extends GuiScreen {
   public static void init() {
      System.out.println("[GuiClient] === GUI INIT === exit was: " + GuiScreen.exit 
            + ", alphaPC value was: " + GuiScreen.alphaPC.getValue()
            + ", alphaPC toValue was: " + GuiScreen.alphaPC.getToValue());
      
      // Полный сброс состояния анимации
      GuiScreen.animation = GuiScreen.animation.animate(1.0, 0.2F);
      
      // Принудительно сбрасываем exit ПЕРЕД запуском анимации
      GuiScreen.exit = false;
      
      // Сбрасываем alpha и запускаем анимацию появления
      GuiScreen.alphaPC.set(0.0);
      GuiScreen.alphaPC.run(1.0, 0.4F, Easings.CIRC_OUT);
      
      // Запоминаем timestamp открытия для grace period защиты
      GuiScreen.openTimestamp = System.currentTimeMillis();
      
      GuiScreen.mainAnimation.reset();
      GuiScreen.alpha.run(1.0);
      
      System.out.println("[GuiClient] === GUI INIT DONE === alphaPC start=" + GuiScreen.alphaPC.getStart()
            + ", duration=" + GuiScreen.alphaPC.getDuration()
            + ", toValue=" + GuiScreen.alphaPC.getToValue()
            + ", timestamp=" + GuiScreen.openTimestamp);
   }
}
