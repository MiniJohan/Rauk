package rauk.com.entity.nav;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.entity.move.SurfaceNode;

/**
 * The way Rauk goes, as a line it can follow smoothly rather than a string of blocks (design doc
 * 4.7). The search's places are pulled straight wherever its body fits all along the straight line
 * (so it doesn't zigzag block by block across a room or down a slanted corridor); the corners
 * left are rounded off as wide as there is room for, wider the faster it means to go; and the
 * whole is a point every few tenths of a block. Where it changes surface (floor to wall, wall to
 * ceiling) or drops, the places it changes at are kept as they are: those it takes hold at.
 */
public final class NavPath {
	/** How far apart the points along the line are. */
	public static final double STEP = 0.3;

	/**
	 * A point on the line: where, which surface its body holds there, how tight it has to fold, and
	 * how far along the line. Where it changes surface the search's place is kept with it.
	 */
	public record Point(Vec3 at, Direction surface, RaukPosture posture, double s, @Nullable SurfaceNode node) {
	}

	private final List<Point> points;
	/** The search's places it was made from. */
	public final List<SurfaceNode> nodes;
	/** Whether it gets all the way to the goal, or only as near as it could. */
	public final boolean reached;

	private NavPath(List<Point> points, List<SurfaceNode> nodes, boolean reached) {
		this.points = points;
		this.nodes = nodes;
		this.reached = reached;
	}

	public static final NavPath EMPTY = new NavPath(List.of(), List.of(), false);

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public Point point(int i) {
		return points.get(Mth.clamp(i, 0, points.size() - 1));
	}

	public Point last() {
		return points.get(points.size() - 1);
	}

	public double length() {
		return points.isEmpty() ? 0 : last().s();
	}

	public List<Point> points() {
		return points;
	}

	/**
	 * Where along the line a position is: the nearest point on the stretch from a point on, looking
	 * only a little way ahead (so a line that doubles back isn't jumped). Returns the index of the
	 * point that stretch starts at and the distance along the line.
	 */
	public double[] project(Vec3 pos, int from, boolean flat) {
		int bestI = Mth.clamp(from, 0, Math.max(0, points.size() - 1));
		double bestS = point(bestI).s(), bestD = Double.MAX_VALUE;
		for (int i = Math.max(0, from); i < Math.min(points.size() - 1, from + 14); i++) {
			Point a = points.get(i), b = points.get(i + 1);
			if (a.surface() != b.surface()) {
				break;
			}
			Vec3 ab = b.at().subtract(a.at()), ap = pos.subtract(a.at());
			if (flat) {
				ab = new Vec3(ab.x, 0, ab.z);
				ap = new Vec3(ap.x, 0, ap.z);
			}
			double len2 = ab.lengthSqr();
			double t = len2 < 1e-9 ? 0 : Mth.clamp(ap.dot(ab) / len2, 0, 1);
			double d = ap.subtract(ab.scale(t)).lengthSqr();
			if (flat) {
				// Height counts for a lot: on a round stair the turn below is right under the one it's on.
				double dy = pos.y - (a.at().y + t * (b.at().y - a.at().y));
				d += 6 * dy * dy;
			}
			if (d < bestD - 1e-9) {
				bestD = d;
				bestI = i;
				bestS = a.s() + t * (b.s() - a.s());
			}
		}
		return new double[] {bestI, bestS, Math.sqrt(bestD)};
	}

	/** The point a distance along the line, not past the end of the stretch on the surface the given point is on. */
	public Vec3 at(double s, int from) {
		int i = Math.max(0, from);
		Direction surface = point(i).surface();
		while (i < points.size() - 1 && points.get(i + 1).s() < s && points.get(i + 1).surface() == surface) {
			i++;
		}
		if (i >= points.size() - 1 || points.get(i + 1).surface() != surface) {
			return points.get(i).at();
		}
		Point a = points.get(i), b = points.get(i + 1);
		double t = b.s() - a.s() < 1e-9 ? 0 : Mth.clamp((s - a.s()) / (b.s() - a.s()), 0, 1);
		return a.at().lerp(b.at(), t);
	}

	/** The first point after this one on another surface, or after a drop: where it takes hold of something new. */
	public int nextChange(int from) {
		for (int i = Math.max(0, from) + 1; i < points.size(); i++) {
			Point a = points.get(i - 1), b = points.get(i);
			if (a.surface() != b.surface() || b.node() != null && a.node() != null && a.surface() == Direction.DOWN
					&& a.at().y - b.at().y > NavRules.STEP) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The same way with a turn walked first: the turn's points (on the floor, in the given posture),
	 * then on from one of the way's points, as one line.
	 */
	public NavPath withTurn(List<Vec3> turn, RaukPosture posture, int join) {
		List<Point> out = new ArrayList<>();
		double s = 0;
		Vec3 prev = null;
		for (Vec3 p : turn) {
			s += prev == null ? 0 : prev.distanceTo(p);
			out.add(new Point(p, Direction.DOWN, posture, s, null));
			prev = p;
		}
		for (int i = Math.max(0, join); i < points.size(); i++) {
			Point p = points.get(i);
			s += prev == null ? 0 : prev.distanceTo(p.at());
			out.add(new Point(p.at(), p.surface(), p.posture(), s, p.node()));
			prev = p.at();
		}
		return new NavPath(out, nodes, reached);
	}

	// --- making it --------------------------------------------------------------------------------

	/**
	 * The line for a way the search found: pulled straight, its corners rounded off with up to the
	 * given radius. With a hunt's prices, a straight line isn't taken where it would cost more than
	 * the way round (out into the light, into the prey's sight).
	 */
	public static NavPath of(NavRules rules, List<SurfaceNode> nodes, boolean reached, double radius, RaukPathfinder.Costs costs) {
		if (nodes.isEmpty()) {
			return EMPTY;
		}
		List<Point> out = new ArrayList<>();
		int i = 0;
		while (i < nodes.size()) {
			int j = i;
			while (j + 1 < nodes.size() && sameRun(nodes.get(j), nodes.get(j + 1))) {
				j++;
			}
			List<SurfaceNode> run = nodes.subList(i, j + 1);
			List<Vertex> line = pull(rules, run, costs);
			if (run.get(0).surface() == Direction.DOWN && radius > 0) {
				line = round(rules, line, radius);
			}
			// The places it changes surface at are kept with their points; the line between is sampled.
			densify(out, line, run.get(0).surface(), run.get(0), run.get(run.size() - 1));
			i = j + 1;
		}
		return new NavPath(out, List.copyOf(nodes), reached);
	}

	private static boolean sameRun(SurfaceNode a, SurfaceNode b) {
		if (a.surface() != b.surface()) {
			return false;
		}
		// Off a ledge it drops: the landing starts a new stretch.
		return a.surface() != Direction.DOWN || Math.abs(a.position().y - b.position().y) <= NavRules.STEP + 0.01;
	}

	/** A corner of the pulled line, and how tight its body is there. */
	private record Vertex(Vec3 at, RaukPosture posture) {
	}

	/**
	 * Pulls the line straight: from each corner kept, on to the furthest place along the way it can
	 * see in a line its body fits along all the way.
	 */
	private static List<Vertex> pull(NavRules rules, List<SurfaceNode> run, RaukPathfinder.Costs costs) {
		List<Vertex> out = new ArrayList<>();
		out.add(new Vertex(run.get(0).position(), run.get(0).posture()));
		int i = 0;
		while (i < run.size() - 1) {
			int best = i + 1;
			int misses = 0;
			for (int j = i + 2; j < run.size() && misses < 5; j++) {
				if (straight(rules, run, i, j, costs)) {
					best = j;
					misses = 0;
				} else {
					misses++;
				}
			}
			SurfaceNode n = run.get(best);
			if (best == i + 1 && n.surface() == Direction.DOWN && !clear(rules, run.get(i), n)) {
				// Even one place to the next: if its body would catch a corner on the way (each fits,
				// but not the straight line between, off-centre as they are), round the corner.
				Vec3 corner = corner(rules, run.get(i), n);
				if (corner != null) {
					out.add(new Vertex(corner, tightest(run, i, best)));
				}
			}
			out.add(new Vertex(n.position(), tightest(run, i, best)));
			i = best;
		}
		return out;
	}

	private static RaukPosture tightest(List<SurfaceNode> run, int a, int b) {
		RaukPosture t = run.get(a).posture();
		for (int k = a; k <= b; k++) {
			if (run.get(k).posture().ordinal() > t.ordinal()) {
				t = run.get(k).posture();
			}
		}
		return t;
	}

	/** Whether its body goes straight from one place on the way to another, as well as by the way between. */
	private static boolean straight(NavRules rules, List<SurfaceNode> run, int a, int b, RaukPathfinder.Costs costs) {
		Vec3 from = run.get(a).position(), to = run.get(b).position();
		Direction surface = run.get(a).surface();
		double length = surface == Direction.DOWN ? Math.hypot(to.x - from.x, to.z - from.z) : to.distanceTo(from);
		if (length > 24) {
			return false;
		}
		RaukPosture tight = tightest(run, a, b);
		double worst = 0;
		if (costs != RaukPathfinder.Costs.NONE) {
			for (int k = a; k <= b; k++) {
				worst = Math.max(worst, costs.extra(run.get(k)));
			}
		}
		int n = Math.max(1, (int) Math.ceil(length / 0.3));
		double h = from.y;
		for (int k = 1; k < n; k++) {
			Vec3 p = from.lerp(to, k / (double) n);
			SurfaceNode m;
			if (surface == Direction.DOWN) {
				m = rules.floorNear(Mth.floor(p.x), Mth.floor(h + 0.01), Mth.floor(p.z), h, 0.6);
				if (m == null) {
					return false;
				}
				h = m.position().y;
				p = new Vec3(p.x, h, p.z);
			} else {
				m = rules.node(clingCell(p, surface, run.get(a).posture()), surface);
				if (m == null) {
					return false;
				}
			}
			// A little room to spare all along, or it keeps to the search's own places: following a line
			// it is never exactly on it, and a door's panel in a doorway leaves a squeezing body very little.
			if (m.posture().ordinal() > tight.ordinal() || !rules.fits(spare(m.posture(), p))) {
				return false;
			}
			if (costs != RaukPathfinder.Costs.NONE && costs.extra(m) > worst + 0.3) {
				return false;
			}
		}
		return surface != Direction.DOWN || Math.abs(to.y - h) <= 0.61;
	}

	/** Whether its body goes straight from one place to the next along the floor without touching anything. */
	private static boolean clear(NavRules rules, SurfaceNode a, SurfaceNode b) {
		return clear(rules, a.position(), b.position(), a.posture().ordinal() >= b.posture().ordinal() ? a.posture() : b.posture());
	}

	private static boolean clear(NavRules rules, Vec3 from, Vec3 to, RaukPosture p) {
		int n = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.2));
		for (int k = 1; k < n; k++) {
			Vec3 q = from.lerp(to, k / (double) n);
			if (!rules.fits(p, q.x, Math.max(from.y, to.y), q.z)) {
				return false;
			}
		}
		return true;
	}

	/** A turn between two places that its body can go straight to and on from: along one way first, then the other. */
	@Nullable
	private static Vec3 corner(NavRules rules, SurfaceNode a, SurfaceNode b) {
		RaukPosture p = a.posture().ordinal() >= b.posture().ordinal() ? a.posture() : b.posture();
		Vec3 from = a.position(), to = b.position();
		double y = Math.max(from.y, to.y);
		for (Vec3 c : new Vec3[] {new Vec3(to.x, y, from.z), new Vec3(from.x, y, to.z)}) {
			if (rules.fits(p, c.x, c.y, c.z) && clear(rules, from, c, p) && clear(rules, c, to, p)) {
				return c;
			}
		}
		return null;
	}

	/** The cell a body clinging at a point is in (its box's bottom centre is off the cell's middle). */
	private static net.minecraft.core.BlockPos clingCell(Vec3 p, Direction surface, RaukPosture posture) {
		Vec3 probe = switch (surface) {
			case UP -> p.add(0, posture.height - 0.05, 0);
			default -> p.add(surface.getStepX() * (posture.width / 2 - 0.45), 0.5, surface.getStepZ() * (posture.width / 2 - 0.45));
		};
		return net.minecraft.core.BlockPos.containing(probe);
	}

	/**
	 * Rounds the corners off: each becomes a curve from a little before it to a little after, as
	 * wide as the radius allows and there is room for its body (and floor under it) all round.
	 */
	private static List<Vertex> round(NavRules rules, List<Vertex> line, double radius) {
		if (line.size() < 3) {
			return line;
		}
		List<Vertex> out = new ArrayList<>();
		out.add(line.get(0));
		for (int i = 1; i < line.size() - 1; i++) {
			Vertex prev = line.get(i - 1), v = line.get(i), next = line.get(i + 1);
			Vec3 a = flat(v.at().subtract(prev.at())), b = flat(next.at().subtract(v.at()));
			double lin = a.length(), lout = b.length();
			if (lin < 1e-3 || lout < 1e-3) {
				out.add(v);
				continue;
			}
			a = a.scale(1 / lin);
			b = b.scale(1 / lout);
			double angle = Math.acos(Mth.clamp(a.dot(b), -1, 1));
			if (angle < 0.12) {
				out.add(v);
				continue;
			}
			double t = Math.min(radius * Math.tan(Math.min(angle, 2.6) / 2), Math.min(0.45 * lin, 0.45 * lout));
			List<Vertex> curve = null;
			for (int tries = 0; tries < 4 && t > 0.15 && curve == null; tries++, t *= 0.5) {
				curve = curve(rules, prev, v, next, a, b, t, lin, lout);
			}
			if (curve != null) {
				out.addAll(curve);
			} else {
				out.add(v);
			}
		}
		out.add(line.get(line.size() - 1));
		return out;
	}

	@Nullable
	private static List<Vertex> curve(NavRules rules, Vertex prev, Vertex v, Vertex next, Vec3 a, Vec3 b, double t, double lin, double lout) {
		Vec3 p0 = v.at().subtract(a.scale(t)).add(0, (prev.at().y - v.at().y) * t / lin, 0);
		Vec3 p2 = v.at().add(b.scale(t)).add(0, (next.at().y - v.at().y) * t / lout, 0);
		RaukPosture posture = v.posture().ordinal() >= next.posture().ordinal() ? v.posture() : next.posture();
		int n = Math.max(4, (int) Math.ceil(2 * t / 0.3));
		List<Vertex> out = new ArrayList<>(n + 1);
		double h = p0.y;
		for (int k = 0; k <= n; k++) {
			double u = k / (double) n;
			Vec3 q = p0.scale((1 - u) * (1 - u)).add(v.at().scale(2 * u * (1 - u))).add(p2.scale(u * u));
			SurfaceNode m = rules.floorNear(Mth.floor(q.x), Mth.floor(h + 0.01), Mth.floor(q.z), h, 0.6);
			if (m == null || m.posture().ordinal() > posture.ordinal() || !rules.fits(spare(m.posture(), new Vec3(q.x, m.position().y, q.z)))) {
				return null;
			}
			h = m.position().y;
			out.add(new Vertex(new Vec3(q.x, h, q.z), posture));
		}
		return out;
	}

	/** Its box at a point with a little room to spare round it. */
	private static net.minecraft.world.phys.AABB spare(RaukPosture p, Vec3 at) {
		return p.dimensions().makeBoundingBox(at).inflate(0.04, -0.02, 0.04);
	}

	private static Vec3 flat(Vec3 v) {
		return new Vec3(v.x, 0, v.z);
	}

	/** A point every few tenths of a block along the line; the stretch's first and last places kept as they are. */
	private static void densify(List<Point> out, List<Vertex> line, Direction surface, SurfaceNode first, SurfaceNode last) {
		double s = out.isEmpty() ? 0 : out.get(out.size() - 1).s() + out.get(out.size() - 1).at().distanceTo(line.get(0).at());
		out.add(new Point(line.get(0).at(), surface, line.get(0).posture(), s, first));
		for (int i = 1; i < line.size(); i++) {
			Vec3 a = line.get(i - 1).at(), b = line.get(i).at();
			double len = a.distanceTo(b);
			int n = Math.max(1, (int) Math.ceil(len / STEP));
			for (int k = 1; k <= n; k++) {
				Vec3 p = a.lerp(b, k / (double) n);
				s += len / n;
				boolean end = i == line.size() - 1 && k == n;
				out.add(new Point(p, surface, line.get(i).posture(), s, end ? last : null));
			}
		}
	}
}
