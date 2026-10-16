package rauk.com.client.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rauk's body as a tree of cuboid parts, posed like the reference drawing. The game builds its
 * model from this and the texture painter paints from it, so the two always agree. It is plain
 * Java with no Minecraft classes, so the painter can run outside the game.
 *
 * <p>Everything is built in "design space": blocks, y up, z forward (the way it faces), x to its
 * left, with the origin on the ground under the hip joints. Box sizes are whole model units
 * (1/64 block), so every box face covers whole texture pixels at 64 pixels per block.
 *
 * <p>The spine is one continuous chain of vertebrae from the skull to the tail tip, laid along a
 * single curve, so the neck flows into the hump, the hump into the waist and the waist into the
 * tail. Ribs, limbs and spikes hang off that chain the way a skeleton's would, which also gives
 * the procedural animation real joints to bend.
 */
public final class RaukRig {
	/** Model units, and texture pixels, per block. */
	public static final int UNITS = 64;
	public static final int TEXTURE_WIDTH = 1024;
	private static final int[] SIDES = {1, -1};

	public enum Mat { BONE, SINEW, CAVITY, SOCKET, CLAW, TOOTH, MOUTH, DROOL, EYE, GLOW }

	// Limb lengths in blocks.
	public static final double UPPER_ARM = 1.70, FOREARM = 1.80, PALM = 0.36;
	public static final double THIGH = 1.80, SHIN = 1.35, METATARSUS = 1.15;

	/**
	 * The line through the vertebrae from the skull to the tail tip, traced from the reference:
	 * the neck arches forward and down from the hump, the spine drops behind the ribs to the
	 * pelvis, and the tail falls behind the legs, then runs forward along the ground beside the
	 * feet and curls up at the tip.
	 */
	private static final V[] AXIS = {
			new V(0, 4.40, 3.38), // 0: atlas, where the skull hangs
			new V(0, 4.82, 3.12),
			new V(0, 5.22, 2.70),
			new V(0, 5.50, 2.20),
			new V(0, 5.68, 1.64), // 4: base of the neck
			new V(0, 5.77, 1.05), // 5: top of the hump
			new V(0, 5.68, 0.50),
			new V(0, 5.38, 0.12), // 7: back of the ribcage
			new V(0, 4.88, -0.06),
			new V(0, 4.40, -0.16),
			new V(0, 3.98, -0.26), // 10: sacrum
			new V(0, 3.55, -0.42),
			new V(0, 3.05, -0.62),
			new V(0, 2.50, -0.84),
			new V(0, 1.95, -1.04),
			new V(0, 1.40, -1.20),
			new V(0, 0.90, -1.30),
			new V(0, 0.46, -1.24),
			new V(-0.02, 0.17, -0.98),
			new V(-0.06, 0.09, -0.45),
			new V(-0.14, 0.08, 0.20),
			new V(-0.30, 0.08, 0.95),
			new V(-0.52, 0.08, 1.70),
			new V(-0.62, 0.10, 2.35),
			new V(-0.50, 0.20, 2.85),
			new V(-0.30, 0.40, 3.05),
	};
	/** Control points on the axis where the regions change. */
	public static final int NECK_BASE = 4, HUMP = 5, RIBS_END = 7, SACRUM = 10;
	/** The skull is laid out in units below, then scaled up by this much. */
	private static final double SKULL_SCALE = 1.3;

	/**
	 * Along the axis, keyed by control-point index: vertebra width and height in units, spike
	 * length and vertebra spacing in blocks. The neck is thick, the thoracic vertebrae are slim
	 * under their tall spikes (the ribs carry the bulk), and the tail tapers to a point.
	 */
	private static final double[][] PROFILE = {
			{0, 15, 14, 0.08, 0.17},
			{2, 20, 19, 0.12, 0.20},
			{4, 22, 21, 0.15, 0.21},
			{5, 15, 15, 0.21, 0.21},
			{7, 15, 14, 0.18, 0.20},
			{8.5, 16, 14, 0.15, 0.19},
			{10, 18, 15, 0.13, 0.19},
			{12, 16, 15, 0.15, 0.18},
			{16, 12, 11, 0.12, 0.15},
			{20, 7, 7, 0.07, 0.12},
			{25, 3, 3, 0.03, 0.09},
	};

	/** Ribcage half-width and depth below the spine, from the front of the chest (0) to the back (1). */
	private static final double[][] RIB_WIDTH = {{0, 0.40}, {0.35, 0.50}, {0.7, 0.46}, {1, 0.34}};
	private static final double[][] RIB_DEPTH = {{0, 0.94}, {0.35, 1.27}, {0.7, 1.16}, {1, 0.66}};
	/**
	 * One rib, as fractions of that width and depth, plus how far it sweeps back toward the tail:
	 * back and down from the spine, bowing, with the free end turning forward again.
	 */
	private static final double[][] RIB_PATH = {
			{0.10, 0.02, 0}, {0.75, 0.12, 0.04}, {1.0, 0.38, 0.16}, {0.96, 0.65, 0.29}, {0.72, 0.88, 0.37}, {0.38, 1.0, 0.36}};

	/**
	 * The bony hands that grip the skull from behind (the second reference), in skull units:
	 * x to the skull's left, y up through the crown, z out through the face. Each finger runs from
	 * the knuckle behind the skull to the claw tip; the last span is the claw.
	 */
	private static final double[][][] LEFT_SKULL_FINGERS = {
			{{10, 4, -22}, {8, 16, -19}, {7, 21.5, -10}, {7, 22, 0}, {7, 19, 9}, {7, 13, 15}, {7, 8, 18}}, // over the crown
			{{13, 1, -21}, {15.5, 11, -15}, {17, 13, -5}, {17, 10, 5}, {16.5, 4, 11}, {15, -1, 15}}, // the temple
			{{14, -2, -20}, {17, 2, -12}, {18, -3, -2}, {18, -9, 6}, {16, -14, 12}}, // over the cheekbone
			{{9, -8, -24}, {7, -15, -15}, {5, -17, -7}}, // thumb, under the skull
	};
	private static final double[][][] RIGHT_SKULL_FINGERS = {
			{{-10, 4, -22}, {-6, 16, -19}, {-3, 21.5, -9}, {-2, 22, 1}, {-2, 19, 9}, {-2, 14, 15}, {-2, 9, 18}},
			{{-13, 1, -21}, {-15.5, 11, -15}, {-17, 13, -5}, {-17, 10, 5}, {-16.5, 4, 11}, {-15, -1, 15}},
			{{-14, -2, -20}, {-17, 2, -12}, {-18, -4, -2}, {-17, -11, 6}, {-14, -17, 12}, {-11, -21, 15}}, // reaches the mouth
			{{-9, -8, -24}, {-7, -15, -15}, {-5, -17, -7}},
	};

	public final List<Part> parts = new ArrayList<>();
	/** Points the texture painter needs: where the mouth is, and so on. */
	public final Map<String, V> marks = new HashMap<>();
	/** The vertebrae from the pelvis forward to the atlas, and where each sits on the axis (control-point index). */
	public final List<Part> spine = new ArrayList<>();
	public final List<Double> spineIndex = new ArrayList<>();
	/** The tail vertebrae from the pelvis back to the tip, and the joints between them (one more: the tip). */
	public final List<Part> tail = new ArrayList<>();
	public final List<V> tailJoints = new ArrayList<>();
	/** Each string of drool, top to bottom. */
	public final List<List<Part>> drool = new ArrayList<>();
	private final Map<String, Part> byName = new HashMap<>();
	private int textureHeight;
	private static RaukRig shared;

	private RaukRig() {
	}

	/** One rig built on first use and shared by the model and the animator; it never changes. */
	public static synchronized RaukRig shared() {
		if (shared == null) {
			shared = build();
		}
		return shared;
	}

	public static RaukRig build() {
		RaukRig rig = new RaukRig();
		rig.spineAndTail();
		rig.pelvis();
		for (int m : SIDES) {
			rig.arm(m);
			rig.leg(m);
		}
		rig.skull();
		rig.pack();
		return rig;
	}

	public int textureWidth() {
		return TEXTURE_WIDTH;
	}

	public int textureHeight() {
		return textureHeight;
	}

	public Part part(String name) {
		return byName.get(name);
	}

	/**
	 * The part's pose relative to its parent in Minecraft model space (units, y down, facing -z,
	 * ground at y = 24): {x, y, z, xRot, yRot, zRot}, rotations in radians applied Z, then Y, then X.
	 */
	public static double[] modelPose(Part p) {
		Frame pf = p.parent == null ? Frame.WORLD : p.parent.frame;
		return modelPose(pf.local(p.frame.o()), pf.localDir(p.frame.x()), pf.localDir(p.frame.y()), pf.localDir(p.frame.z()),
				p.parent == null);
	}

	/**
	 * The same, from a local transform in design space: origin o and axes cx, cy, cz relative to the
	 * parent. Top-level parts sit relative to the model root, which puts the ground at y = 24.
	 */
	public static double[] modelPose(V o, V cx, V cy, V cz, boolean topLevel) {
		// Design space to model space flips y and z: R_model = C R C with C = diag(1, -1, -1).
		double[][] r = {
				{cx.x(), -cy.x(), -cz.x()},
				{-cx.y(), cy.y(), cz.y()},
				{-cx.z(), cy.z(), cz.z()}};
		double xRot, yRot, zRot;
		if (Math.abs(r[2][0]) < 0.99999) {
			yRot = Math.asin(-r[2][0]);
			xRot = Math.atan2(r[2][1], r[2][2]);
			zRot = Math.atan2(r[1][0], r[0][0]);
		} else {
			yRot = r[2][0] < 0 ? Math.PI / 2 : -Math.PI / 2;
			xRot = Math.atan2(-r[1][2], r[1][1]);
			zRot = 0;
		}
		double y = -o.y() * UNITS + (topLevel ? 24 : 0);
		return new double[] {o.x() * UNITS, y, -o.z() * UNITS, xRot, yRot, zRot};
	}

	/** The box's minimum corner in the part's model space (units). */
	public static double[] modelCorner(Box b) {
		return new double[] {b.x0 * UNITS, -b.y0 * UNITS - b.h, -b.z0 * UNITS - b.d};
	}

	// --- spine, ribs and tail -------------------------------------------------------------------

	private void spineAndTail() {
		Curve axis = new Curve(AXIS, 48);
		V[] dorsal = axis.transport(HUMP, V.Y);
		double sacrum = axis.ctrlS[SACRUM];

		// From the sacrum forward to the skull: pelvis, lumbar, thoracic, then neck vertebrae.
		List<Double> front = stations(axis, sacrum, 0, -1);
		Part prev = null;
		for (int i = 0; i + 1 < front.size(); i++) {
			double a = front.get(i), b = front.get(i + 1), idx = axis.index((a + b) / 2);
			V pa = axis.at(a), pb = axis.at(b);
			Part p = add(i == 0 ? "pelvis" : "spine_" + i, prev, Frame.along(pa, pb.sub(pa), axis.along(dorsal, (a + b) / 2)));
			double len = pb.sub(pa).len();
			vertebra(p, len, idx, true);
			if (idx > NECK_BASE - 0.15 && idx < RIBS_END + 0.25) {
				ribs(p, len, Math.clamp((idx - NECK_BASE) / (RIBS_END - NECK_BASE), 0, 1), i);
			}
			spine.add(p);
			spineIndex.add(idx);
			prev = p;
		}

		// From the sacrum back to the tail tip.
		List<Double> back = stations(axis, sacrum, axis.length(), 1);
		prev = spine.get(0);
		for (int i = 0; i + 1 < back.size(); i++) {
			double a = back.get(i), b = back.get(i + 1), idx = axis.index((a + b) / 2);
			V pa = axis.at(a), pb = axis.at(b);
			Part p = add("tail_" + i, prev, Frame.along(pa, pb.sub(pa), axis.along(dorsal, (a + b) / 2)));
			vertebra(p, pb.sub(pa).len(), idx, false);
			tail.add(p);
			tailJoints.add(pa);
			prev = p;
		}
		tailJoints.add(axis.at(back.get(back.size() - 1)));
	}

	private static List<Double> stations(Curve axis, double from, double to, int dir) {
		List<Double> out = new ArrayList<>();
		double s = from;
		out.add(s);
		while (true) {
			double step = profile(axis.index(s))[3];
			double next = s + dir * step;
			if ((to - next) * dir <= step * 0.5) {
				out.add(to);
				return out;
			}
			out.add(next);
			s = next;
		}
	}

	/** One vertebra: a body, the processes its region has, and a spike raked toward the tail. */
	private void vertebra(Part p, double len, double idx, boolean towardHead) {
		double[] pr = profile(idx);
		int w = (int) Math.round(pr[0]), h = (int) Math.round(pr[1]);
		double mid = len / 2, tailward = towardHead ? -1 : 1;
		box(p, 0, 0, mid, w, h, units(len) + 2, Mat.BONE);
		double ringZ = towardHead ? len - 1.5 / UNITS : 1.5 / UNITS;
		if (idx < NECK_BASE + 0.3) {
			// Neck: a raised ring at the head end, a keel underneath, knobs at the sides.
			box(p, 0, 0, ringZ, w + 5, h + 4, 3, Mat.BONE);
			box(p, 0, -(h / 2.0 + 1.5) / UNITS, mid, 6, 3, units(len * 0.6), Mat.BONE);
			for (int m : SIDES) {
				box(p, m * (w / 2.0 + 2) / UNITS, 0.01, mid, 4, 6, units(len * 0.5), Mat.BONE);
			}
		} else if (idx > RIBS_END + 0.3 && towardHead) {
			// Lower back: flat transverse processes.
			for (int m : SIDES) {
				box(p, m * (w / 2.0 + 5) / UNITS, 0.01, mid, 10, 3, units(len * 0.55), Mat.BONE);
			}
		} else if (!towardHead) {
			// Tail: a knobbed joint at each end, chevrons under the first half.
			if (w >= 5) {
				box(p, 0, 0, ringZ, w + 2, h + 2, 2, Mat.BONE);
			}
			if (idx < SACRUM + 7) {
				box(p, 0, -(h / 2.0 + 2) / UNITS, mid, 3, 4, units(len * 0.5), Mat.BONE);
			}
		}
		spike(p, len, h, pr[2], tailward);
	}

	/** A thorn on the back of a vertebra, raked toward the tail and hooked at the tip. */
	private void spike(Part v, double len, int h, double length, double tailward) {
		if (length < 0.015) {
			return;
		}
		Frame f = v.frame;
		V tail = f.z().mul(tailward);
		double rake = Math.toRadians(36);
		V dir = f.y().mul(Math.cos(rake)).add(tail.mul(Math.sin(rake)));
		int thick = Math.max(1, (int) Math.round(length * 20)), wide = Math.max(2, (int) Math.round(length * 36));
		Frame base = Frame.bar(f.point(0, h / 2.0 / UNITS - 0.012, len * 0.5), dir, f.x());
		Part s1 = bar(v.name + "_spike", v, base, length * 0.55, thick, wide, 0.012, Mat.BONE);
		Frame hook = base.at(base.point(0, length * 0.55, 0)).rotate(dir.cross(tail).norm(), Math.toRadians(32));
		bar(v.name + "_spike_tip", s1, hook, length * 0.5, Math.max(1, thick - 1), Math.max(1, wide / 2), 0.01, Mat.BONE);
	}

	/** A pair of ribs arching down from a thoracic vertebra, and the dark body cavity they cage. */
	private void ribs(Part v, double len, double u, int index) {
		double width = interp(u, RIB_WIDTH), depth = interp(u, RIB_DEPTH);
		Frame f = v.frame;
		double zc = len * 0.5;
		for (int m : SIDES) {
			V[] ctrl = new V[RIB_PATH.length];
			for (int i = 0; i < ctrl.length; i++) {
				double[] r = RIB_PATH[i];
				ctrl[i] = f.point(m * width * r[0], -depth * r[1], zc - r[2]);
			}
			chain("rib_" + side(m) + "_" + index, v, Curve.resample(ctrl, 7), f.z(), 4, 4, 3, 3, 0.012, Mat.BONE);
		}
		// The cavity follows the ribs' sweep so it stays inside them.
		int d = units(len) + 2;
		box(v, 0, -depth * 0.22, zc - 0.06, units(1.2 * width), units(depth * 0.30), d, Mat.CAVITY);
		box(v, 0, -depth * 0.55, zc - 0.22, units(1.7 * width), units(depth * 0.42), d, Mat.CAVITY);
		box(v, 0, -depth * 0.80, zc - 0.33, units(1.0 * width), units(depth * 0.14), d, Mat.CAVITY);
	}

	// --- pelvis and legs ----------------------------------------------------------------------

	private void pelvis() {
		Part pelvis = spine.get(0);
		box(pelvis, 0, -0.03, 0.08, 24, 9, 22, Mat.BONE); // sacrum
		for (int m : SIDES) {
			String n = side(m);
			V hip = hip(m);
			V lateral = new V(m, 0, 0);
			Part socket = add("hip_" + n, pelvis, Frame.bar(hip, V.Y, V.X));
			box(socket, m * 0.02, 0, 0, 12, 12, 12, Mat.BONE);
			bar("ilium_" + n, pelvis, hip.add(new V(0, 0.04, -0.02)), new V(m * 0.25, 4.30, -0.22), lateral, 4, 22, 0.03, Mat.BONE);
			bar("iliac_crest_" + n, pelvis, new V(m * 0.22, 4.30, -0.04), new V(m * 0.27, 4.27, -0.38), lateral, 6, 6, 0.02, Mat.BONE);
			bar("ischium_" + n, pelvis, hip, new V(m * 0.18, 3.52, -0.32), lateral, 6, 6, 0.02, Mat.BONE);
			bar("pubis_" + n, pelvis, hip, new V(m * 0.05, 3.62, 0.20), lateral, 5, 5, 0.02, Mat.BONE);
		}
	}

	private static V hip(int m) {
		return new V(m * 0.30, 3.80, 0.02);
	}

	/** Digitigrade legs in a stalking stride: the left reaches forward, the right pushes off behind. */
	private void leg(int m) {
		String n = side(m);
		V hip = hip(m);
		V ball = m > 0 ? new V(0.36, 0.30, 1.15) : new V(-0.36, 0.30, -0.70);
		V meta = (m > 0 ? new V(0, -0.85, 0.53) : new V(0, -0.917, 0.399)).norm();
		V ankle = ball.sub(meta.mul(METATARSUS));
		V knee = ik(hip, ankle, THIGH, SHIN, new V(m * 0.15, 0, 1));

		// Local axes on every limb bone: x to the left, y down the bone, z behind it.
		Part thigh = add("leg_" + n + "_thigh", spine.get(0), Frame.bar(hip, knee.sub(hip), V.X));
		box(thigh, -m * 0.05, 0.02, 0, 10, 10, 10, Mat.BONE); // femoral head
		box(thigh, m * 0.07, 0.07, 0.01, 8, 10, 9, Mat.BONE); // greater trochanter
		box(thigh, m * 0.01, 0.40, -0.005, 14, units(0.70), 15, Mat.SINEW);
		box(thigh, 0, 1.00, -0.01, 11, units(0.62), 12, Mat.SINEW);
		box(thigh, 0, 1.47, 0, 9, units(0.40), 9, Mat.SINEW);
		box(thigh, m * 0.085, 0.95, -0.02, 3, units(1.30), 3, Mat.SINEW);
		box(thigh, 0, 0.95, 0, 7, units(1.70), 7, Mat.BONE);
		box(thigh, 0, THIGH - 0.02, 0.005, 13, 10, 11, Mat.BONE); // knee
		box(thigh, 0, THIGH - 0.07, -0.075, 7, 9, 4, Mat.BONE); // kneecap

		Part shin = add("leg_" + n + "_shin", thigh, Frame.bar(knee, ankle.sub(knee), V.X));
		box(shin, 0, 0.06, 0, 11, 10, 10, Mat.BONE);
		box(shin, 0, SHIN / 2, -0.015, 8, units(SHIN), 8, Mat.BONE); // tibia
		box(shin, m * 0.05, SHIN * 0.46, 0.02, 4, units(SHIN * 0.85), 4, Mat.BONE); // fibula
		box(shin, 0, 0.38, 0.05, 7, units(0.60), 7, Mat.SINEW); // calf
		box(shin, 0, SHIN - 0.02, 0, 10, 8, 9, Mat.BONE); // ankle

		Part foot = add("leg_" + n + "_foot", shin, Frame.bar(ankle, ball.sub(ankle), V.X));
		box(foot, 0, METATARSUS / 2, 0, 7, units(METATARSUS), 7, Mat.BONE);
		box(foot, 0, METATARSUS * 0.48, -0.04, 3, units(METATARSUS * 0.85), 3, Mat.SINEW); // tendon
		box(foot, 0, 0.02, 0.06, 8, 10, 7, Mat.BONE); // heel
		box(foot, 0, METATARSUS - 0.02, 0, 10, 8, 8, Mat.BONE); // ball

		V fwd = new V(m * 0.12, 0, 1).norm();
		double[] fan = {-24, 0, 24};
		for (int i = 0; i < 3; i++) {
			V d = fwd.rotate(V.Y, Math.toRadians(fan[i] * m));
			double s = i == 1 ? 1.0 : 0.86;
			digit("toe_" + n + "_" + i, foot, ball, d, new double[] {42, 20}, new double[] {0.21 * s, 0.16 * s},
					new double[] {55, 75}, new double[] {0.08, 0.06}, 6, 5);
		}
		V spur = ankle.lerp(ball, 0.72);
		digit("dewclaw_" + n, foot, spur, fwd.mul(-1), new double[] {35}, new double[] {0.09}, new double[] {60, 80},
				new double[] {0.06, 0.04}, 4, 4);
	}

	/**
	 * A toe: phalanges pitched down from the horizontal by the given angles, then a claw that
	 * hooks into the ground.
	 */
	private void digit(String name, Part parent, V start, V forward, double[] pitch, double[] lens, double[] clawPitch,
			double[] clawLens, int w0, int w1) {
		V lateral = V.Y.cross(forward).norm();
		Part p = parent;
		V at = start;
		for (int i = 0; i < lens.length; i++) {
			V dir = forward.mul(Math.cos(Math.toRadians(pitch[i]))).sub(V.Y.mul(Math.sin(Math.toRadians(pitch[i]))));
			int w = i == 0 ? w0 : w1;
			p = bar(name + "_" + i, p, Frame.bar(at, dir, lateral), lens[i], w, w, 0.012, Mat.BONE);
			box(p, 0, 0, 0, w + 2, w + 2, w + 2, Mat.BONE);
			at = at.add(dir.mul(lens[i]));
		}
		for (int i = 0; i < clawLens.length; i++) {
			V dir = forward.mul(Math.cos(Math.toRadians(clawPitch[i]))).sub(V.Y.mul(Math.sin(Math.toRadians(clawPitch[i]))));
			int w = Math.max(1, w1 - 2 - i);
			p = bar(name + "_claw_" + i, p, Frame.bar(at, dir, lateral), clawLens[i], w, w + 1, 0.01, Mat.CLAW);
			at = at.add(dir.mul(clawLens[i]));
		}
	}

	// --- shoulders, arms and hands --------------------------------------------------------------

	private void arm(int m) {
		String n = side(m);
		Part chest = nearestVertebra(4.55);
		V shoulder = new V(m * 0.50, 5.05, 1.45);
		V elbow = shoulder.add((m > 0 ? new V(0.06, -0.99, -0.10) : new V(-0.05, -0.985, 0.17)).norm().mul(UPPER_ARM));
		V wrist = elbow.add((m > 0 ? new V(0, -0.66, 0.75) : new V(0.03, -0.82, 0.57)).norm().mul(FOREARM));
		V handDir = (m > 0 ? new V(0.02, -0.88, 0.47) : new V(0, -0.95, 0.30)).norm();

		// Shoulder blade: a plate lying on the upper ribs behind the joint, with a ridge across it.
		V top = new V(m * 0.20, 5.66, 0.98), low = new V(m * 0.40, 5.16, 0.92), border = top.lerp(low, 0.5);
		Frame sf = Frame.bar(shoulder, border.sub(shoulder), top.sub(low));
		Part blade = add("scapula_" + n, chest, sf);
		double sl = border.sub(shoulder).len();
		double out = Math.signum(sf.z().dot(new V(m, 0.3, 0)));
		box(blade, 0, sl * 0.30, 0, 14, units(sl * 0.6), 4, Mat.BONE);
		box(blade, 0, sl * 0.74, 0, 34, units(sl * 0.52), 3, Mat.BONE);
		box(blade, 0, sl * 0.55, out * 0.03, 4, units(sl * 0.8), 5, Mat.BONE);
		box(blade, 0, 0, 0, 12, 12, 12, Mat.BONE);

		Part upper = add("arm_" + n + "_upper", chest, Frame.bar(shoulder, elbow.sub(shoulder), V.X));
		box(upper, 0, 0.03, 0, 11, 11, 11, Mat.BONE); // head of the humerus
		box(upper, m * 0.015, 0.14, 0, 10, units(0.22), 10, Mat.BONE); // deltoid
		box(upper, 0, UPPER_ARM * 0.5, 0, 7, units(UPPER_ARM * 0.95), 7, Mat.BONE);
		box(upper, 0, 0.65, -0.045, 6, units(0.90), 5, Mat.SINEW); // biceps
		box(upper, 0, 0.80, 0.05, 6, units(1.10), 5, Mat.SINEW); // triceps
		box(upper, m * 0.045, 0.90, 0, 3, units(1.20), 3, Mat.SINEW);
		box(upper, 0, UPPER_ARM - 0.04, 0, 12, 9, 9, Mat.BONE); // elbow

		Part fore = add("arm_" + n + "_fore", upper, Frame.bar(elbow, wrist.sub(elbow), V.X));
		box(fore, 0, -0.02, 0.05, 8, 10, 8, Mat.BONE); // point of the elbow
		box(fore, m * 0.035, FOREARM / 2, -0.012, 5, units(FOREARM * 0.99), 5, Mat.BONE); // radius
		box(fore, -m * 0.035, FOREARM / 2, 0.012, 5, units(FOREARM), 5, Mat.BONE); // ulna
		box(fore, 0, 0.45, 0.04, 6, units(0.75), 5, Mat.SINEW);
		box(fore, 0, FOREARM - 0.02, 0, 11, 7, 8, Mat.BONE); // wrist

		Frame hf = Frame.bar(wrist, handDir, V.X); // z is the palm side
		Part hand = add("hand_" + n, fore, hf);
		box(hand, 0, PALM / 2, 0, 14, units(PALM), 4, Mat.BONE);
		for (int i = 0; i < 4; i++) {
			box(hand, m * (-0.066 + 0.044 * i), PALM / 2, -0.03, 3, units(PALM * 0.95), 3, Mat.BONE);
		}
		double[] lengths = {0.95, 1.0, 0.94, 0.78}, fan = {-7, -2, 3, 9};
		for (int i = 0; i < 4; i++) {
			Frame f = hf.at(hf.point(m * (-0.066 + 0.044 * i), PALM, 0)).rotate(hf.z(), Math.toRadians(-m * fan[i]));
			boolean broken = m > 0 && i == 2; // the left ring finger healed crooked
			finger("finger_" + n + "_" + i, hand, f, new double[] {0.24, 0.18, 0.13}, lengths[i], new double[] {18, 32, 30},
					broken);
		}
		Frame thumb = hf.at(hf.point(-m * 0.075, 0.06, 0.01)).rotate(hf.z(), Math.toRadians(m * 55));
		thumb = thumb.rotate(thumb.x(), Math.toRadians(25));
		finger("thumb_" + n, hand, thumb, new double[] {0.20, 0.15}, 1.0, new double[] {15, 30}, false);
	}

	/** A long bony finger: phalanges curling toward the palm, knuckles, then a hooked claw. */
	private void finger(String name, Part hand, Frame f, double[] lens, double scale, double[] curls, boolean broken) {
		Part p = hand;
		for (int k = 0; k < lens.length; k++) {
			f = f.rotate(f.x(), Math.toRadians(curls[k]));
			if (broken && k == 1) {
				f = f.rotate(f.z(), Math.toRadians(18));
			}
			int w = k == 0 ? 5 : 4;
			p = bar(name + "_" + k, p, f, lens[k] * scale, w, w, 0.012, Mat.BONE);
			box(p, 0, 0, 0, w + 2, w + 2, w + 2, Mat.BONE);
			f = f.at(f.point(0, lens[k] * scale, 0));
		}
		double[] claw = {0.08, 0.06, 0.035}, hook = {22, 30, 30};
		for (int k = 0; k < claw.length; k++) {
			f = f.rotate(f.x(), Math.toRadians(hook[k]));
			int w = 3 - k;
			p = bar(name + "_claw_" + k, p, f, claw[k] * scale, w, Math.max(1, w), 0.008, Mat.CLAW);
			f = f.at(f.point(0, claw[k] * scale, 0));
		}
	}

	// --- skull --------------------------------------------------------------------------------

	/**
	 * A human skull hanging face-down from the end of the neck, the jaw dropped open, gripped from
	 * behind by two bony hands. Built in skull units (1/64 block): x left, y up through the crown,
	 * z out through the face.
	 */
	private void skull() {
		Part atlas = spine.get(spine.size() - 1);
		// The face looks forward and down; the open jaw then hangs straight down, as in the drawing.
		V face = new V(0, -0.375, 0.927).norm(), crown = new V(0, 0.927, 0.375).norm();
		Frame frame = new Frame(V.ZERO, crown.cross(face), crown, face);
		// The neck enters high on the back of the skull, so the skull hangs from it.
		V attach = sp(frame, 0, 9, -22);
		Part sk = add("head", atlas, frame.at(AXIS[0].sub(attach)));
		Mat bone = Mat.BONE;

		// Braincase, rounded with stepped caps.
		sb(sk, -13, -6, -19, 13, 16, 11, bone);
		sb(sk, -11, 16, -16, 11, 19, 8, bone);
		sb(sk, -8, 19, -12, 8, 20, 4, bone);
		sb(sk, -11, -4, -22, 11, 14, -19, bone);
		sb(sk, -8, -2, -24, 8, 10, -22, bone);
		sbm(sk, 13, -3, -15, 15, 12, 7, bone);
		sb(sk, -12, 1, 11, 12, 13, 14, bone); // forehead
		sb(sk, -10, 13, 9, 10, 15, 12, bone);
		sbm(sk, 2, 0, 13, 13, 3, 16, bone); // brow ridges, one arching over each socket
		sbm(sk, 5, 3, 13, 11, 4, 15, bone);
		sb(sk, -3, -2, 14, 3, 2, 16, bone);
		// Eye sockets: black hollows framed by the brow, outer rims, nasal bridge and cheekbones.
		sb(sk, -12, -11, 8, 12, 1, 13, Mat.SOCKET);
		sbm(sk, 11, -10, 11, 14, 1, 16, bone);
		sb(sk, -2, -7, 13, 2, 0, 16, bone);
		sbm(sk, 5, -13, 10, 14, -9, 16, bone);
		sbm(sk, 13, -12, -6, 16, -8, 12, bone); // zygomatic arches
		// Corners filled in so the sockets read round rather than square.
		sbm(sk, 2, -2, 13, 4, 0, 15, bone);
		sbm(sk, 9, -2, 13, 11, 0, 15, bone);
		sbm(sk, 2, -9, 13, 4, -7, 15, bone);
		sbm(sk, 9, -9, 13, 11, -7, 15, bone);
		// The eyes: small, black and deep-set at the back of each socket. Each is its own part, so
		// the eyeshine layer can light just them: a pinprick of a pupil, and behind it a faint glow
		// that fills the back of the socket round it.
		for (int m : new int[] {1, -1}) {
			Part eye = add("eye_" + side(m), sk, sk.frame.at(sp(sk.frame, m * 6.5, -4.5, 13.6)));
			box(eye, 0, 0, 0, 2, 2, 1, Mat.EYE);
			box(eye, 0, 0, -0.6 / UNITS, 7, 6, 1, Mat.GLOW);
		}
		// The nose: a dark, pear-shaped hole between two bony edges.
		sb(sk, -4, -17, 9, 4, -9, 13, Mat.SOCKET);
		sbm(sk, 4, -17, 10, 7, -9, 15, bone);
		sbm(sk, 2, -12, 10, 4, -9, 15, bone);
		// Upper jaw, skull base, mastoids and the throat.
		sb(sk, -9, -21, 5, 9, -16, 15, bone);
		sbm(sk, 7, -18, 2, 12, -12, 13, bone);
		sb(sk, -11, -13, -17, 11, -6, 6, bone);
		sbm(sk, 10, -15, -14, 13, -9, -9, bone);
		sb(sk, -8, -20, -4, 8, -13, 5, Mat.MOUTH);
		// Upper teeth: incisors, long fangs, cheek teeth.
		for (int i = -2; i < 2; i++) {
			sb(sk, i * 2, -25, 13, i * 2 + 2, -21, 15, Mat.TOOTH);
		}
		sbm(sk, 4, -31, 12, 6, -21, 14, Mat.TOOTH);
		sbm(sk, 6, -24, 10, 8, -21, 12, Mat.TOOTH);
		sbm(sk, 7, -24, 7, 9, -21, 10, Mat.TOOTH);
		sbm(sk, 7, -23, 4, 9, -21, 7, Mat.TOOTH);
		marks.put("mouth", sp(sk.frame, 0, -22, 10));

		// The jaw hangs open, dropped down and forward out of its sockets and a little askew.
		Frame jf = sk.frame.at(sp(sk.frame, 0, -14, -5)).rotate(sk.frame.x(), Math.toRadians(40)).rotate(face, Math.toRadians(6));
		Part jaw = add("jaw", sk, jf);
		sbm(jaw, 10, -18, -4, 13, 2, 3, bone); // rami
		sbm(jaw, 10, -22, -5, 13, -16, 6, bone);
		sbm(jaw, 9, -22, 3, 12, -16, 13, bone);
		sbm(jaw, 7, -22, 12, 10, -16, 18, bone);
		sb(jaw, -8, -23, 16, 8, -16, 21, bone); // chin
		sb(jaw, -5, -24, 19, 5, -20, 22, bone);
		sbm(jaw, 10, -4, 3, 12, 4, 7, bone);
		sb(jaw, -7, -20, -2, 7, -17, 17, Mat.MOUTH); // tongue
		for (int i = -2; i < 2; i++) {
			sb(jaw, i * 2, -16, 18, i * 2 + 2, -12, 20, Mat.TOOTH);
		}
		sbm(jaw, 5, -16, 16, 7, -5, 18, Mat.TOOTH); // lower fangs
		sbm(jaw, 7, -16, 12, 9, -13, 15, Mat.TOOTH);
		sbm(jaw, 8, -16, 8, 10, -13, 11, Mat.TOOTH);
		sbm(jaw, 9, -16, 4, 11, -13, 7, Mat.TOOTH);

		skullHands(sk);

		// Thin strings of bloody drool hanging from the teeth.
		drool("drool_0", jaw, sp(jaw.frame, -3, -13, 19), 1.20, 0.05);
		drool("drool_1", jaw, sp(jaw.frame, 6, -6, 17), 0.70, -0.04);
		drool("drool_2", sk, sp(sk.frame, 5, -31, 13), 1.45, 0.03);
	}

	private void skullHands(Part sk) {
		Frame s = sk.frame;
		for (int m : SIDES) {
			String n = side(m);
			Part wrist = bar("skull_wrist_" + n, sk, sp(s, m * 8, -12, -38), sp(s, m * 10, -9, -30), s.x(), 6, 6, 0.02, Mat.BONE);
			bar("skull_palm_" + n, wrist, sp(s, m * 10, -9, -30), sp(s, m * 12, 3, -22), s.x(), 4, 11, 0.02, Mat.BONE);
			double[][][] fingers = m > 0 ? LEFT_SKULL_FINGERS : RIGHT_SKULL_FINGERS;
			for (int i = 0; i < fingers.length; i++) {
				double[][] pts = fingers[i];
				V[] ctrl = new V[pts.length];
				for (int k = 0; k < pts.length; k++) {
					ctrl[k] = sp(s, pts[k][0], pts[k][1], pts[k][2]);
				}
				skullFinger("skull_finger_" + n + "_" + i, sk, ctrl);
			}
		}
	}

	/** A finger following the given control points around the skull; the last span is the claw. */
	private void skullFinger(String name, Part parent, V[] ctrl) {
		Curve c = new Curve(ctrl, 24);
		double clawStart = c.ctrlS[ctrl.length - 2], total = c.length();
		int n = Math.max(2, (int) Math.round(total / (6.0 / UNITS)));
		Part p = parent;
		V hint = parent.frame.x();
		int knuckle = 1;
		for (int i = 0; i < n; i++) {
			double a = total * i / n, b = total * (i + 1) / n;
			V pa = c.at(a), pb = c.at(b);
			Frame f = Frame.bar(pa, pb.sub(pa), hint);
			boolean claw = a >= clawStart - 1e-6;
			int w = claw ? Math.max(1, 3 - (int) ((a - clawStart) / (total - clawStart) * 3)) : 3;
			p = bar(name + "_" + i, p, f, pb.sub(pa).len(), w, w, 0.006, claw ? Mat.CLAW : Mat.BONE);
			if (!claw && knuckle < ctrl.length - 1 && a >= c.ctrlS[knuckle] - 1e-6) {
				box(p, 0, 0, 0, w + 1, w + 1, w + 1, Mat.BONE);
				knuckle++;
			}
			hint = f.x();
		}
	}

	/** A thin string of drool hanging down, wavering a little, ending in a heavy drop. */
	private void drool(String name, Part parent, V start, double length, double drift) {
		int segs = 4;
		Part p = parent;
		V at = start;
		List<Part> strand = new ArrayList<>();
		drool.add(strand);
		for (int i = 0; i < segs; i++) {
			double sway = i % 2 == 0 ? drift : -drift * 0.6;
			V dir = new V(sway, -1, sway * 0.5).norm();
			p = bar(name + "_" + i, p, Frame.bar(at, dir, V.X), length / segs, 1, 1, 0.004, Mat.DROOL);
			strand.add(p);
			at = at.add(dir.mul(length / segs));
		}
		box(p, 0, length / segs, 0, 2, 3, 2, Mat.DROOL);
	}

	/** A point in skull units on the given frame. */
	private static V sp(Frame skull, double x, double y, double z) {
		return skull.point(x * SKULL_SCALE / UNITS, y * SKULL_SCALE / UNITS, z * SKULL_SCALE / UNITS);
	}

	/** A box in skull units, given by its corners. */
	private static void sb(Part p, double x0, double y0, double z0, double x1, double y1, double z1, Mat mat) {
		long ax = Math.round(x0 * SKULL_SCALE), ay = Math.round(y0 * SKULL_SCALE), az = Math.round(z0 * SKULL_SCALE);
		long bx = Math.round(x1 * SKULL_SCALE), by = Math.round(y1 * SKULL_SCALE), bz = Math.round(z1 * SKULL_SCALE);
		p.boxes.add(new Box(ax / (double) UNITS, ay / (double) UNITS, az / (double) UNITS, (int) (bx - ax), (int) (by - ay),
				(int) (bz - az), mat));
	}

	/** The same box on both sides of the skull's midline, given for the left side. */
	private static void sbm(Part p, double x0, double y0, double z0, double x1, double y1, double z1, Mat mat) {
		sb(p, x0, y0, z0, x1, y1, z1, mat);
		sb(p, -x1, y0, z0, -x0, y1, z1, mat);
	}

	// --- building blocks ----------------------------------------------------------------------

	private Part add(String name, Part parent, Frame frame) {
		if (byName.containsKey(name)) {
			throw new IllegalStateException("Duplicate part " + name);
		}
		Part p = new Part(name, parent, frame);
		parts.add(p);
		byName.put(name, p);
		return p;
	}

	/** A box centred on a point in the part's frame (blocks), sized in units. */
	private static void box(Part p, double cx, double cy, double cz, int w, int h, int d, Mat mat) {
		p.boxes.add(new Box(cx - w / 2.0 / UNITS, cy - h / 2.0 / UNITS, cz - d / 2.0 / UNITS, w, h, d, mat));
	}

	/** A part on the given bar frame, holding one box that runs along its y axis for len blocks. */
	private Part bar(String name, Part parent, Frame f, double len, int w, int d, double overlap, Mat mat) {
		Part p = add(name, parent, f);
		box(p, 0, len / 2, 0, w, units(len + 2 * overlap), d, mat);
		return p;
	}

	/** A bar from a to b, its x axis as close to xHint as possible. */
	private Part bar(String name, Part parent, V a, V b, V xHint, int w, int d, double overlap, Mat mat) {
		return bar(name, parent, Frame.bar(a, b.sub(a), xHint), b.sub(a).len(), w, d, overlap, mat);
	}

	/** Bars through the points, each the child of the one before, tapering from (w0, d0) to (w1, d1). */
	private Part chain(String name, Part parent, List<V> pts, V xHint, int w0, int d0, int w1, int d1, double overlap, Mat mat) {
		Part p = parent;
		V hint = xHint;
		int n = pts.size() - 1;
		for (int i = 0; i < n; i++) {
			double t = n == 1 ? 0 : i / (double) (n - 1);
			V a = pts.get(i), b = pts.get(i + 1);
			Frame f = Frame.bar(a, b.sub(a), hint);
			p = bar(name + "_" + i, p, f, b.sub(a).len(), (int) Math.round(w0 + (w1 - w0) * t),
					(int) Math.round(d0 + (d1 - d0) * t), overlap, mat);
			hint = f.x();
		}
		return p;
	}

	private Part nearestVertebra(double index) {
		int best = 0;
		for (int i = 1; i < spine.size(); i++) {
			if (Math.abs(spineIndex.get(i) - index) < Math.abs(spineIndex.get(best) - index)) {
				best = i;
			}
		}
		return spine.get(best);
	}

	/** Where the knee goes so the thigh and shin keep their lengths, bending toward the pole. */
	private static V ik(V hip, V ankle, double l1, double l2, V pole) {
		V d = ankle.sub(hip);
		double dist = Math.min(d.len(), l1 + l2 - 1e-4);
		V dn = d.norm();
		double a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist);
		double h = Math.sqrt(Math.max(l1 * l1 - a * a, 0));
		return hip.add(dn.mul(a)).add(pole.reject(dn).norm().mul(h));
	}

	static int units(double blocks) {
		return Math.max(1, (int) Math.round(blocks * UNITS));
	}

	private static String side(int m) {
		return m > 0 ? "l" : "r";
	}

	private static double[] profile(double idx) {
		for (int i = 0; i + 1 < PROFILE.length; i++) {
			double[] a = PROFILE[i], b = PROFILE[i + 1];
			if (idx <= b[0] || i + 2 == PROFILE.length) {
				double t = Math.clamp((idx - a[0]) / (b[0] - a[0]), 0, 1);
				double[] out = new double[a.length - 1];
				for (int k = 1; k < a.length; k++) {
					out[k - 1] = a[k] + (b[k] - a[k]) * t;
				}
				return out;
			}
		}
		throw new IllegalStateException();
	}

	private static double interp(double u, double[][] table) {
		for (int i = 0; i + 1 < table.length; i++) {
			if (u <= table[i + 1][0] || i + 2 == table.length) {
				double t = Math.clamp((u - table[i][0]) / (table[i + 1][0] - table[i][0]), 0, 1);
				return table[i][1] + (table[i + 1][1] - table[i][1]) * t;
			}
		}
		throw new IllegalStateException();
	}

	/**
	 * Lays every box's faces out in the texture the way vanilla unfolds a cuboid: a strip of
	 * (2d + 2w) by (d + h) pixels. Shelf packing, tallest first, with a one-pixel gutter.
	 */
	private void pack() {
		List<Box> all = new ArrayList<>();
		for (Part p : parts) {
			all.addAll(p.boxes);
		}
		all.sort(Comparator.comparingInt(Box::texHeight).thenComparingInt(Box::texWidth).reversed());
		int x = 0, y = 0, row = 0;
		for (Box b : all) {
			int bw = b.texWidth() + 1, bh = b.texHeight() + 1;
			if (bw > TEXTURE_WIDTH) {
				throw new IllegalStateException("Box too wide for the texture: " + bw);
			}
			if (x + bw > TEXTURE_WIDTH) {
				x = 0;
				y += row;
				row = 0;
			}
			b.u = x;
			b.v = y;
			x += bw;
			row = Math.max(row, bh);
		}
		textureHeight = (y + row + 63) / 64 * 64;
	}

	// --- types --------------------------------------------------------------------------------

	public static final class Part {
		public final String name;
		public final Part parent;
		/** Where the part sits in the rest pose, in design space. */
		public final Frame frame;
		public final List<Box> boxes = new ArrayList<>();

		Part(String name, Part parent, Frame frame) {
			this.name = name;
			this.parent = parent;
			this.frame = frame;
		}
	}

	public static final class Box {
		/** Minimum corner in the part's frame, in blocks. */
		public final double x0, y0, z0;
		/** Size in model units along the part's x, y and z axes. */
		public final int w, h, d;
		public final Mat mat;
		/** Top-left corner of the box's faces in the texture. */
		public int u, v;

		Box(double x0, double y0, double z0, int w, int h, int d, Mat mat) {
			this.x0 = x0;
			this.y0 = y0;
			this.z0 = z0;
			this.w = Math.max(1, w);
			this.h = Math.max(1, h);
			this.d = Math.max(1, d);
			this.mat = mat;
		}

		public int texWidth() {
			return 2 * (d + w);
		}

		public int texHeight() {
			return d + h;
		}
	}

	public record V(double x, double y, double z) {
		public static final V ZERO = new V(0, 0, 0), X = new V(1, 0, 0), Y = new V(0, 1, 0), Z = new V(0, 0, 1);

		public V add(V o) {
			return new V(x + o.x, y + o.y, z + o.z);
		}

		public V sub(V o) {
			return new V(x - o.x, y - o.y, z - o.z);
		}

		public V mul(double k) {
			return new V(x * k, y * k, z * k);
		}

		public double dot(V o) {
			return x * o.x + y * o.y + z * o.z;
		}

		public V cross(V o) {
			return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
		}

		public double len() {
			return Math.sqrt(dot(this));
		}

		public V norm() {
			double l = len();
			return l < 1e-12 ? this : mul(1 / l);
		}

		public V lerp(V o, double t) {
			return add(o.sub(this).mul(t));
		}

		/** This vector without its component along the unit vector n. */
		public V reject(V n) {
			return sub(n.mul(dot(n)));
		}

		/** Rotated by angle (radians) about the unit axis a, right-handed. */
		public V rotate(V a, double angle) {
			double c = Math.cos(angle), s = Math.sin(angle);
			return mul(c).add(a.cross(this).mul(s)).add(a.mul(a.dot(this) * (1 - c)));
		}
	}

	/** An origin and three orthonormal, right-handed axes. */
	public record Frame(V o, V x, V y, V z) {
		public static final Frame WORLD = new Frame(V.ZERO, V.X, V.Y, V.Z);

		/** y along dir, x as close to xHint as it can be. Limb bones, fingers, ribs and spikes use this. */
		public static Frame bar(V o, V dir, V xHint) {
			V y = dir.norm();
			V x = xHint.reject(y);
			if (x.len() < 1e-6) {
				x = V.Z.reject(y);
			}
			if (x.len() < 1e-6) {
				x = V.X.reject(y);
			}
			x = x.norm();
			return new Frame(o, x, y, x.cross(y));
		}

		/** z along dir, y as close to yHint as it can be. Vertebrae use this, with y on the back. */
		public static Frame along(V o, V dir, V yHint) {
			V z = dir.norm();
			V y = yHint.reject(z);
			if (y.len() < 1e-6) {
				y = V.Y.reject(z);
			}
			y = y.norm();
			return new Frame(o, y.cross(z), y, z);
		}

		public V point(double lx, double ly, double lz) {
			return o.add(x.mul(lx)).add(y.mul(ly)).add(z.mul(lz));
		}

		public V point(V l) {
			return point(l.x(), l.y(), l.z());
		}

		public V dir(double lx, double ly, double lz) {
			return x.mul(lx).add(y.mul(ly)).add(z.mul(lz));
		}

		public V local(V p) {
			V d = p.sub(o);
			return new V(d.dot(x), d.dot(y), d.dot(z));
		}

		public V localDir(V d) {
			return new V(d.dot(x), d.dot(y), d.dot(z));
		}

		public Frame at(V p) {
			return new Frame(p, x, y, z);
		}

		public Frame rotate(V axis, double angle) {
			return new Frame(o, x.rotate(axis, angle), y.rotate(axis, angle), z.rotate(axis, angle));
		}
	}

	/** A centripetal Catmull-Rom curve through control points, sampled densely by arc length. */
	static final class Curve {
		final V[] p;
		final double[] s;
		final double[] ctrlS;
		final int[] ctrlK;

		Curve(V[] c, int perSpan) {
			int n = c.length;
			p = new V[(n - 1) * perSpan + 1];
			s = new double[p.length];
			ctrlS = new double[n];
			ctrlK = new int[n];
			int k = 0;
			for (int i = 0; i < n - 1; i++) {
				V p0 = i == 0 ? c[0].mul(2).sub(c[1]) : c[i - 1];
				V p3 = i + 2 < n ? c[i + 2] : c[n - 1].mul(2).sub(c[n - 2]);
				for (int j = 0; j < perSpan; j++, k++) {
					p[k] = centripetal(p0, c[i], c[i + 1], p3, j / (double) perSpan);
					s[k] = k == 0 ? 0 : s[k - 1] + p[k].sub(p[k - 1]).len();
					if (j == 0) {
						ctrlS[i] = s[k];
						ctrlK[i] = k;
					}
				}
			}
			p[k] = c[n - 1];
			s[k] = s[k - 1] + p[k].sub(p[k - 1]).len();
			ctrlS[n - 1] = s[k];
			ctrlK[n - 1] = k;
		}

		/** Points along the curve through ctrl, perSpan pieces between each pair of control points. */
		static List<V> resample(V[] ctrl, int pieces) {
			Curve c = new Curve(ctrl, 24);
			List<V> out = new ArrayList<>();
			for (int i = 0; i <= pieces; i++) {
				out.add(c.at(c.length() * i / pieces));
			}
			return out;
		}

		double length() {
			return s[s.length - 1];
		}

		private int span(double d) {
			int lo = 0, hi = s.length - 1;
			if (d <= 0) {
				return 0;
			}
			if (d >= s[hi]) {
				return hi - 1;
			}
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (s[mid] <= d) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			return lo;
		}

		private double frac(int i, double d) {
			return Math.clamp((d - s[i]) / Math.max(s[i + 1] - s[i], 1e-12), 0, 1);
		}

		V at(double d) {
			int i = span(d);
			return p[i].lerp(p[i + 1], frac(i, d));
		}

		/** The fractional control-point index at arc length d. */
		double index(double d) {
			for (int i = 0; i + 1 < ctrlS.length; i++) {
				if (d <= ctrlS[i + 1] || i + 2 == ctrlS.length) {
					return i + Math.clamp((d - ctrlS[i]) / (ctrlS[i + 1] - ctrlS[i]), 0, 1);
				}
			}
			return ctrlS.length - 1;
		}

		private V tangent(int k) {
			return p[Math.min(k + 1, p.length - 1)].sub(p[Math.max(k - 1, 0)]).norm();
		}

		/** A direction carried along the curve without twisting, starting from up at a control point. */
		V[] transport(int startCtrl, V up) {
			V[] out = new V[p.length];
			int k0 = ctrlK[startCtrl];
			out[k0] = up.reject(tangent(k0)).norm();
			for (int k = k0 + 1; k < p.length; k++) {
				out[k] = out[k - 1].reject(tangent(k)).norm();
			}
			for (int k = k0 - 1; k >= 0; k--) {
				out[k] = out[k + 1].reject(tangent(k)).norm();
			}
			return out;
		}

		V along(V[] field, double d) {
			int i = span(d);
			return field[i].lerp(field[i + 1], frac(i, d)).norm();
		}

		private static V centripetal(V p0, V p1, V p2, V p3, double u) {
			double t0 = 0;
			double t1 = t0 + Math.sqrt(Math.max(p1.sub(p0).len(), 1e-6));
			double t2 = t1 + Math.sqrt(Math.max(p2.sub(p1).len(), 1e-6));
			double t3 = t2 + Math.sqrt(Math.max(p3.sub(p2).len(), 1e-6));
			double t = t1 + (t2 - t1) * u;
			V a1 = p0.mul((t1 - t) / (t1 - t0)).add(p1.mul((t - t0) / (t1 - t0)));
			V a2 = p1.mul((t2 - t) / (t2 - t1)).add(p2.mul((t - t1) / (t2 - t1)));
			V a3 = p2.mul((t3 - t) / (t3 - t2)).add(p3.mul((t - t2) / (t3 - t2)));
			V b1 = a1.mul((t2 - t) / (t2 - t0)).add(a2.mul((t - t0) / (t2 - t0)));
			V b2 = a2.mul((t3 - t) / (t3 - t1)).add(a3.mul((t - t1) / (t3 - t1)));
			return b1.mul((t2 - t) / (t2 - t1)).add(b2.mul((t - t1) / (t2 - t1)));
		}
	}
}
