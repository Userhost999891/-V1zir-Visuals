package dev.v1zir.visuals.module.impl.visuals;

import dev.v1zir.visuals.module.Category;
import dev.v1zir.visuals.module.Module;
import dev.v1zir.visuals.module.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

/** Moves, scales and rotates the items in your hands. */
public final class ViewModelModule extends Module {
	public final NumberSetting x = add(new NumberSetting("X", 0.0, -1.0, 1.0, 0.01));
	public final NumberSetting y = add(new NumberSetting("Y", 0.0, -1.0, 1.0, 0.01));
	public final NumberSetting z = add(new NumberSetting("Z", 0.0, -1.0, 1.0, 0.01));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.3, 2.0, 0.01));
	public final NumberSetting rotation = add(new NumberSetting("Rotation", 0, -180, 180, 1).suffix("°"));

	public ViewModelModule() {
		super("View Model", "Customise held item position", Category.VISUALS);
	}

	public void apply(MatrixStack matrices, Arm arm) {
		float side = arm == Arm.RIGHT ? 1f : -1f;
		matrices.translate(x.f() * side, y.f(), z.f());
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation.f() * side));
		float s = scale.f();
		matrices.scale(s, s, s);
	}
}
