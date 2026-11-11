/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.SkyboxModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressBossBars(GuiGraphics graphics, CallbackInfo callback) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
         && ClientContext.getModule(NoRenderModule.class).bossBarsSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
   private void suppressBossBarDarkening(CallbackInfoReturnable<Boolean> callback) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
            && ClientContext.getModule(NoRenderModule.class).bossBarsSetting.getValue()
         || ClientContext.getModule(SkyboxModule.class).isToggled()
            && ClientContext.getModule(SkyboxModule.class).isCurrentDimensionEnabled()) {
         callback.setReturnValue(false);
         callback.cancel();
      }
   }
}
