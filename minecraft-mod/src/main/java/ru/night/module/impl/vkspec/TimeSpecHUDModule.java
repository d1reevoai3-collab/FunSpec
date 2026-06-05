package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.BooleanSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Время и спек", description = "Время и ник в спеке", category = Category.HUD, bind = 0)
public class TimeSpecHUDModule extends Module {
    public BooleanSetting showTime = new BooleanSetting("Показывать время", FunSpecMod.getInstance().getConfig().showTimeHUD) {
        @Override
        public void set(boolean state) {
            super.set(state);
            FunSpecMod.getInstance().getConfig().showTimeHUD = state;
            FunSpecMod.getInstance().getConfig().save();
        }
    };
    
    public BooleanSetting showSpec = new BooleanSetting("Показывать спек", FunSpecMod.getInstance().getConfig().showSpecHUD) {
        @Override
        public void set(boolean state) {
            super.set(state);
            FunSpecMod.getInstance().getConfig().showSpecHUD = state;
            FunSpecMod.getInstance().getConfig().save();
        }
    };

    public TimeSpecHUDModule() {
        this.enable = true; // Always enabled, uses settings inside
        this.addSettings(showTime, showSpec);
    }
}
