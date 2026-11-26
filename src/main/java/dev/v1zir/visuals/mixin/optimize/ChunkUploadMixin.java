package dev.v1zir.visuals.mixin.optimize;

import dev.v1zir.visuals.optimize.UploadPacer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

/**
 * Chunk upload pacing: the builder's upload queue is wrapped once it exists, so the per-frame upload of
 * finished sections stops at a time budget (see UploadPacer). Sections are built exactly as before.
 */
@Mixin(ChunkBuilder.class)
public abstract class ChunkUploadMixin {
	@Shadow
	@Final
	@Mutable
	private Queue<Runnable> uploadQueue;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void v1zir$paceUploads(CallbackInfo ci) {
		uploadQueue = new UploadPacer(uploadQueue);
	}
}
