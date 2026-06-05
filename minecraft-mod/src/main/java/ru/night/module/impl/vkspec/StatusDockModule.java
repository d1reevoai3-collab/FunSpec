package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.BooleanSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Статус док", description = "Док-статус на экране", category = Category.HUD, bind = 0)
public class StatusDockModule extends Module {
    public BooleanSetting showReason = new BooleanSetting("Показывать причину", FunSpecMod.getInstance().getConfig().showReasonInDock) {
        @Override
        public void set(boolean state) {
            super.set(state);
            FunSpecMod.getInstance().getConfig().showReasonInDock = state;
            FunSpecMod.getInstance().getConfig().save();
        }
    };

    public StatusDockModule() {
        this.enable = FunSpecMod.getInstance().getConfig().showStatusDock;
        this.addSettings(showReason);
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().showStatusDock = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().showStatusDock = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
