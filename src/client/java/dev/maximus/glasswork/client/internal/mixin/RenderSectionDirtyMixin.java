package dev.maximus.glasswork.client.internal.mixin;

import dev.maximus.glasswork.api.GlassworkAPI;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class RenderSectionDirtyMixin {
    @Shadow
    public abstract BlockPos getOrigin();

    @Inject(method = "setDirty(Z)V", at = @At("HEAD"))
    private void glasswork$onSetDirty(boolean bl, CallbackInfo ci) {
        GlassworkAPI._clearSection(SectionPos.of(this.getOrigin()));
    }
}
