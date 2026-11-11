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
import dev.zio.client.modules.misc.NoPacketKickModule;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin implements PacketListener {

   @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
   public void suppressDecoderExceptionDisconnect(DisconnectionDetails details, CallbackInfo callback) {
      if (ClientContext.getModule(NoPacketKickModule.class).isToggled()
         && this.protocol() == ConnectionProtocol.PLAY
         && details.reason().getString().contains("io.netty.handler.codec.DecoderException")) {
         callback.cancel();
      }
   }
}
