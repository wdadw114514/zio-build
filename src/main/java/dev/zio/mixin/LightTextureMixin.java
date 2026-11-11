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
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.zio.client.modules.render.AmbienceModule;
import dev.zio.client.ClientContext;
import dev.zio.client.render.ClientRenderPipelines;
import java.awt.Color;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LightTexture.class)
public class LightTextureMixin {
   @Unique
   private static final Vector3f lightmapTint = new Vector3f();

   @ModifyExpressionValue(
      method = "updateLightTexture",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderPipelines;LIGHTMAP:Lcom/mojang/blaze3d/pipeline/RenderPipeline;")
   )
   private RenderPipeline overrideLightmapPipeline(RenderPipeline originalPipeline) {
      if (ClientContext.getModule(AmbienceModule.class).isToggled()
         && ClientContext.getModule(AmbienceModule.class).brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN) {
         return ClientRenderPipelines.AMBIENCE;
      } else {
         return ClientContext.getModule(AmbienceModule.class).isToggled()
               && ClientContext.getModule(AmbienceModule.class).occlusionSetting.getValue()
               && ClientContext.getModule(AmbienceModule.class).occlusionSetting
                  .isVisible()
            ? ClientRenderPipelines.OCCLUSION
            : originalPipeline;
      }
   }

   @ModifyArgs(
      method = "updateLightTexture",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;",
         ordinal = 1
      )
   )
   private void overrideLightmapTint(Args arguments) {
      boolean tintEnabled = ClientContext.getModule(AmbienceModule.class).brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN
         || ClientContext.getModule(AmbienceModule.class).occlusionSetting.getValue()
            && ClientContext.getModule(AmbienceModule.class).occlusionSetting
               .isVisible();
      Color tintColor = ClientContext.getModule(AmbienceModule.class).brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN
         ? ClientContext.getModule(AmbienceModule.class).colorSetting.getValue()
         : ClientContext.getModule(AmbienceModule.class).occlusionColorSetting.getValue();
      if (ClientContext.getModule(AmbienceModule.class).isToggled() && tintEnabled) {
         arguments.set(
            0,
            lightmapTint.set(
                  tintColor.getRed(), tintColor.getGreen(), tintColor.getBlue()
               )
               .div(255.0F)
         );
      }
   }

   @ModifyReturnValue(method = "pack", at = @At("RETURN"))
   private static int overridePackedLight(int originalLight) {
      if (ClientContext.getModule(AmbienceModule.class).isToggled()
         && ClientContext.getModule(AmbienceModule.class).brightnessSetting.getValue()
            == AmbienceModule.BrightnessChoice.SCREEN) {
         return 0;
      } else {
         return ClientContext.getModule(AmbienceModule.class).isToggled()
               && ClientContext.getModule(AmbienceModule.class).occlusionSetting
                  .isVisible()
               && ClientContext.getModule(AmbienceModule.class).occlusionSetting.getValue()
               && originalLight == 15728880
            ? 15728864
            : originalLight;
      }
   }
}
