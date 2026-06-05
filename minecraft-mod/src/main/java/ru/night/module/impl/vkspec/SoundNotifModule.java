package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.ModeSetting;
import ru.night.module.api.setting.impl.SliderSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Звук уведомлений", description = "Звук и длительность уведомлений", category = Category.General, bind = 0)
public class SoundNotifModule extends Module {
    public ModeSetting soundType = new ModeSetting("Звук", "Стандартный", "Стандартный", "Пинг", "Уровень");

    public SliderSetting notifDuration = new SliderSetting("Длительность (сек)", FunSpecMod.getInstance().getConfig().notificationDurationSeconds, 1, 30, 1, false) {
        @Override
        public void onChange(float newValue) {
            super.onChange(newValue);
            FunSpecMod.getInstance().getConfig().notificationDurationSeconds = (int)newValue;
            FunSpecMod.getInstance().getConfig().notificationDurationTicks = (int)newValue * 20;
            FunSpecMod.getInstance().getConfig().save();
        }
    };

    public SoundNotifModule() {
        this.enable = FunSpecMod.getInstance().getConfig().enableSound;
        notifDuration.onlyInt = true;
        
        String currentSound = FunSpecMod.getInstance().getConfig().notificationSound;
        if (currentSound.equals("entity.arrow.hit_player")) soundType.currentMode = "Пинг";
        else if (currentSound.equals("entity.player.levelup")) soundType.currentMode = "Уровень";
        else soundType.currentMode = "Стандартный";
        
        this.addSettings(soundType, notifDuration);
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().enableSound = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().enableSound = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
