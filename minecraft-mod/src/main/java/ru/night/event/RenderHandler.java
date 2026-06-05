package ru.night.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import ru.night.event.render.RenderEvent;
import ru.night.ui.gui.GuiClient;
import ru.night.ui.gui.component.render.GuiRender;

@Environment(EnvType.CLIENT)
public final class RenderHandler {
   private static volatile boolean registered = false;

   private RenderHandler() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         EventManager.register(new Object() {
            @EventInit
            public void onRender(RenderEvent event) {
               MinecraftClient client = event.client();
               if (client != null) {
                  if (client.currentScreen instanceof GuiClient) {
                     double[] mouseX = new double[1];
                     double[] mouseY = new double[1];
                     if (client.getWindow() != null) {
                        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), mouseX, mouseY);
                        if (client.mouse != null) {
                           client.mouse.unlockCursor();
                        }
                     }

                     int mouseXInt = (int)mouseX[0];
                     int mouseYInt = (int)mouseY[0];
                     DrawContext drawContext = null;
                     GuiRender.render(event.renderer(), drawContext, mouseXInt, mouseYInt, 1.0f);
                  }
               }
            }
         });
      }
   }
}
