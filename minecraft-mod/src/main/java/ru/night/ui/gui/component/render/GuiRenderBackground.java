package ru.night.ui.gui.component.render;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import ru.night.ui.gui.GuiScreen;
import ru.night.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class GuiRenderBackground extends GuiScreen {
   public static void renderBackground(Renderer2D renderer2D, MatrixStack pose, float mainAlpha) {
      float x = GuiScreen.x;
      float y = GuiScreen.y;
      float w = GuiScreen.width;
      float h = GuiScreen.height;
      float rounding = 8.0F;
      
      // Shadow (large, soft shadow for depth)
      int shadowColor = Renderer2D.ColorUtil.rgba(0, 0, 0, (int)(160.0F * mainAlpha));
      renderer2D.shadow(x, y, w, h, rounding, 20.0F, 0.0F, shadowColor);
      
      // Secondary deeper shadow for focus
      int innerShadowColor = Renderer2D.ColorUtil.rgba(0, 0, 0, (int)(100.0F * mainAlpha));
      renderer2D.shadow(x, y, w, h, rounding, 8.0F, 2.0F, innerShadowColor);

      // Blur the background for the glassmorphism effect
      renderer2D.prepareBlur(16.0F);
      renderer2D.blur(x, y, w, h, rounding, mainAlpha);

      // Gradient glassmorphism background (dark theme)
      int bgTop = Renderer2D.ColorUtil.rgba(20, 20, 25, (int)(160.0F * mainAlpha));
      int bgBottom = Renderer2D.ColorUtil.rgba(12, 12, 16, (int)(180.0F * mainAlpha));
      renderer2D.verticalGradient(x, y, w, h, rounding, bgTop, bgBottom);

      // Super premium glowing outline (gradient outline)
      int outlineTop = Renderer2D.ColorUtil.rgba(255, 255, 255, (int)(50.0F * mainAlpha));
      int outlineBottom = Renderer2D.ColorUtil.rgba(255, 255, 255, (int)(10.0F * mainAlpha));
      renderer2D.verticalGradient(x, y, w, 1.0F, outlineTop, outlineTop); // top edge
      renderer2D.verticalGradient(x, y + h - 1.0F, w, 1.0F, outlineBottom, outlineBottom); // bottom edge
      renderer2D.verticalGradient(x, y, 1.0F, h, outlineTop, outlineBottom); // left edge
      renderer2D.verticalGradient(x + w - 1.0F, y, 1.0F, h, outlineTop, outlineBottom); // right edge
   }
}
