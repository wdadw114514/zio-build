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
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.zio.client.modules.render.AmbienceModule;
import dev.zio.client.modules.render.ChamsModule;
import dev.zio.client.ClientAccess;
import dev.zio.client.ClientContext;
import dev.zio.client.render.ShaderPassRenderState;
import dev.zio.client.render.LevelRendererFramebufferAccess;
import dev.zio.client.managers.RotationManager;
import dev.zio.client.render.ShaderPass;
import dev.zio.client.managers.RenderTargetRegistry;
import dev.zio.client.render.RenderUtils;
import dev.zio.client.render.IrisShaderBridge;
import dev.zio.client.render.EntityRenderStateAccess;
import dev.zio.client.events.WorldRenderEvent;
import dev.zio.client.managers.RenderStateManager;
import dev.zio.client.modules.player.FreecamModule;
import dev.zio.client.events.BlockOutlineRenderEvent;
import dev.zio.client.modules.render.NoRenderModule;
import dev.zio.client.modules.render.SkyboxModule;
import dev.zio.client.modules.render.ViewClipModule;
import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements ClientAccess, LevelRendererFramebufferAccess {
   @Unique
   private PoseStack worldPoseStack;
   @Unique
   private AtomicBoolean savedHideGui;
   @Shadow
   @Nullable
   private ClientLevel level;
   @Shadow
   @Nullable
   private RenderTarget entityOutlineTarget;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Unique
   private Stack<RenderTarget> entityOutlineTargetStack;
   @Unique
   private Stack<ResourceHandle<RenderTarget>> entityOutlineHandleStack;

   // Mojang leaves the weather render pass lambda unnamed.
   @WrapWithCondition(
      method = "method_62216(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;IF)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;)V"
      )
   )
   private boolean shouldRenderWeather(
      WeatherEffectRenderer weatherRenderer, MultiBufferSource buffers, Vec3 cameraPosition, WeatherRenderState weatherState
   ) {
      return !ClientContext.getModule(AmbienceModule.class).isToggled()
         || !ClientContext.getModule(AmbienceModule.class).worldWeatherSetting.getValue()
         || ClientContext.getModule(AmbienceModule.class).weatherSetting.getValue()
            != AmbienceModule.WeatherChoice.CLEAR;
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void renderWorldEvents(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      Matrix4f inputMatrix3,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      PoseStack poses = new PoseStack();
      float partialTick = RenderUtils.getPartialTick();
      GpuBufferSlice previousFog = RenderSystem.getShaderFog();
      RenderSystem.setShaderFog(
         ClientAccess.zioMinecraft.gameRenderer
            .fogRenderer
            .getBuffer(FogMode.NONE)
      );
      poses.pushPose();
      RenderUtils.applyCameraRotation(poses);
      WorldRenderEvent.BeforeDepthClear beforeDepthEvent = WorldRenderEvent.BeforeDepthClear.create(
         poses, partialTick
      );
      ClientAccess.eventBus.post(
         beforeDepthEvent
      );
      RenderUtils.clearDepth(
         ClientAccess.zioMinecraft.getMainRenderTarget()
      );
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
      WorldRenderEvent.Main mainEvent = WorldRenderEvent.Main.create(
         poses, partialTick
      );
      ClientAccess.eventBus.post(
         mainEvent
      );
      RenderUtils.clearDepth(
         ClientAccess.zioMinecraft.getMainRenderTarget()
      );
      WorldRenderEvent.Overlay overlayEvent = WorldRenderEvent.Overlay.create(
         poses, partialTick
      );
      ClientAccess.eventBus.post(
         overlayEvent
      );
      poses.popPose();
      RenderSystem.setShaderFog(previousFog);
   }

   // Mojang leaves the world render pass lambda unnamed.
   @Inject(
      method = "method_62214(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;", shift = Shift.BEFORE)
   )
   private void postBeforeBufferSourceEvent(
      GpuBufferSlice inputBuffer,
      LevelRenderState renderState,
      ProfilerFiller profiler,
      Matrix4f inputMatrix,
      ResourceHandle firstTarget,
      ResourceHandle secondTarget,
      boolean renderFlag,
      ResourceHandle thirdTarget,
      ResourceHandle fourthTarget,
      CallbackInfo callback,
      @Local PoseStack poses
   ) {
      WorldRenderEvent.BeforeBufferSource event = WorldRenderEvent.BeforeBufferSource.create(
         poses, RenderUtils.getPartialTick()
      );
      ClientAccess.eventBus.post(
         event
      );
   }

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
         shift = Shift.BEFORE
      )
   )
   private void addSkyShaderPass(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      Matrix4f inputMatrix3,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback,
      @Local FrameGraphBuilder frameGraph
   ) {
      if (ClientContext.getModule(SkyboxModule.class).isToggled()
         && ClientContext.getModule(SkyboxModule.class).shaderSetting.getValue()) {
         FramePass skyFramePass = frameGraph.addPass("sky_zio");
         this.targets.main = skyFramePass.readsAndWrites(this.targets.main);
         skyFramePass.executes(
            () -> {
               ShaderPass skyShader = ClientContext.shaderRegistry
                  .getPass("sky");
               ShaderPassRenderState.INSTANCE
                  .configure(skyShader);
               RenderStateManager.addStates(
                  ShaderPassRenderState.INSTANCE
               );
               ClientContext.getModule(SkyboxModule.class).drawShaderQuad();
               RenderStateManager.reset();
               RenderUtils.INSTANCE
                  .fillRect(
                     this.worldPoseStack, 0.0F, 0.0F, 1.0F, 1.0F, 0
                  );
            }
         );
      }
   }

   // Mojang leaves the world render pass lambda unnamed.
   @WrapWithCondition(
      method = "method_62214(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;ZLcom/mojang/blaze3d/resource/ResourceHandle;Lcom/mojang/blaze3d/resource/ResourceHandle;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderBlockOutline(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;ZLnet/minecraft/client/renderer/state/LevelRenderState;)V"
      )
   )
   private boolean shouldRenderBlockOutline(
      LevelRenderer renderer, BufferSource bufferSource, PoseStack poseStack, boolean translucent, LevelRenderState renderState
   ) {
      if (!(ClientAccess.zioMinecraft.hitResult instanceof BlockHitResult)) {
         return true;
      }

      BlockPos blockPos = ((BlockHitResult)ClientAccess.zioMinecraft.hitResult)
         .getBlockPos();
      BlockOutlineRenderEvent event = new BlockOutlineRenderEvent(
         poseStack, null, blockPos, ClientAccess.zioMinecraft.level.getBlockState(blockPos)
      );
      ClientAccess.eventBus.post(
         event
      );
      return !event.isCancelled();
   }

   @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
   private void suppressSkyBlindness(
      Camera camera, CallbackInfoReturnable<Boolean> callback
   ) {
      if (ClientContext.getModule(NoRenderModule.class).isToggled()
         && ClientContext.getModule(NoRenderModule.class).blindnessSetting.getValue()) {
         callback.setReturnValue(null);
      }
   }

   @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
   private boolean applyCameraSpectatorOverride(boolean originalSpectator) {
      return ClientContext.getModule(FreecamModule.class).isToggled()
         || (ClientContext.getModule(ViewClipModule.class).isToggled() && ClientAccess.zioMinecraft.gameRenderer.getMainCamera().isDetached())
         || originalSpectator;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void captureWorldRenderState(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      Matrix4f inputMatrix3,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      RenderUtils.setPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
      this.worldPoseStack = new PoseStack();
   }

   // Mojang leaves the background color render pass lambda unnamed.
   @ModifyExpressionValue(method = "method_62218(Lorg/joml/Vector4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
   private int applySkyboxBackgroundColor(int originalColor) {
      return ClientContext.getModule(SkyboxModule.class).isToggled()
            && ClientContext.getModule(SkyboxModule.class).isCurrentDimensionEnabled()
         ? ARGB.color(
            0,
            (ClientContext.getModule(SkyboxModule.class).backgroundSetting.getValue())
               .hashCode()
         )
         : originalColor;
   }

   // Mojang leaves the sky render pass lambda unnamed.
   @ModifyArgs(
      method = "method_62215(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/SkyRenderState;Lnet/minecraft/client/renderer/SkyRenderer;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFog(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
   )
   private static void applySkyFogSlice(Args arguments) {
      if (ClientContext.getModule(SkyboxModule.class).isToggled()
         && ClientContext.getModule(SkyboxModule.class).splitFogSetting.getValue()
         && ClientContext.getModule(SkyboxModule.class).isCurrentDimensionEnabled()) {
         arguments.set(
            0,
            ClientContext.getModule(SkyboxModule.class).getSkySlice()
         );
      }
   }

   @WrapWithCondition(
      method = "submitEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
      )
   )
   private boolean shouldSubmitVisibleEntity(
      EntityRenderDispatcher dispatcher, EntityRenderState renderState, CameraRenderState cameraState, double x, double y, double z, PoseStack poses, SubmitNodeCollector collector
   ) {
      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      return (
            !ClientContext.getModule(NoRenderModule.class).entitiesSetting.getValue()
               || entity == null
               || !ClientContext.getModule(NoRenderModule.class).shouldHideEntity(entity)
         )
         && !ClientContext.getModule(ChamsModule.class).shouldRenderThroughWalls(
            entity
         );
   }

   @Inject(method = "resize", at = @At("HEAD"))
   private void resizeClientRenderTargets(int width, int height, CallbackInfo callback) {
      RenderTargetRegistry.resizeRenderTargets(width, height);
   }

   @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
   private boolean applyFreecamDetachedCamera(boolean originalDetached) {
      return ClientContext.getModule(FreecamModule.class).isToggled() || originalDetached;
   }

   @Inject(method = "addWeatherPass", at = @At("HEAD"))
   private void beginWeatherPassColor(
      FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 0.25F);
   }

   @Inject(method = "addWeatherPass", at = @At("TAIL"))
   private void resetWeatherPassColor(
      FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo callback
   ) {
      RenderUtils.setRenderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @ModifyExpressionValue(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", ordinal = 1)
   )
   private Object applyCloudHeight(Object originalHeight) {
      return ClientContext.getModule(SkyboxModule.class).getCloudHeight((Float)originalHeight);
   }

   @ModifyExpressionValue(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", ordinal = 0)
   )
   private Object applyCloudColor(Object originalColor) {
      return ClientContext.getModule(SkyboxModule.class).isToggled()
            && ClientContext.getModule(SkyboxModule.class).cloudsSetting.getValue()
         ? (ClientContext.getModule(SkyboxModule.class).cloudsColorSetting.getValue())
            .hashCode()
         : originalColor;
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void hideGuiForFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      Matrix4f inputMatrix3,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      if (ClientContext.getModule(FreecamModule.class).shouldHideHands()
         && IrisShaderBridge.isHandRendererAvailable()) {
         this.savedHideGui = new AtomicBoolean();
         this.savedHideGui
            .setPlain(ClientAccess.zioMinecraft.options.hideGui);
         ClientAccess.zioMinecraft.options.hideGui = true;
      }
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void restoreGuiAfterFreecamHands(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      Matrix4f inputMatrix3,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback
   ) {
      if (ClientContext.getModule(FreecamModule.class).shouldHideHands()
         && this.savedHideGui != null) {
         ClientAccess.zioMinecraft.options.hideGui = this.savedHideGui
            .getPlain();
         this.savedHideGui = null;
      }
   }

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE_ASSIGN",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/client/renderer/culling/Frustum;"
      )
   )
   private void captureCullFrustum(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderFlag1,
      Camera camera,
      Matrix4f inputMatrix1,
      Matrix4f inputMatrix2,
      Matrix4f inputMatrix3,
      GpuBufferSlice inputBuffer,
      Vector4f inputColor,
      boolean renderFlag2,
      CallbackInfo callback,
      @Local Frustum frustum
   ) {
      RotationManager.setFrustum(frustum);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializeFramebufferStacks(CallbackInfo callback) {
      this.entityOutlineTargetStack = new ObjectArrayList<>();
      this.entityOutlineHandleStack = new ObjectArrayList<>();
   }

   @Override
   public void zio$pushFramebuffer(RenderTarget renderTarget) {
      this.entityOutlineTargetStack.push(this.entityOutlineTarget);
      this.entityOutlineTarget = renderTarget;
      this.entityOutlineHandleStack.push(this.targets.entityOutline);
      this.targets.entityOutline = () -> renderTarget;
   }

   @Override
   public void zio$popFramebuffer() {
      this.entityOutlineTarget = this.entityOutlineTargetStack.pop();
      this.targets.entityOutline = this.entityOutlineHandleStack.pop();
   }
}
