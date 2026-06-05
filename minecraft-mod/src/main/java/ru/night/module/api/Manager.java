package ru.night.module.api;

import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.Night;

@Environment(EnvType.CLIENT)
public class Manager {
   public ArrayList<Module> module = new ArrayList<>();

   public Manager() {
      this.module.add(new ru.night.module.impl.vkspec.ModEnableModule());
      this.module.add(new ru.night.module.impl.vkspec.SoundNotifModule());
      this.module.add(new ru.night.module.impl.vkspec.NotificationsHUDModule());
      this.module.add(new ru.night.module.impl.vkspec.StatusDockModule());
      this.module.add(new ru.night.module.impl.vkspec.TimeSpecHUDModule());
      this.module.add(new ru.night.module.impl.vkspec.ProgressBarModule());
      this.module.add(new ru.night.module.impl.vkspec.AutoReconModule());
      this.module.add(new ru.night.module.impl.vkspec.SmartReconModule());
      this.module.add(new ru.night.module.impl.vkspec.AutoKeyModule());
   }

   public ArrayList<Module> getModules() {
      return this.module;
   }

   public <T extends Module> T get(Class<T> clazz) {
      return this.module.stream().filter(module -> clazz.isAssignableFrom(module.getClass())).map(clazz::cast).findFirst().orElse(null);
   }

   public Module getModule(Class<?> class1) {
      for (Module module1 : this.module) {
         if (module1.getClass() == class1) {
            return module1;
         }
      }
      return null;
   }

   public ArrayList<Module> getType(Category category) {
      ArrayList<Module> modules = new ArrayList<>();
      for (Module module1 : this.module) {
         if (module1.category == category) {
            modules.add(module1);
         }
      }
      return modules;
   }

   public Module[] getBind(int bind) {
      return Night.get.manager.module.stream().filter(module -> module.bind == bind).toArray(Module[]::new);
   }
}
