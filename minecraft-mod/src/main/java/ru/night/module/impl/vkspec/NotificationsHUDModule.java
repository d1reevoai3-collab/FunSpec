package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.SliderSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Уведомления HUD", description = "Уведомления на экране", category = Category.HUD, bind = 0)
public class NotificationsHUDModule extends Module {
    public SliderSetting maxNotif = new SliderSetting("Макс. уведомлений", FunSpecMod.getInstance().getConfig().maxNotificationsOnScreen, 1, 10, 1, false) {
        @Override
        public void onChange(float newValue) {
            super.onChange(newValue);
            FunSpecMod.getInstance().getConfig().maxNotificationsOnScreen = (int)newValue;
            FunSpecMod.getInstance().getConfig().save();
        }
    };

    public NotificationsHUDModule() {
        this.enable = FunSpecMod.getInstance().getConfig().showNotificationsHUD;
        maxNotif.onlyInt = true;
        this.addSettings(maxNotif);
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().showNotificationsHUD = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().showNotificationsHUD = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
