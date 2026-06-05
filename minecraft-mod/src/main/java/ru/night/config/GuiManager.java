package ru.night.config;

import ru.night.module.api.Category;
import ru.night.module.api.Theme;

public class GuiManager {
    private Category currentCategory = Category.General;
    private Theme currentTheme = Theme.THEME1;
    public void init() {}
    public Theme getCurrentTheme() { return currentTheme; }
    public void setGuiTheme(Theme theme) { this.currentTheme = theme; }
    public Category getCurrentCategory() { return currentCategory; }
    public void setGuiCategory(Category category) { this.currentCategory = category; }
}
