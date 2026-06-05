package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Прогресс-бар", description = "Индикатор прогресса", category = Category.HUD, bind = 0)
public class ProgressBarModule extends Module {
    public ProgressBarModule() {
        this.enable = FunSpecMod.getInstance().getConfig().showProgressBar;
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().showProgressBar = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().showProgressBar = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
