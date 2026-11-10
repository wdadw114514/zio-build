/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client;

import dev.zio.client.bootstrap.ClientInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZioClient implements ModInitializer, ClientModInitializer {
   public static final String MOD_ID = "zio";
   public static final String NAME = "Zio";
   public static final String PROJECT_URL = "https://github.com/Cypphi/open-zio";
   public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
      .orElseThrow(() -> new IllegalStateException("Zio mod metadata is missing"))
      .getMetadata().getVersion().getFriendlyString();
   public static final String USER_AGENT = NAME + "/" + VERSION;
   public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

   private static final ClientInitializer INITIALIZER = new ClientInitializer();

   @Override
   public void onInitialize() {
      INITIALIZER.initialize();
   }

   @Override
   public void onInitializeClient() {
      INITIALIZER.initializeClient();
   }
}
