/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.SkyboxModule;
import java.awt.Color;
import java.nio.ByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   @Unique
   private SkyboxModule.FogDistances capturedFogDistances;

   @Inject(method = "updateBuffer", at = @At("HEAD"))
   private void captureFogDistances(
      ByteBuffer buffer, int bufferOffset, Vector4f fogColor, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudsEnd, CallbackInfo callback
   ) {
      this.capturedFogDistances = new SkyboxModule.FogDistances(
         environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd, skyEnd, cloudsEnd
      );
   }

   @Inject(
      method = "setupFog",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;",
         shift = Shift.BEFORE,
         remap = false
      )
   )
   private void applyNoRenderFogSettings(
      Camera camera, int renderDistance, DeltaTracker deltaTracker, float ignoredSetupFloat, ClientLevel level, CallbackInfoReturnable<Vector4f> callback, @Local Vector4f fogColor, @Local FogData fogData
   ) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
         && ClientContext.getModule(NoRenderModule.class).fogSetting.getValue()) {
         ClientContext.getModule(NoRenderModule.class).applyFogSettings(
            fogData, renderDistance
         );
      }

      if (ClientContext.getModule(SkyboxModule.class).isToggled()
         && ClientContext.getModule(SkyboxModule.class).isCurrentDimensionEnabled()) {
         Color configuredColor = ClientContext.getModule(SkyboxModule.class).fogSetting.getValue();
         float red = configuredColor.getRed() / 255.0F;
         float green = configuredColor.getGreen() / 255.0F;
         float blue = configuredColor.getBlue() / 255.0F;
         float alpha = configuredColor.getAlpha() / 255.0F;
         fogColor.set(red, green, blue, alpha);
      }
   }

   @Inject(method = "setupFog", at = @At("TAIL"))
   private void updateSplitSkyFogBuffer(
      Camera camera, int renderDistance, DeltaTracker deltaTracker, float ignoredSetupFloat, ClientLevel level, CallbackInfoReturnable<Vector4f> callback
   ) {
      if (ClientContext.getModule(SkyboxModule.class).isToggled()
         && ClientContext.getModule(SkyboxModule.class).splitFogSetting.getValue()
         && this.capturedFogDistances != null) {
         Color backgroundColor = ClientContext.getModule(SkyboxModule.class).backgroundSetting.getValue();
         Vector4f skyColor = new Vector4f(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), backgroundColor.getAlpha()).div(255.0F);

         try (MappedView mappedBuffer = RenderSystem.getDevice()
               .createCommandEncoder()
               .mapBuffer(
                  ClientContext.getModule(SkyboxModule.class).getSkyFogBuffer()
                     .currentBuffer(),
                  false,
                  true
               )) {
            this.updateBuffer(
               mappedBuffer.data(),
               0,
               skyColor,
               this.capturedFogDistances
                  .environmentalStart(),
               this.capturedFogDistances
                  .environmentalEnd(),
               this.capturedFogDistances
                  .renderDistanceStart(),
               this.capturedFogDistances
                  .renderDistanceEnd(),
               this.capturedFogDistances
                  .skyEnd(),
               this.capturedFogDistances
                  .cloudsEnd()
            );
         }
      }
   }

   @Shadow
   protected abstract void updateBuffer(ByteBuffer buffer, int bufferOffset, Vector4f fogColor, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudsEnd);
}
