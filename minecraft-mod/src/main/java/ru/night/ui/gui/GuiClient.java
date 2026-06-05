package ru.night.ui.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import ru.night.Night;
import ru.night.config.GuiManager;
import ru.night.event.EventInit;
import ru.night.event.EventManager;
import ru.night.event.render.RenderEvent;
import ru.night.module.api.Category;
import ru.night.module.api.Manager;
import ru.night.module.api.Theme;
import ru.night.ui.gui.component.main.GuiCharTyped;
import ru.night.ui.gui.component.main.GuiInit;
import ru.night.ui.gui.component.main.GuiKeyPressed;
import ru.night.ui.gui.component.main.GuiMouseDragged;
import ru.night.ui.gui.component.main.GuiMouseReleased;
import ru.night.ui.gui.component.main.GuiMouseScrolled;
import ru.night.ui.gui.component.main.GuiShouldCloseOnEsc;
import ru.night.ui.gui.component.mouse.GuiMouseClicked;
import ru.night.ui.gui.component.render.GuiRender;
import ru.night.ui.gui.theme.ThemeScreen;
import ru.night.util.player.MovementManager;
import ru.night.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class GuiClient extends Screen {
   public ThemeScreen themeScreen;
   public MinecraftClient mc = MinecraftClient.getInstance();
   private static volatile boolean eventsRegistered = false;
   
   // Debug: ограничение спама в логах
   private int lowAlphaLogCount = 0;
   private static final int MAX_LOW_ALPHA_LOGS = 5;

   public GuiClient() {
      super(Text.literal("Gui"));
   }

   public static void registerEventHandlers() {
      if (!eventsRegistered) {
         eventsRegistered = true;
         EventManager.register(new Object() {
            @EventInit
            public void onRender(RenderEvent event) {
               MinecraftClient client = event.client();
               if (client != null && client.currentScreen instanceof GuiClient) {
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
         });
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.currentScreen instanceof GuiClient && client.getWindow() != null) {
          ru.night.util.render.core.Renderer2D renderer = Night.getRenderer();
          if (renderer != null) {
              int width = client.getWindow().getFramebufferWidth();
              int height = client.getWindow().getFramebufferHeight();
              
              renderer.begin(width, height);
              try {
                  float alpha = GuiScreen.alphaPC.get();
                  if (alpha < 0.01f) {
                      if (lowAlphaLogCount < MAX_LOW_ALPHA_LOGS) {
                          System.out.println("[GuiClient] alpha low: " + alpha 
                              + " exit=" + GuiScreen.exit 
                              + " toValue=" + GuiScreen.alphaPC.getToValue()
                              + " alive=" + GuiScreen.alphaPC.isAlive());
                          lowAlphaLogCount++;
                      }
                  } else {
                      // Сбрасываем счётчик когда alpha нормальная
                      lowAlphaLogCount = 0;
                  }
                  GuiRender.render(renderer, context, mouseX, mouseY, deltaTicks);
              } catch (Exception e) {
                  e.printStackTrace();
              } finally {
                  renderer.end();
              }
          }
      }
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   public void renderInGameBackground(DrawContext context) {
   }

   public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
      Renderer2D renderer = Night.getRenderer();
      return renderer != null && GuiMouseClicked.mouseClicked(renderer, pMouseX, pMouseY, pButton) ? true : super.mouseClicked(pMouseX, pMouseY, pButton);
   }

   public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
      GuiMouseReleased.mouseReleased();
      return super.mouseReleased(pMouseX, pMouseY, pButton);
   }

   public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
      return GuiMouseDragged.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY) ? true : super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
   }

   public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
      return GuiMouseScrolled.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY) ? true : super.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      // Логируем КАЖДОЕ нажатие клавиши чтобы понять кто вызывает close
      if (keyCode == 256) {
         System.out.println("[GuiClient] ESC keyPressed! exit=" + GuiScreen.exit 
             + " alpha=" + GuiScreen.alphaPC.getValue()
             + " timeSinceOpen=" + (System.currentTimeMillis() - GuiScreen.openTimestamp) + "ms");
      }
      return GuiKeyPressed.keyPressed(keyCode, scanCode, modifiers) ? true : super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean charTyped(char codePoint, int modifiers) {
      return GuiCharTyped.charTyped(codePoint, modifiers) ? true : super.charTyped(codePoint, modifiers);
   }

   public boolean shouldCloseOnEsc() {
      return GuiShouldCloseOnEsc.shouldCloseOnEsc();
   }

   public void close() {
      System.out.println("[GuiClient] close() called! exit=" + GuiScreen.exit + " alpha=" + GuiScreen.alphaPC.getValue());
      MovementManager.getInstance().unlockMovement("Search");
      GuiScreen.activeSearch = false;
      GuiScreen.searchText = "";
      Night.get.guiManager.setGuiCategory(GuiScreen.selectedCategories);
      super.close();
   }

   public void tick() {
      super.tick();
      // Обновляем анимацию в tick — это гарантирует прогресс даже без render
      GuiScreen.alphaPC.update();
      
      if (GuiScreen.exit && GuiScreen.alphaPC.isFinished()) {
         System.out.println("[GuiClient] tick() closing — exit animation finished");
         this.close();
         GuiScreen.exit = false;
      }
   }

   public boolean shouldPause() {
      return false;
   }

   public void init() {
      super.init();
      this.themeScreen = new ThemeScreen();
      this.lowAlphaLogCount = 0; // Сброс лог-счётчика при открытии
      GuiInit.init();
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.mouse != null) {
         client.mouse.unlockCursor();
      }

      GuiScreen.categories = Category.values();
      GuiScreen.themes = Theme.values();
      GuiScreen.width = 366.475F;
      GuiScreen.height = 238.805F;
      GuiScreen.x = 480.0F - GuiScreen.width / 2.0F;
      GuiScreen.y = 260.0F - GuiScreen.height / 2.0F;
      GuiScreen.mainAnimation.reset();
      if (Night.get.guiManager == null) {
         Night.get.guiManager = new GuiManager();
         Night.get.guiManager.init();
      }

      GuiScreen.selectedTheme = Night.get.guiManager.getCurrentTheme();
      GuiScreen.preSelectedTheme = Night.get.guiManager.getCurrentTheme();
      GuiScreen.selectedCategories = Night.get.guiManager.getCurrentCategory();
      if (Night.get.manager == null) {
         Night.get.manager = new Manager();
      }

      GuiScreen.modules = Night.get.manager.getType(GuiScreen.selectedCategories);
   }
}
