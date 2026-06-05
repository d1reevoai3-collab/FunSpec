package ru.night.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.night.module.api.setting.Setting;

@Environment(EnvType.CLIENT)
public class SliderSetting extends Setting {
   public float current;
   public float minimum;
   public float maximum;
   public float increment;
   public float sliderWidth;
   public boolean sliding;
   public boolean percent;
   public boolean onlyInt;
   public String description;

   public SliderSetting(String name, float current, float minimum, float maximum, float increment, boolean percent) {
      this.name = name;
      this.minimum = minimum;
      this.current = current;
      this.maximum = maximum;
      this.increment = increment;
      this.description = this.description;
      this.percent = percent;
      this.onlyInt = false;
   }

   public SliderSetting onlyInt() {
      this.onlyInt = true;
      return this;
   }

   public float get() {
      return this.current;
   }
   
   public void onChange(float newValue) {
       // override me
   }

   public SliderSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }
}
