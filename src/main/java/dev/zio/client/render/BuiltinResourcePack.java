/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.ZioClient;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

public final class BuiltinResourcePack implements PackResources {
   PackLocationInfo location;

   public BuiltinResourcePack() {
      this.location = new PackLocationInfo("zio", Component.literal(ZioClient.NAME), PackSource.BUILT_IN, Optional.empty());
   }

   @Override
   public void close() {
   }

   @Override
   public Set getNamespaces(PackType packType) {
      return null;
   }

   @Override
   public void listResources(PackType packType, String namespace, String path, ResourceOutput output) {
   }

   @Nullable
   @Override
   public Object getMetadataSection(MetadataSectionType metadataType) {
      return null;
   }

   @Nullable
   @Override
   public IoSupplier getResource(PackType packType, Identifier location) {
      return null;
   }

   @Override
   public PackLocationInfo location() {
      return this.location;
   }

   @Nullable
   @Override
   public IoSupplier getRootResource(String... path) {
      return null;
   }
}
