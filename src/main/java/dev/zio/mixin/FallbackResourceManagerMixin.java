/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zio.client.ClientAccess;
import dev.zio.client.render.BuiltinResourcePack;
import dev.zio.client.managers.ConfigurationManager;
import dev.zio.client.ZioClient;
import java.io.FileInputStream;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallbackResourceManager.class)
public class FallbackResourceManagerMixin {
   @ModifyReturnValue(method = "getResource", at = @At("RETURN"))
   private Optional resolveClientResource(
      Optional originalResource, @Local(argsOnly = true) Identifier resourceId
   ) {
      if (resourceId.getNamespace().equals("zio-mount")) {
         return Optional.of(
            new Resource(
               new BuiltinResourcePack(),
               () -> new FileInputStream(
                  ConfigurationManager.ROOT_DIRECTORY
                     .resolve(resourceId.getPath())
                     .toFile()
               )
            )
         );
      } else if (originalResource.isEmpty()
         && (resourceId.getNamespace().equals("zio") || isBuiltinShaderResource(resourceId))
         && ClientAccess.hasBundledLanguageResource()) {
         String namespace = isBuiltinShaderResource(resourceId) ? "minecraft" : "zio";
         return Optional.of(
            new Resource(
               new BuiltinResourcePack(),
               () -> ZioClient.class.getResourceAsStream(
                  "/assets/%s/%s".formatted(namespace, resourceId.getPath())
               )
            )
         );
      } else {
         return originalResource;
      }
   }

   private static boolean isBuiltinShaderResource(Identifier resourceId) {
      String path = resourceId.getPath();
      return path.contains("/blur_mask.") || path.contains("/bloom.");
   }
}
