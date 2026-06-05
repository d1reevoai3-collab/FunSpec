package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.SliderSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Авто-спек", description = "Автоматический /spec при заходе", category = Category.Automation, bind = 0)
public class AutoReconModule extends Module {
    public SliderSetting delay = new SliderSetting("Задержка перед /spec", FunSpecMod.getInstance().getConfig().autoSpecDelayTicks, 0, 200, 1, false) {
        @Override
        public void onChange(float newValue) {
            super.onChange(newValue);
            FunSpecMod.getInstance().getConfig().autoSpecDelayTicks = (int)newValue;
            FunSpecMod.getInstance().getConfig().save();
        }
    };

    public AutoReconModule() {
        this.enable = FunSpecMod.getInstance().getConfig().autoSpec;
        delay.onlyInt = true;
        this.addSettings(delay);
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().autoSpec = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().autoSpec = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
