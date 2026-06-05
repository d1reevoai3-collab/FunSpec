package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Умный спек", description = "Умный спек", category = Category.Automation, bind = 0)
public class SmartReconModule extends Module {
    public SmartReconModule() {
        this.enable = FunSpecMod.getInstance().getConfig().smartSpec;
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().smartSpec = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().smartSpec = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
