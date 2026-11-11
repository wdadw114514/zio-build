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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.XrayModule;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

   @Inject(method = {"tesselateWithAO", "tesselateWithoutAO"}, at = @At("HEAD"), cancellable = true)
   private void suppressHiddenBlockEntityMesh(
      BlockAndTintGetter level, List parts, BlockState blockState, BlockPos blockPos, PoseStack poses, VertexConsumer vertices, boolean checkSides, int packedOverlay, CallbackInfo callback
   ) {
      if (XrayModule.getInstance()
            .isToggled()
         && !XrayModule.getInstance()
            .shouldRenderBlock(
               blockPos,
               blockState.getBlock()
            )
         && XrayModule.getInstance()
            .shouldHideUnselectedBlocks()) {
         callback.cancel();
      } else {
         if (ClientContext.getModule(NoRenderModule.class).shouldHideBlockEntity(blockState)) {
            callback.cancel();
         }
      }
   }

   @ModifyArgs(
      method = "putQuadData",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V")
   )
   private void applyXrayQuadAlpha(
      Args arguments, @Local(argsOnly = true) BlockPos blockPos, @Local(argsOnly = true) BlockState blockState
   ) {
      if (ClientContext.getModule(XrayModule.class).isToggled()
         && !ClientContext.getModule(XrayModule.class).alphaSetting
            .isAtMinimum()
         && !ClientContext.getModule(XrayModule.class).alphaSetting
            .isAtMaximum()
         && !ClientContext.getModule(XrayModule.class).shouldRenderBlock(
            blockPos,
            blockState.getBlock()
         )) {
         arguments.set(
            6,
            (ClientContext.getModule(XrayModule.class).alphaSetting.getValue()).intValue()
               / 100.0F
         );
      }
   }
}
