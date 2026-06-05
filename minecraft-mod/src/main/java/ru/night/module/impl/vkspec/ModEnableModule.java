package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.SliderSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Включить мод", description = "Главный переключатель мода", category = Category.General, bind = 0)
public class ModEnableModule extends Module {
    public ModEnableModule() {
        this.enable = FunSpecMod.getInstance().getConfig().modEnabled;
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().modEnabled = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().modEnabled = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
