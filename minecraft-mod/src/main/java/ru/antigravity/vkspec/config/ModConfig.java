package ru.antigravity.vkspec.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "FunSpecMod.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public boolean modEnabled = true;
    public int serverPort = 23588;
    public boolean enableSound = true;
    public int notificationDurationTicks = 200; // 10 секунд (20 тиков = 1 секунда)
    
    // Авто-спек: автоматически заходить на сервер и писать /spec
    public boolean autoSpec = true;
    
    // Задержка перед /spec после перехода на сервер (в тиках, 60 = 3 секунды)
    public int autoSpecDelayTicks = 60;
    
    // Авто ключ
    public boolean autoKey = false;
    public String autoKeyValue = "";
    
    // Новые фичи
    public float hudX = -1.0f; // -1 means default
    public float hudY = 14.0f;
    public float hudScale = 1.0f;
    public float dockX = -1.0f;
    public float dockY = -1.0f;
    public float dockScale = 1.0f;
    public boolean showNotificationsHUD = true;
    public boolean showStatusDock = true;
    public boolean smartSpec = true;
    public boolean showReasonInDock = true;
    public int themeIndex = 0;
    
    public String notificationSound = "entity.experience_orb.pickup";
    public int maxNotificationsOnScreen = 3;
    
    // HUD функции
    public boolean showTimeHUD = true;
    public boolean showSpecHUD = true;
    public boolean showProgressBar = true;
    public int notificationDurationSeconds = 10;
    
    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) return config;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }
    
    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
