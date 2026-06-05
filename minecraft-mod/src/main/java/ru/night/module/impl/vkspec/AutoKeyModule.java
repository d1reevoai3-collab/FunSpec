package ru.night.module.impl.vkspec;

import ru.night.module.api.Category;
import ru.night.module.api.IModule;
import ru.night.module.api.Module;
import ru.night.module.api.setting.impl.StringSetting;
import ru.antigravity.vkspec.FunSpecMod;

@IModule(name = "Авто ключ", description = "Автоматическое сохранение ключа", category = Category.Automation, bind = 0)
public class AutoKeyModule extends Module {
    public StringSetting keyValue = new StringSetting("Ключ", FunSpecMod.getInstance().getConfig().autoKeyValue) {
        @Override
        public void set(String input) {
            super.set(input);
            FunSpecMod.getInstance().getConfig().autoKeyValue = input;
            FunSpecMod.getInstance().getConfig().save();
        }
    };

    public AutoKeyModule() {
        this.enable = FunSpecMod.getInstance().getConfig().autoKey;
        this.addSettings(keyValue);
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        FunSpecMod.getInstance().getConfig().autoKey = true;
        FunSpecMod.getInstance().getConfig().save();
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        FunSpecMod.getInstance().getConfig().autoKey = false;
        FunSpecMod.getInstance().getConfig().save();
    }
}
