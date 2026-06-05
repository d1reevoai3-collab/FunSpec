package ru.night.ui.gui.widget.settings;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.module.api.Module;
import ru.night.module.api.setting.Setting;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface PopupOpenContext {
   void openForSetting(Module var1, Setting var2, double var3, double var5, Object var7);
}
