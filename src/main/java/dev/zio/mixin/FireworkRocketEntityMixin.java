/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.math.MathUtils;
import dev.zio.client.utils.entity.PlayerStateUtils;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.events.FireworkRocketEntityTickEvent;
import dev.zio.client.modules.movement.FireworksModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin extends Entity {
   @Shadow
   @Nullable
   private LivingEntity attachedToEntity;

   public FireworkRocketEntityMixin(EntityType entityType, Level level) {
      super(entityType, level);
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void beginRocketTick(CallbackInfo callback) {
      ElytraFlyModule.hook = true;
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void endRocketTick(CallbackInfo callback) {
      ElytraFlyModule.hook = false;
   }

   @Inject(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", shift = Shift.BEFORE),
      cancellable = true
   )
   private void cancelGrimRocketMovement(CallbackInfo callback) {
      if (ClientContext.getModule(FireworksModule.class).isGrimBoostEnabled()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "tick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", shift = Shift.AFTER),
      cancellable = true
   )
   private void applyAdditionalBoost(
      CallbackInfo callback, @Local(ordinal = 0) Vec3 lookDirection
   ) {
      if (ClientContext.getModule(FireworksModule.class).resetRocketAge) {
         this.tickCount = 1;
         ClientContext.getModule(FireworksModule.class).resetRocketAge = false;
      }
      if (ClientContext.getModule(FireworksModule.class).isGrimBoostEnabled()) {
         callback.cancel();
         return;
      }
      float additionalBoost = ClientContext.getModule(FireworksModule.class).getAdditionalBoost(true);
      if (this.attachedToEntity != Minecraft.getInstance().player || additionalBoost == 0.0F) {
         return;
      }
      float yawRadians = MathUtils.toRadians(this.attachedToEntity.getYRot());
      if (additionalBoost > 0.5) {
         additionalBoost = Mth.clamp(0.2F + 0.3F * this.tickCount, 0.0F, additionalBoost);
      }
      if (!ClientContext.getModule(FireworksModule.class).isFastLaunchActive() && !ClientContext.getModule(FireworksModule.class).directionalSetting.getValue()) {
         this.attachedToEntity.push(Mth.sin(yawRadians) * -additionalBoost, 0.0, Mth.cos(yawRadians) * additionalBoost);
      } else {
         this.attachedToEntity.push(lookDirection.scale(additionalBoost));
      }
   }

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
   private boolean useActualFlightState(boolean original) {
      ElytraFlyModule.InfDurabilityChoice durability = ClientContext.getModule(ElytraFlyModule.class).infDurabilitySetting.getValue();
      if (!durability.isDisabled() && ClientAccess.isLocalPlayer(this.attachedToEntity)) {
         return PlayerStateUtils.isFallFlying();
      }
      return original;
   }

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
   private Vec3 modifyRocketLookDirection(Vec3 originalLook) {
      FireworkRocketEntityTickEvent rotationEvent = new FireworkRocketEntityTickEvent(this.attachedToEntity.getYRot(), this.attachedToEntity.getXRot());
      ClientAccess.eventBus
         .post(rotationEvent);
      return rotationEvent.isCancelled()
         ? this.attachedToEntity
            .calculateViewVector(
               rotationEvent.getPitch(),
               rotationEvent.getYaw()
            )
         : originalLook;
   }

}
