/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render;

import dev.zio.client.ClientAccess;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.zio.mixin.MinecraftAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public final class RenderTargetUtils implements ClientAccess {
   private static RenderTarget savedMainRenderTarget;

   public static void popFramebuffer() {
      ((LevelRendererFramebufferAccess)ClientAccess.zioMinecraft.levelRenderer).zio$popFramebuffer();
   }

   public static void restoreRenderTargets() {
      popFramebuffer();
      if (savedMainRenderTarget != null) {
         setMainRenderTarget(
            savedMainRenderTarget
         );
         savedMainRenderTarget = null;
      }
   }

   private RenderTargetUtils() {
      throw new AssertionError();
   }

   public static void pushFramebuffer(RenderTarget target) {
      ((LevelRendererFramebufferAccess)ClientAccess.zioMinecraft.levelRenderer)
         .zio$pushFramebuffer(target);
   }

   public static void overrideRenderTargets(RenderTarget target) {
      savedMainRenderTarget = ClientAccess.zioMinecraft.getMainRenderTarget();
      pushFramebuffer(target);
      setMainRenderTarget(target);
   }

   public static void setMainRenderTarget(RenderTarget target) {
      ((MinecraftAccessor)ClientAccess.zioMinecraft).zio$setFramebuffer(target);
   }

   public static String readShaderSource(String resourcePath) {
      try {
         InputStream stream = BaseShaderPass.class.getResourceAsStream("/assets/zio/shaders/" + resourcePath);
         assert stream != null;
         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      } catch (IOException | AssertionError failure) {
         failure.printStackTrace();
         return "";
      }
   }
}
