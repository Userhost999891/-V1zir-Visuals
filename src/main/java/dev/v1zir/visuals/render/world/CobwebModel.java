package dev.v1zir.visuals.render.world;

import dev.v1zir.visuals.module.impl.world.CobwebsModule;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The cobweb's block model while Cobwebs is on: its quads go to the translucent layer, their vertices at the chosen
 * opacity. It goes through Fabric's renderer API, so vanilla's renderer and Sodium draw it alike (Sodium keeps a quad
 * translucent when its vertices are not opaque, though the texture itself has no translucent pixels). Off, the model
 * is vanilla's. Called on chunk building threads.
 */
public final class CobwebModel extends WrapperBlockStateModel {
	public CobwebModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockRenderView blockView, BlockPos pos, BlockState state, Random random,
						  Predicate<Direction> cullTest) {
		int alpha = CobwebsModule.webAlpha();
		if (alpha < 0) {
			super.emitQuads(emitter, blockView, pos, state, random, cullTest);
			return;
		}
		emitter.pushTransform(quad -> {
			quad.renderLayer(BlockRenderLayer.TRANSLUCENT);
			for (int i = 0; i < 4; i++) {
				int color = quad.color(i);
				quad.color(i, ((color >>> 24) * alpha + 127) / 255 << 24 | color & 0xFFFFFF);
			}
			return true;
		});
		try {
			super.emitQuads(emitter, blockView, pos, state, random, cullTest);
		} finally {
			emitter.popTransform();
		}
	}

	/** No geometry key while the quads are changed: a cache keyed by vanilla's would hand back the opaque web. */
	@Override
	@Nullable
	public Object createGeometryKey(BlockRenderView blockView, BlockPos pos, BlockState state, Random random) {
		return CobwebsModule.webAlpha() < 0 ? super.createGeometryKey(blockView, pos, state, random) : null;
	}
}
