package ru.night.module.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.util.render.animation.util.Animation;

@Environment(EnvType.CLIENT)
public enum Category {
   General("Основные", "a"),
   HUD("Интерфейс", "e"),
   Automation("Автоматизация", "c"),
   Tickets("Заявки", "d"),
   History("История", "f"),
   Logs("Логи", "b");

   private final String name;
   private final String icon;
   public Animation anim33 = new Animation();
   public Animation anim44 = new Animation();

   private Category(String name, String icon) {
      this.name = name;
      this.icon = icon;
   }

   public String getIcon() {
      return this.icon;
   }

   public String getName() {
      return this.name;
   }
}
