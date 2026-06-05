package ru.antigravity.vkspec.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.antigravity.vkspec.FunSpecMod;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    
    // Внедряемся в самое начало метода, который отвечает за нажатие кнопки "Взять блок" (СКМ)
    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void onDoItemPick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        
        // Если наша кнопка (СКМ) назначена на копирование ника, и она нажата
        if (FunSpecMod.getCopyNickKeyBinding() != null && FunSpecMod.getCopyNickKeyBinding().isPressed()) {
            
            // Если мы сейчас кого-то спекаем по заявке, ИЛИ смотрим на игрока
            if (FunSpecMod.getInstance().getCurrentlySpectating() != null || 
                (client.targetedEntity != null && client.targetedEntity instanceof PlayerEntity)) {
                
                // ОТМЕНЯЕМ ванильное действие (чтобы не открывалось меню спектатора)
                ci.cancel();
            }
        }
    }
}
