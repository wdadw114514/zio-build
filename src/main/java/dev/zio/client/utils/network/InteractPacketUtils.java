/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils.network;

import dev.zio.client.ClientAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public final class InteractPacketUtils implements ClientAccess {
   private InteractPacketUtils() {
   }

   public static Entity getEntity(ServerboundInteractPacket packet) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      packet.write(buffer);
      ClientLevel level = ClientAccess.zioMinecraft.level;
      int entityId = buffer.readVarInt();
      return level.getEntity(entityId);
   }

   public static Action getAction(ServerboundInteractPacket packet) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      packet.write(buffer);
      buffer.readVarInt();
      Class<Action> actionType = Action.class;
      return buffer.readEnum(actionType);
   }

   public enum Action {
      INTERACT,
      ATTACK,
      INTERACT_AT
   }
}
