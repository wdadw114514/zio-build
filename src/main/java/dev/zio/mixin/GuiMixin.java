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
import dev.zio.client.ClientContext;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.modules.render.CrosshairModule;
import dev.zio.client.modules.misc.ExtraTabModule;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.modules.combat.NoHitDelayModule;
import dev.zio.client.modules.render.NoRenderModule;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public class GuiMixin {
   @Shadow
   @Final
   private PlayerTabOverlay tabList;
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(method = "render", at = @At("HEAD"))
   private void resetHudRenderColor(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
   private void suppressHeldItemTooltip(GuiGraphics graphics, CallbackInfo callback) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
         && ClientContext.getModule(NoRenderModule.class).heldTooltipsSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
   private void suppressEffectIcons(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      if (ClientContext.getModule(HUDModule.class).isToggled()
         && ClientContext.getModule(HUDModule.class).iconsSetting
               .getValue()
            == HUDModule.EffectIconMode.HIDE) {
         callback.cancel();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void cancelVanillaCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
      if (!ClientContext.getModule(CrosshairModule.class).isToggled()) {
         return;
      }
      if (ClientContext.getModule(CrosshairModule.class).shouldHideVanillaCrosshair()) {
         callback.cancel();
         return;
      }
      float guiScale = this.minecraft.getWindow().getGuiScale();
      int pixelScale = Math.max(1, Math.round(guiScale * ClientContext.getModule(CrosshairModule.class).scaleSetting.getValue() / 2.0F));
      float scale = pixelScale / guiScale;
      int left = (graphics.guiWidth() - 15) / 2;
      int top = (graphics.guiHeight() - 15) / 2;
      float x = Math.round((left + 7.5F) * guiScale - 7.5F * pixelScale) / guiScale;
      float y = Math.round((top + 7.5F) * guiScale - 7.5F * pixelScale) / guiScale;
      Matrix3x2fStack poses = graphics.pose();
      poses.pushMatrix();
      poses.translate(x, y);
      poses.scale(scale, scale);
      poses.translate(-left, -top);
   }

   @Inject(method = "renderCrosshair", at = @At("RETURN"))
   private void restoreCrosshairPose(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
      if (ClientContext.getModule(CrosshairModule.class).isToggled() && !ClientContext.getModule(CrosshairModule.class).shouldHideVanillaCrosshair()) {
         graphics.pose().popMatrix();
      }
   }

   @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
   private boolean showVanillaCrosshairInThirdPerson(boolean firstPerson) {
      return firstPerson || ClientContext.getModule(CrosshairModule.class).isToggled() && !ClientContext.getModule(CrosshairModule.class).shouldHideVanillaCrosshair() && ClientContext.getModule(CrosshairModule.class).thirdPersonSetting.getValue();
   }

   @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
   public Object suppressCrosshairAttackIndicator(Object originalStatus) {
      return ClientContext.getModule(NoHitDelayModule.class).isToggled() ? AttackIndicatorStatus.OFF : originalStatus;
   }

   @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
   private void suppressScoreboardSidebar(
      GuiGraphics graphics, Objective objective, CallbackInfo callback
   ) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled() && ClientContext.getModule(NoRenderModule.class).scoreBoardSetting.getValue()) {
         callback.cancel();
         return;
      }
      graphics.pose().pushMatrix();
      graphics.pose()
         .translate(
            -Math.max(
               ClientContext.getModule(HUDModule.class).getSafeX()
                  - 1,
               0
            ),
            0.0F
         );
   }

   @Inject(method = "displayScoreboardSidebar", at = @At("TAIL"), cancellable = true)
   private void restoreScoreboardPose(
      GuiGraphics graphics, Objective objective, CallbackInfo callback
   ) {
      graphics.pose().popMatrix();
   }

   @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
   private void suppressActionBar(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
         && ClientContext.getModule(NoRenderModule.class).actionBarSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true)
   private void renderSmoothTabList(
      GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback
   ) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()
         && ClientContext.getModule(ExtraTabModule.class).smoothSetting.getValue()) {
         callback.cancel();
         Scoreboard scoreboard = this.minecraft.level.getScoreboard();
         Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
         Matrix3x2fStack poses = graphics.pose();
         float openProgress = ClientContext.getModule(ExtraTabModule.class).getOpenProgress();
         if (openProgress != 0.0F) {
            poses.pushMatrix();
            poses.translate(0.0F, (1.0F - openProgress) * -graphics.guiHeight());
            this.tabList.render(graphics, graphics.guiWidth(), scoreboard, objective);
            poses.popMatrix();
         }
      }
   }

   @ModifyArgs(
      method = "renderCameraOverlays",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0)
   )
   private void applyBlindnessOverlayVisibility(Args arguments) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
         && ClientContext.getModule(NoRenderModule.class).blindnessSetting.getValue()) {
         arguments.set(
            2,
            0.0F
         );
      }
   }
}
