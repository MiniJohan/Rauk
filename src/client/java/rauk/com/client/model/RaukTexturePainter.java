package rauk.com.client.model;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import rauk.com.client.model.RaukRig.Box;
import rauk.com.client.model.RaukRig.Mat;
import rauk.com.client.model.RaukRig.Part;
import rauk.com.client.model.RaukRig.V;

/**
 * Paints Rauk's texture from the rig. Every texel knows where it sits on the body in the
 * reference pose, so the noise, stains and shading run continuously across box edges, and
 * creases are darkened by ambient occlusion from a voxel copy of the body.
 *
 * <p>Run with {@code gradlew paintRaukTexture}; it writes the entity texture into the mod's
 * resources.
 */
public final class RaukTexturePainter {
	/** Voxel size for the occlusion grid, in blocks. */
	private static final double CELL = 1.0 / 40;
	/** How far occlusion looks. Short, so limbs that move later don't print shadows on each other. */
	private static final double AO_RANGE = 0.28;
	private static final V[] DIRECTIONS = sphere(96);

	private final RaukRig rig;
	private final int[] argb;
	private final int width, height;
	private final double gx, gy, gz;
	private final int nx, ny, nz;
	private final boolean[] solid;
	private final V mouth;

	/**
	 * What is painted: the body itself, or the eyeshine drawn over it (design doc 9), which shares
	 * the body's layout and is transparent everywhere but the eyes.
	 */
	private enum Layer { BASE, EYES }

	private Layer layer = Layer.BASE;

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "rauk.png");
		RaukRig rig = RaukRig.build();
		RaukTexturePainter painter = new RaukTexturePainter(rig);
		Files.createDirectories(out.toAbsolutePath().getParent());
		String base = out.getFileName().toString().replace(".png", "");
		for (Layer layer : Layer.values()) {
			painter.layer = layer;
			java.util.Arrays.fill(painter.argb, 0);
			painter.covered = new boolean[painter.argb.length];
			painter.paint();
			Path file = layer == Layer.BASE ? out : out.resolveSibling(base + "_" + layer.name().toLowerCase() + ".png");
			ImageIO.write(painter.image(), "png", file.toFile());
		}
		int boxes = rig.parts.stream().mapToInt(p -> p.boxes.size()).sum();
		System.out.printf("Rauk: %d parts, %d boxes, texture %dx%d -> %s (and overlays)%n", rig.parts.size(), boxes, rig.textureWidth(),
				rig.textureHeight(), out.toAbsolutePath());
	}

	private RaukTexturePainter(RaukRig rig) {
		this.rig = rig;
		this.width = rig.textureWidth();
		this.height = rig.textureHeight();
		this.argb = new int[width * height];
		this.mouth = rig.marks.get("mouth");

		double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
		for (Part p : rig.parts) {
			for (Box b : p.boxes) {
				for (V c : corners(p, b)) {
					lo[0] = Math.min(lo[0], c.x());
					lo[1] = Math.min(lo[1], c.y());
					lo[2] = Math.min(lo[2], c.z());
					hi[0] = Math.max(hi[0], c.x());
					hi[1] = Math.max(hi[1], c.y());
					hi[2] = Math.max(hi[2], c.z());
				}
			}
		}
		gx = lo[0] - 0.4;
		gy = lo[1] - 0.4;
		gz = lo[2] - 0.4;
		nx = (int) Math.ceil((hi[0] - lo[0] + 0.8) / CELL);
		ny = (int) Math.ceil((hi[1] - lo[1] + 0.8) / CELL);
		nz = (int) Math.ceil((hi[2] - lo[2] + 0.8) / CELL);
		solid = new boolean[nx * ny * nz];
		for (Part p : rig.parts) {
			for (Box b : p.boxes) {
				voxelize(p, b);
			}
		}
	}

	private BufferedImage image() {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, width, height, argb, 0, width);
		return img;
	}

	private void paint() {
		for (Part p : rig.parts) {
			for (Box b : p.boxes) {
				paintBox(p, b);
			}
		}
		// Grow each painted face a couple of pixels into the gutters so edges never sample empty texels.
		for (int pass = 0; pass < 2; pass++) {
			int[] copy = argb.clone();
			boolean[] had = covered.clone();
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (had[y * width + x]) {
						continue;
					}
					int[][] near = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
					for (int[] q : near) {
						if (q[0] >= 0 && q[1] >= 0 && q[0] < width && q[1] < height && had[q[1] * width + q[0]]) {
							argb[y * width + x] = copy[q[1] * width + q[0]];
							covered[y * width + x] = true;
							break;
						}
					}
				}
			}
		}
	}

	/** Which texels belong to a face (or have grown from one), as opposed to empty gutter. */
	private boolean[] covered;

	// --- faces ----------------------------------------------------------------------------------

	/**
	 * Walks the six faces exactly as vanilla's ModelPart.Cube lays them out, so each texel's
	 * position on the box is known. Positions here are in the part's model space (units, y and z
	 * flipped from design space).
	 */
	private void paintBox(Part p, Box b) {
		int w = b.w, h = b.h, d = b.d, u = b.u, v = b.v;
		double[] c = RaukRig.modelCorner(b);
		double x0 = c[0], y0 = c[1], z0 = c[2], x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < d; j++) {
				texel(p, b, u + d + i, v + j, x0 + i + 0.5, y0, z1 - j - 0.5, 0, -1, 0, i, j, w, d);
				texel(p, b, u + d + w + i, v + j, x0 + i + 0.5, y1, z1 - j - 0.5, 0, 1, 0, i, j, w, d);
			}
		}
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < d; i++) {
				texel(p, b, u + i, v + d + j, x0, y0 + j + 0.5, z1 - i - 0.5, -1, 0, 0, i, j, d, h);
				texel(p, b, u + d + w + i, v + d + j, x1, y0 + j + 0.5, z0 + i + 0.5, 1, 0, 0, i, j, d, h);
			}
			for (int i = 0; i < w; i++) {
				texel(p, b, u + d + i, v + d + j, x0 + i + 0.5, y0 + j + 0.5, z0, 0, 0, -1, i, j, w, h);
				texel(p, b, u + 2 * d + w + i, v + d + j, x1 - i - 0.5, y0 + j + 0.5, z1, 0, 0, 1, i, j, w, h);
			}
		}
	}

	private void texel(Part p, Box b, int tu, int tv, double xm, double ym, double zm, double nxm, double nym, double nzm,
			int fa, int fb, int fw, int fh) {
		if (tu < 0 || tv < 0 || tu >= width || tv >= height) {
			return;
		}
		V local = new V(xm / RaukRig.UNITS, -ym / RaukRig.UNITS, -zm / RaukRig.UNITS);
		V pos = p.frame.point(local);
		V normal = p.frame.dir(nxm, -nym, -nzm);
		// Where the texel sits within the box, 0..1 along each of its axes.
		V q = new V((local.x() - b.x0) * RaukRig.UNITS / b.w, (local.y() - b.y0) * RaukRig.UNITS / b.h,
				(local.z() - b.z0) * RaukRig.UNITS / b.d);
		int edge = Math.min(Math.min(fa, fw - 1 - fa), Math.min(fb, fh - 1 - fb));
		covered[tv * width + tu] = true;
		argb[tv * width + tu] = layer == Layer.BASE ? 0xFF000000 | shade(p, b, pos, normal, q, edge, tu, tv) : overlay(b, q);
	}

	// --- overlays -------------------------------------------------------------------------------

	/**
	 * One texel of the eyeshine: a cold silver-green pinprick on each pupil, a faint glow round it
	 * fading out toward the edge of the socket, nothing anywhere else.
	 */
	private int overlay(Box b, V q) {
		if (b.mat == Mat.EYE) {
			return 0xFF << 24 | 236 << 16 | 255 << 8 | 240;
		}
		if (b.mat == Mat.GLOW) {
			double r = Math.hypot((q.x() - 0.5) * 2, (q.y() - 0.5) * 2);
			double glow = 0.6 * Math.pow(Math.clamp(1 - r, 0, 1), 1.6);
			return 0xFF << 24 | (int) Math.round(150 * glow) << 16 | (int) Math.round(235 * glow) << 8 | (int) Math.round(180 * glow);
		}
		return 0;
	}

	// --- shading ------------------------------------------------------------------------------

	private int shade(Part p, Box b, V pos, V n, V q, int edge, int tu, int tv) {
		double ao = occlusion(pos, n);
		double[] c;
		switch (b.mat) {
			case BONE, SINEW -> {
				boolean sinew = b.mat == Mat.SINEW;
				double t = Math.clamp(0.5 + 0.9 * fbm(pos, 2.2, 11), 0, 1);
				c = sinew ? mix(rgb(212, 204, 194), rgb(184, 174, 164), t) : mix(rgb(233, 229, 218), rgb(206, 199, 184), t);
				// Water-stain mottling, like old plaster.
				c = mix(c, rgb(170, 164, 156), Math.clamp(fbm(pos, 0.9, 21) * 1.6 - 0.45, 0, 0.35));
				if (sinew) {
					// Fibres running along the muscle.
					double fibre = noise(q.x() * 9 * b.w / 8.0, q.y() * 1.5, q.z() * 9 * b.d / 8.0, 31);
					c = scale(c, 1 + 0.10 * fibre);
				}
				c = scale(c, 1 + 0.05 * noise(pos.x() * 64, pos.y() * 64, pos.z() * 64, 41));
				// Creases yellow and darken, and the shadows are hatched like the ink drawing.
				c = mix(c, sinew ? rgb(150, 124, 118) : rgb(186, 170, 136), (1 - ao) * 0.5);
				// Soaked in its kills' blood, wet and dried, before the shading darkens the creases.
				c = bloody(c, p, pos, n, q, 0.92);
				c = scale(c, 0.36 + 0.64 * ao);
				if (ao < 0.84 && ((tu + tv) & 3) == 0) {
					c = scale(c, 1 - (0.84 - ao) * 0.6);
				}
				if (edge == 0) {
					c = scale(c, 0.9);
				}
				if (p.name.equals("head") && crack(pos)) {
					c = scale(c, 0.32);
				}
			}
			case CAVITY -> {
				c = scale(rgb(38, 24, 24), (0.8 + 0.3 * fbm(pos, 6, 51)) * (0.5 + 0.5 * ao));
			}
			case SOCKET, GLOW -> c = rgb(10, 8, 8);
			case EYE -> c = rgb(14, 14, 16);
			case MOUTH -> c = scale(rgb(58, 22, 24), (0.75 + 0.3 * fbm(pos, 9, 61)) * (0.5 + 0.5 * ao));
			case CLAW -> {
				c = mix(rgb(70, 60, 52), rgb(26, 22, 20), Math.clamp(q.y(), 0, 1));
				if (n.y() > 0.5) {
					c = scale(c, 1.25);
				}
				c = scale(c, 0.6 + 0.4 * ao);
				c = bloody(c, p, pos, n, q, 0.8);
			}
			case TOOTH -> {
				// Yellow-grey, browner toward the root, gums at the root itself.
				double root = p.name.equals("jaw") ? 1 - q.y() : q.y();
				c = mix(rgb(222, 208, 170), rgb(150, 122, 88), Math.clamp((root - 0.55) * 2, 0, 1));
				if (root > 0.85) {
					c = rgb(104, 36, 36);
				}
				c = scale(c, 0.55 + 0.45 * ao);
				if (edge == 0) {
					c = scale(c, 0.85);
				}
				c = mix(c, bloodColor(pos), mouthBlood(pos) * 0.75);
			}
			case DROOL -> {
				c = n.x() > 0.5 || n.z() > 0.5 ? rgb(96, 30, 26) : rgb(48, 10, 10);
				c = scale(c, 0.85 + 0.25 * noise(pos.x() * 40, pos.y() * 40, pos.z() * 40, 81));
			}
			default -> c = rgb(255, 0, 255);
		}
		return pack(c);
	}

	/**
	 * It is never clean (decided in review, 2026-10-06): soaked in what it has killed. The jaws,
	 * chin and throat are thick with it and it has run down from them; the hands it kills with are
	 * soaked to the wrist and splashed up the forearms, the hands gripping the skull too; and there
	 * are splashes, dried spatter and runs of it all over the body.
	 */
	private double[] bloody(double[] c, Part p, V pos, V n, V q, double strength) {
		String name = p.name;
		double g = mouthBlood(pos);
		if (name.startsWith("hand_") || name.startsWith("finger_") || name.startsWith("thumb_") || name.startsWith("skull_finger_")
				|| name.startsWith("skull_palm_")) {
			g = Math.max(g, 0.72 + 0.28 * fbm(pos, 7, 601));
		} else if (name.startsWith("arm_") && name.endsWith("_fore")) {
			// From the wrist up the forearm, thinning out.
			g = Math.max(g, smooth(Math.clamp((q.y() - 0.3) / 0.7, 0, 1)) * (0.6 + 0.4 * fbm(pos, 6, 603)));
		}
		double splash = fbm(pos, 1.8, 611);
		if (splash > 0.14) {
			g = Math.max(g, Math.clamp((splash - 0.14) * 4.5, 0, 0.8));
		}
		// Runs: streaks stretched up and down the body, where it ran before it dried.
		double run = noise(pos.x() * 15, pos.y() * 2.4, pos.z() * 15, 621);
		if (run > 0.42 && fbm(pos, 1.2, 623) > -0.05) {
			g = Math.max(g, Math.clamp((run - 0.42) * 3.2, 0, 0.85));
		}
		// Underneath, where it hangs its head over what it eats.
		if (n.y() < -0.4 && mouth != null && pos.sub(mouth).len() < 2.2) {
			g = Math.max(g, 0.5 * Math.clamp(0.5 + fbm(pos, 4, 627), 0, 1));
		}
		return mix(c, bloodColor(pos), Math.clamp(g, 0, 1) * strength);
	}

	/** Fresh and wet in places, dried dark and brown in others. */
	private static double[] bloodColor(V pos) {
		double wet = Math.clamp(0.5 + 1.4 * fbm(pos, 3.5, 631), 0, 1);
		double[] c = mix(rgb(62, 20, 14), rgb(122, 10, 12), wet);
		return scale(c, 0.85 + 0.25 * noise(pos.x() * 48, pos.y() * 48, pos.z() * 48, 637));
	}

	/** Thick around the mouth, chin and teeth, and running down from them. */
	private double mouthBlood(V pos) {
		if (mouth == null) {
			return 0;
		}
		V d = pos.sub(mouth);
		// Below the mouth it has run further than above it.
		double reach = d.y() < 0 ? Math.sqrt(d.x() * d.x() + d.z() * d.z() + d.y() * d.y() * 0.25) : d.len();
		double around = Math.clamp(1 - reach / 0.42, 0, 1) * Math.clamp(0.55 + 1.1 * fbm(pos, 9, 91), 0, 1);
		return Math.clamp(around * 1.5, 0, 1);
	}

	/** Thin jagged lines across the cranium: the zero crossings of a low-frequency noise, masked to a few places. */
	private static boolean crack(V pos) {
		double line = noise(pos.x() * 7, pos.y() * 7, pos.z() * 7, 101) + 0.25 * noise(pos.x() * 26, pos.y() * 26, pos.z() * 26, 103);
		return Math.abs(line) < 0.075 && fbm(pos, 2.5, 107) > 0.12;
	}

	// --- occlusion ----------------------------------------------------------------------------

	private double occlusion(V p, V n) {
		V start = p.add(n.mul(CELL * 1.3));
		double hit = 0, total = 0;
		for (V d : DIRECTIONS) {
			double c = d.dot(n);
			if (c <= 0.05) {
				continue;
			}
			total += c;
			for (double t = CELL * 0.75; t <= AO_RANGE; t += CELL * 0.75) {
				if (solid(start.add(d.mul(t)))) {
					hit += c * (1 - 0.5 * t / AO_RANGE);
					break;
				}
			}
		}
		return total == 0 ? 1 : 1 - hit / total;
	}

	private boolean solid(V p) {
		int i = (int) Math.floor((p.x() - gx) / CELL), j = (int) Math.floor((p.y() - gy) / CELL), k = (int) Math.floor((p.z() - gz) / CELL);
		return i >= 0 && j >= 0 && k >= 0 && i < nx && j < ny && k < nz && solid[(i * ny + j) * nz + k];
	}

	private void voxelize(Part p, Box b) {
		double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
		for (V c : corners(p, b)) {
			lo[0] = Math.min(lo[0], c.x());
			lo[1] = Math.min(lo[1], c.y());
			lo[2] = Math.min(lo[2], c.z());
			hi[0] = Math.max(hi[0], c.x());
			hi[1] = Math.max(hi[1], c.y());
			hi[2] = Math.max(hi[2], c.z());
		}
		double sx = b.w / (double) RaukRig.UNITS, sy = b.h / (double) RaukRig.UNITS, sz = b.d / (double) RaukRig.UNITS;
		for (int i = (int) ((lo[0] - gx) / CELL); i <= (int) ((hi[0] - gx) / CELL); i++) {
			for (int j = (int) ((lo[1] - gy) / CELL); j <= (int) ((hi[1] - gy) / CELL); j++) {
				for (int k = (int) ((lo[2] - gz) / CELL); k <= (int) ((hi[2] - gz) / CELL); k++) {
					V l = p.frame.local(new V(gx + (i + 0.5) * CELL, gy + (j + 0.5) * CELL, gz + (k + 0.5) * CELL));
					double ax = l.x() - b.x0, ay = l.y() - b.y0, az = l.z() - b.z0;
					if (ax >= 0 && ay >= 0 && az >= 0 && ax <= sx && ay <= sy && az <= sz) {
						solid[(i * ny + j) * nz + k] = true;
					}
				}
			}
		}
	}

	private static V[] corners(Part p, Box b) {
		double sx = b.w / (double) RaukRig.UNITS, sy = b.h / (double) RaukRig.UNITS, sz = b.d / (double) RaukRig.UNITS;
		V[] out = new V[8];
		for (int i = 0; i < 8; i++) {
			out[i] = p.frame.point(b.x0 + ((i & 1) == 0 ? 0 : sx), b.y0 + ((i & 2) == 0 ? 0 : sy), b.z0 + ((i & 4) == 0 ? 0 : sz));
		}
		return out;
	}

	private static V[] sphere(int n) {
		V[] out = new V[n];
		double golden = Math.PI * (3 - Math.sqrt(5));
		for (int i = 0; i < n; i++) {
			double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y), a = golden * i;
			out[i] = new V(Math.cos(a) * r, y, Math.sin(a) * r);
		}
		return out;
	}

	// --- noise and colour ---------------------------------------------------------------------

	private static double hash(int x, int y, int z, int seed) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 1442695041;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF * 2 - 1;
	}

	/** Smooth value noise in -1..1. */
	private static double noise(double x, double y, double z, int seed) {
		int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
		double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
		double v = 0;
		for (int i = 0; i < 8; i++) {
			int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
			double w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
			v += w * hash(x0 + dx, y0 + dy, z0 + dz, seed);
		}
		return v;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double fbm(V p, double freq, int seed) {
		double v = 0, amp = 0.5, f = freq;
		for (int o = 0; o < 3; o++) {
			v += amp * noise(p.x() * f, p.y() * f, p.z() * f, seed + o);
			amp *= 0.5;
			f *= 2.1;
		}
		return v;
	}

	private static double[] rgb(int r, int g, int b) {
		return new double[] {r, g, b};
	}

	private static double[] mix(double[] a, double[] b, double t) {
		return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	private static double[] scale(double[] a, double k) {
		return new double[] {a[0] * k, a[1] * k, a[2] * k};
	}

	private static int pack(double[] c) {
		int r = (int) Math.round(Math.clamp(c[0], 0, 255)), g = (int) Math.round(Math.clamp(c[1], 0, 255)),
				b = (int) Math.round(Math.clamp(c[2], 0, 255));
		return r << 16 | g << 8 | b;
	}
}
