/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.zio.client.ClientContext;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.modules.render.SkyboxModule;
import java.awt.Color;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {

   @ModifyReturnValue(method = "getBaseColor", at = @At("RETURN"))
   private int blendConfiguredFogColor(int originalColor) {
      if (ClientContext.getModule(SkyboxModule.class).isToggled()
         && ClientContext.getModule(SkyboxModule.class).isCurrentDimensionEnabled()) {
         Color configuredColor = ClientContext.getModule(SkyboxModule.class).backgroundSetting.getValue();
         Color baseColor = new Color(originalColor, true);
         return ColorUtils.blend(
               configuredColor, baseColor, configuredColor.getAlpha() / 255.0F
            )
            .hashCode();
      } else {
         return originalColor;
      }
   }
}
