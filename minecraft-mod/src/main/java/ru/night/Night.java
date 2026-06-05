package ru.night;

import ru.night.module.api.Manager;
import ru.night.ui.gui.GuiClient;
import ru.night.util.render.core.Renderer2D;
import ru.night.util.render.backends.gl.GlBackend;
import ru.night.util.render.text.FontRegistry;
import ru.night.config.GuiManager;
import ru.night.config.ConfigManager;

public class Night {
    public static Night get = new Night();
    public Manager manager = new Manager();
    public GuiClient guiClient = new GuiClient();
    public GuiManager guiManager = new GuiManager();
    public ConfigManager configManager = new ConfigManager();
    private static Renderer2D renderer;

    public Night() {
        get = this;
    }

    public static Renderer2D getRenderer() {
        if (renderer == null) {
            System.out.println("[FunSpec] ========================================");
            System.out.println("[FunSpec] INITIALIZING RENDERER (Shaders & Fonts)");
            System.out.println("[FunSpec] ========================================");
            GlBackend backend = new GlBackend();
            renderer = new Renderer2D(backend);
            FontRegistry.initialize(backend, renderer);
            backend.warmupShaders();
            System.out.println("[FunSpec] RENDERER INITIALIZATION COMPLETE");
        }
        return renderer;
    }

    public static boolean isRendererInitialized() {
        return renderer != null;
    }

    public static void ensureRendererInitialized() {
        getRenderer();
    }
}
