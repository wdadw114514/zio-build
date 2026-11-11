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
import dev.zio.client.modules.render.AnimationsModule;
import dev.zio.client.settings.options.AutoCrystalInstantChoice;
import dev.zio.client.modules.combat.AutoCrystalModule;
import dev.zio.client.ClientContext;
import dev.zio.client.utils.combat.EndCrystalState;
import dev.zio.client.render.EntityRenderStateAccess;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalModel.class)
public class EndCrystalModelMixin {
   @Shadow
   @Final
   public ModelPart base;
   @Shadow
   @Final
   public ModelPart innerGlass;
   @Shadow
   @Final
   public ModelPart outerGlass;
   @Shadow
   @Final
   public ModelPart cube;

   @ModifyExpressionValue(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;ageInTicks:F", ordinal = 0), require = 0)
   private float applyCrystalRotationSpeed(
      float originalRotation, @Local(argsOnly = true) EndCrystalRenderState renderState
   ) {
      return originalRotation * this.updateCrystalAnimationAndGetSpeed(renderState);
   }

   @Unique
   private float updateCrystalAnimationAndGetSpeed(EndCrystalRenderState renderState) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      float speedMultiplier = 1.0F;
      if (entity.tickCount < 10
         && ClientContext.getModule(AutoCrystalModule.class).isToggled()
         && (ClientContext.getModule(AutoCrystalModule.class).instantSetting
               .getValue())
            .isEnabled()
         && ((EndCrystalState)entity).isClientAttacked()) {
         speedMultiplier *= 1.5F;
      }

      if (ClientContext.getModule(AnimationsModule.class).isToggled()
         && ClientContext.getModule(AnimationsModule.class).crystalsSetting.getValue()) {
         speedMultiplier *= ClientContext.getModule(AnimationsModule.class).rotationSpeedSetting.getValue();
         this.base.skipDraw = ClientContext.getModule(AnimationsModule.class).shouldHideCrystalPart(0);
         this.outerGlass.skipDraw = ClientContext.getModule(AnimationsModule.class).shouldHideCrystalPart(1);
         this.innerGlass.skipDraw = ClientContext.getModule(AnimationsModule.class).shouldHideCrystalPart(2);
         this.cube.skipDraw = ClientContext.getModule(AnimationsModule.class).shouldHideCrystalPart(3);
      }
      return speedMultiplier;
   }
}
