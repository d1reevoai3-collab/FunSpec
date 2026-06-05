package ru.night.ui.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.event.EventInit;
import ru.night.event.input.KeyInputEvent;

@Environment(EnvType.CLIENT)
public class GuiOpenHandler {
   @EventInit
   public void onKeyInput(KeyInputEvent event) {
   }
}
