package rauk.com.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * What Rauk does to the player's view (design doc 5.5, 9): the camera shakes when it lands hard
 * nearby, pounds past at a gallop, grabs you or bites; and a red flash fills the screen at the bite.
 */
public final class RaukEffects {
	/** How hard the view shakes right now, in degrees, dying away. */
	private static double shake;
	private static long lastFrame;
	private static double flash, time;

	private RaukEffects() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register((graphics, delta) -> {
			if (flash > 0.01) {
				int alpha = (int) Math.round(Math.min(1, flash) * 200);
				graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0x8A0303);
			}
		});
	}

	/** Shakes the view by this much (degrees), if it isn't shaking harder already. */
	public static void shake(double degrees) {
		shake = Math.max(shake, degrees);
	}

	/** A shake that weakens with distance: full at the source, nothing beyond the range. */
	public static void shakeNear(double x, double y, double z, double degrees, double range) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		double d = player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
		if (d < range) {
			shake(degrees * (1 - d / range));
		}
	}

	/** The screen floods red, then fades over a second or two. */
	public static void flash(double strength) {
		flash = Math.max(flash, strength);
	}

	/** Called while the camera is set up each frame: a quick, uneven tremor that dies away. */
	public static void apply(PoseStack pose) {
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1e9, 0.1);
		lastFrame = now;
		time += dt;
		shake *= Math.exp(-dt * 4.5);
		flash *= Math.exp(-dt * 1.6);
		if (shake < 0.02) {
			return;
		}
		double roll = shake * (Math.sin(time * 41) * 0.6 + Math.sin(time * 23 + 1.3) * 0.4);
		double pitch = shake * 0.7 * (Math.sin(time * 37 + 0.7) * 0.6 + Math.sin(time * 19 + 2.1) * 0.4);
		pose.mulPose(Axis.ZP.rotationDegrees((float) roll));
		pose.mulPose(Axis.XP.rotationDegrees((float) pitch));
	}
}
