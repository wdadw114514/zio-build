/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.utils.math.MathConstants;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.zio.client.modules.render.AnimationsModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.render.EntityRenderStateAccess;
import dev.zio.client.events.NameTagRenderEvent;
import dev.zio.client.render.ClientRenderTypes;
import dev.zio.client.modules.movement.ElytraFlyModule;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin extends LivingEntityRenderer {
   @Unique
   AbstractClientPlayer cachedScalingPlayer;

   public AvatarRendererMixin(Context context, PlayerModel playerModel, float shadowRadius) {
      super(context, playerModel, shadowRadius);
   }

   @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
   private void applyRemotePlayerSneakOffset(
      AvatarRenderState renderState, CallbackInfoReturnable<Vec3> callback
   ) {
      if (ClientContext.getModule(AnimationsModule.class).isSneakingAnimationEnabled()
         && !ClientAccess.isLocalPlayer(
            EntityRenderStateAccess.getEntity(renderState)
         )) {
         callback.setReturnValue(new Vec3(0.0, renderState.scale * -2.0F / 16.0, 0.0));
      }
   }

   @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
   private void captureScalingPlayer(
      AvatarRenderState renderState, PoseStack poses, CallbackInfo callback
   ) {
      if (EntityRenderStateAccess.getEntity(renderState) instanceof AbstractClientPlayer scalingPlayer) {
         this.cachedScalingPlayer = scalingPlayer;
      }
   }

   @ModifyArgs(method = "scale", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
   private void applyPlayerScaleAndSneakState(
      Args arguments, @Local(argsOnly = true) AvatarRenderState renderState
   ) {
      if (ClientContext.getModule(AnimationsModule.class).isToggled()
         && ClientContext.getModule(AnimationsModule.class).isPlayerScalingEnabled()
         && this.cachedScalingPlayer != Minecraft.getInstance().player) {
         arguments.set(
            0,
            arguments.<Float>get(0)
               * ClientContext.getModule(AnimationsModule.class).playerScaleSetting.getValue()
         );
         arguments.set(
            1,
            arguments.<Float>get(1)
               * ClientContext.getModule(AnimationsModule.class).playerScaleSetting.getValue()
         );
         arguments.set(
            2,
            arguments.<Float>get(2)
               * ClientContext.getModule(AnimationsModule.class).playerScaleSetting.getValue()
         );
      }

      if (ClientContext.getModule(AnimationsModule.class).isSneakingAnimationEnabled()
         && !ClientAccess.isLocalPlayer(
            this.cachedScalingPlayer
         )) {
         renderState.isCrouching = true;
         renderState.isDiscrete = true;
      }
   }

   @ModifyArgs(method = "setupRotations", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
   private void applySilentElytraPitch(Args arguments) {
      if (ClientContext.getModule(ElytraFlyModule.class).isToggled()) {
         if (ClientContext.getModule(ElytraFlyModule.class).modeSetting.getValue()
               == ElytraFlyModule.ModeChoice.BOUNCE
            && ClientContext.getModule(ElytraFlyModule.class).canStartFlightFromJump()
            && ClientContext.getModule(ElytraFlyModule.class).pitchLockSetting.getValue()
            && ClientContext.getModule(ElytraFlyModule.class).silentSetting.getValue()) {
            arguments.set(
               0,
               Axis.XP
                  .rotationDegrees(-MathConstants.FORTY_FIVE_DEGREES)
            );
         }
      }
   }

   @ModifyExpressionValue(
      method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityTranslucent(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
   )
   private RenderType applyAvatarHandShaderType(
      RenderType originalType, @Local(argsOnly = true) Identifier texture
   ) {
      return ClientContext.getModule(NoRenderModule.class).isToggled()
            && ClientContext.getModule(NoRenderModule.class).handsSetting.getValue()
            && ClientContext.shaderRegistry.renderingIntoTarget
         ? RenderTypes.entitySolid(texture)
         : originalType;
   }

   @ModifyExpressionValue(
      method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityTranslucent(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
   )
   private RenderType applyAvatarHandOpacity(
      RenderType originalType, @Local(argsOnly = true) Identifier texture
   ) {
      return ClientContext.getModule(NoRenderModule.class).getHandOpacity()
            != 1.0F
         ? ClientRenderTypes.entitySolid(texture)
         : originalType;
   }

   @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
   private void onAvatarNameTag(
      AvatarRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      if (ClientContext.shaderRegistry.renderingIntoTarget) {
         callback.cancel();
         return;
      }

      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      NameTagRenderEvent event = new NameTagRenderEvent(poses, entity);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Override
   protected float getShadowRadius(EntityRenderState renderState) {
      return super.getShadowRadius((LivingEntityRenderState)renderState);
   }
}
