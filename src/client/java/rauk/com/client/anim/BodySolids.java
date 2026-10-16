package rauk.com.client.anim;

import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import rauk.com.client.model.RaukRig.V;

/**
 * The solid blocks round Rauk's body, for keeping its head and tail out of walls: each part is a
 * ball, and a ball that would end up inside something is brought back toward a place known to be
 * clear (where the body has just been) until it isn't. Block shapes are looked up once a tick.
 */
final class BodySolids {
	private static final AABB[] NONE = new AABB[0];
	private final Long2ObjectOpenHashMap<AABB[]> boxes = new Long2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private Level level;
	private int tick = Integer.MIN_VALUE;

	/** Starts a frame; the shapes looked up are kept for the rest of the tick. */
	void begin(Level level, int tick) {
		if (level != this.level || tick != this.tick) {
			boxes.clear();
			this.level = level;
			this.tick = tick;
		}
	}

	private AABB[] at(int x, int y, int z) {
		long key = BlockPos.asLong(x, y, z);
		AABB[] b = boxes.get(key);
		if (b == null) {
			pos.set(x, y, z);
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				b = NONE;
			} else {
				List<AABB> list = shape.toAabbs();
				b = new AABB[list.size()];
				for (int i = 0; i < b.length; i++) {
					b[i] = list.get(i).move(x, y, z);
				}
			}
			boxes.put(key, b);
		}
		return b;
	}

	/** Whether a ball of radius r at p overlaps anything solid. */
	boolean hits(V p, double r) {
		int x0 = floor(p.x() - r), x1 = floor(p.x() + r), y0 = floor(p.y() - r), y1 = floor(p.y() + r);
		int z0 = floor(p.z() - r), z1 = floor(p.z() + r);
		double r2 = r * r;
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (AABB a : at(x, y, z)) {
						if (distance2(p, a) < r2) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** Whether a point is inside something solid by more than the margin (for checking the body). */
	boolean inside(V p, double margin) {
		for (AABB a : at(floor(p.x()), floor(p.y()), floor(p.z()))) {
			if (p.x() > a.minX + margin && p.x() < a.maxX - margin && p.y() > a.minY + margin && p.y() < a.maxY - margin
					&& p.z() > a.minZ + margin && p.z() < a.maxZ - margin) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The point nearest p on the way to it from safe where the ball is clear: p itself if it is.
	 * If safe isn't clear either, p pushed out of what it is in by the shortest way.
	 */
	V clear(V safe, V p, double r) {
		if (!hits(p, r)) {
			return p;
		}
		if (hits(safe, r)) {
			return pushOut(p, r);
		}
		double lo = 0, hi = 1;
		for (int i = 0; i < 8; i++) {
			double mid = (lo + hi) / 2;
			if (hits(safe.lerp(p, mid), r)) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return safe.lerp(p, lo);
	}

	private static final V[] WAYS_OUT;

	static {
		java.util.List<V> ways = new java.util.ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x != 0 || y != 0 || z != 0) {
						ways.add(new V(x, y, z).norm());
					}
				}
			}
		}
		WAYS_OUT = ways.toArray(new V[0]);
	}

	/**
	 * Out of whatever the ball is in by the nearest way to open space, however deep in a wall of
	 * blocks it is (no more than max away): box by box first, and if that leads only into the next
	 * block, straight out every way there is, the nearest clear place found. Unchanged if none is.
	 */
	V escape(V p, double r, double max) {
		V out = pushOut(p, r);
		if (!hits(out, r)) {
			return out;
		}
		V best = p;
		double bestD = Double.MAX_VALUE;
		for (V way : WAYS_OUT) {
			for (double d = 0.1; d <= max && d < bestD; d += 0.1) {
				V q = p.add(way.mul(d));
				if (!hits(q, r)) {
					best = q;
					bestD = d;
					break;
				}
			}
		}
		return best;
	}

	/**
	 * How far a ball gets moving from one place to another before it touches something: the last
	 * clear place on the way, so it never comes out on the far side of a wall. From itself if it
	 * starts in something.
	 */
	V sweep(V from, V to, double r) {
		V d = to.sub(from);
		double len = d.len();
		int steps = Math.max(1, (int) Math.ceil(len / (r * 0.5)));
		V last = from;
		for (int i = 1; i <= steps; i++) {
			V p = from.add(d.mul(i / (double) steps));
			if (hits(p, r)) {
				return last;
			}
			last = p;
		}
		return to;
	}

	/** Out of whatever the ball is in, a box at a time, by the shortest way out of each. */
	V pushOut(V p, double r) {
		for (int iter = 0; iter < 4; iter++) {
			V push = null;
			int x0 = floor(p.x() - r), x1 = floor(p.x() + r), y0 = floor(p.y() - r), y1 = floor(p.y() + r);
			int z0 = floor(p.z() - r), z1 = floor(p.z() + r);
			search:
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						for (AABB a : at(x, y, z)) {
							if (distance2(p, a) < r * r) {
								push = out(p, a, r);
								break search;
							}
						}
					}
				}
			}
			if (push == null) {
				return p;
			}
			p = p.add(push);
		}
		return p;
	}

	/** The shortest move that takes a ball out of a box. */
	private static V out(V p, AABB a, double r) {
		V near = new V(clamp(p.x(), a.minX, a.maxX), clamp(p.y(), a.minY, a.maxY), clamp(p.z(), a.minZ, a.maxZ));
		V d = p.sub(near);
		double len = d.len();
		if (len > 1e-6) {
			return d.mul((r - len) / len + 1e-3);
		}
		// The centre is inside: out through the nearest face.
		double[] depth = {p.x() - a.minX, a.maxX - p.x(), p.y() - a.minY, a.maxY - p.y(), p.z() - a.minZ, a.maxZ - p.z()};
		int best = 0;
		for (int i = 1; i < 6; i++) {
			if (depth[i] < depth[best]) {
				best = i;
			}
		}
		double m = depth[best] + r + 1e-3;
		return switch (best) {
			case 0 -> new V(-m, 0, 0);
			case 1 -> new V(m, 0, 0);
			case 2 -> new V(0, -m, 0);
			case 3 -> new V(0, m, 0);
			case 4 -> new V(0, 0, -m);
			default -> new V(0, 0, m);
		};
	}

	private static double distance2(V p, AABB a) {
		double dx = Math.max(0, Math.max(a.minX - p.x(), p.x() - a.maxX));
		double dy = Math.max(0, Math.max(a.minY - p.y(), p.y() - a.maxY));
		double dz = Math.max(0, Math.max(a.minZ - p.z(), p.z() - a.maxZ));
		return dx * dx + dy * dy + dz * dz;
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}
}
