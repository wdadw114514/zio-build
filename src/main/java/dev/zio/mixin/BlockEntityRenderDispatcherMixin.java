/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.ESPModule;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

   @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
   private void suppressBlockEntitySubmission(
      BlockEntityRenderState renderState, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo callback
   ) {
      if (!ClientContext.getModule(ESPModule.class).isRenderingBlockEntities()) {
         if (ClientContext.getModule(NoRenderModule.class).shouldHideBlockEntityType(
            renderState.blockEntityType
         )) {
            callback.cancel();
         } else {
            if (ClientContext.getModule(NoRenderModule.class).isToggled()
               && ClientContext.getModule(NoRenderModule.class).tileEntitiesSetting.getValue()
               && renderState.blockPos.distSqr(Minecraft.getInstance().gameRenderer.getMainCamera().blockPosition())
                  > ClientContext.getModule(NoRenderModule.class).tileDistanceSetting
                        .getValue()
                     * ClientContext.getModule(NoRenderModule.class).tileDistanceSetting
                        .getValue()) {
               callback.cancel();
            }
         }
      }
   }

}
