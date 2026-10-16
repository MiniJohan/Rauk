package rauk.com.hotel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import rauk.com.hotel.HotelMaterials.Feel;

/**
 * The hotel's furniture and fittings (hotel design 12): what each piece is, how it sits, and its
 * shape as a list of boxes, the way Rauk's body is built. The game makes its blocks and their
 * outlines from this; the asset tool writes their models from the same boxes; the texture painter
 * paints what they're made of. Plain Java, no game classes.
 *
 * <p>Every piece is drawn facing north: its front towards -z, its back at z = 16 (a wall piece's
 * wall is there), standing on y = 0 (a ceiling piece hangs from the top of its cell). Sizes are in
 * sixteenths of a block. A piece several blocks big is drawn whole and cut into its cells.
 */
public final class HotelFurniture {
	private HotelFurniture() {
	}

	/** How a piece takes up space, which decides its block states. */
	public enum Form {
		/** One block. */
		SINGLE,
		/** One block that joins the same piece beside it into a row: a sofa, a bar counter. */
		RUN,
		/** One block that joins the same piece above and below it: a curtain. */
		STACK,
		/** One block that joins the same piece on any side, without a facing: a table. */
		TABLE,
		/** Several blocks, placed and broken together: a bed, a wardrobe, a fireplace. */
		MULTI
	}

	/** What it stands on or hangs from. Wall and ceiling pieces have no collision. */
	public enum Mount { FLOOR, WALL, CEILING }

	/** Whether it gives light, and how: on the hotel's wiring, or a flame. */
	public enum Light { NONE, ELECTRIC, FLAME }

	/** The faces of a box, in the order a box keeps their textures. */
	public static final String[] FACES = {"north", "east", "south", "west", "up", "down"};
	public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3, UP = 4, DOWN = 5;

	/**
	 * A box of a model, in sixteenths. {@code faces} holds a texture role for each face (null for no
	 * face). A glowing box shines when its piece is lit. A box can be turned about one axis (x, y or
	 * z, or '-' for none) by up to 45 degrees about an origin. Its textures are laid over it as
	 * tiles, running on from box to box, unless it has a {@code fit}: the bounds its textures are
	 * stretched over once (a door, a painting), kept when the box is cut.
	 */
	public record Box(double x0, double y0, double z0, double x1, double y1, double z1, String[] faces, boolean glow, char axis,
			double angle, double ox, double oy, double oz, double[] fit) {
		public Box {
			if (x1 < x0 || y1 < y0 || z1 < z0) {
				throw new IllegalArgumentException("Inside-out box " + x0 + "," + y0 + "," + z0 + " to " + x1 + "," + y1 + "," + z1);
			}
		}

		public boolean turned() {
			return axis != '-' && angle != 0;
		}

		Box with(double nx0, double ny0, double nz0, double nx1, double ny1, double nz1, String[] f) {
			return new Box(nx0, ny0, nz0, nx1, ny1, nz1, f, glow, axis, angle, ox, oy, oz, fit);
		}

		Box moved(double dx, double dy, double dz) {
			double[] f = fit == null ? null : new double[] {fit[0] + dx, fit[1] + dy, fit[2] + dz, fit[3] + dx, fit[4] + dy, fit[5] + dz};
			return new Box(x0 + dx, y0 + dy, z0 + dz, x1 + dx, y1 + dy, z1 + dz, faces, glow, axis, angle, ox + dx, oy + dy, oz + dz, f);
		}
	}

	/** Which state of a piece a model is for. */
	public record Look(String run, int joins, int look, boolean lit) {
		/** Joined to the same piece to the north, east, south or west (tables). */
		public boolean joined(int dir) {
			return (joins & (1 << dir)) != 0;
		}

		public boolean lowEnd() {
			return run.equals("single") || run.equals("left") || run.equals("bottom");
		}

		public boolean highEnd() {
			return run.equals("single") || run.equals("right") || run.equals("top");
		}
	}

	/**
	 * Where the reception safe's four dials are across its front, in sixteenths (puzzle 1: its block
	 * and renderer use these), first to last as someone in front of it reads them: from its own right.
	 */
	public static final double[] SAFE_DIALS = {25, 19, 13, 7};

	public static final List<String> RUNS = List.of("single", "left", "middle", "right");
	public static final List<String> STACKS = List.of("single", "bottom", "middle", "top");
	/** Joins bits, by direction index: north 1, east 2, south 4, west 8. */
	public static final int[] JOIN = {1, 2, 4, 8};

	/** A piece of furniture. Set up with the chained setters, then left alone. */
	public static final class Piece {
		public final String id, name;
		public final Form form;
		public Mount mount = Mount.FLOOR;
		public Feel feel = Feel.WOOD;
		public int w = 1, h = 1, d = 1;
		/** Whether it has a front, turned to face a way; without one it may be set down at any turn. */
		public boolean facing = true;
		public boolean solid = true;
		public Light light = Light.NONE;
		/** Its light level when lit. */
		public int glow;
		/** How many looks it has (portraits): a property, the same over all its cells. */
		public int looks = 1;
		/** Where its flames are (x, y, z, and 0 for a candle's, 1 for a fire's), for flicker and smoke. */
		public final List<double[]> flames = new ArrayList<>();
		/** Which of its cells give out the light, if not all of them. */
		public int[] glowParts;
		/** The textures its roles are painted with; a role not here is a texture of its own name. */
		public final Map<String, String> skin = new LinkedHashMap<>();
		/** Its group, for the creative tab and the catalogue. */
		public String group = "";
		/** A piece that is a block of another sort: "switch", a light switch (its looks: off and on). */
		public String kind = "";
		Function<Look, List<Box>> model;

		Piece(String id, String name, Form form) {
			this.id = id;
			this.name = name;
			this.form = form;
		}

		Piece size(int w, int h, int d) {
			this.w = w;
			this.h = h;
			this.d = d;
			return this;
		}

		Piece mount(Mount m) {
			mount = m;
			if (m != Mount.FLOOR) {
				solid = false;
			}
			return this;
		}

		Piece feel(Feel f) {
			feel = f;
			return this;
		}

		Piece noFacing() {
			facing = false;
			return this;
		}

		Piece soft() {
			solid = false;
			return this;
		}

		Piece light(Light l, int level) {
			light = l;
			glow = level;
			return this;
		}

		Piece looks(int n) {
			looks = n;
			return this;
		}

		Piece flame(double x, double y, double z) {
			flames.add(new double[] {x, y, z, 0});
			return this;
		}

		Piece fire(double x, double y, double z) {
			flames.add(new double[] {x, y, z, 1});
			return this;
		}

		Piece glowParts(int... parts) {
			glowParts = parts;
			return this;
		}

		Piece skin(String... pairs) {
			for (int i = 0; i + 1 < pairs.length; i += 2) {
				skin.put(pairs[i], pairs[i + 1]);
			}
			return this;
		}

		Piece model(Function<Look, List<Box>> m) {
			model = m;
			return this;
		}

		Piece group(String g) {
			group = g;
			return this;
		}

		Piece kind(String k) {
			kind = k;
			return this;
		}

		public int parts() {
			return w * h * d;
		}

		/** A cell's place in the piece, x across, y up and z back from its front, by part number. */
		public int[] cellOf(int part) {
			return new int[] {part % w, (part / w) % h, part / (w * h)};
		}

		public int partAt(int x, int y, int z) {
			return x + w * (y + h * z);
		}

		/** The texture a role is painted with: {@code rauk:block/...}, or as given if it names its own namespace. */
		public String texture(String role) {
			String t = skin.getOrDefault(role, role);
			return t.contains(":") ? t : "rauk:block/" + t;
		}

		/** Whether a cell of it gives light when lit. */
		public boolean glows(int part) {
			if (glowParts == null) {
				return true;
			}
			for (int p : glowParts) {
				if (p == part) {
					return true;
				}
			}
			return false;
		}

		/** Every look it has to be drawn in, with what it is told about its state. */
		public List<Look> allLooks() {
			List<Look> out = new ArrayList<>();
			List<String> runs = form == Form.RUN ? RUNS : form == Form.STACK ? STACKS : List.of("single");
			int joins = form == Form.TABLE ? 16 : 1;
			for (String run : runs) {
				for (int j = 0; j < joins; j++) {
					for (int look = 0; look < looks; look++) {
						out.add(new Look(run, j, look, false));
						if (light != Light.NONE || kind.equals("switch")) {
							out.add(new Look(run, j, look, true));
						}
					}
				}
			}
			return out;
		}

		public List<Box> boxes(Look look) {
			return model.apply(look);
		}
	}

	// --- cutting a piece into its cells ----------------------------------------------------------

	/**
	 * The boxes of one cell of a piece, in that cell's own sixteenths. Boxes are cut at every
	 * sixteenth boundary so each face's texture comes from one tile; faces where a box was cut are
	 * inside it and left out. A piece may reach past its cells at its edges (a chair's back over
	 * the top, a chandelier's arms out to the side): what does is kept by the edge cell. A turned
	 * box can't be cut: it goes whole to the cell its turning point is in.
	 */
	public static List<Box> cell(Piece piece, Look look, int part) {
		int[] c = piece.cellOf(part);
		List<Box> out = new ArrayList<>();
		for (Box b : piece.boxes(look)) {
			if (b.turned()) {
				int[] at = {cellIndex(b.ox(), piece.w), cellIndex(b.oy(), piece.h), cellIndex(b.oz(), piece.d)};
				if (at[0] == c[0] && at[1] == c[1] && at[2] == c[2]) {
					out.add(b.moved(-c[0] * 16, -c[1] * 16, -c[2] * 16));
				}
				continue;
			}
			double[] lo = {b.x0(), b.y0(), b.z0()}, hi = {b.x1(), b.y1(), b.z1()};
			int[] size = {piece.w, piece.h, piece.d};
			boolean empty = false;
			for (int a = 0; a < 3; a++) {
				boolean flat = lo[a] == hi[a];
				lo[a] = Math.max(lo[a], c[a] == 0 ? Double.NEGATIVE_INFINITY : c[a] * 16);
				hi[a] = Math.min(hi[a], c[a] == size[a] - 1 ? Double.POSITIVE_INFINITY : c[a] * 16 + 16);
				empty |= hi[a] < lo[a] || hi[a] == lo[a] && !flat;
			}
			if (empty) {
				continue;
			}
			for (Box piece16 : split(b.with(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], b.faces()), b)) {
				Box m = piece16.moved(-c[0] * 16, -c[1] * 16, -c[2] * 16);
				out.add(m);
			}
		}
		for (Box b : out) {
			for (double v : new double[] {b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1()}) {
				if (v < -16 || v > 32) {
					throw new IllegalStateException(piece.id + " reaches too far out of its cell: " + b);
				}
			}
		}
		return out;
	}

	private static int cellIndex(double v, int n) {
		return Math.clamp((int) Math.floor(v / 16), 0, n - 1);
	}

	/**
	 * A box cut at every multiple of 16, leaving out the faces where it was cut, which are inside
	 * the original (and where it was cut out of its cell, which a neighbour carries on).
	 */
	private static List<Box> split(Box b, Box original) {
		List<Box> out = new ArrayList<>();
		double[] xs = cuts(b.x0(), b.x1()), ys = cuts(b.y0(), b.y1()), zs = cuts(b.z0(), b.z1());
		for (int i = 0; i + 1 < xs.length || i == 0 && xs.length == 1; i++) {
			double x0 = xs[i], x1 = xs.length == 1 ? xs[0] : xs[i + 1];
			for (int j = 0; j + 1 < ys.length || j == 0 && ys.length == 1; j++) {
				double y0 = ys[j], y1 = ys.length == 1 ? ys[0] : ys[j + 1];
				for (int k = 0; k + 1 < zs.length || k == 0 && zs.length == 1; k++) {
					double z0 = zs[k], z1 = zs.length == 1 ? zs[0] : zs[k + 1];
					String[] f = b.faces().clone();
					// A face is kept only where the original box had it.
					if (x0 != original.x0()) {
						f[WEST] = null;
					}
					if (x1 != original.x1()) {
						f[EAST] = null;
					}
					if (y0 != original.y0()) {
						f[DOWN] = null;
					}
					if (y1 != original.y1()) {
						f[UP] = null;
					}
					if (z0 != original.z0()) {
						f[NORTH] = null;
					}
					if (z1 != original.z1()) {
						f[SOUTH] = null;
					}
					out.add(b.with(x0, y0, z0, x1, y1, z1, f));
				}
			}
		}
		return out;
	}

	private static double[] cuts(double lo, double hi) {
		if (lo == hi) {
			return new double[] {lo};
		}
		List<Double> c = new ArrayList<>();
		c.add(lo);
		for (double v = Math.floor(lo / 16) * 16 + 16; v < hi; v += 16) {
			if (v > lo) {
				c.add(v);
			}
		}
		c.add(hi);
		return c.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/** For each face: which axis its u and v run along (0 x, 1 y, 2 z), and whether they run backwards. */
	private static final int[][] FACE_AXES = {{0, 1, 1, 1}, {2, 1, 1, 1}, {0, 0, 1, 1}, {2, 0, 1, 1}, {0, 0, 2, 0}, {0, 0, 2, 1}};

	/**
	 * The texture coordinates of a face of a box: from where the box is, the 16 by 16 tile laid over
	 * the world so wood grain and patterns run on across boxes and cells; or, for a box with a fit,
	 * the texture stretched once over the fit's bounds.
	 */
	public static double[] uv(Box b, int face) {
		int[] f = FACE_AXES[face];
		double[] lo = {b.x0(), b.y0(), b.z0()}, hi = {b.x1(), b.y1(), b.z1()};
		double[] u = range(b, lo[f[0]], hi[f[0]], f[0], f[1] == 1), v = range(b, lo[f[2]], hi[f[2]], f[2], f[3] == 1);
		return new double[] {u[0], v[0], u[1], v[1]};
	}

	private static double[] range(Box b, double lo, double hi, int axis, boolean backwards) {
		if (b.fit() != null) {
			double start = b.fit()[axis], end = b.fit()[axis + 3], size = Math.max(1e-6, end - start);
			double a = backwards ? (end - hi) / size * 16 : (lo - start) / size * 16;
			double c = backwards ? (end - lo) / size * 16 : (hi - start) / size * 16;
			return new double[] {round(Math.clamp(a, 0, 16)), round(Math.clamp(c, 0, 16))};
		}
		double[] r = backwards ? new double[] {16 - hi, 16 - lo} : new double[] {lo, hi};
		fold(r);
		return r;
	}

	/** Brings a range into one tile (0 to 16), keeping its size; a range over 16 is squeezed into it. */
	private static void fold(double[] r) {
		double size = r[1] - r[0];
		if (size >= 16) {
			r[0] = 0;
			r[1] = 16;
			return;
		}
		double start = r[0] - Math.floor(r[0] / 16) * 16;
		if (start + size > 16 + 1e-9) {
			start = 16 - size;
		}
		r[0] = round(start);
		r[1] = round(start + size);
	}

	private static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}

	/** The outline of one cell: its boxes kept within the cell (a turned box by its untouched bounds). */
	public static List<double[]> outline(Piece piece, Look look, int part) {
		List<double[]> out = new ArrayList<>();
		for (Box b : cell(piece, look, part)) {
			double x0 = Math.max(0, b.x0()), y0 = Math.max(0, b.y0()), z0 = Math.max(0, b.z0());
			double x1 = Math.min(16, b.x1()), y1 = Math.min(16, b.y1()), z1 = Math.min(16, b.z1());
			if (x1 - x0 >= 0.5 && y1 - y0 >= 0.25 && z1 - z0 >= 0.5) {
				out.add(new double[] {x0, y0, z0, x1, y1, z1});
			}
		}
		return out;
	}

	// --- drawing boxes ---------------------------------------------------------------------------

	/** Builds a model's boxes: {@code box} adds one, the others change the last one added. */
	public static final class M {
		final List<Box> out = new ArrayList<>();

		public M box(double x0, double y0, double z0, double x1, double y1, double z1, String role) {
			String[] f = new String[6];
			Arrays.fill(f, role);
			out.add(new Box(x0, y0, z0, x1, y1, z1, f, false, '-', 0, 0, 0, 0, null));
			return this;
		}

		private Box last() {
			return out.get(out.size() - 1);
		}

		private void replace(Box b) {
			out.set(out.size() - 1, b);
		}

		/** The last box's face (by index) in another texture, or none. */
		public M face(int face, String role) {
			Box b = last();
			String[] f = b.faces().clone();
			f[face] = role;
			replace(b.with(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), f));
			return this;
		}

		public M top(String role) {
			return face(UP, role);
		}

		/** Top and bottom both. */
		public M ends(String role) {
			return face(UP, role).face(DOWN, role);
		}

		public M front(String role) {
			return face(NORTH, role);
		}

		public M back(String role) {
			return face(SOUTH, role);
		}

		/** Only the given faces, the rest left off. */
		public M only(int... faces) {
			Box b = last();
			String[] f = new String[6];
			for (int i : faces) {
				f[i] = b.faces()[i];
			}
			replace(b.with(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), f));
			return this;
		}

		public M glow() {
			Box b = last();
			replace(new Box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), b.faces(), true, b.axis(), b.angle(), b.ox(), b.oy(), b.oz(),
					b.fit()));
			return this;
		}

		/** The last box's textures stretched once over it, instead of tiled. */
		public M fit() {
			Box b = last();
			replace(new Box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), b.faces(), b.glow(), b.axis(), b.angle(), b.ox(), b.oy(),
					b.oz(), new double[] {b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1()}));
			return this;
		}

		/** Turns the last box about an axis through a point. */
		public M turn(char axis, double angle, double ox, double oy, double oz) {
			Box b = last();
			replace(new Box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), b.faces(), b.glow(), axis, angle, ox, oy, oz, b.fit()));
			return this;
		}

		/** Turns the last box about an axis through its own middle. */
		public M turn(char axis, double angle) {
			Box b = last();
			return turn(axis, angle, (b.x0() + b.x1()) / 2, (b.y0() + b.y1()) / 2, (b.z0() + b.z1()) / 2);
		}

		/** Adds another model's boxes, moved. */
		public M add(List<Box> boxes, double dx, double dy, double dz) {
			for (Box b : boxes) {
				out.add(b.moved(dx, dy, dz));
			}
			return this;
		}

		/** Every box so far, mirrored across x = mid (added to the ones there, for symmetric things). */
		public M mirrorX(double mid) {
			List<Box> copy = new ArrayList<>(out);
			for (Box b : copy) {
				String[] f = b.faces().clone();
				String e = f[EAST];
				f[EAST] = f[WEST];
				f[WEST] = e;
				double[] fit = b.fit() == null ? null
						: new double[] {2 * mid - b.fit()[3], b.fit()[1], b.fit()[2], 2 * mid - b.fit()[0], b.fit()[4], b.fit()[5]};
				out.add(new Box(2 * mid - b.x1(), b.y0(), b.z0(), 2 * mid - b.x0(), b.y1(), b.z1(), f, b.glow(), b.axis(),
						b.axis() == 'x' ? b.angle() : -b.angle(), 2 * mid - b.ox(), b.oy(), b.oz(), fit));
			}
			return this;
		}

		public List<Box> done() {
			return Collections.unmodifiableList(new ArrayList<>(out));
		}
	}

	/**
	 * The same boxes stood on their back, as if knocked over backwards: what was up now points to
	 * the front. Turned boxes are left out (they can't turn a second way).
	 */
	public static List<Box> tipped(List<Box> boxes, double depth) {
		double top = 0;
		for (Box b : boxes) {
			top = Math.max(top, b.y1());
		}
		// Turned about x: (x, y, z) to (x, depth - z, y). The back lies on the floor, the top
		// points to the back, the legs to the front; pulled forward if it would stick out behind.
		double shift = Math.max(0, top - 16);
		List<Box> out = new ArrayList<>();
		for (Box b : boxes) {
			if (b.turned()) {
				continue;
			}
			String[] f = new String[6];
			f[EAST] = b.faces()[EAST];
			f[WEST] = b.faces()[WEST];
			f[SOUTH] = b.faces()[UP];
			f[NORTH] = b.faces()[DOWN];
			f[DOWN] = b.faces()[SOUTH];
			f[UP] = b.faces()[NORTH];
			out.add(new Box(b.x0(), depth - b.z1(), b.y0() - shift, b.x1(), depth - b.z0(), b.y1() - shift, f, b.glow(), '-', 0, 0, 0,
					0, null));
		}
		return out;
	}

	// --- the catalogue -----------------------------------------------------------------------------

	private static final Map<String, Piece> BY_ID = new HashMap<>();

	public static List<Piece> all() {
		return FurnitureCatalog.ALL;
	}

	private static synchronized Map<String, Piece> byId() {
		if (BY_ID.isEmpty()) {
			for (Piece p : all()) {
				BY_ID.put(p.id, p);
			}
		}
		return BY_ID;
	}

	public static Piece get(String id) {
		Piece p = byId().get(id);
		if (p == null) {
			throw new IllegalArgumentException("No furniture " + id);
		}
		return p;
	}

	/** Whether a block id ({@code rauk:club_chair}) is a piece of furniture. */
	public static boolean is(String blockId) {
		return blockId.startsWith("rauk:") && byId().containsKey(blockId.substring(5));
	}
}
