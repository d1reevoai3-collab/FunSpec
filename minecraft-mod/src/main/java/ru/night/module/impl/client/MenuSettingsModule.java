package ru.night.module.impl.client;

public class MenuSettingsModule {
    public int bind = -1;
    public static MenuSettingsModule getInstanceIfAvailable() {
        return new MenuSettingsModule();
    }
    public double getMenuScaleValue() {
        return 1.0;
    }
}
