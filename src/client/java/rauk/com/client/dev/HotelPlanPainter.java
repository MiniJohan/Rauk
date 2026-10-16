package rauk.com.client.dev;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import rauk.com.hotel.HotelBuilder;
import rauk.com.hotel.HotelFurniture;
import rauk.com.hotel.HotelPlan;
import rauk.com.hotel.HotelPlan.Area;
import rauk.com.hotel.HotelPlan.Kind;
import rauk.com.hotel.HotelPlan.Level;
import rauk.com.hotel.HotelPlan.Style;

/**
 * Draws the hotel's floor plans from {@link HotelBuilder}, one image a level and an overview of
 * them all ({@code ./gradlew drawHotelPlans}, into docs/hotel). Each plan is a cut through the
 * level at head height: walls dark, spaces colored by their style, the open middle of the atrium
 * and the drops hatched, stairs with an arrow pointing up them, doors, shutters and water marked.
 */
public final class HotelPlanPainter {
	private static final int MARGIN = 2;
	private static final int X_MIN = -6, X_MAX = 85, Z_MIN = -4, Z_MAX = 126;

	private HotelPlanPainter() {
	}

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "docs/hotel");
		Files.createDirectories(out);
		HotelPlan plan = HotelBuilder.plan();
		int s = 9;
		BufferedImage[] levels = new BufferedImage[Level.values().length];
		for (Level lv : Level.values()) {
			BufferedImage img = draw(plan, lv, s);
			levels[lv.ordinal()] = img;
			ImageIO.write(img, "png", out.resolve("plan_" + lv.label + ".png").toFile());
		}
		// The overview: top floors first, the way you'd stack them.
		int w = levels[0].getWidth(), h = levels[0].getHeight();
		BufferedImage all = new BufferedImage(w * 2, h * 4, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = all.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, all.getWidth(), all.getHeight());
		Level[] order = {Level.R, Level.L4, Level.L3, Level.L2, Level.L1, Level.B1, Level.B2};
		for (int i = 0; i < order.length; i++) {
			g.drawImage(levels[order[i].ordinal()], (i % 2) * w, (i / 2) * h, null);
		}
		legend(g, w, 3 * h, w, h);
		g.dispose();
		ImageIO.write(all, "png", out.resolve("plans.png").toFile());
		System.out.println("Hotel plans drawn into " + out.toAbsolutePath());
		// The route map: how many openings, and a few routes across the building.
		var nav = rauk.com.hotel.HotelNav.get();
		System.out.println("Openings between spaces: " + nav.portals.size());
		double[][] trips = {{40.5, -7, 7.5, 65.5, -7, 12.5}, {40.5, 0, 27.5, 65.5, -7, 12.5}, {15.5, 11, 22.5, 40.5, 14, 7.5},
				{65.5, -7, 12.5, 53.5, -14, 8.5}, {12.5, -7, 27.5, 40.5, 21, 9.5}, {5.5, 7, 40.5, 70.5, 14, 40.5}};
		for (double[] t : trips) {
			var route = nav.route(t[0], t[1], t[2], t[3], t[4], t[5]);
			StringBuilder line = new StringBuilder(String.format("%.0f %.0f %.0f -> %.0f %.0f %.0f:", t[0], t[1], t[2], t[3], t[4], t[5]));
			if (route == null) {
				line.append(" NO ROUTE");
			} else {
				for (var p : route) {
					line.append(" [").append(plan.areas.get(p.a()).name()).append(" | ").append(plan.areas.get(p.b()).name()).append(" @ ")
							.append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(']');
				}
			}
			System.out.println(line);
		}
	}

	private static BufferedImage draw(HotelPlan plan, Level lv, int s) {
		int cols = X_MAX - X_MIN + 1, rows = Z_MAX - Z_MIN + 1;
		int top = 34;
		BufferedImage img = new BufferedImage(cols * s + MARGIN * 2, rows * s + top + MARGIN, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xF4F1EA));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		int y = lv.base + 1;
		for (int z = Z_MIN; z <= Z_MAX; z++) {
			for (int x = X_MIN; x <= X_MAX; x++) {
				int px = MARGIN + (x - X_MIN) * s, pz = top + (z - Z_MIN) * s;
				cell(g, plan, lv, x, y, z, px, pz, s);
			}
		}
		// Grid every 8 blocks, faint.
		g.setColor(new Color(0, 0, 0, 22));
		for (int x = 0; x <= HotelPlan.WIDTH; x += 8) {
			int px = MARGIN + (x - X_MIN) * s;
			g.drawLine(px, top, px, img.getHeight());
		}
		for (int z = 0; z <= HotelPlan.TOTAL_DEPTH; z += 8) {
			int pz = top + (z - Z_MIN) * s;
			g.drawLine(0, pz, img.getWidth(), pz);
		}
		labels(g, plan, lv, y, s, top);
		g.setColor(new Color(0x222222));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
		g.drawString("Level " + lv.label + "  " + lv.title, 8, 24);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		String note = "sea (north) at the top, front doors at the bottom corner  |  grid 8 blocks  |  floor y " + lv.base;
		g.drawString(note, img.getWidth() - g.getFontMetrics().stringWidth(note) - 8, 24);
		g.dispose();
		return img;
	}

	static void cell(Graphics2D g, HotelPlan plan, Level lv, int x, int y, int z, int px, int pz, int s) {
		String block = plan.get(x, y, z);
		String id = HotelPlan.blockId(block);
		Area owner = plan.owner(x, y, z);
		Color c;
		if (block.isEmpty()) {
			// Nothing at head height: show the roof or glass below, if there is one in this level.
			for (int yy = y - 1; yy >= lv.base - 1; yy--) {
				String under = HotelPlan.blockId(plan.get(x, yy, z));
				if (!under.isEmpty()) {
					fill(g, px, pz, s, under.contains("glass") ? new Color(0xBFE3EE) : under.contains("iron_block") ? new Color(0x9AA3AA)
							: new Color(0xC9CDD3));
					return;
				}
			}
			return;
		}
		boolean fitting = id.contains("door") || id.equals("minecraft:iron_bars") || id.contains("ladder") || id.contains("water");
		if (!fitting && stairsUnder(plan, lv, x, z) != null && (owner == null || owner.kind() != Kind.LIFT)) {
			stairs(g, plan, lv, x, z, px, pz, s);
			return;
		}
		if (id.equals("minecraft:air")) {
			boolean floorless = plan.isAir(x, lv.base - 1, z) && plan.isAir(x, lv.base, z);
			if (owner != null && owner.kind() == Kind.LIFT) {
				fill(g, px, pz, s, new Color(0x4A4A4A));
				g.setColor(new Color(0x888888));
				g.drawLine(px, pz, px + s, pz + s);
				g.drawLine(px + s, pz, px, pz + s);
				return;
			}
			if (stairsUnder(plan, lv, x, z) != null) {
				stairs(g, plan, lv, x, z, px, pz, s);
				return;
			}
			if (plan.get(x, lv.base, z).equals("minecraft:water") || plan.get(x, lv.base - 1, z).equals("minecraft:water")) {
				fill(g, px, pz, s, new Color(0x4A7FD0));
				return;
			}
			c = owner == null ? new Color(0xDDDDDD) : new Color(owner.style().color);
			if (floorless) {
				c = c.darker().darker();
				fill(g, px, pz, s, c);
				g.setColor(new Color(255, 255, 255, 60));
				g.drawLine(px, pz + s, px + s, pz);
				return;
			}
			fill(g, px, pz, s, c);
			furniture(g, HotelPlan.blockId(plan.get(x, lv.base, z)), px, pz, s);
			return;
		}
		if (HotelFurniture.is(id) || fitting(id)) {
			fill(g, px, pz, s, owner == null ? new Color(0xDDDDDD) : new Color(owner.style().color));
			furniture(g, HotelPlan.blockId(plan.get(x, lv.base, z)), px, pz, s);
			furniture(g, id, px, pz, s);
			return;
		}
		if (id.equals("minecraft:water")) {
			c = new Color(0x3A6FC4);
		} else if (id.contains("door")) {
			c = new Color(0xA0642C);
		} else if (id.equals("rauk:shutter")) {
			c = new Color(0x4C6A80);
		} else if (id.equals("minecraft:iron_bars")) {
			fill(g, px, pz, s, owner == null ? new Color(0xBBBBBB) : new Color(owner.style().color));
			g.setColor(new Color(0x555555));
			g.fillOval(px + s / 2 - 2, pz + s / 2 - 2, 4, 4);
			return;
		} else if (id.contains("glass")) {
			c = new Color(0xA8D8E8);
		} else if (id.contains("ladder")) {
			c = new Color(0xC8A060);
		} else if (id.contains("bookshelf")) {
			c = new Color(0x7A3E2A);
		} else if (id.contains("stairs")) {
			stairs(g, plan, lv, x, z, px, pz, s);
			return;
		} else if (id.contains("trapdoor")) {
			c = new Color(0x8A6A40);
		} else {
			c = new Color(0x2E2724);
		}
		fill(g, px, pz, s, c);
	}

	/** The fittings that fill a block: shelves, cases, crates. */
	private static boolean fitting(String id) {
		return id.contains("bookcase") || id.contains("shelves") || id.contains("pigeonholes") || id.contains("wine_rack")
				|| id.contains("bar_cabinet") || id.equals("rauk:crate");
	}

	/** Furniture on the plan: a rug as a faint wash, lights as a yellow dot, the rest as a brown block. */
	private static void furniture(Graphics2D g, String id, int px, int pz, int s) {
		if (!HotelFurniture.is(id) && !fitting(id)) {
			return;
		}
		if (id.endsWith("_rug")) {
			g.setColor(new Color(150, 40, 40, 70));
			g.fillRect(px, pz, s, s);
			return;
		}
		if (fitting(id)) {
			g.setColor(new Color(0x6A4630));
			g.fillRect(px + 1, pz + 1, s - 2, s - 2);
			return;
		}
		HotelFurniture.Piece piece = HotelFurniture.get(id.substring(5));
		if (piece.light != HotelFurniture.Light.NONE) {
			g.setColor(piece.light == HotelFurniture.Light.FLAME ? new Color(0xFF9A30) : new Color(0xFFE070));
			g.fillOval(px + s / 4, pz + s / 4, s / 2, s / 2);
			return;
		}
		if (!piece.solid) {
			g.setColor(new Color(90, 70, 60, 110));
			g.fillRect(px + s / 3, pz + s / 3, s / 3, s / 3);
			return;
		}
		g.setColor(new Color(0x8A5A3A));
		g.fillRect(px + 2, pz + 2, s - 4, s - 4);
	}

	/** The stair block at this column within the level, if any. */
	private static String stairsUnder(HotelPlan plan, Level lv, int x, int z) {
		for (int y = lv.base - 1; y <= lv.base + 5; y++) {
			if (plan.get(x, y, z).contains("_stairs")) {
				return plan.get(x, y, z);
			}
		}
		return null;
	}

	private static void stairs(Graphics2D g, HotelPlan plan, Level lv, int x, int z, int px, int pz, int s) {
		String st = stairsUnder(plan, lv, x, z);
		int step = 0;
		for (int y = lv.base - 1; y <= lv.base + 5; y++) {
			if (plan.get(x, y, z).contains("_stairs")) {
				step = y - lv.base;
			}
		}
		int shade = 200 - Math.max(0, step) * 14;
		fill(g, px, pz, s, new Color(shade, shade - 10, shade - 25));
		g.setColor(new Color(0x333333));
		g.setStroke(new BasicStroke(1.4f));
		int cx = px + s / 2, cz = pz + s / 2, r = s / 2 - 1;
		int dx = 0, dz = 0;
		if (st != null) {
			if (st.contains("facing=north")) {
				dz = -1;
			} else if (st.contains("facing=south")) {
				dz = 1;
			} else if (st.contains("facing=east")) {
				dx = 1;
			} else {
				dx = -1;
			}
		}
		g.drawLine(cx - dx * r, cz - dz * r, cx + dx * r, cz + dz * r);
		g.drawLine(cx + dx * r, cz + dz * r, cx + dx * r / 2 - dz * r / 2, cz + dz * r / 2 - dx * r / 2);
		g.drawLine(cx + dx * r, cz + dz * r, cx + dx * r / 2 + dz * r / 2, cz + dz * r / 2 + dx * r / 2);
		g.setStroke(new BasicStroke(1f));
	}

	private static void fill(Graphics2D g, int px, int pz, int s, Color c) {
		g.setColor(c);
		g.fillRect(px, pz, s, s);
	}

	/** Each space's name, in its middle, as large as fits. */
	static void labels(Graphics2D g, HotelPlan plan, Level lv, int y, int s, int top) {
		for (Area a : plan.areas) {
			if (a.kind() == Kind.CRAWL || a.kind() == Kind.TUNNEL && a.x1() - a.x0() < 3 && a.z1() - a.z0() < 3) {
				continue;
			}
			int cx = (int) Math.floor(a.centerX()), cz = (int) Math.floor(a.centerZ());
			if (y < a.y0() || y > a.y1() || plan.owner(cx, y, cz) != a && a.kind() != Kind.LIFT) {
				if (!(y >= a.y0() && y <= a.y1() && a.kind() == Kind.STAIRS)) {
					continue;
				}
			}
			int w = (a.x1() - a.x0() + 1) * s, h = (a.z1() - a.z0() + 1) * s;
			String name = a.name();
			int size = 14;
			Font f;
			FontMetrics fm;
			do {
				f = new Font(Font.SANS_SERIF, Font.BOLD, size);
				fm = g.getFontMetrics(f);
				size--;
			} while (size > 7 && fm.stringWidth(name) > w - 4 && !(h > w * 2));
			boolean vertical = fm.stringWidth(name) > w - 4 && h > w;
			int px = MARGIN + (int) ((a.centerX() - X_MIN) * s), pz = top + (int) ((a.centerZ() - Z_MIN) * s);
			Graphics2D t = (Graphics2D) g.create();
			t.translate(px, pz);
			if (vertical) {
				t.rotate(-Math.PI / 2);
				size = 14;
				do {
					f = new Font(Font.SANS_SERIF, Font.BOLD, size);
					fm = t.getFontMetrics(f);
					size--;
				} while (size > 7 && fm.stringWidth(name) > h - 4);
			}
			t.setFont(f);
			int tw = fm.stringWidth(name);
			t.setColor(new Color(255, 255, 255, 170));
			t.drawString(name, -tw / 2 + 1, fm.getAscent() / 2 - 1);
			t.setColor(a.style() == Style.RUINED || a.style() == Style.HIDDEN || a.kind() == Kind.LIFT ? Color.WHITE : new Color(0x1E1A18));
			t.drawString(name, -tw / 2, fm.getAscent() / 2 - 2);
			t.dispose();
		}
	}

	private static void legend(Graphics2D g, int x0, int y0, int w, int h) {
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
		g.setColor(new Color(0x222222));
		g.drawString("The Raukvik Grand: the shell (step 2)", x0 + 20, y0 + 40);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
		int y = y0 + 80;
		for (Style st : Style.values()) {
			g.setColor(new Color(st.color));
			g.fillRect(x0 + 24, y - 13, 26, 16);
			g.setColor(new Color(0x222222));
			g.drawRect(x0 + 24, y - 13, 26, 16);
			g.drawString(st.name().charAt(0) + st.name().substring(1).toLowerCase(), x0 + 60, y);
			y += 26;
		}
		y = y0 + 80;
		int x = x0 + 260;
		String[][] keys = {{"2E2724", "Wall"}, {"A0642C", "Door"}, {"4C6A80", "Shutter (window, front doors)"}, {"4A7FD0", "Water"},
				{"A8D8E8", "Glass"}, {"4A4A4A", "Lift shaft"}, {"C8BEAA", "Stairs (arrow points up)"}, {"555555", "Railing, cage (bars)"},
				{"3A4250", "Open below (atrium, drops)"}};
		for (String[] k : keys) {
			g.setColor(new Color(Integer.parseInt(k[0], 16)));
			g.fillRect(x, y - 13, 26, 16);
			g.setColor(new Color(0x222222));
			g.drawRect(x, y - 13, 26, 16);
			g.drawString(k[1], x + 36, y);
			y += 26;
		}
	}
}
