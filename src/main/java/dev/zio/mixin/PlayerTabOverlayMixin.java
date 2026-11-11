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
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.authlib.GameProfile;
import dev.zio.client.ClientContext;
import dev.zio.client.render.ColorUtils;
import dev.zio.client.modules.misc.ExtraTabModule;
import dev.zio.client.modules.player.NameProtectModule;
import dev.zio.client.modules.render.NoRenderModule;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @ModifyReceiver(
      method = "getPlayerInfos",
      at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;")
   )
   private Stream filterTabPlayers(Stream players, Comparator comparator) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()
         && ClientContext.getModule(ExtraTabModule.class).displaySetting.getValue()
            != ExtraTabModule.DisplayChoice.ALL) {
         String ownName = Minecraft.getInstance().getUser().getName();
         return players.filter(
            (java.util.function.Predicate<net.minecraft.client.multiplayer.PlayerInfo>)(playerInfo -> ClientContext.socialRegistry
                     .getRole(
                        playerInfo.getProfile().name()
                     )
                  != null
               || playerInfo.getProfile().name().equals(ownName)
         ));
      } else {
         return players;
      }
   }

   @ModifyExpressionValue(method = "getPlayerInfos", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;PLAYER_COMPARATOR:Ljava/util/Comparator;"))
   private Comparator overridePlayerComparator(Comparator originalComparator) {
      return ClientContext.getModule(ExtraTabModule.class).isToggled()
            && ClientContext.getModule(ExtraTabModule.class).sortSetting.getValue()
         ? ClientContext.getModule(ExtraTabModule.class).createPlayerComparator()
         : originalComparator;
   }

   @ModifyArg(method = "getPlayerInfos", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;limit(J)Ljava/util/stream/Stream;"))
   private long overridePlayerLimit(long originalLimit) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()) {
         return ClientContext.getModule(ExtraTabModule.class).limitSetting
               .isAtMaximum()
            ? Long.MAX_VALUE
            : (ClientContext.getModule(ExtraTabModule.class).limitSetting.getValue())
               .intValue();
      } else {
         return originalLimit;
      }
   }

   @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerInfo;showHat()Z"))
   private boolean enablePlayerHatLayer(boolean originalHatVisible) {
      return ClientContext.getModule(ExtraTabModule.class).isToggled()
            && ClientContext.getModule(ExtraTabModule.class).betterSkinSetting.getValue()
         ? true
         : originalHatVisible;
   }

   @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
   private void highlightPlayerName(
      PlayerInfo playerInfo, CallbackInfoReturnable<Component> callback
   ) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()
         && ClientContext.getModule(ExtraTabModule.class).highlightSetting.getValue()) {
         String plainName = ChatFormatting.stripFormatting(((Component)callback.getReturnValue()).getString());
         if (plainName == null) {
            return;
         }

         String profileName = playerInfo.getProfile().name();
         if (ClientContext.getModule(ExtraTabModule.class).socialSetting.getValue()) {
            Color socialColor = ClientContext.socialRegistry
               .getColor(profileName, null);
            if (socialColor != null) {
               callback.setReturnValue(Component.literal(plainName).withStyle(nameStyle -> nameStyle.withColor(socialColor.hashCode())));
            }
         }

         if (Minecraft.getInstance().getUser().getName().equals(profileName)) {
            callback.setReturnValue(
               Component.literal(plainName)
                  .withStyle(
                     selfStyle -> selfStyle.withColor(
                        (ClientContext.getModule(ExtraTabModule.class).selfSetting.getValue())
                           .hashCode()
                     )
                  )
            );
         }
      }
   }

   @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isLocalServer()Z"))
   private void updateTabLayout(
      GuiGraphics graphics,
      int screenWidth,
      Scoreboard scoreboard,
      Objective objective,
      CallbackInfo callback,
      @Local(ordinal = 4) LocalIntRef playerCountRef,
      @Local(ordinal = 5) LocalIntRef rowCountRef,
      @Local(ordinal = 6) LocalIntRef columnCountRef,
      @Local(ordinal = 0) List players
   ) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()) {
         int playerCount = players.size();
         int rowCount = playerCount;

         int columnCount;
         for (columnCount = 1;
            rowCount
               > (
                  40
               );
            rowCount = (playerCount + columnCount - 1) / columnCount
         ) {
            columnCount++;
         }

         columnCountRef.set(columnCount);
         rowCountRef.set(rowCount);
         playerCountRef.set(playerCount);
      }
   }

   @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
   private void renderCustomLatency(
      GuiGraphics graphics, int entryWidth, int entryX, int entryY, PlayerInfo playerInfo, CallbackInfo callback
   ) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()
         && ClientContext.getModule(ExtraTabModule.class).latencySetting.getValue()
            != ExtraTabModule.LatencyChoice.VANILLA) {
         if (ClientContext.getModule(ExtraTabModule.class).latencySetting.getValue()
            == ExtraTabModule.LatencyChoice.NONE) {
            callback.cancel();
         } else {
            int latencyColor = ClientContext.getModule(ExtraTabModule.class).coloredSetting.getValue()
               ? ColorUtils.getLatencyColor(playerInfo.getLatency())
                  .hashCode()
               : ChatFormatting.YELLOW.getColor();
            MutableComponent latencyText = Component.literal(String.valueOf(playerInfo.getLatency())).withColor(latencyColor);
            graphics.drawString(this.minecraft.font, latencyText, entryX + entryWidth - this.minecraft.font.width(latencyText), entryY, -1);
            callback.cancel();
         }
      }
   }

   @ModifyExpressionValue(method = "render", at = @At(value = "CONSTANT", args = "intValue=13"))
   private int overridePingIconWidth(int originalWidth) {
      return ClientContext.getModule(ExtraTabModule.class).isToggled()
            && ClientContext.getModule(ExtraTabModule.class).latencySetting.getValue()
               != ExtraTabModule.LatencyChoice.VANILLA
         ? 0
         : originalWidth;
   }

   @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0))
   private void includeLatencyTextInEntryWidth(Args arguments, @Local PlayerInfo playerInfo) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()
         && ClientContext.getModule(ExtraTabModule.class).latencySetting.getValue()
            == ExtraTabModule.LatencyChoice.TEXT) {
         arguments.set(1, arguments.<Integer>get(1) + this.minecraft.font.width(" " + playerInfo.getLatency()));
      }
   }

   @ModifyArgs(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;renderTablistScore(Lnet/minecraft/world/scores/Objective;ILnet/minecraft/client/gui/components/PlayerTabOverlay$ScoreDisplayEntry;IILjava/util/UUID;Lnet/minecraft/client/gui/GuiGraphics;)V"
      )
   )
   private void reserveLatencyTextInScoreBounds(Args arguments, @Local PlayerInfo playerInfo) {
      if (ClientContext.getModule(ExtraTabModule.class).isToggled()
         && ClientContext.getModule(ExtraTabModule.class).latencySetting.getValue()
            == ExtraTabModule.LatencyChoice.TEXT) {
         int reservedWidth = this.minecraft.font.width(playerInfo.getLatency() + "")
            + 2;
         int firstScoreBound = arguments.<Integer>get(3);
         int secondScoreBound = arguments.<Integer>get(4);
         arguments.set(
            3,
            firstScoreBound - reservedWidth
         );
         arguments.set(4, secondScoreBound - reservedWidth);
      }
   }

   @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;isEncrypted()Z"))
   private boolean suppressTabIcons(boolean originalVisible) {
      return originalVisible
         && !ClientContext.getModule(NoRenderModule.class).shouldHideTabIcons();
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void applyTabScale(
      GuiGraphics graphics, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo callback
   ) {
      if (ClientContext.getModule(ExtraTabModule.class).getTabScale()
         != 1.0F) {
         graphics.pose().pushMatrix();
         graphics.pose()
            .scale(
               ClientContext.getModule(ExtraTabModule.class).getTabScale(),
               ClientContext.getModule(ExtraTabModule.class).getTabScale()
            );
      }
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void restoreTabScale(
      GuiGraphics graphics, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo callback
   ) {
      if (ClientContext.getModule(ExtraTabModule.class).getTabScale()
         != 1.0F) {
         graphics.pose().popMatrix();
      }
   }

   @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
   private int adjustTabWidthForScale(int originalWidth) {
      return ClientContext.getModule(ExtraTabModule.class).getTabScale()
            == 1.0F
         ? originalWidth
         : (int)(
            originalWidth
               / ClientContext.getModule(ExtraTabModule.class).getTabScale()
         );
   }

   // Mojang leaves the player name lookup lambda unnamed.
   @Inject(method = "method_46511(Lnet/minecraft/client/multiplayer/PlayerInfo;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private static void protectPlayerName(
      PlayerInfo playerInfo, CallbackInfoReturnable<String> callback
   ) {
      GameProfile localProfile = Minecraft.getInstance().player.getGameProfile();
      if (ClientContext.getModule(NameProtectModule.class).isToggled()) {
         if (playerInfo.getProfile().equals(localProfile)) {
            callback.setReturnValue(
               ClientContext.getModule(NameProtectModule.class).getProtectedName()
            );
         } else if (ClientContext.getModule(NameProtectModule.class).othersSetting.getValue()) {
            callback.setReturnValue("redacted");
         }
      }
   }
}
