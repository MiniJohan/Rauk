package rauk.com.client.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.anim.RaukSkeleton;
import rauk.com.client.anim.Xform;
import rauk.com.client.model.RaukRig;
import rauk.com.client.model.RaukRig.V;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.combat.Region;

/**
 * A ray against Rauk's body as it is posed this frame: every bone and plate of the model, each a
 * box on its part. Its skull, arms and tail reach well outside its collision box, so attacks are
 * aimed at what the player actually sees.
 */
public final class RaukHitTest {
	/** Thin bones (fingers, ribs) are made a little easier to hit than they look. */
	private static final double MARGIN = 0.05;

	public record Hit(RaukEntity rauk, Region region, Vec3 at, double distance) {
	}

	private RaukHitTest() {
	}

	@Nullable
	public static Hit ray(RaukEntity rauk, Vec3 from, Vec3 dir, double reach) {
		RaukSkeleton sk = RaukSkeleton.shared();
		Xform[] world = RaukAnimator.of(rauk).worldTransforms();
		V o = new V(from.x, from.y, from.z);
		V d = new V(dir.x, dir.y, dir.z);
		double best = reach;
		int bestPart = -1;
		for (int j = 0; j < sk.count; j++) {
			RaukRig.Part part = sk.rig.parts.get(j);
			Xform x = world[j];
			V lo = x.toLocal(o);
			V ld = x.r().conj().rotate(d);
			for (RaukRig.Box b : part.boxes) {
				if (b.mat == RaukRig.Mat.DROOL) {
					continue;
				}
				double t = slab(lo, ld, b.x0 - MARGIN, b.y0 - MARGIN, b.z0 - MARGIN, b.x0 + b.w / (double) RaukRig.UNITS + MARGIN,
						b.y0 + b.h / (double) RaukRig.UNITS + MARGIN, b.z0 + b.d / (double) RaukRig.UNITS + MARGIN);
				if (t >= 0 && t < best) {
					best = t;
					bestPart = j;
				}
			}
		}
		if (bestPart < 0) {
			return null;
		}
		return new Hit(rauk, Region.ofPart(sk.names[bestPart]), from.add(dir.scale(best)), best);
	}

	/** Where the ray enters the box, as a distance along it; -1 if it misses. */
	private static double slab(V o, V d, double x0, double y0, double z0, double x1, double y1, double z1) {
		double tmin = 0, tmax = Double.MAX_VALUE;
		double[] os = {o.x(), o.y(), o.z()}, ds = {d.x(), d.y(), d.z()}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(ds[i]) < 1e-9) {
				if (os[i] < lo[i] || os[i] > hi[i]) {
					return -1;
				}
				continue;
			}
			double a = (lo[i] - os[i]) / ds[i], b = (hi[i] - os[i]) / ds[i];
			tmin = Math.max(tmin, Math.min(a, b));
			tmax = Math.min(tmax, Math.max(a, b));
			if (tmin > tmax) {
				return -1;
			}
		}
		return tmin;
	}
}
