package rauk.com.client.dev;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

/**
 * Paints the hotel's block textures (hotel design 11.3), 16 by 16 like the game's own, from code:
 * dark mahogany and brass, damask, marble, blue stucco and limestone, slate, and the grime, rust
 * and char of the wrecked parts. {@code ./gradlew paintHotelTextures} writes them into the mod's
 * textures and a sheet of them all into docs/hotel/materials.png. Each texture has its own seed,
 * so painting again gives the same pixels.
 */
public final class HotelTexturePainter {
	// --- the palette ----------------------------------------------------------------------------

	static final int MAH_DARK = rgb(40, 19, 11), MAH = rgb(68, 34, 21), MAH_MID = rgb(86, 44, 27), MAH_LIGHT = rgb(110, 60, 37),
			MAH_HI = rgb(138, 80, 48);
	static final int BRASS = rgb(174, 136, 58), BRASS_HI = rgb(224, 192, 112), BRASS_DARK = rgb(108, 80, 32), PATINA = rgb(92, 110, 76);
	static final int LIME = rgb(212, 202, 178), LIME_SHADOW = rgb(158, 148, 124), LIME_HI = rgb(232, 224, 204);
	static final int STUCCO = rgb(88, 106, 132), STUCCO_JOINT = rgb(66, 80, 100);
	static final int SLATE = rgb(58, 66, 84), SLATE_DARK = rgb(36, 42, 56), SLATE_HI = rgb(84, 94, 116);
	static final int IRON = rgb(26, 24, 26), GRIME = rgb(96, 80, 48), RUST = rgb(128, 70, 38), MOLD = rgb(62, 84, 52);
	static final int MARBLE = rgb(226, 214, 190), MARBLE_VEIN = rgb(172, 156, 128);

	// --- a texture --------------------------------------------------------------------------------

	/** A 16 by 16 texture being painted; coordinates wrap, so patterns tile. */
	static final class Tex {
		final int w, h;
		final int[] p;
		final Random r;
		/** Which look of the texture this is: 0 for the first. Painters vary what they draw by it. */
		final int look;

		Tex(long seed, int look) {
			this(seed, look, 16, 16);
		}

		Tex(long seed, int look, int w, int h) {
			r = new Random(seed);
			this.look = look;
			this.w = w;
			this.h = h;
			p = new int[w * h];
		}

		Tex fill(int c) {
			java.util.Arrays.fill(p, 0xFF000000 | c);
			return this;
		}

		int get(int x, int y) {
			return p[Math.floorMod(y, h) * w + Math.floorMod(x, w)] & 0xFFFFFF;
		}

		void set(int x, int y, int c) {
			p[Math.floorMod(y, h) * w + Math.floorMod(x, w)] = 0xFF000000 | c;
		}

		void clear(int x, int y) {
			p[Math.floorMod(y, h) * w + Math.floorMod(x, w)] = 0;
		}

		void mix(int x, int y, int c, double t) {
			set(x, y, HotelTexturePainter.mix(get(x, y), c, t));
		}

		void shade(int x, int y, double f) {
			set(x, y, HotelTexturePainter.shade(get(x, y), f));
		}

		void rect(int x0, int y0, int x1, int y1, int c) {
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					set(x, y, c);
				}
			}
		}

		/** Every pixel a little lighter or darker, at random. */
		void noise(double amount) {
			for (int i = 0; i < p.length; i++) {
				if ((p[i] >>> 24) != 0) {
					p[i] = 0xFF000000 | HotelTexturePainter.shade(p[i] & 0xFFFFFF, 1 + (r.nextDouble() * 2 - 1) * amount);
				}
			}
		}

		/** Grain running along x (or y) within a box: streaks of darker and lighter. */
		void grain(int x0, int y0, int x1, int y1, boolean alongX, double amount) {
			int across0 = alongX ? y0 : x0, across1 = alongX ? y1 : x1, along0 = alongX ? x0 : y0, along1 = alongX ? x1 : y1;
			for (int a = across0; a <= across1; a++) {
				double line = 1 + (r.nextDouble() * 2 - 1) * amount;
				double phase = r.nextDouble() * 6;
				for (int b = along0; b <= along1; b++) {
					double f = line + Math.sin(phase + b * 0.7) * amount * 0.4;
					if (r.nextDouble() < 0.08) {
						f -= amount * 1.5;
					}
					int x = alongX ? b : a, y = alongX ? a : b;
					shade(x, y, f);
				}
			}
		}

		/** A random walk of pixels, for veins and cracks. */
		void walk(int x, int y, int steps, int c, double t, int dx, int dy) {
			for (int i = 0; i < steps; i++) {
				mix(x, y, c, t);
				int turn = r.nextInt(4);
				if (turn == 0) {
					x += dx;
				} else if (turn == 1) {
					y += dy;
				} else {
					x += dx;
					y += dy;
				}
				if (r.nextInt(5) == 0) {
					dx = r.nextInt(3) - 1;
					dx = dx == 0 ? 1 : dx;
				}
			}
		}

		/** A soft blob of colour, darkest in its middle. */
		void blob(double cx, double cy, double radius, int c, double t) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius;
					if (d < 1) {
						mix(x, y, c, t * (1 - d * d) * (0.75 + r.nextDouble() * 0.25));
					}
				}
			}
		}

		BufferedImage image() {
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			img.setRGB(0, 0, w, h, p, 0, w);
			return img;
		}
	}

	static int rgb(int r, int g, int b) {
		return (Math.clamp(r, 0, 255) << 16) | (Math.clamp(g, 0, 255) << 8) | Math.clamp(b, 0, 255);
	}

	static int shade(int c, double f) {
		return rgb((int) Math.round((c >> 16 & 255) * f), (int) Math.round((c >> 8 & 255) * f), (int) Math.round((c & 255) * f));
	}

	static int mix(int a, int b, double t) {
		t = Math.clamp(t, 0, 1);
		return rgb((int) Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t),
				(int) Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t), (int) Math.round((a & 255) * (1 - t) + (b & 255) * t));
	}

	// --- the textures -----------------------------------------------------------------------------

	private static final Map<String, Consumer<Tex>> PAINTERS = new LinkedHashMap<>();
	/** The size of each texture that isn't 16 by 16 (a painting two blocks high is 16 by 32). */
	private static final Map<String, int[]> SIZES = new java.util.HashMap<>();

	static {
		// Outside.
		PAINTERS.put("stucco", t -> stucco(t, false));
		PAINTERS.put("stucco_stained", t -> stucco(t, true));
		PAINTERS.put("limestone", HotelTexturePainter::limestone);
		PAINTERS.put("rusticated_limestone", HotelTexturePainter::rusticated);
		PAINTERS.put("limestone_keystone", HotelTexturePainter::keystone);
		PAINTERS.put("limestone_cornice", HotelTexturePainter::cornice);
		for (String part : new String[] {"base", "shaft", "capital"}) {
			PAINTERS.put("limestone_pilaster_" + part, t -> column(t, part, LIME, LIME_HI, LIME_SHADOW, LIME_SHADOW, true));
			PAINTERS.put("limestone_column_" + part, t -> column(t, part, LIME, LIME_HI, LIME_SHADOW, LIME_SHADOW, false));
			PAINTERS.put("mahogany_column_" + part, t -> column(t, part, MAH_MID, MAH_HI, MAH_DARK, BRASS, false));
			PAINTERS.put("mahogany_pilaster_" + part, t -> column(t, part, MAH_MID, MAH_HI, MAH_DARK, BRASS, true));
			PAINTERS.put("marble_column_" + part, t -> column(t, part, MARBLE, rgb(244, 236, 218), MARBLE_VEIN, rgb(190, 152, 74), false));
		}
		PAINTERS.put("slate_shingles", HotelTexturePainter::slate);
		PAINTERS.put("wrought_iron_railing", HotelTexturePainter::railing);
		PAINTERS.put("wrought_iron_railing_edge", t -> ironEdge(t, true));
		PAINTERS.put("iron_lattice", HotelTexturePainter::lattice);
		PAINTERS.put("iron_lattice_edge", t -> ironEdge(t, false));
		PAINTERS.put("brass", HotelTexturePainter::brass);
		// The reshaped hotel (hotel design 14).
		PAINTERS.put("ashlar", t -> ashlar(t, false));
		PAINTERS.put("ashlar_stained", t -> ashlar(t, true));
		PAINTERS.put("brass_lattice", HotelTexturePainter::brassLattice);
		PAINTERS.put("brass_lattice_edge", t -> {
			t.fill(BRASS_DARK);
			t.noise(0.15);
			t.rect(0, 0, 15, 0, BRASS_HI);
		});
		for (String part : new String[] {"base", "shaft", "capital"}) {
			PAINTERS.put("brass_column_" + part, t -> column(t, part, mix(BRASS, rgb(96, 70, 36), 0.3), BRASS_HI, BRASS_DARK, PATINA, false));
		}
		PAINTERS.put("canopy_bronze", HotelTexturePainter::canopy);
		PAINTERS.put("dead_hedge", HotelTexturePainter::deadHedge);
		PAINTERS.put("cinema_screen", HotelTexturePainter::screen);
		PAINTERS.put("carpet_floor", t -> runner(t, false));
		PAINTERS.put("iron_pipe", HotelTexturePainter::pipe);
		// Walls.
		PAINTERS.put("mahogany_wainscot", t -> wainscot(t, 0));
		PAINTERS.put("mahogany_wainscot_worn", t -> wainscot(t, 1));
		PAINTERS.put("charred_wainscot", t -> wainscot(t, 2));
		PAINTERS.put("damask_green", t -> damask(t, rgb(46, 68, 52), rgb(74, 100, 76), false));
		PAINTERS.put("damask_green_worn", t -> damask(t, rgb(46, 68, 52), rgb(74, 100, 76), true));
		PAINTERS.put("damask_red", t -> damask(t, rgb(100, 18, 22), rgb(132, 32, 34), false));
		PAINTERS.put("damask_red_worn", t -> damask(t, rgb(100, 18, 22), rgb(132, 32, 34), true));
		PAINTERS.put("ballroom_panel", t -> gildedPanel(t, false));
		PAINTERS.put("ballroom_panel_worn", t -> gildedPanel(t, true));
		PAINTERS.put("cream_plaster_worn", HotelTexturePainter::creamPlaster);
		PAINTERS.put("marble_wall", t -> marble(t, MARBLE, MARBLE_VEIN, true));
		PAINTERS.put("tile_wall_grimy", HotelTexturePainter::wallTiles);
		PAINTERS.put("plaster_peeling", HotelTexturePainter::peeling);
		PAINTERS.put("concrete_stained", HotelTexturePainter::concrete);
		// Floors.
		PAINTERS.put("dark_boards", t -> boards(t, false));
		PAINTERS.put("charred_boards", t -> boards(t, true));
		PAINTERS.put("parquet", HotelTexturePainter::parquet);
		PAINTERS.put("marble_cream", t -> marble(t, MARBLE, MARBLE_VEIN, false));
		PAINTERS.put("marble_black", t -> marble(t, rgb(34, 32, 32), rgb(118, 112, 102), false));
		PAINTERS.put("tile_floor_grimy", HotelTexturePainter::floorTiles);
		PAINTERS.put("algae_mosaic", HotelTexturePainter::mosaic);
		PAINTERS.put("runner_center", t -> runner(t, false));
		PAINTERS.put("runner_edge", t -> runner(t, true));
		// Ceilings.
		PAINTERS.put("coffered_mahogany", HotelTexturePainter::coffer);
		PAINTERS.put("plaster_ceiling", t -> plaster(t, false));
		PAINTERS.put("plaster_ceiling_cracked", t -> plaster(t, true));
		PAINTERS.put("gilded_ceiling", HotelTexturePainter::gildedCeiling);
		FurniturePainter.register(PAINTERS, SIZES);
	}

	/** Blue stucco in courses of dressed blocks, running bond; stained with ninety years of rain. */
	static void stucco(Tex t, boolean stained) {
		t.fill(STUCCO);
		for (int course = 0; course < 2; course++) {
			int y0 = course * 8, joint = course == 0 ? 11 : 3;
			for (int stone = 0; stone < 2; stone++) {
				double f = 0.96 + t.r.nextDouble() * 0.08;
				for (int y = y0; y < y0 + 7; y++) {
					for (int x = 0; x < 16; x++) {
						boolean left = stone == 0 ? x < joint : x >= joint;
						if (left) {
							t.shade(x, y, f);
						}
					}
				}
			}
			t.rect(0, y0 + 7, 15, y0 + 7, STUCCO_JOINT);
			t.rect(joint, y0, joint, y0 + 6, STUCCO_JOINT);
			for (int x = 0; x < 16; x++) {
				if (x != joint) {
					t.mix(x, y0, rgb(122, 140, 164), 0.35);
				}
			}
		}
		t.noise(0.035);
		if (stained && t.look != 1) {
			for (int i = 0; i < (t.look == 0 ? 4 : 2); i++) {
				int x = t.r.nextInt(16), len = 6 + t.r.nextInt(10);
				double strength = 0.25 + t.r.nextDouble() * 0.3;
				for (int y = 0; y < len; y++) {
					t.mix(x, y, rgb(48, 54, 62), strength * (1 - y / (double) len));
					if (t.r.nextInt(4) == 0) {
						x += t.r.nextBoolean() ? 1 : -1;
					}
				}
			}
		}
		if (stained && t.look == 1) {
			t.blob(4 + t.r.nextInt(8), 14, 6, rgb(70, 86, 64), 0.4);
		}
		if (stained && t.look == 2) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 5, rgb(150, 158, 166), 0.3);
		}
	}

	static final int ASHLAR = rgb(222, 207, 176), ASHLAR_JOINT = rgb(178, 160, 128);

	/**
	 * Cream ashlar, the walls of the whole hotel since the reshaping (after the reference photo):
	 * smooth dressed stone in courses half a block high, the joints fine and staggered; weathered,
	 * with soot and rain run off it in streaks, and green where it stays damp.
	 */
	static void ashlar(Tex t, boolean stained) {
		t.fill(ASHLAR);
		for (int course = 0; course < 2; course++) {
			int y0 = course * 8, joint = course == 0 ? 10 : 2;
			for (int stone = 0; stone < 2; stone++) {
				double f = 0.97 + t.r.nextDouble() * 0.06;
				for (int y = y0; y < y0 + 7; y++) {
					for (int x = 0; x < 16; x++) {
						if (stone == 0 ? x < joint : x >= joint) {
							t.shade(x, y, f);
						}
					}
				}
			}
			t.rect(0, y0 + 7, 15, y0 + 7, ASHLAR_JOINT);
			t.rect(joint, y0, joint, y0 + 6, ASHLAR_JOINT);
			for (int x = 0; x < 16; x++) {
				if (x != joint) {
					t.mix(x, y0, rgb(240, 230, 210), 0.4);
				}
			}
		}
		t.noise(0.025);
		if (stained) {
			int streaks = t.look == 0 ? 4 : t.look == 1 ? 2 : 1;
			for (int i = 0; i < streaks; i++) {
				int x = t.r.nextInt(16), len = 7 + t.r.nextInt(9);
				double strength = 0.2 + t.r.nextDouble() * 0.25;
				for (int y = 0; y < len; y++) {
					t.mix(x, y, rgb(92, 84, 70), strength * (1 - y / (double) len));
					if (t.r.nextInt(5) == 0) {
						x = Math.floorMod(x + (t.r.nextBoolean() ? 1 : -1), 16);
					}
				}
			}
			if (t.look == 2) {
				t.blob(t.r.nextInt(16), 13, 6, rgb(110, 122, 88), 0.3);
			}
		}
	}

	/** The corner's tall window: an ornate grid of brass, rosettes where it crosses (after the photo's panel). */
	static void brassLattice(Tex t) {
		for (int i = 0; i < t.p.length; i++) {
			t.p[i] = 0;
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = Math.floorMod(x, 8) - 3.5, dy = Math.floorMod(y, 8) - 3.5;
				double ring = Math.hypot(dx, dy);
				if (ring > 2.6 && ring < 3.8 || x % 8 == 0 || y % 8 == 0) {
					t.set(x, y, (x + y) % 3 == 0 ? BRASS_HI : BRASS);
				}
			}
		}
		for (int[] c : new int[][] {{3, 3}, {4, 4}, {11, 11}, {12, 12}, {3, 12}, {12, 3}, {11, 4}, {4, 11}}) {
			t.set(c[0], c[1], BRASS_DARK);
		}
	}

	/** The canopy over the doors: dark bronze plate, riveted, a brass band at its edge. */
	static void canopy(Tex t) {
		t.fill(rgb(52, 44, 34));
		t.noise(0.08);
		t.rect(0, 0, 15, 1, BRASS);
		t.rect(0, 2, 15, 2, BRASS_DARK);
		for (int x = 2; x < 16; x += 4) {
			t.set(x, 6, rgb(90, 76, 56));
			t.set(x, 12, rgb(90, 76, 56));
		}
		t.blob(t.r.nextInt(16), 10, 4, PATINA, 0.35);
	}

	/** The cinema's screen: canvas gone grey and yellow, stained, here and there split and hanging. */
	static void screen(Tex t) {
		t.fill(rgb(206, 200, 184));
		t.noise(0.04);
		for (int i = 0; i < 3; i++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 3 + t.r.nextInt(4), rgb(168, 150, 108), 0.25);
		}
		if (t.look == 1) {
			int x = 3 + t.r.nextInt(10);
			for (int y = 0; y < 16; y++) {
				t.set(x, y, rgb(24, 22, 22));
				if (y % 3 == 0) {
					x += t.r.nextBoolean() ? 1 : -1;
				}
			}
		}
		if (t.look == 2) {
			t.blob(8, 4, 5, rgb(120, 40, 34), 0.35);
		}
	}

	/** A hedge long dead: grey-brown twigs in a tangle, a few dry leaves still on it. */
	static void deadHedge(Tex t) {
		t.fill(rgb(54, 46, 36));
		for (int i = 0; i < 26; i++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(16);
			int dx = t.r.nextBoolean() ? 1 : -1, dy = t.r.nextBoolean() ? 1 : -1;
			int c = t.r.nextBoolean() ? rgb(104, 92, 76) : rgb(84, 72, 58);
			for (int k = 0; k < 3 + t.r.nextInt(4); k++) {
				t.set(Math.floorMod(x, 16), Math.floorMod(y, 16), c);
				x += t.r.nextInt(3) == 0 ? 0 : dx;
				y += t.r.nextInt(3) == 0 ? 0 : dy;
			}
		}
		for (int i = 0; i < 9; i++) {
			t.set(t.r.nextInt(16), t.r.nextInt(16), t.r.nextBoolean() ? rgb(122, 96, 52) : rgb(98, 82, 44));
		}
		t.noise(0.05);
	}

	/** Dressed limestone: one block, a fine joint along its bottom and side. */
	static void limestone(Tex t) {
		t.fill(LIME);
		t.noise(0.03);
		t.rect(0, 15, 15, 15, mix(LIME, LIME_SHADOW, 0.6));
		t.rect(15, 0, 15, 14, mix(LIME, LIME_SHADOW, 0.6));
		t.rect(0, 0, 14, 0, mix(LIME, LIME_HI, 0.5));
		for (int i = 0; i < 5; i++) {
			t.shade(t.r.nextInt(15), t.r.nextInt(15), 0.9);
		}
	}

	/** Rusticated: each block with deep channels cut along its top and side, the face tooled. */
	static void rusticated(Tex t) {
		t.fill(LIME);
		t.noise(0.05);
		t.rect(0, 0, 15, 0, rgb(112, 102, 84));
		t.rect(0, 1, 15, 1, rgb(150, 140, 118));
		t.rect(0, 0, 0, 15, rgb(112, 102, 84));
		t.rect(1, 1, 1, 15, rgb(150, 140, 118));
		t.rect(2, 2, 15, 2, LIME_HI);
		t.rect(2, 2, 2, 15, LIME_HI);
		t.rect(2, 15, 15, 15, mix(LIME, LIME_SHADOW, 0.5));
		t.rect(15, 2, 15, 15, mix(LIME, LIME_SHADOW, 0.5));
	}

	/** An arch's keystone: a carved wedge with a scroll, standing proud of the stone round it. */
	static void keystone(Tex t) {
		limestone(t);
		for (int y = 0; y < 16; y++) {
			int half = 5 - y / 5;
			int x0 = 8 - half - 1, x1 = 7 + half + 1;
			for (int x = x0; x <= x1; x++) {
				t.set(x, y, mix(LIME_HI, LIME, 0.4));
			}
			t.set(x0, y, LIME_HI);
			t.set(x1, y, LIME_SHADOW);
		}
		t.rect(5, 0, 10, 0, LIME_SHADOW);
		// A carved acanthus leaf down its face: a stem with leaflets either side.
		for (int y = 3; y <= 12; y++) {
			t.set(7, y, LIME_SHADOW);
			t.set(8, y, mix(LIME_HI, LIME, 0.2));
		}
		for (int[] s : new int[][] {{6, 4}, {5, 5}, {9, 4}, {10, 5}, {6, 7}, {5, 8}, {9, 7}, {10, 8}, {6, 10}, {9, 10}}) {
			t.set(s[0], s[1], LIME_SHADOW);
		}
		t.noise(0.02);
	}

	/** A cornice seen from the front: a moulded lip, then dentils, then beads, top to bottom. */
	static void cornice(Tex t) {
		t.fill(LIME);
		t.rect(0, 0, 15, 0, LIME_HI);
		t.rect(0, 1, 15, 2, mix(LIME, LIME_HI, 0.4));
		t.rect(0, 3, 15, 3, LIME_SHADOW);
		t.rect(0, 4, 15, 5, LIME);
		t.rect(0, 6, 15, 6, LIME_SHADOW);
		for (int x = 0; x < 16; x++) {
			boolean dentil = x % 4 < 2;
			t.rect(x, 7, x, 9, dentil ? LIME_HI : rgb(122, 112, 94));
		}
		t.rect(0, 10, 15, 10, LIME_SHADOW);
		t.rect(0, 11, 15, 11, LIME);
		t.rect(0, 12, 15, 12, LIME_HI);
		t.rect(0, 13, 15, 13, LIME_SHADOW);
		t.rect(0, 14, 15, 15, mix(LIME, LIME_SHADOW, 0.35));
		t.noise(0.025);
	}

	/**
	 * A column or pilaster piece: the base (plinth and roll mouldings), the fluted shaft, the
	 * capital (abacus, scrolls, necking), in the given stone or wood, with an accent colour for
	 * the bands (brass on mahogany, gold on marble).
	 */
	static void column(Tex t, String part, int base, int hi, int dark, int accent, boolean flat) {
		t.fill(base);
		Runnable flutes = () -> {
			for (int y = 0; y < 16; y++) {
				for (int x : new int[] {4, 7, 10}) {
					t.set(x, y, mix(base, dark, 0.65));
					t.set(x + 1, y, mix(base, hi, 0.5));
				}
				t.set(1, y, mix(base, hi, 0.4));
				t.set(14, y, mix(base, dark, 0.45));
			}
		};
		switch (part) {
			case "shaft" -> flutes.run();
			case "base" -> {
				flutes.run();
				t.rect(0, 6, 15, 6, accent);
				t.rect(0, 7, 15, 8, mix(base, hi, 0.5));
				t.rect(0, 9, 15, 9, mix(base, dark, 0.5));
				t.rect(0, 10, 15, 11, mix(base, hi, 0.3));
				t.rect(0, 12, 15, 15, mix(base, dark, 0.15));
				t.rect(0, 12, 15, 12, hi);
				t.rect(0, 15, 15, 15, dark);
			}
			case "capital" -> {
				flutes.run();
				t.rect(0, 0, 15, 0, hi);
				t.rect(0, 1, 15, 3, base);
				t.rect(0, 3, 15, 3, dark);
				t.rect(0, 4, 15, 8, mix(base, hi, 0.2));
				for (int[] c : new int[][] {{2, 6}, {13, 6}}) {
					t.set(c[0] - 1, c[1] - 1, dark);
					t.set(c[0], c[1] - 1, dark);
					t.set(c[0] + 1, c[1], dark);
					t.set(c[0], c[1] + 1, dark);
					t.set(c[0] - 1, c[1] + 1, dark);
					t.set(c[0] - 1, c[1], accent);
				}
				t.rect(5, 5, 10, 7, mix(base, hi, 0.45));
				t.rect(0, 9, 15, 9, accent);
				t.rect(0, 10, 15, 10, mix(base, dark, 0.5));
			}
			default -> {
			}
		}
		if (base == MAH_MID) {
			t.grain(2, 0, 13, 15, false, 0.05);
		} else {
			t.noise(0.025);
		}
		if (flat) {
			t.rect(0, 0, 0, 15, mix(base, dark, 0.6));
		}
	}

	/** Fish-scale slate: rounded shingles in offset rows, each a slightly different stone. */
	static void slate(Tex t) {
		t.fill(SLATE_DARK);
		for (int row = 0; row < 4; row++) {
			int y0 = row * 4, offset = (row % 2) * 2;
			for (int s = -1; s < 5; s++) {
				int cx = s * 4 + offset;
				double tone = 0.88 + t.r.nextDouble() * 0.24;
				for (int dy = 0; dy < 4; dy++) {
					double half = dy < 2 ? 2 : dy == 2 ? 1.5 : 1;
					for (int x = cx; x < cx + 4; x++) {
						double d = Math.abs(x + 0.5 - (cx + 2));
						if (d <= half) {
							int c = dy == 0 ? SLATE_HI : SLATE;
							if (d > half - 1 && dy >= 2) {
								c = SLATE_DARK;
							}
							t.set(x, y0 + dy, shade(c, tone));
						}
					}
				}
			}
		}
		t.noise(0.04);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 2.5, rgb(110, 116, 80), 0.3);
	}

	/** Ornate black iron with brass: a rail, bars, and a scroll between each pair. Clear between. */
	static void railing(Tex t) {
		for (int i = 0; i < 256; i++) {
			t.p[i] = 0;
		}
		t.rect(0, 0, 15, 0, BRASS);
		t.rect(0, 1, 15, 2, IRON);
		t.rect(0, 14, 15, 15, IRON);
		for (int x : new int[] {1, 5, 10, 14}) {
			t.rect(x, 3, x, 13, IRON);
		}
		for (int[] s : new int[][] {{2, 9}, {3, 8}, {3, 7}, {2, 6}, {12, 9}, {11, 8}, {11, 7}, {12, 6}}) {
			t.set(s[0], s[1], IRON);
		}
		for (int y = 5; y <= 11; y++) {
			if (y != 8) {
				t.set(7, y, IRON);
				t.set(8, y, IRON);
			}
		}
		t.set(7, 8, BRASS_HI);
		t.set(8, 8, BRASS);
		t.set(1, 3, BRASS);
		t.set(14, 3, BRASS);
	}

	/** A lift cage's iron lattice: a diamond grid with brass rosettes where it crosses. */
	static void lattice(Tex t) {
		for (int i = 0; i < 256; i++) {
			t.p[i] = 0;
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (Math.floorMod(x + y, 4) == 0 || Math.floorMod(x - y, 4) == 0) {
					t.set(x, y, IRON);
				}
			}
		}
		for (int y = 0; y < 16; y += 4) {
			for (int x = 0; x < 16; x += 4) {
				if (Math.floorMod(x / 4 + y / 4, 2) == 0) {
					t.set(x, y, BRASS);
				}
			}
		}
		t.rect(0, 0, 15, 0, IRON);
		t.rect(0, 15, 15, 15, IRON);
	}

	static void ironEdge(Tex t, boolean brassTop) {
		t.fill(IRON);
		t.noise(0.2);
		if (brassTop) {
			t.rect(0, 0, 15, 0, BRASS);
		}
	}

	/** Tarnished brass: brushed, with polished highlights and patches of green patina. */
	static void brass(Tex t) {
		t.fill(mix(BRASS, rgb(96, 70, 36), 0.3));
		t.grain(0, 0, 15, 15, false, 0.07);
		for (int y = 0; y < 16; y++) {
			t.mix(3, y, BRASS_HI, 0.5);
			t.mix(4, y, BRASS_HI, 0.25);
			t.mix(12, y, BRASS_DARK, 0.35);
		}
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 4.5, PATINA, 0.6);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(60, 48, 30), 0.5);
		t.rect(0, 0, 15, 0, BRASS_HI);
		t.rect(0, 15, 15, 15, BRASS_DARK);
	}

	/** An old iron pipe, rusting, with a flanged joint. */
	static void pipe(Tex t) {
		t.fill(rgb(70, 62, 58));
		t.grain(0, 0, 15, 15, false, 0.08);
		for (int x = 0; x < 16; x++) {
			t.mix(x, 3, rgb(110, 102, 96), 0.4);
			t.mix(x, 12, rgb(40, 36, 34), 0.4);
		}
		t.rect(0, 6, 15, 6, rgb(96, 88, 82));
		t.rect(0, 7, 15, 8, rgb(84, 76, 70));
		t.rect(0, 9, 15, 9, rgb(48, 42, 40));
		for (int i = 0; i < 4; i++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(16);
			for (int k = 0; k < 4 + t.r.nextInt(5); k++) {
				t.mix(x, y + k, RUST, 0.6);
			}
		}
	}

	/**
	 * The lower panelling: a chair rail along the top, a raised mahogany panel in its frame, the
	 * skirting at the bottom. Worn: faded and scratched, stained along the floor. Charred: burned
	 * black, cracked, worst at the bottom.
	 */
	static void wainscot(Tex t, int state) {
		t.fill(MAH);
		t.rect(0, 0, 15, 0, MAH_HI);
		t.rect(0, 1, 15, 1, MAH_MID);
		t.rect(0, 2, 15, 2, MAH_DARK);
		t.rect(0, 3, 15, 12, MAH_MID);
		t.grain(0, 3, 15, 12, false, 0.05);
		t.rect(3, 4, 12, 4, MAH_DARK);
		t.rect(3, 4, 3, 11, MAH_DARK);
		t.rect(4, 11, 12, 11, MAH_LIGHT);
		t.rect(12, 5, 12, 11, MAH_LIGHT);
		t.rect(4, 5, 11, 10, MAH);
		t.grain(4, 5, 11, 10, false, 0.06);
		t.rect(5, 6, 10, 6, mix(MAH, MAH_LIGHT, 0.4));
		t.rect(0, 13, 15, 13, MAH_LIGHT);
		t.rect(0, 14, 15, 15, MAH_DARK);
		t.noise(0.02);
		if (state == 1) {
			for (int i = 0; i < 256; i++) {
				t.p[i] = 0xFF000000 | mix(t.p[i] & 0xFFFFFF, rgb(120, 96, 76), 0.18);
			}
			for (int i = 0; i < 4; i++) {
				int x = t.r.nextInt(14), y = 3 + t.r.nextInt(9);
				for (int k = 0; k < 3; k++) {
					t.mix(x + k, y + k / 2, rgb(170, 130, 96), 0.6);
				}
			}
			t.blob(t.r.nextInt(16), 15, 5, rgb(58, 46, 30), 0.45);
			for (int x = 0; x < 16; x++) {
				t.mix(x, 0, rgb(150, 140, 126), 0.45);
			}
		} else if (state == 2) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					t.set(x, y, mix(t.get(x, y), rgb(22, 16, 14), 0.55 + y * 0.025));
				}
			}
			char_cracks(t);
		}
	}

	static void char_cracks(Tex t) {
		for (int i = 0; i < 6; i++) {
			t.walk(t.r.nextInt(16), t.r.nextInt(16), 5, rgb(8, 6, 6), 0.9, t.r.nextBoolean() ? 1 : -1, 1);
		}
		for (int i = 0; i < 4; i++) {
			t.mix(t.r.nextInt(16), t.r.nextInt(16), rgb(150, 70, 30), 0.6);
		}
		for (int i = 0; i < 8; i++) {
			t.mix(t.r.nextInt(16), t.r.nextInt(16), rgb(118, 112, 104), 0.6);
		}
	}

	/** The damask medallion, half of it: the other half is its mirror. */
	private static final String[] DAMASK = {
			"........",
			".......#",
			"......#.",
			".....#.#",
			"....#.#.",
			"...#..#.",
			"..#..#..",
			".#..#..#",
			".#..#..#",
			"..#..#..",
			"...#..#.",
			"....#.#.",
			".....#.#",
			"......#.",
			".......#",
			"#.......",
	};

	/** Damask wallpaper: a woven medallion repeat. Worn: faded, water-stained, a corner peeling. */
	static void damask(Tex t, int base, int pattern, boolean worn) {
		t.fill(base);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int hx = x < 8 ? x : 15 - x;
				if (DAMASK[y].charAt(hx) == '#') {
					t.set(x, y, pattern);
				}
				if (x % 2 == 0) {
					t.shade(x, y, 0.97);
				}
			}
		}
		t.set(7, 7, mix(pattern, BRASS, 0.6));
		t.set(8, 8, mix(pattern, BRASS, 0.6));
		t.noise(0.025);
		if (worn) {
			for (int i = 0; i < 256; i++) {
				t.p[i] = 0xFF000000 | mix(t.p[i] & 0xFFFFFF, rgb(140, 128, 104), 0.22);
			}
			int x0 = t.look == 2 ? -10 : t.r.nextInt(12);
			for (int y = 0; y < 16 && x0 >= 0; y++) {
				double s = 0.45 * (1 - y / 22.0);
				for (int x = x0; x < x0 + 3; x++) {
					t.mix(x, y, rgb(96, 76, 44), s);
				}
				t.mix(x0 - 1, y, rgb(70, 52, 30), s * 0.8);
				if (t.r.nextInt(5) == 0) {
					x0 += t.r.nextBoolean() ? 1 : -1;
				}
			}
			if (t.look == 0) {
				for (int y = 0; y < 5; y++) {
					for (int x = 15 - (4 - y); x < 16; x++) {
						t.set(x, y, rgb(196, 182, 150));
					}
					t.set(15 - (4 - y) - 1, y, rgb(222, 210, 180));
				}
			}
			if (t.look == 2) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, MOLD, 0.4);
			}
		}
	}

	/** The ballroom's cream panels framed in gold. Worn: grimy, cracked, the gilt flaking off. */
	static void gildedPanel(Tex t, boolean worn) {
		int cream = rgb(222, 208, 176), gold = rgb(190, 152, 70), goldHi = rgb(232, 200, 120);
		t.fill(cream);
		t.noise(0.02);
		t.rect(0, 0, 15, 0, mix(cream, gold, 0.3));
		t.rect(0, 15, 15, 15, mix(cream, rgb(150, 130, 100), 0.4));
		for (int i = 2; i <= 13; i++) {
			t.set(i, 2, gold);
			t.set(i, 13, gold);
			t.set(2, i, gold);
			t.set(13, i, gold);
			t.set(i, 3, mix(cream, rgb(160, 140, 104), 0.35));
			t.set(3, i, mix(cream, rgb(160, 140, 104), 0.35));
		}
		for (int[] c : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
			t.set(c[0], c[1], goldHi);
		}
		t.set(7, 7, gold);
		t.set(8, 8, gold);
		t.set(7, 8, goldHi);
		t.set(8, 7, gold);
		if (worn) {
			for (int i = 0; i < 256; i++) {
				t.p[i] = 0xFF000000 | mix(t.p[i] & 0xFFFFFF, rgb(130, 112, 84), 0.2);
			}
			if (t.look == 0) {
				t.walk(t.r.nextInt(16), 0, 14, rgb(90, 76, 58), 0.7, 1, 1);
			}
			for (int i = 0; i < (t.look == 1 ? 4 : 1); i++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 2, rgb(160, 156, 146), 0.6);
			}
			for (int x = 0; x < 16; x++) {
				t.mix(x, 15, GRIME, 0.5);
				t.mix(x, 14, GRIME, 0.3);
			}
		}
	}

	/** The grand rooms' cream plaster, gone dull: dust, faint damp, a crack in some. */
	static void creamPlaster(Tex t) {
		t.fill(rgb(214, 200, 168));
		t.noise(0.022);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 7, rgb(176, 160, 128), 0.25);
		if (t.look == 1) {
			t.walk(t.r.nextInt(16), 0, 16, rgb(120, 104, 80), 0.6, 1, 1);
		}
		if (t.look == 2) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, 15, GRIME, 0.35);
				t.mix(x, 14, GRIME, 0.2);
			}
		}
	}

	/** Marble: a cream (or black) slab with veins, a fine joint round it; on walls in courses. */
	static void marble(Tex t, int base, int vein, boolean wall) {
		t.fill(base);
		t.noise(0.02);
		// Cloudy tone across the slab, then one soft vein and a fainter one.
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 9, vein, 0.12);
		int x = t.r.nextInt(16);
		for (int y = 0; y < 16; y++) {
			t.mix(x, y, vein, 0.38);
			t.mix(x + 1, y, vein, 0.14);
			if (t.r.nextInt(3) == 0) {
				x += t.r.nextBoolean() ? 1 : -1;
			}
		}
		if (t.look % 2 == 0) {
			int y = t.r.nextInt(16);
			for (int xx = 0; xx < 16; xx++) {
				t.mix(xx, y, vein, 0.18);
				if (t.r.nextInt(3) == 0) {
					y += t.r.nextBoolean() ? 1 : -1;
				}
			}
		}
		int joint = mix(base, vein, 0.55);
		if (wall) {
			t.rect(0, 7, 15, 7, joint);
			t.rect(0, 15, 15, 15, joint);
			t.rect(11, 0, 11, 6, joint);
			t.rect(3, 8, 3, 14, joint);
		} else {
			t.rect(0, 0, 15, 0, joint);
			t.rect(0, 0, 0, 15, joint);
			t.rect(1, 1, 15, 1, mix(base, rgb(255, 255, 255), 0.15));
		}
	}

	/** White tiles gone yellow-brown with grime from the floor up, streaked, one cracked. */
	static void wallTiles(Tex t) {
		int tile = rgb(206, 202, 188), grout = rgb(104, 98, 84);
		t.fill(tile);
		for (int row = 0; row < 4; row++) {
			int y0 = row * 4, offset = (row % 2) * 4;
			t.rect(0, y0 + 3, 15, y0 + 3, grout);
			for (int x = offset; x < 16 + offset; x += 8) {
				t.rect(Math.floorMod(x + 7, 16), y0, Math.floorMod(x + 7, 16), y0 + 2, grout);
				t.mix(Math.floorMod(x, 16), y0, rgb(236, 234, 226), 0.6);
			}
		}
		t.noise(0.03);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, y, GRIME, 0.04 + y * 0.018);
			}
		}
		for (int i = 0; i < 3; i++) {
			int x = t.r.nextInt(16), len = 5 + t.r.nextInt(9);
			for (int y = 0; y < len; y++) {
				t.mix(x, y, rgb(110, 92, 58), 0.35);
			}
		}
		t.walk(t.r.nextInt(8) + 4, t.r.nextInt(8), 5, rgb(60, 56, 48), 0.8, 1, 1);
	}

	/** Old plaster gone brown with damp, a patch fallen away to the lath behind, some mould. */
	static void peeling(Tex t) {
		t.fill(rgb(182, 168, 136));
		t.noise(0.05);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 6, rgb(130, 108, 70), 0.35);
		// A ragged patch fallen away near an edge, wider than it is tall: in one look of ten, a
		// smaller one in another.
		int cx = t.r.nextBoolean() ? 3 + t.r.nextInt(3) : 10 + t.r.nextInt(3), cy = t.r.nextBoolean() ? 3 : 12;
		double hole = t.look == 0 ? 4.0 : t.look == 1 ? 2.4 : 0;
		double wobble = t.r.nextDouble() * 6;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double d = Math.hypot((x - cx) * 0.6, (y - cy) * 1.3) + Math.sin(x * 1.7 + wobble) * 0.6 + t.r.nextDouble() * 0.8;
				if (hole == 0) {
					continue;
				}
				if (d < hole) {
					t.set(x, y, y % 3 == 2 ? rgb(34, 26, 20) : shade(rgb(108, 76, 46), 0.9 + t.r.nextDouble() * 0.2));
				} else if (d < hole + 0.8) {
					t.set(x, y, rgb(214, 202, 172));
				}
			}
		}
		if (t.look == 2 || t.look == 3) {
			t.blob(t.r.nextInt(16), 14, 4, MOLD, 0.5);
			t.blob(t.r.nextInt(16), 13, 2, rgb(40, 50, 34), 0.4);
		}
	}

	/** Bare concrete: board-formed, tie holes, rust run from them, water stains. */
	static void concrete(Tex t) {
		t.fill(rgb(118, 116, 110));
		t.noise(0.07);
		for (int x = 0; x < 16; x++) {
			t.mix(x, 8, rgb(92, 90, 86), 0.4);
		}
		for (int[] h : t.look == 0 ? new int[][] {{4, 4}, {12, 12}} : new int[0][]) {
			t.set(h[0], h[1], rgb(40, 38, 36));
			for (int y = h[1] + 1; y < h[1] + 6; y++) {
				t.mix(h[0], y, RUST, 0.5 - (y - h[1]) * 0.07);
			}
		}
		if (t.look != 2) {
			t.blob(t.r.nextInt(16), 0, 6, rgb(72, 70, 64), 0.35);
		}
		for (int i = 0; i < 6; i++) {
			t.shade(t.r.nextInt(16), t.r.nextInt(16), 0.75);
		}
	}

	/** Dark floorboards (or burned ones): four boards, joints staggered, grain along them. */
	static void boards(Tex t, boolean charred) {
		t.fill(charred ? rgb(36, 28, 24) : rgb(58, 30, 18));
		for (int b = 0; b < 4; b++) {
			int y0 = b * 4;
			double tone = 0.9 + t.r.nextDouble() * 0.2;
			for (int y = y0; y < y0 + 4; y++) {
				for (int x = 0; x < 16; x++) {
					t.shade(x, y, tone);
				}
			}
			t.grain(0, y0, 15, y0 + 2, true, 0.06);
			t.rect(0, y0 + 3, 15, y0 + 3, charred ? rgb(12, 10, 8) : rgb(28, 14, 8));
			int end = t.r.nextInt(16);
			t.rect(end, y0, end, y0 + 2, charred ? rgb(12, 10, 8) : rgb(30, 16, 9));
			t.mix(0, y0, rgb(110, 64, 40), 0.2);
		}
		if (charred) {
			char_cracks(t);
		}
	}

	/** Herringbone parquet: slats in chevrons, each its own shade, dark seams between. */
	static void parquet(Tex t) {
		int base = rgb(112, 66, 34), seam = rgb(50, 28, 14);
		t.fill(base);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int col = x / 8, local = x % 8;
				// Slats run up and away from the middle of each column: a chevron.
				int d = Math.floorMod(col == 0 ? y + local : y + (7 - local), 16);
				int slat = d / 3 + col * 7;
				double tone = 0.84 + ((slat * 37 + col * 13) % 11) / 32.0;
				t.set(x, y, shade(base, tone));
				if (d % 3 == 0) {
					t.set(x, y, mix(seam, base, 0.3));
				}
				if (local == 0 && col == 1 || local == 7 && col == 0) {
					t.set(x, y, seam);
				}
			}
		}
		t.noise(0.03);
	}

	/** Small square tiles on the kitchen floor, grimy, some stained dark. */
	static void floorTiles(Tex t) {
		int tile = rgb(176, 170, 156), grout = rgb(70, 64, 54);
		t.fill(tile);
		for (int ty = 0; ty < 4; ty++) {
			for (int tx = 0; tx < 4; tx++) {
				double tone = 0.92 + t.r.nextDouble() * 0.12;
				for (int y = ty * 4; y < ty * 4 + 3; y++) {
					for (int x = tx * 4; x < tx * 4 + 3; x++) {
						t.shade(x, y, tone);
					}
				}
			}
		}
		for (int i = 0; i < 16; i++) {
			t.rect(i, 3, i, 3, grout);
			t.rect(i, 7, i, 7, grout);
			t.rect(i, 11, i, 11, grout);
			t.rect(i, 15, i, 15, grout);
			t.rect(3, i, 3, i, grout);
			t.rect(7, i, 7, i, grout);
			t.rect(11, i, 11, i, grout);
			t.rect(15, i, 15, i, grout);
		}
		t.noise(0.04);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 6, GRIME, 0.4);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(52, 36, 24), 0.45);
	}

	/** The spa's mosaic: little white and teal tiles, green with algae in patches. */
	static void mosaic(Tex t) {
		int grout = rgb(64, 84, 74);
		t.fill(grout);
		for (int ty = 0; ty < 8; ty++) {
			for (int tx = 0; tx < 8; tx++) {
				int c = t.r.nextInt(10) < 7 ? rgb(198, 214, 204) : rgb(104, 156, 146);
				t.set(tx * 2, ty * 2, shade(c, 0.94 + t.r.nextDouble() * 0.1));
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (x % 2 == 1 || y % 2 == 1) {
					t.set(x, y, grout);
				}
			}
		}
		for (int y = 0; y < 16; y += 2) {
			for (int x = 0; x < 16; x += 2) {
				int c = t.get(x, y);
				t.set(x + 1, y, c);
				t.set(x, y + 1, c);
				t.set(x + 1, y + 1, shade(c, 0.92));
			}
		}
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 6, rgb(66, 96, 46), 0.6);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(40, 60, 30), 0.5);
	}

	/** The corridor runner: deep red with a small lattice and gold dots; the edge has the border. */
	static void runner(Tex t, boolean edge) {
		int red = rgb(118, 18, 22), dark = rgb(84, 10, 14), gold = rgb(168, 128, 58);
		t.fill(red);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (Math.floorMod(x + y, 6) == 0 || Math.floorMod(x - y, 6) == 0) {
					t.set(x, y, dark);
				}
			}
		}
		for (int y = 3; y < 16; y += 6) {
			for (int x = 0; x < 16; x += 6) {
				t.set(x, y, gold);
			}
		}
		t.noise(0.05);
		if (edge) {
			t.rect(0, 0, 15, 0, rgb(36, 20, 12));
			t.rect(0, 1, 15, 4, gold);
			for (int x = 0; x < 16; x++) {
				if (x % 4 == 0 || x % 4 == 1) {
					t.set(x, 2 + (x % 4), dark);
				} else {
					t.set(x, 3 - (x % 4 - 2), dark);
				}
			}
			t.rect(0, 5, 15, 5, dark);
			t.noise(0.03);
		}
	}

	/** A coffered ceiling: a dark mahogany frame round a deep recess with a brass boss. */
	static void coffer(Tex t) {
		t.fill(MAH_MID);
		t.grain(0, 0, 15, 15, true, 0.05);
		t.rect(0, 0, 15, 0, MAH_DARK);
		t.rect(0, 0, 0, 15, MAH_DARK);
		t.rect(1, 1, 15, 1, MAH_HI);
		t.rect(1, 1, 1, 15, MAH_HI);
		t.rect(3, 3, 12, 12, rgb(34, 16, 10));
		t.rect(3, 3, 12, 3, MAH_LIGHT);
		t.rect(3, 3, 3, 12, MAH_LIGHT);
		t.rect(4, 4, 12, 4, MAH_DARK);
		t.grain(5, 5, 11, 11, true, 0.05);
		t.set(7, 7, BRASS_HI);
		t.set(8, 7, BRASS);
		t.set(7, 8, BRASS);
		t.set(8, 8, BRASS_DARK);
	}

	/** Plain plaster ceiling; or cracked, with water rings and damp. */
	static void plaster(Tex t, boolean cracked) {
		t.fill(rgb(206, 196, 168));
		t.noise(0.025);
		if (cracked && (t.look == 0 || t.look == 1)) {
			t.walk(0, t.r.nextInt(16), 20, rgb(80, 66, 48), 0.85, 1, t.r.nextBoolean() ? 1 : -1);
			t.walk(t.r.nextInt(16), 0, 10, rgb(90, 74, 54), 0.7, 1, 1);
		}
		if (cracked && (t.look == 0 || t.look == 2)) {
			int cx = 4 + t.r.nextInt(8), cy = 4 + t.r.nextInt(8);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double d = Math.hypot(x - cx, y - cy);
					if (Math.abs(d - 4) < 0.7) {
						t.mix(x, y, rgb(120, 92, 52), 0.55);
					} else if (d < 4) {
						t.mix(x, y, rgb(170, 146, 102), 0.3);
					}
				}
			}
		}
	}

	/** The ballroom's ceiling: cream with a gilded diamond lattice and bosses where it crosses. */
	static void gildedCeiling(Tex t) {
		int cream = rgb(220, 206, 170), gold = rgb(188, 150, 70);
		t.fill(cream);
		t.noise(0.02);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (Math.floorMod(x + y, 8) == 0 || Math.floorMod(x - y, 8) == 0) {
					t.set(x, y, gold);
				}
			}
		}
		for (int[] c : new int[][] {{0, 0}, {8, 8}, {0, 8}, {8, 0}}) {
			t.set(c[0], c[1], rgb(236, 206, 128));
		}
	}

	// --- writing them -----------------------------------------------------------------------------

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/rauk/textures/block");
		Path sheet = Path.of(args.length > 1 ? args[1] : "docs/hotel/materials.png");
		Files.createDirectories(out);
		Map<String, BufferedImage> painted = new LinkedHashMap<>();
		Map<String, Integer> looks = new java.util.HashMap<>();
		for (var m : rauk.com.hotel.HotelMaterials.ALL) {
			for (String tex : rauk.com.hotel.HotelMaterials.textures(m)) {
				looks.put(tex, m.variants());
			}
		}
		for (var e : PAINTERS.entrySet()) {
			int n = looks.getOrDefault(e.getKey(), 1);
			for (int look = 0; look < n; look++) {
				String name = look == 0 ? e.getKey() : e.getKey() + "_" + look;
				int[] size = SIZES.getOrDefault(e.getKey(), new int[] {16, 16});
				Tex t = new Tex(name.hashCode() * 31L + 1931, look, size[0], size[1]);
				e.getValue().accept(t);
				BufferedImage img = t.image();
				ImageIO.write(img, "png", out.resolve(name + ".png").toFile());
				Integer frametime = FurniturePainter.ANIMATED.get(name);
				if (frametime != null) {
					Files.writeString(out.resolve(name + ".png.mcmeta"), "{\n  \"animation\": {\"frametime\": " + frametime + "}\n}\n");
				}
				painted.put(name, img);
			}
		}
		// Every texture used by a material must have been painted.
		for (var m : rauk.com.hotel.HotelMaterials.ALL) {
			for (String tex : rauk.com.hotel.HotelMaterials.textures(m)) {
				if (!painted.containsKey(tex)) {
					throw new IllegalStateException("No painter for " + tex);
				}
			}
		}
		// And every texture the furniture's models use.
		for (var piece : rauk.com.hotel.HotelFurniture.all()) {
			for (var look : piece.allLooks()) {
				for (int part = 0; part < piece.parts(); part++) {
					for (var box : rauk.com.hotel.HotelFurniture.cell(piece, look, part)) {
						for (String role : box.faces()) {
							String tex = role == null ? "" : piece.texture(role);
							if (tex.startsWith("rauk:block/")) {
								String name = tex.substring("rauk:block/".length()) + (look.lit() && box.glow() ? "_lit" : "");
								if (!painted.containsKey(name)) {
									throw new IllegalStateException("No painter for " + name + " (" + piece.id + ")");
								}
							}
						}
					}
				}
			}
		}
		Map<String, BufferedImage> materials = new LinkedHashMap<>(), furniture = new LinkedHashMap<>();
		painted.forEach((k, v) -> (k.startsWith("f_") ? furniture : materials).put(k, v));
		drawSheet(materials, sheet);
		drawSheet(furniture, sheet.resolveSibling("furniture_textures.png"));
		if (args.length > 2) {
			HotelBlockAssets.write(Path.of(args[2]));
		}
		System.out.println("Painted " + painted.size() + " hotel textures into " + out.toAbsolutePath());
	}

	/** All of them on one sheet, each as a 3 by 3 tiling so patterns show how they repeat. */
	private static void drawSheet(Map<String, BufferedImage> painted, Path path) throws IOException {
		int scale = 3, tile = 16 * scale * 3, cols = 10, label = 18;
		// One square per texture, its looks mixed at random the way they are in the game.
		Map<String, java.util.List<BufferedImage>> groups = new LinkedHashMap<>();
		for (var e : painted.entrySet()) {
			String base = e.getKey().replaceAll("_[0-9]+$", "");
			groups.computeIfAbsent(base, k -> new java.util.ArrayList<>()).add(e.getValue());
		}
		Random pick = new Random(7);
		int rows = (groups.size() + cols - 1) / cols;
		BufferedImage img = new BufferedImage(cols * (tile + 8) + 8, rows * (tile + label + 8) + 8, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0x2A2624));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		int i = 0;
		for (var e : groups.entrySet()) {
			int x0 = 8 + (i % cols) * (tile + 8), y0 = 8 + (i / cols) * (tile + label + 8);
			g.setColor(new Color(0x5A5450));
			g.fillRect(x0, y0, tile, tile);
			BufferedImage first = e.getValue().get(0);
			if (first.getWidth() != first.getHeight()) {
				// A painting or the like: drawn once, as big as fits.
				double f = Math.min((double) tile / first.getWidth(), (double) tile / first.getHeight());
				int dw = (int) (first.getWidth() * f), dh = (int) (first.getHeight() * f);
				g.drawImage(first, x0 + (tile - dw) / 2, y0 + (tile - dh) / 2, dw, dh, null);
			} else {
				for (int ty = 0; ty < 3; ty++) {
					for (int tx = 0; tx < 3; tx++) {
						BufferedImage look = e.getValue().get(pick.nextInt(e.getValue().size()));
						g.drawImage(look, x0 + tx * 16 * scale, y0 + ty * 16 * scale, 16 * scale, 16 * scale, null);
					}
				}
			}
			g.setColor(new Color(0xE8E0D0));
			g.drawString(e.getKey(), x0, y0 + tile + 14);
			i++;
		}
		g.dispose();
		Files.createDirectories(path.getParent());
		ImageIO.write(img, "png", path.toFile());
	}

	private HotelTexturePainter() {
	}
}
