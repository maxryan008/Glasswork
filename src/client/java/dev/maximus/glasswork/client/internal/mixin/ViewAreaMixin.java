package dev.maximus.glasswork.client.internal.mixin;

import dev.maximus.glasswork.api.GlassworkAPI;
import dev.maximus.glasswork.client.internal.mesh.TranslucentMeshStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ViewArea.class)
public abstract class ViewAreaMixin {

    @Shadow
    public SectionRenderDispatcher.RenderSection[] sections;

    @Shadow
    protected int sectionGridSizeX, sectionGridSizeY, sectionGridSizeZ;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected abstract int getSectionIndex(int x, int y, int z);

    @Inject(method = "setDirty", at = @At("HEAD"))
    private void glasswork$whenDirty(int x, int y, int z, boolean important, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        SectionPos sec = SectionPos.of(x, y, z);

        TranslucentMeshStore.markDirty(sec.origin());

        GlassworkAPI._bumpGeneration(sec);
        GlassworkAPI._clearSection(sec);
    }
}