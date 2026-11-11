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
import dev.zio.client.modules.misc.BetterChatModule;
import dev.zio.client.ClientContext;
import dev.zio.client.render.FontRenderer;
import dev.zio.client.render.AtlasTextRenderer;
import dev.zio.client.modules.client.FontsModule;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ComponentRenderUtils.class)
public class ComponentRenderUtilsMixin {

   @ModifyExpressionValue(method = "wrapComponents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;getSplitter()Lnet/minecraft/client/StringSplitter;"))
   private static StringSplitter useChatFontSplitter(StringSplitter originalSplitter) {
      AtlasTextRenderer textRenderer = FontRenderer.INSTANCE
         .getRenderer();
      return textRenderer != null
            && ClientContext.getModule(FontsModule.class).isToggled()
            && ClientContext.getModule(FontsModule.class).chatSetting.getValue()
         ? textRenderer.getSplitter()
         : originalSplitter;
   }

   @ModifyVariable(method = "wrapComponents", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static FormattedText highlightOwnName(FormattedText originalText) {
      return ClientContext.getModule(BetterChatModule.class).highlightNames(originalText);
   }

}
