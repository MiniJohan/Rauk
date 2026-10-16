package rauk.com.entity.nav;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.move.SurfaceNode;

/**
 * Where Rauk's body can be and how it gets from one place to the next (design doc 4), over any
 * {@link NavView}: the places are {@link SurfaceNode}s (a cell and the side of it it holds on to),
 * each with the largest posture whose box fits there.
 *
 * <ul>
 * <li><b>The floor:</b> it stands on whatever is under it, stairs, slabs and carpets included, at
 * the height its body would rest at, and only where there is something under at least half its
 * feet (not along the top of a railing). It walks in eight directions, up and down a step.</li>
 * <li><b>Walls and ceilings:</b> anything sturdy it can dig its nails into ({@link NavBlock}); it
 * climbs straight and diagonally across them, round inside and outside corners.</li>
 * <li><b>Between them:</b> onto a wall in front, over the edge of a ledge onto the face below, over
 * the top of a wall onto the floor beyond, down off a ledge, off a ceiling onto the floor.</li>
 * </ul>
 */
public final class NavRules {
	public static final RaukPosture[] FLOOR_POSTURES = {RaukPosture.QUADRUPED, RaukPosture.CRAWL, RaukPosture.SQUEEZE};
	public static final RaukPosture[] CLING_POSTURES = {RaukPosture.CLING, RaukPosture.SQUEEZE};
	public static final double TRANSITION = 0.8;
	/** The most a step changes the floor's height and is still walked rather than climbed or dropped. */
	public static final double STEP = 1.1;
	private static final int NONE = 2;
	private static final Vec3[] LEVEL_OFFSETS = {Vec3.ZERO, new Vec3(0.12, 0, 0), new Vec3(-0.12, 0, 0), new Vec3(0, 0, 0.12),
			new Vec3(0, 0, -0.12), new Vec3(0.5, 0, 0), new Vec3(-0.5, 0, 0), new Vec3(0, 0, 0.5), new Vec3(0, 0, -0.5)};
	private static final int[][] RING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

	/** Where the places it can be are kept once worked out: 0 for not yet, see {@link #code}. */
	public interface Cache {
		int get(int x, int y, int z, int surface);

		void put(int x, int y, int z, int surface, int code);

		/** A cache for one search in the world. */
		static Cache map() {
			// Made as they are needed: a quick look at one box (does a posture fit here?) needs none.
			Long2IntOpenHashMap[] maps = new Long2IntOpenHashMap[6];
			return new Cache() {
				@Override
				public int get(int x, int y, int z, int surface) {
					return maps[surface] == null ? 0 : maps[surface].get(BlockPos.asLong(x, y, z));
				}

				@Override
				public void put(int x, int y, int z, int surface, int code) {
					if (maps[surface] == null) {
						maps[surface] = new Long2IntOpenHashMap();
					}
					maps[surface].put(BlockPos.asLong(x, y, z), code);
				}
			};
		}
	}

	/** A way from one place to another, what it costs (about a block walked for each), and what sort of move it is. */
	public record Move(SurfaceNode to, double cost, Kind kind) {
	}

	public enum Kind { WALK, CHANGE, DROP }

	public final NavView view;
	private final Cache cache;

	public NavRules(NavView view, Cache cache) {
		this.view = view;
		this.cache = cache;
	}

	/** Rules over the world as it is, with a cache of their own. */
	public static NavRules world(net.minecraft.world.level.BlockGetter level) {
		return new NavRules(NavView.of(level), Cache.map());
	}

	// --- places -----------------------------------------------------------------------------------

	/** Where Rauk can be in this cell holding this side, or null if it can't. */
	@Nullable
	public SurfaceNode node(BlockPos cell, Direction surface) {
		return node(cell.getX(), cell.getY(), cell.getZ(), surface);
	}

	@Nullable
	public SurfaceNode node(int x, int y, int z, Direction surface) {
		int s = surface.ordinal();
		int code = cache.get(x, y, z, s);
		if (code == 0) {
			code = surface == Direction.DOWN ? floorCode(x, y, z) : clingCode(x, y, z, surface);
			cache.put(x, y, z, s, code);
		}
		return decode(x, y, z, surface, code);
	}

	/**
	 * A place as a number: bit 0 set if there is one, its posture in bits 1 to 3, which slide of
	 * the box it is in bits 4 to 7, and on the floor how far above the cell's bottom it stands, in
	 * 64ths, in bits 8 to 13. {@link #NONE} if there's none.
	 */
	public static int code(int posture, int offset, double raise) {
		return 1 | posture << 1 | offset << 4 | Mth.clamp((int) Math.round(raise * 64), 0, 63) << 8;
	}

	@Nullable
	public static SurfaceNode decode(int x, int y, int z, Direction surface, int code) {
		if ((code & 1) == 0) {
			return null;
		}
		RaukPosture p = RaukPosture.values()[(code >> 1) & 7];
		int offset = (code >> 4) & 15;
		BlockPos cell = new BlockPos(x, y, z);
		Vec3 at = position(cell, surface, p).add(offsets(surface)[offset]);
		if (surface == Direction.DOWN) {
			at = at.add(0, ((code >> 8) & 63) / 64.0, 0);
		}
		return new SurfaceNode(cell, surface, p, at);
	}

	/** The bottom centre of a posture's box placed in a cell against the given side. */
	public static Vec3 position(BlockPos cell, Direction surface, RaukPosture p) {
		double x = cell.getX() + 0.5, y = cell.getY(), z = cell.getZ() + 0.5;
		return switch (surface) {
			case DOWN -> new Vec3(x, y, z);
			case UP -> new Vec3(x, y + 1 - p.height, z);
			default -> {
				double shift = 0.5 - p.width / 2;
				yield new Vec3(x + surface.getStepX() * shift, y, z + surface.getStepZ() * shift);
			}
		};
	}

	/** Small slides along the surface that let a wide box fit a narrow space off-centre. */
	public static Vec3[] offsets(Direction surface) {
		if (surface.getAxis() == Direction.Axis.Y) {
			// The small ones let a squeezing body clear an open door's panel in a doorway.
			return LEVEL_OFFSETS;
		}
		return surface.getAxis() == Direction.Axis.X ? Z_SLIDES : X_SLIDES;
	}

	private static final Vec3[] Z_SLIDES = {Vec3.ZERO, new Vec3(0, 0, 0.5), new Vec3(0, 0, -0.5)};
	private static final Vec3[] X_SLIDES = {Vec3.ZERO, new Vec3(0.5, 0, 0), new Vec3(-0.5, 0, 0)};

	/**
	 * On the floor: the height its body rests at here is the highest thing under its feet within a
	 * step of what is under its middle; the place belongs to the cell that height is in. There must
	 * be something under its middle and under at least four of nine points spread over its feet,
	 * and its box must fit at that height.
	 */
	private int floorCode(int x, int y, int z) {
		for (AABB a : view.at(x, y, z).boxes) {
			// Solid through its middle, bottom to top: a wall, not a place.
			if (a.minY <= 0.01 && a.maxY >= 0.99 && a.minX <= 0.3 && a.maxX >= 0.7 && a.minZ <= 0.3 && a.maxZ >= 0.7) {
				return NONE;
			}
		}
		if (tooDeep(x, y, z) || !anyTops(x, y, z)) {
			return NONE;
		}
		for (int pi = 0; pi < FLOOR_POSTURES.length; pi++) {
			RaukPosture p = FLOOR_POSTURES[pi];
			double hw = p.width / 2;
			for (int oi = 0; oi < LEVEL_OFFSETS.length; oi++) {
				double cx = x + 0.5 + LEVEL_OFFSETS[oi].x, cz = z + 0.5 + LEVEL_OFFSETS[oi].z;
				double hc = topAt(cx, cz, y - 0.5, y + 0.999);
				if (Double.isNaN(hc)) {
					continue;
				}
				double h = Math.max(hc, maxTopUnder(cx - hw + 0.05, cz - hw + 0.05, cx + hw - 0.05, cz + hw - 0.05, hc, hc + 0.6));
				if (h < y || h >= y + 1) {
					continue;
				}
				if (footing(cx, cz, hw, h) < 4 || ridge(x, z, h)) {
					continue;
				}
				if (fits(p, cx, h, cz)) {
					return code(p.ordinal(), oi, h - y);
				}
			}
		}
		return NONE;
	}

	/** Anything to stand on under or round the cell at all. */
	private boolean anyTops(int x, int y, int z) {
		for (int dy = -1; dy <= 0; dy++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (view.at(x + dx, y + dy, z + dz).tops.length > 0) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Whether a place is the top of something narrow with a drop on both sides (a hedge, a railing,
	 * the top of a wall): no floor for its body, folded small or not. It steps over such things.
	 */
	private boolean ridge(int x, int z, double h) {
		return drop(x + 1, z, h) && drop(x - 1, z, h) || drop(x, z + 1, h) && drop(x, z - 1, h);
	}

	/** Open at its height beside it, with nothing to stand on within a step below. */
	private boolean drop(int x, int z, double h) {
		return !view.at(x, Mth.floor(h + 0.5), z).blocked && Double.isNaN(topAt(x + 0.5, z + 0.5, h - 1.1, h + 0.3));
	}

	/** How many of nine points over its feet have something to stand on a little under the given height. */
	private int footing(double cx, double cz, double hw, double h) {
		int n = 0;
		double r = Math.max(0.1, hw - 0.15);
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				if (!Double.isNaN(topAt(cx + i * r, cz + j * r, h - 0.6, h + 0.001))) {
					n++;
				}
			}
		}
		return n;
	}

	private int clingCode(int x, int y, int z, Direction surface) {
		if (view.at(x, y, z).blocked || tooDeep(x, y, z)) {
			return NONE;
		}
		if (!grips(x + surface.getStepX(), y + surface.getStepY(), z + surface.getStepZ(), surface.getOpposite())) {
			return NONE;
		}
		BlockPos cell = new BlockPos(x, y, z);
		Vec3[] slides = offsets(surface);
		for (int pi = 0; pi < CLING_POSTURES.length; pi++) {
			RaukPosture p = CLING_POSTURES[pi];
			Vec3 base = position(cell, surface, p);
			for (int oi = 0; oi < slides.length; oi++) {
				Vec3 at = base.add(slides[oi]);
				if (fits(p, at.x, at.y, at.z)) {
					return code(p.ordinal(), oi, 0);
				}
			}
		}
		return NONE;
	}

	/**
	 * Water (design doc 4.5): it has no fat and heavy bones, so it sinks. It wades through anything
	 * up to about five blocks deep, walking the bottom, and goes nowhere deeper.
	 */
	private boolean tooDeep(int x, int y, int z) {
		int depth = 0;
		for (int yy = y; depth <= 5 && view.at(x, yy, z).water; yy++) {
			depth++;
		}
		if (depth == 0) {
			return false;
		}
		for (int yy = y - 1; depth <= 5 && view.at(x, yy, z).water; yy--) {
			depth++;
		}
		return depth > 5;
	}

	// --- moves ------------------------------------------------------------------------------------

	/** Every way on from a place, with what each costs before the hunt's own prices. */
	public List<Move> moves(SurfaceNode n) {
		List<Move> out = new ArrayList<>(16);
		if (n.surface() == Direction.DOWN) {
			floorMoves(n, out);
		} else {
			clingMoves(n, out);
		}
		return out;
	}

	private void floorMoves(SurfaceNode n, List<Move> out) {
		BlockPos c = n.cell();
		double h = n.position().y;
		SurfaceNode[] ring = new SurfaceNode[8];
		for (int k = 0; k < 8; k += 2) {
			ring[k] = floorNear(c.getX() + RING[k][0], c.getY(), c.getZ() + RING[k][1], h, STEP);
		}
		for (int k = 1; k < 8; k += 2) {
			SurfaceNode a = ring[k - 1], b = ring[(k + 1) % 8];
			// Diagonally only across a level floor, never round the corner of a wall or a step.
			if (a != null && b != null && Math.abs(a.position().y - h) <= 0.6 && Math.abs(b.position().y - h) <= 0.6) {
				ring[k] = floorNear(c.getX() + RING[k][0], c.getY(), c.getZ() + RING[k][1], h, 0.6);
			}
		}
		for (SurfaceNode m : ring) {
			if (m != null) {
				add(out, n, m, Math.max(0, m.position().y - h) * 0.5, Kind.WALK);
			}
		}
		for (int k = 0; k < 8; k += 2) {
			if (ring[k] != null) {
				continue;
			}
			Direction d = Direction.fromDelta(RING[k][0], 0, RING[k][1]);
			BlockPos next = c.relative(d);
			if (view.at(next).blocked) {
				// Something low in the way (a hedge, a kerb, a fountain's rim) with floor beyond: over it.
				double t = topAt(next.getX() + 0.5, next.getZ() + 0.5, h + 0.2, h + STEP);
				if (!Double.isNaN(t)) {
					BlockPos beyond = next.relative(d);
					SurfaceNode m = floorNear(beyond.getX(), c.getY(), beyond.getZ(), h, STEP);
					if (m != null && fits(n.posture(), next.getX() + 0.5, t, next.getZ() + 0.5)) {
						add(out, n, m, 0.5 + (t - h), Kind.WALK);
					}
				}
				// A wall in front: up it.
				add(out, n, node(c, d), TRANSITION, Kind.CHANGE);
				continue;
			}
			// Open in front with no floor: over the edge onto the face below, or down to the floor.
			add(out, n, node(next.below(), d.getOpposite()), TRANSITION, Kind.CHANGE);
			for (int k2 = 2; k2 <= 9; k2++) {
				BlockPos at = next.below(k2 - 1);
				SurfaceNode m = node(at, Direction.DOWN);
				if (m != null) {
					// Only down a hole its body goes through: two blocks wide at least.
					if (h - m.position().y > STEP && dropPoint(m.position(), h, RaukPosture.CRAWL) != null) {
						add(out, n, m, 0.3 * k2, Kind.DROP);
					}
					break;
				}
				if (view.at(at).blocked) {
					break;
				}
			}
		}
		// Corners where a tall box can't reach the last cell before the wall: onto it a cell early.
		for (Direction o : Direction.Plane.HORIZONTAL) {
			add(out, n, node(c.relative(o), o), TRANSITION, Kind.CHANGE);
			add(out, n, node(c.above(), o), TRANSITION, Kind.CHANGE);
		}
	}

	/** The floor in a column near a height: in the cell at that level, or one up or down. */
	@Nullable
	public SurfaceNode floorNear(int x, int y, int z, double h, double tolerance) {
		SurfaceNode best = null;
		double bestD = tolerance;
		for (int dy : new int[] {0, 1, -1}) {
			SurfaceNode m = node(x, y + dy, z, Direction.DOWN);
			if (m != null) {
				double d = Math.abs(m.position().y - h);
				if (d <= bestD) {
					bestD = d;
					best = m;
				}
			}
		}
		return best;
	}

	private void clingMoves(SurfaceNode n, List<Move> out) {
		Direction s = n.surface();
		BlockPos c = n.cell();
		List<Direction> along = new ArrayList<>(4);
		for (Direction d : Direction.values()) {
			if (d.getAxis() == s.getAxis()) {
				continue;
			}
			BlockPos next = c.relative(d);
			if (view.at(next).blocked) {
				// An inside corner: onto the surface in front (the floor too, climbing down onto it).
				add(out, n, node(c, d), TRANSITION, Kind.CHANGE);
			} else if (view.at(next.relative(s)).blocked) {
				SurfaceNode m = node(next, s);
				if (m != null) {
					add(out, n, m, 0, Kind.WALK);
					along.add(d);
				}
			} else {
				// An outside corner: round the edge onto the far side of the block it was holding.
				add(out, n, node(next.relative(s), d.getOpposite()), TRANSITION, Kind.CHANGE);
			}
		}
		// Diagonally across the surface, where both ways round are surface too.
		for (int i = 0; i < along.size(); i++) {
			for (int j = i + 1; j < along.size(); j++) {
				Direction a = along.get(i), b = along.get(j);
				if (a.getAxis() == b.getAxis()) {
					continue;
				}
				BlockPos diag = c.relative(a).relative(b);
				if (view.at(diag).blocked || !view.at(diag.relative(s)).blocked) {
					continue;
				}
				SurfaceNode m = node(diag, s);
				if (m != null) {
					add(out, n, m, 0, Kind.WALK);
				}
			}
		}
		for (Direction o : Direction.values()) {
			if (o.getAxis() != s.getAxis()) {
				add(out, n, node(c.relative(o), o), TRANSITION, Kind.CHANGE);
				add(out, n, node(c.relative(s.getOpposite()), o), TRANSITION, Kind.CHANGE);
			}
		}
		if (s == Direction.UP) {
			// Let go of the ceiling and drop to the floor.
			for (int k = 1; k <= 9; k++) {
				BlockPos at = c.below(k);
				SurfaceNode m = node(at, Direction.DOWN);
				if (m != null) {
					add(out, n, m, 1.5 + 0.2 * (k + 1), Kind.DROP);
					break;
				}
				if (view.at(at).blocked) {
					break;
				}
			}
		}
	}

	private void add(List<Move> out, SurfaceNode from, @Nullable SurfaceNode to, double extra, Kind kind) {
		if (to == null || to.surface() != from.surface() && !sweeps(from, to)) {
			return;
		}
		double distance = Math.max(from.position().distanceTo(to.position()), 0.5);
		if (kind == Kind.DROP) {
			distance = Math.max(0.5, Math.hypot(from.position().x - to.position().x, from.position().z - to.position().z));
		}
		double[] t = terms(to);
		out.add(new Move(to, distance * t[0] + extra + t[1], kind));
	}

	/** What being at a place costs, by the block and once: per block walked, and once (a door to open). */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<double[]>[] terms = newTermMaps();

	@SuppressWarnings("unchecked")
	private static it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<double[]>[] newTermMaps() {
		return new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap[6];
	}

	private double[] terms(SurfaceNode to) {
		int s = to.surface().ordinal();
		if (terms[s] == null) {
			terms[s] = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
		}
		long key = to.cell().asLong();
		double[] t = terms[s].get(key);
		if (t == null) {
			// Wading is slow: round a pool rather than through it, when there's a way round.
			double wet = view.at(to.cell()).water ? 2 : 1;
			t = new double[] {surfaceCost(to.surface()) * postureCost(to.posture()) * wet + wallCost(to) + ledgeCost(to), doorCost(to)};
			terms[s].put(key, t);
		}
		return t;
	}

	/**
	 * Whether its body can get from one surface to another, not just be at both: going round an
	 * edge (under a landing and up its side) it moves out and then up, or up and then out, and one
	 * of the two has to be clear.
	 */
	private boolean sweeps(SurfaceNode from, SurfaceNode to) {
		Vec3 a = from.position(), b = to.position();
		if (from.surface() == Direction.DOWN || to.surface() == Direction.DOWN || Math.abs(a.y - b.y) < 0.3
				|| Math.abs(a.x - b.x) + Math.abs(a.z - b.z) < 0.3) {
			return true;
		}
		RaukPosture p = from.posture().width >= to.posture().width ? from.posture() : to.posture();
		for (Vec3 corner : new Vec3[] {new Vec3(b.x, a.y, b.z), new Vec3(a.x, b.y, a.z)}) {
			if (fits(p.dimensions().makeBoundingBox(corner).deflate(0.05))) {
				return true;
			}
		}
		return false;
	}

	public static double surfaceCost(Direction s) {
		return s == Direction.DOWN ? 1.0 : s == Direction.UP ? 1.8 : 1.5;
	}

	public static double postureCost(RaukPosture p) {
		return switch (p) {
			case CRAWL -> 1.5;
			case SQUEEZE -> 2.5;
			default -> 1.0;
		};
	}

	/**
	 * On the floor, hugging a wall costs a little: it goes down the middle of a corridor rather
	 * than along one side of it, so its long body and its hands keep off the walls.
	 */
	private double wallCost(SurfaceNode n) {
		if (n.surface() != Direction.DOWN) {
			return 0;
		}
		BlockPos c = n.cell();
		int walls = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (view.at(c.relative(d)).blocked || view.at(c.relative(d).above()).blocked) {
				walls++;
			}
		}
		return 0.04 * walls;
	}

	/** Along the edge of a drop (a gallery's, a stairwell's) it goes a little wide of it. */
	private double ledgeCost(SurfaceNode n) {
		if (n.surface() != Direction.DOWN) {
			return 0;
		}
		BlockPos c = n.cell();
		int edges = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = c.relative(d);
			if (!view.at(side).blocked && view.at(side.below()).tops.length == 0 && view.at(side.below(2)).tops.length == 0) {
				edges++;
			}
		}
		return Math.min(0.5, 0.25 * edges);
	}

	private double doorCost(SurfaceNode n) {
		AABB box = n.posture().dimensions().makeBoundingBox(n.position());
		for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
			for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
				for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
					if (view.at(x, y, z).openable) {
						return 1.5;
					}
				}
			}
		}
		return 0;
	}

	// --- blocks and boxes -------------------------------------------------------------------------

	public boolean blocked(BlockPos p) {
		return view.at(p).blocked;
	}

	/** Whether it can dig its nails into this face of the block (in water, any sturdy face). */
	public boolean grips(BlockPos p, Direction face) {
		return grips(p.getX(), p.getY(), p.getZ(), face);
	}

	public boolean grips(int x, int y, int z, Direction face) {
		boolean wet = view.at(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ()).water;
		return view.at(x, y, z).grips(face, wet);
	}

	public boolean fits(RaukPosture p, double x, double y, double z) {
		return fits(p.dimensions().makeBoundingBox(x, y, z).deflate(0.02));
	}

	/** Whether a box is clear of every block, doors it can open counted open, carpets not counted. */
	public boolean fits(AABB box) {
		for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
			for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
				for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
					for (AABB part : view.at(x, y, z).boxes) {
						if (part.minX + x < box.maxX && part.maxX + x > box.minX && part.minY + y < box.maxY && part.maxY + y > box.minY
								&& part.minZ + z < box.maxZ && part.maxZ + z > box.minZ) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	/** The highest top of something to stand on at a point, between two heights; NaN if none. */
	public double topAt(double px, double pz, double lo, double hi) {
		int bx = Mth.floor(px), bz = Mth.floor(pz);
		double fx = px - bx, fz = pz - bz, best = Double.NaN;
		for (int by = Mth.floor(lo) - 1; by <= Mth.floor(hi); by++) {
			for (AABB a : view.at(bx, by, bz).tops) {
				double t = by + a.maxY;
				if (t >= lo && t <= hi && fx >= a.minX - 1e-6 && fx <= a.maxX + 1e-6 && fz >= a.minZ - 1e-6 && fz <= a.maxZ + 1e-6
						&& !(t <= best)) {
					best = t;
				}
			}
		}
		return best;
	}

	/** The highest top of anything to stand on under a stretch of floor, between two heights; NaN if none. */
	private double maxTopUnder(double x0, double z0, double x1, double z1, double lo, double hi) {
		double best = Double.NaN;
		for (int by = Mth.floor(lo) - 1; by <= Mth.floor(hi); by++) {
			for (int bz = Mth.floor(z0); bz <= Mth.floor(z1); bz++) {
				for (int bx = Mth.floor(x0); bx <= Mth.floor(x1); bx++) {
					for (AABB a : view.at(bx, by, bz).tops) {
						double t = by + a.maxY;
						if (t >= lo && t <= hi && bx + a.minX < x1 && bx + a.maxX > x0 && bz + a.minZ < z1 && bz + a.maxZ > z0 && !(t <= best)) {
							best = t;
						}
					}
				}
			}
		}
		return best;
	}

	/**
	 * Where over a drop its body falls clear all the way down to a spot on the floor below: the
	 * spot itself or up to half a block off it, or null if nowhere near does.
	 */
	@Nullable
	public Vec3 dropPoint(Vec3 landing, double fromY, RaukPosture posture) {
		double[] shifts = {0, 0.5, -0.5};
		Vec3 best = null;
		for (double dx : shifts) {
			for (double dz : shifts) {
				Vec3 at = landing.add(dx, 0, dz);
				boolean clear = true;
				for (double y = landing.y; y <= fromY + 0.01 && clear; y += 0.5) {
					clear = fits(posture.dimensions().makeBoundingBox(at.x, Math.min(y, fromY - 0.05), at.z).deflate(0.02));
				}
				if (clear && (best == null || at.distanceToSqr(landing) < best.distanceToSqr(landing))) {
					best = at;
				}
			}
		}
		return best;
	}

	/** Why a cell and side is or isn't a place it can be: for the /rauk probe test command. */
	public String explain(BlockPos cell, Direction surface) {
		StringBuilder out = new StringBuilder(cell.toShortString() + " " + surface + ": ");
		NavBlock self = view.at(cell);
		if (surface != Direction.DOWN && self.blocked) {
			return out.append("cell blocked").toString();
		}
		if (surface == Direction.DOWN) {
			double hc = topAt(cell.getX() + 0.5, cell.getZ() + 0.5, cell.getY() - 0.5, cell.getY() + 0.999);
			out.append("floor under middle ").append(Double.isNaN(hc) ? "none" : String.format("%.2f", hc));
		} else {
			BlockPos support = cell.relative(surface);
			out.append(grips(support, surface.getOpposite()) ? "grips" : "NO grip");
		}
		SurfaceNode n = node(cell, surface);
		return out.append(n == null ? ", nothing fits" : ", fits " + n.posture() + " at " + n.position()).toString();
	}
}
