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
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.modules.client.HudColorMode;
import dev.zio.client.modules.client.HUDModule;
import dev.zio.client.commands.PanicCommand;
import dev.zio.client.modules.client.UIModule;
import dev.zio.client.modules.hud.WatermarkModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
   private static WatermarkModule watermarkModule = ClientContext.getModule(WatermarkModule.class);

   protected TitleScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void renderTitleWatermark(
      GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback, @Local(ordinal = 1) float opacity
   ) {
      if (!PanicCommand.unloaded) {
         Minecraft client = ClientAccess.zioMinecraft;
         int hudColor = (ClientContext.getModule(HUDModule.class).fadeSetting.getValue()).getColor(ClientContext.getModule(HUDModule.class), 0.0F).getRGB();
         String versionText = ChatFormatting.WHITE + watermarkModule.getVersionText();
         graphics.drawString(
            client.font,
            "Zio " + versionText,
            2,
            2,
            ARGB.color(opacity, hudColor)
         );
      }
   }

   @Override
   public boolean keyPressed(KeyEvent keyEvent) {
      if (!ClientContext.getModule(UIModule.class).getKeybind().mouse() && ClientContext.getModule(UIModule.class).getKeybind().code() == keyEvent.key()) {
         ClientContext.getModule(UIModule.class).enable();
         ClientContext.getClickGui().open();
      }

      return super.keyPressed(keyEvent);
   }
}
