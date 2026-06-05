package ru.night.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.client.util.math.MatrixStack;
import ru.night.event.Event;

@Environment(EnvType.CLIENT)
public class RenderItemEvent extends Event {
   private final MatrixStack matrix;
   private final Hand hand;

   public RenderItemEvent(MatrixStack matrix, Hand hand) {
      this.matrix = matrix;
      this.hand = hand;
   }

   public MatrixStack getMatrix() {
      return this.matrix;
   }

   public Hand getHand() {
      return this.hand;
   }

   public boolean isRightHand() {
      return this.hand == Hand.MAIN_HAND;
   }
}
