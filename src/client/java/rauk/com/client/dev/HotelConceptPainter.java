package rauk.com.client.dev;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import rauk.com.hotel.HotelBuilder;
import rauk.com.hotel.HotelPlan;
import rauk.com.hotel.HotelPlan.Level;

/**
 * The reshaped hotel as a concept for review (2026-10-12, {@code ./gradlew drawHotelConcept}, into
 * docs/hotel): the building as it is, drawn from the plan, and the new wings round it as zones. The
 * U opens north to the sea; its two arms run south and turn in at 45 degrees to meet at a rounded
 * corner, which is the new front entrance. Plus a sketch of that corner from the street.
 */
public final class HotelConceptPainter {
	private static final int MARGIN = 2, TOP = 52, S = 7;
	/** The same origin as {@link HotelPlanPainter}, so its labels land in the right place. */
	private static final int X_MIN = -6, X_MAX = 85, Z_MIN = -4, Z_MAX = 128;
	/** Where the old building ends and the new one starts drawing. */
	private static final int OLD_Z_MAX = 50;

	private static final Color BG = new Color(0xF4F1EA), WALL = new Color(0x2B2420), INK = new Color(0x1E1A18);
	private static final Color LOBBY = new Color(0xE8E2D4), GRAND = new Color(0xD8C8A8), COZY = new Color(0x7A9A6E),
			COZY_RED = new Color(0xB05050), WORN = new Color(0x8C9A84), SERVICE = new Color(0x9C8F8A), RUNDOWN = new Color(0xA79A86),
			RUINED = new Color(0x5E5652), KITCHEN = new Color(0xB8B4A0), GLASS = new Color(0xB8DCEB), GARDEN = new Color(0x8FA06A),
			CINEMA = new Color(0x6A3F52), CORRIDOR = new Color(0xC9B99A), HIDDEN = new Color(0x5A4E4A), MACHINE = new Color(0x7A7872),
			WET = new Color(0x7FB3A8), NEW_EDGE = new Color(0xC0392B);

	private HotelConceptPainter() {
	}

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "docs/hotel");
		Files.createDirectories(out);
		HotelPlan plan = HotelBuilder.plan();
		BufferedImage l1 = level(plan, Level.L1, "Level 1  Lobby floor (concept)");
		BufferedImage l2 = level(plan, Level.L2, "Levels 2 and 3  Guest floors (concept)");
		BufferedImage b1 = level(plan, Level.B1, "Level B1  Service level, B2 below (concept)");
		ImageIO.write(l1, "png", out.resolve("concept_1.png").toFile());
		ImageIO.write(l2, "png", out.resolve("concept_2.png").toFile());
		ImageIO.write(b1, "png", out.resolve("concept_B1.png").toFile());
		BufferedImage corner = corner();
		ImageIO.write(corner, "png", out.resolve("concept_corner.png").toFile());
		int w = l1.getWidth(), h = l1.getHeight();
		BufferedImage all = new BufferedImage(w * 3, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = all.createGraphics();
		g.drawImage(l1, 0, 0, null);
		g.drawImage(l2, w, 0, null);
		g.drawImage(b1, w * 2, 0, null);
		g.dispose();
		ImageIO.write(all, "png", out.resolve("concept.png").toFile());
		System.out.println("Hotel concept drawn into " + out.toAbsolutePath());
	}

	// --- plans ------------------------------------------------------------------------------------

	private static int px(double x) {
		return MARGIN + (int) Math.round((x - X_MIN) * S);
	}

	private static int pz(double z) {
		return TOP + (int) Math.round((z - Z_MIN) * S);
	}

	private static BufferedImage level(HotelPlan plan, Level lv, String title) {
		int cols = X_MAX - X_MIN + 1, rows = Z_MAX - Z_MIN + 1;
		BufferedImage img = new BufferedImage(cols * S + MARGIN * 2, rows * S + TOP + MARGIN, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(BG);
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		// The building as it is now, from the plan.
		int y = lv.base + 1;
		for (int z = Z_MIN; z <= OLD_Z_MAX; z++) {
			for (int x = X_MIN; x <= X_MAX; x++) {
				HotelPlanPainter.cell(g, plan, lv, x, y, z, px(x), pz(z), S);
			}
		}
		HotelPlanPainter.labels(g, plan, lv, y, S, TOP);
		switch (lv) {
			case L1 -> lobbyFloor(g);
			case L2 -> guestFloor(g);
			default -> serviceLevel(g);
		}
		g.setColor(new Color(0, 0, 0, 22));
		for (int x = 0; x <= 80; x += 8) {
			g.drawLine(px(x), TOP, px(x), img.getHeight());
		}
		for (int z = 0; z <= 128; z += 8) {
			g.drawLine(0, pz(z), img.getWidth(), pz(z));
		}
		g.setColor(new Color(0x222222));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
		g.drawString(title, 8, 24);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString("sea (north) at the top  |  grid 8 blocks  |  the building as it is, and the new parts outlined in red", 8, 42);
		g.dispose();
		return img;
	}

	/** The shape of the new wings, shared by every level: the arms, the junctions and the diagonal wings. */
	private static Path2D shell() {
		Path2D p = new Path2D.Double();
		// Outside edge: down the west arm, in at 45 degrees to the corner, round it, out and up the east arm.
		p.moveTo(px(-1), pz(48));
		p.lineTo(px(-1), pz(82));
		p.lineTo(px(32.5), pz(115.5));
		p.quadTo(px(39.5), pz(121.5), px(46.5), pz(115.5));
		p.lineTo(px(80), pz(82));
		p.lineTo(px(80), pz(48));
		// Inside edge, round the courtyard.
		p.lineTo(px(62), pz(48));
		p.lineTo(px(62), pz(82));
		p.lineTo(px(55), pz(82));
		p.lineTo(px(39.5), pz(97.5));
		p.lineTo(px(24), pz(82));
		p.lineTo(px(17), pz(82));
		p.lineTo(px(17), pz(48));
		p.closePath();
		return p;
	}

	private static void base(Graphics2D g, Color fill) {
		Path2D shell = shell();
		g.setColor(fill);
		g.fill(shell);
		g.setColor(WALL);
		g.setStroke(new BasicStroke(3));
		g.draw(shell);
	}

	private static void outline(Graphics2D g) {
		g.setColor(NEW_EDGE);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] {6, 4}, 0));
		g.draw(shell());
		g.setStroke(new BasicStroke(1));
	}

	/** A strip of the diagonal wing, as a band between two lines parallel to its walls, from a to b along it. */
	private static Polygon diagonal(boolean west, double from, double to, double near, double far) {
		// Along the west wing: from the junction (0) to the corner (1); across it: 0 on the courtyard
		// side, 1 on the street side.
		double[][] in = west ? new double[][] {{24, 82}, {39.5, 97.5}} : new double[][] {{55, 82}, {39.5, 97.5}};
		double[][] outer = west ? new double[][] {{-1, 82}, {32.5, 115.5}} : new double[][] {{80, 82}, {46.5, 115.5}};
		Polygon poly = new Polygon();
		for (double[] st : new double[][] {{from, near}, {to, near}, {to, far}, {from, far}}) {
			double ix = in[0][0] + (in[1][0] - in[0][0]) * st[0], iz = in[0][1] + (in[1][1] - in[0][1]) * st[0];
			double ox = outer[0][0] + (outer[1][0] - outer[0][0]) * st[0], oz = outer[0][1] + (outer[1][1] - outer[0][1]) * st[0];
			poly.addPoint(px(ix + (ox - ix) * st[1]), pz(iz + (oz - iz) * st[1]));
		}
		return poly;
	}

	private static void zone(Graphics2D g, java.awt.Shape shape, Color fill) {
		g.setColor(fill);
		g.fill(shape);
		g.setColor(WALL);
		g.setStroke(new BasicStroke(1.6f));
		g.draw(shape);
		g.setStroke(new BasicStroke(1));
	}

	private static java.awt.Rectangle rect(double x0, double z0, double x1, double z1) {
		return new java.awt.Rectangle(px(x0), pz(z0), px(x1 + 1) - px(x0), pz(z1 + 1) - pz(z0));
	}

	/** The corridor down the middle of a diagonal wing, as a thick line. */
	private static void diagonalCorridor(Graphics2D g, boolean west, Color c) {
		Polygon p = diagonal(west, 0.02, 0.92, 0.42, 0.58);
		zone(g, p, c);
	}

	private static void rotunda(Graphics2D g, Color fill, String... lines) {
		Ellipse2D c = new Ellipse2D.Double(px(30), pz(98.5), 19 * S, 19 * S);
		zone(g, c, fill);
		label(g, 39.5, 106, 11, true, INK, lines);
	}

	private static void label(Graphics2D g, double x, double z, int size, boolean bold, Color c, String... lines) {
		Font f = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
		g.setFont(f);
		FontMetrics fm = g.getFontMetrics();
		int lh = fm.getHeight() - 2;
		int y0 = pz(z) - lh * lines.length / 2 + fm.getAscent();
		for (int i = 0; i < lines.length; i++) {
			int w = fm.stringWidth(lines[i]);
			g.setColor(new Color(255, 255, 255, 160));
			g.drawString(lines[i], px(x) - w / 2 + 1, y0 + i * lh + 1);
			g.setColor(c);
			g.drawString(lines[i], px(x) - w / 2, y0 + i * lh);
		}
	}

	/** Text running up a narrow strip. */
	private static void labelUp(Graphics2D g, double x, double z, int size, Color c, String text) {
		Graphics2D t = (Graphics2D) g.create();
		t.translate(px(x), pz(z));
		t.rotate(-Math.PI / 2);
		t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
		FontMetrics fm = t.getFontMetrics();
		int w = fm.stringWidth(text);
		t.setColor(new Color(255, 255, 255, 150));
		t.drawString(text, -w / 2 + 1, fm.getAscent() / 2);
		t.setColor(c);
		t.drawString(text, -w / 2, fm.getAscent() / 2 - 1);
		t.dispose();
	}

	/** Text along a diagonal wing, turned to run with it. */
	private static void labelAlong(Graphics2D g, boolean west, double along, double across, int size, Color c, String text) {
		double[][] in = west ? new double[][] {{24, 82}, {39.5, 97.5}} : new double[][] {{55, 82}, {39.5, 97.5}};
		double[][] outer = west ? new double[][] {{-1, 82}, {32.5, 115.5}} : new double[][] {{80, 82}, {46.5, 115.5}};
		double ix = in[0][0] + (in[1][0] - in[0][0]) * along, iz = in[0][1] + (in[1][1] - in[0][1]) * along;
		double ox = outer[0][0] + (outer[1][0] - outer[0][0]) * along, oz = outer[0][1] + (outer[1][1] - outer[0][1]) * along;
		double x = ix + (ox - ix) * across, z = iz + (oz - iz) * across;
		Graphics2D t = (Graphics2D) g.create();
		t.translate(px(x), pz(z));
		t.rotate(west ? Math.PI / 4 : -Math.PI / 4);
		t.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
		FontMetrics fm = t.getFontMetrics();
		int w = fm.stringWidth(text);
		t.setColor(new Color(255, 255, 255, 150));
		t.drawString(text, -w / 2 + 1, fm.getAscent() / 2);
		t.setColor(c);
		t.drawString(text, -w / 2, fm.getAscent() / 2 - 1);
		t.dispose();
	}

	private static void note(Graphics2D g, double x, double z, String... lines) {
		label(g, x, z, 11, false, new Color(0x7A1F14), lines);
	}

	private static void courtyard(Graphics2D g) {
		zone(g, rect(18, 51, 61, 81), GARDEN);
		// The dry fountain in the middle, paths out to the wings.
		g.setColor(new Color(0xD8D0BC));
		g.fillRect(px(18), pz(65), px(62) - px(18), 2 * S);
		g.fillRect(px(39), pz(51), 2 * S, pz(82) - pz(51));
		zone(g, new Ellipse2D.Double(px(36), pz(62), 8 * S, 8 * S), new Color(0xBFB6A2));
		label(g, 39.5, 53.5, 13, true, INK, "Courtyard");
		label(g, 39.5, 76, 11, false, INK, "dead garden, gravel paths,", "a dry fountain, statues");
	}

	/** Level 1: the corner entrance, the rotunda, the palm court, the arcade and the cinema. */
	private static void lobbyFloor(Graphics2D g) {
		base(g, LOBBY);
		courtyard(g);
		// West arm: a long corridor down its middle, rooms either side.
		zone(g, rect(8, 49, 10, 81), CORRIDOR);
		labelUp(g, 9.5, 65, 8, INK, "corridor 33 long");
		zone(g, rect(0, 49, 7, 58), GRAND);
		zone(g, rect(0, 60, 7, 70), COZY);
		zone(g, rect(0, 72, 7, 81), WORN);
		zone(g, rect(11, 49, 16, 62), COZY_RED);
		zone(g, rect(11, 64, 16, 72), SERVICE);
		zone(g, rect(11, 74, 23, 85), SERVICE);
		label(g, 4, 53.5, 9, true, INK, "Luggage", "hall");
		label(g, 4, 65, 9, true, INK, "Music", "room");
		label(g, 4, 76.5, 9, true, INK, "Manager's", "office");
		label(g, 14, 55.5, 9, true, Color.WHITE, "Ladies'", "lounge");
		label(g, 14, 68, 9, true, INK, "Lavat.");
		label(g, 17.5, 79.5, 9, true, INK, "West stair", "tower");
		// East arm: the cinema, two floors high.
		zone(g, rect(63, 49, 79, 72), CINEMA);
		g.setColor(new Color(0xEEE6D0));
		g.fillRect(px(64), pz(49.4), px(79) - px(64), S);
		label(g, 71.5, 52, 9, true, INK, "screen");
		label(g, 71.5, 60, 13, true, Color.WHITE, "Cinema", "\"The Aurora\"");
		label(g, 71.5, 67, 9, false, Color.WHITE, "raked seats, organ pit,", "2 floors high");
		zone(g, rect(56, 74, 68, 85), SERVICE);
		label(g, 62, 79.5, 9, true, INK, "East stair", "tower");
		// The diagonal wings: the arcade of shops (west), the cinema foyer and tea room (east).
		diagonalCorridor(g, true, CORRIDOR);
		diagonalCorridor(g, false, CORRIDOR);
		Color[] shops = {GRAND, COZY, WORN, KITCHEN};
		String[] shopNames = {"Barber's", "Telegraph & post", "Florist", "Jeweller"};
		for (int i = 0; i < 4; i++) {
			zone(g, diagonal(true, 0.06 + i * 0.205, 0.24 + i * 0.205, 0.6, 0.98), shops[i]);
			labelAlong(g, true, 0.15 + i * 0.205, 0.79, 9, INK, shopNames[i]);
		}
		zone(g, diagonal(true, 0.1, 0.85, 0.02, 0.4), LOBBY);
		labelAlong(g, true, 0.47, 0.21, 9, INK, "the Arcade (shop windows on the court)");
		labelAlong(g, true, 0.47, 0.5, 9, INK, "long gallery, 50 blocks");
		zone(g, diagonal(false, 0.06, 0.55, 0.6, 0.98), CINEMA);
		labelAlong(g, false, 0.3, 0.79, 9, Color.WHITE, "Cinema foyer, ticket hall, bar");
		zone(g, diagonal(false, 0.6, 0.92, 0.6, 0.98), COZY_RED);
		labelAlong(g, false, 0.76, 0.79, 9, Color.WHITE, "Tea room");
		zone(g, diagonal(false, 0.1, 0.85, 0.02, 0.4), COZY);
		labelAlong(g, false, 0.47, 0.21, 9, INK, "cloakrooms, writing room");
		labelAlong(g, false, 0.47, 0.5, 9, INK, "long gallery, 50 blocks");
		// The palm court in the court's south point, glass roofed.
		Polygon palm = new Polygon(new int[] {px(26), px(53), px(39.5)}, new int[] {pz(84), pz(84), pz(97.5)}, 3);
		zone(g, palm, GLASS);
		label(g, 39.5, 88.5, 11, true, INK, "Palm court", "glass roof");
		// The corner: the rotunda, its canopy, the doors.
		rotunda(g, LOBBY, "Rotunda", "entrance hall,", "reception,", "round stair", "+ brass cage lift");
		g.setColor(new Color(0xB08D57));
		g.setStroke(new BasicStroke(5));
		g.draw(new Arc2D.Double(px(27.5), pz(96.5), 24 * S, 24 * S, 205, 130, Arc2D.OPEN));
		g.setStroke(new BasicStroke(1));
		note(g, 39.5, 124.5, "canopy over the corner doors");
		// What changes in the old part.
		note(g, 39.5, 59.5, "the old entrance hall becomes the garden hall:", "its front doors open onto the court;",
				"reception moves to the rotunda");
		outline(g);
	}

	/** Levels 2 and 3: guest rooms all round, a corridor ring round the court on every floor. */
	private static void guestFloor(Graphics2D g) {
		base(g, COZY);
		zone(g, rect(18, 51, 61, 81), new Color(0xA9B78A));
		label(g, 39.5, 66, 13, true, INK, "Courtyard (open to the sky)");
		// The corridor ring: down the arms, along the diagonals, round the rotunda.
		zone(g, rect(8, 37, 10, 81), CORRIDOR);
		zone(g, rect(69, 37, 71, 81), CORRIDOR);
		diagonalCorridor(g, true, CORRIDOR);
		diagonalCorridor(g, false, CORRIDOR);
		for (int z = 49; z < 80; z += 6) {
			zone(g, rect(0, z, 7, z + 4), COZY);
			zone(g, rect(11, z, 16, z + 4), WORN);
		}
		zone(g, rect(63, 49, 68, 72), CINEMA);
		label(g, 65.5, 63, 9, true, Color.WHITE, "Cinema", "balcony", "+ booth");
		for (int z = 49; z < 80; z += 6) {
			zone(g, rect(72, z, 79, z + 4), COZY);
		}
		zone(g, rect(11, 49, 16, 60), COZY_RED);
		label(g, 13.5, 54.5, 9, true, Color.WHITE, "Children's", "playroom");
		zone(g, rect(11, 74, 23, 85), SERVICE);
		zone(g, rect(56, 74, 68, 85), SERVICE);
		label(g, 17.5, 79.5, 9, true, INK, "stair tower", "linen, chute");
		label(g, 62, 79.5, 9, true, INK, "stair tower", "linen, chute");
		for (int i = 0; i < 5; i++) {
			zone(g, diagonal(true, 0.04 + i * 0.17, 0.19 + i * 0.17, 0.6, 0.98), i % 2 == 0 ? COZY : WORN);
			zone(g, diagonal(true, 0.04 + i * 0.17, 0.19 + i * 0.17, 0.02, 0.4), WORN);
			zone(g, diagonal(false, 0.04 + i * 0.17, 0.19 + i * 0.17, 0.6, 0.98), i % 2 == 0 ? COZY : RUINED);
			zone(g, diagonal(false, 0.04 + i * 0.17, 0.19 + i * 0.17, 0.02, 0.4), WORN);
		}
		labelAlong(g, true, 0.45, 0.79, 9, INK, "suites, street side");
		labelAlong(g, true, 0.45, 0.5, 9, INK, "corridor 50 blocks, maids' closets");
		labelAlong(g, false, 0.45, 0.79, 9, Color.WHITE, "suites (burned on 3)");
		labelAlong(g, false, 0.45, 0.5, 9, INK, "corridor 50 blocks, dumbwaiters");
		Polygon palm = new Polygon(new int[] {px(26), px(53), px(39.5)}, new int[] {pz(84), pz(84), pz(97.5)}, 3);
		zone(g, palm, GLASS);
		label(g, 39.5, 88.5, 9, true, INK, "palm court below,", "glass roof");
		rotunda(g, LOBBY, "Rotunda gallery", "round the void;", "corner suite on 3");
		zone(g, new Polygon(new int[] {px(33), px(46), px(43), px(36)}, new int[] {pz(115), pz(115), pz(119.5), pz(119.5)}, 4), COZY_RED);
		note(g, 39.5, 124, "sun lounge behind the tall lattice window");
		labelUp(g, 9.5, 64, 8, INK, "45 blocks straight, from the old south corridor");
		labelUp(g, 70.5, 64, 8, INK, "45 blocks straight");
		outline(g);
	}

	/** B1: the servants' tunnel all round, the ice house, the laundry chute's foot; B2 kept for the station. */
	private static void serviceLevel(Graphics2D g) {
		base(g, new Color(0xCFC7BC));
		zone(g, rect(18, 51, 61, 81), new Color(0xBDB4A2));
		label(g, 39.5, 66, 11, false, INK, "earth under the courtyard");
		zone(g, rect(8, 46, 10, 81), SERVICE);
		zone(g, rect(69, 46, 71, 81), SERVICE);
		diagonalCorridor(g, true, SERVICE);
		diagonalCorridor(g, false, SERVICE);
		labelUp(g, 9.5, 63, 9, INK, "servants' tunnel");
		labelUp(g, 70.5, 63, 9, INK, "servants' tunnel");
		labelAlong(g, true, 0.45, 0.5, 9, INK, "servants' tunnel, ring under all the wings");
		labelAlong(g, false, 0.45, 0.5, 9, INK, "servants' tunnel");
		zone(g, rect(0, 49, 7, 64), KITCHEN);
		label(g, 4, 56.5, 9, true, INK, "Laundry", "chute's foot");
		zone(g, rect(0, 66, 7, 81), RUNDOWN);
		label(g, 4, 73.5, 9, true, INK, "Porters'", "rooms");
		zone(g, rect(72, 49, 79, 66), MACHINE);
		label(g, 75.5, 57.5, 9, true, Color.WHITE, "Organ blower,", "cinema boiler");
		zone(g, rect(72, 68, 79, 81), RUNDOWN);
		label(g, 75.5, 74.5, 9, true, INK, "Staff", "lockers");
		zone(g, diagonal(true, 0.1, 0.85, 0.6, 0.98), RUNDOWN);
		labelAlong(g, true, 0.47, 0.79, 9, INK, "stores, coal, the boot room");
		zone(g, diagonal(false, 0.1, 0.85, 0.6, 0.98), KITCHEN);
		labelAlong(g, false, 0.47, 0.79, 9, INK, "wine store, cold rooms");
		Polygon ice = new Polygon(new int[] {px(26), px(53), px(39.5)}, new int[] {pz(84), pz(84), pz(97.5)}, 3);
		zone(g, ice, new Color(0xCFE3EA));
		label(g, 39.5, 88.5, 10, true, INK, "Ice house");
		rotunda(g, HIDDEN);
		label(g, 39.5, 104, 10, true, Color.WHITE, "stair down", "to B2");
		// B2, later: the station under the corner.
		g.setColor(new Color(0x4A6FA5));
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {8, 5}, 0));
		g.drawRect(px(14), pz(108), px(66) - px(14), pz(120) - pz(108));
		g.setStroke(new BasicStroke(1));
		label(g, 39.5, 122.5, 10, true, new Color(0x2F4F7F), "B2, later: Raukvik Station, a private platform and tunnels");
		note(g, 39.5, 44.5, "the tunnel joins the old service corridor");
		outline(g);
	}

	// --- the corner, from the street --------------------------------------------------------------

	/** The rounded corner as you walk up to it, after the reference photo. */
	private static BufferedImage corner() {
		int w = 900, h = 760;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x2A3140));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(0x3A3A3A));
		g.fillRect(0, 660, w, 100);
		int cx = w / 2, ground = 660, floorH = 70;
		Color stone = new Color(0xD9CCB0), stoneDark = new Color(0xBFAE8C), rust = new Color(0xC7B691);
		// The two street fronts going away either side, foreshortened, and the round corner between.
		for (int side : new int[] {-1, 1}) {
			Polygon front = new Polygon();
			front.addPoint(cx + side * 150, ground);
			front.addPoint(cx + side * 450, ground - 40);
			front.addPoint(cx + side * 450, ground - 40 - floorH * 4 - 20);
			front.addPoint(cx + side * 150, ground - floorH * 4 - 50);
			g.setColor(side < 0 ? stoneDark : stone);
			g.fill(front);
			for (int f = 1; f <= 3; f++) {
				for (int i = 0; i < 4; i++) {
					double t = (i + 0.5) / 4;
					int x = (int) (cx + side * (170 + 260 * t)), y = (int) (ground - f * floorH - 40 * t - 10);
					g.setColor(new Color(0x32302C));
					g.fillRoundRect(x - 13, y - 42, 26, 46, f == 3 ? 26 : 4, f == 3 ? 26 : 4);
					if (f < 3 && i % 2 == 1) {
						g.setColor(new Color(0x1E1E1E));
						g.drawRect(x - 18, y - 8, 36, 10);
						g.setColor(new Color(0x5E6B3C));
						g.fillOval(x - 12, y - 18, 10, 12);
					}
				}
			}
			// The rusticated ground floor and its tall windows.
			for (int i = 0; i < 4; i++) {
				double t = (i + 0.5) / 4;
				int x = (int) (cx + side * (170 + 260 * t)), y = (int) (ground - 40 * t);
				g.setColor(rust);
				g.fillRect(x - 30, y - floorH + 4, 60, floorH - 6);
				g.setColor(new Color(0x2C2A26));
				g.fillRect(x - 16, y - floorH + 12, 32, floorH - 16);
			}
		}
		// The rounded corner: a tower of stone with the tall lattice window up its middle.
		g.setColor(stone);
		g.fillRect(cx - 150, ground - floorH * 4 - 50, 300, floorH * 4 + 50);
		g.setColor(new Color(0x3B342C));
		g.fillRoundRect(cx - 40, ground - floorH * 4 - 30, 80, floorH * 3 + 20, 80, 80);
		g.setColor(new Color(0xB08D57));
		g.setStroke(new BasicStroke(1.4f));
		for (int y = ground - floorH * 4 - 10; y < ground - floorH - 20; y += 14) {
			for (int x = cx - 34; x < cx + 34; x += 14) {
				g.drawOval(x, y, 14, 14);
			}
		}
		for (int side : new int[] {-1, 1}) {
			for (int f = 1; f <= 3; f++) {
				int x = cx + side * 95, y = ground - f * floorH - 10;
				g.setColor(new Color(0x32302C));
				g.fillRoundRect(x - 14, y - 44, 28, 48, f == 3 ? 28 : 4, f == 3 ? 28 : 4);
			}
		}
		// The cornice, the mansard with its dormers, the crown with the name.
		g.setColor(new Color(0xE6DCC4));
		g.fillRect(cx - 460, ground - floorH * 4 - 62, 920, 14);
		g.setColor(new Color(0x3C4048));
		Polygon mansard = new Polygon(new int[] {cx - 450, cx + 450, cx + 420, cx - 420}, new int[] {ground - floorH * 4 - 62,
				ground - floorH * 4 - 62, ground - floorH * 5 - 70, ground - floorH * 5 - 70}, 4);
		g.fill(mansard);
		for (int i = -3; i <= 3; i++) {
			g.setColor(new Color(0x5A5E66));
			g.fillRect(cx + i * 120 - 16, ground - floorH * 5 - 50, 32, 46);
			g.setColor(new Color(0x26282C));
			g.fillRect(cx + i * 120 - 9, ground - floorH * 5 - 42, 18, 34);
		}
		g.setColor(new Color(0x56606C));
		g.fillArc(cx - 110, ground - floorH * 6 - 70, 220, 140, 0, 180);
		g.setColor(new Color(0x8A94A0));
		g.drawArc(cx - 110, ground - floorH * 6 - 70, 220, 140, 0, 180);
		g.setColor(new Color(0x56606C));
		g.fillRect(cx - 12, ground - floorH * 6 - 96, 24, 30);
		g.setColor(new Color(0xB08D57));
		g.fillRect(cx - 2, ground - floorH * 6 - 116, 4, 22);
		g.setColor(new Color(0xB08D57));
		g.setFont(new Font(Font.SERIF, Font.BOLD, 30));
		String name = "RAUKVIK GRAND";
		g.drawString(name, cx - g.getFontMetrics().stringWidth(name) / 2, ground - floorH * 5 - 84);
		// Strings of bulbs from the crown, most of them dead.
		for (int side : new int[] {-1, 1}) {
			for (int i = 0; i < 14; i++) {
				double t = i / 13.0;
				int x = (int) (cx + side * (40 + 380 * t)), y = (int) (ground - floorH * 6 - 30 + 120 * t + 30 * Math.sin(Math.PI * t * 2));
				g.setColor(i % 5 == 3 ? new Color(0xFFE7A0) : new Color(0x6B6656));
				g.fillOval(x - 3, y - 3, 6, 6);
			}
		}
		// The canopy over the corner doors, on brass trees.
		g.setColor(new Color(0x2E2A24));
		g.fillRoundRect(cx - 230, ground - floorH - 6, 460, 22, 20, 20);
		g.setColor(new Color(0x6E7A4A));
		g.fillRoundRect(cx - 226, ground - floorH - 16, 452, 14, 14, 14);
		g.setColor(new Color(0xB08D57));
		g.setStroke(new BasicStroke(5));
		for (int x : new int[] {cx - 150, cx - 50, cx + 50, cx + 150}) {
			g.drawLine(x, ground, x, ground - floorH + 34);
			g.drawLine(x, ground - floorH + 34, x - 26, ground - floorH + 16);
			g.drawLine(x, ground - floorH + 34, x + 26, ground - floorH + 16);
			g.drawLine(x, ground - floorH + 34, x, ground - floorH + 16);
		}
		g.setStroke(new BasicStroke(1));
		g.setColor(new Color(0x15130F));
		g.fillRect(cx - 34, ground - 54, 68, 54);
		g.setColor(new Color(0xEDE7D8));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		g.drawString("The new front: the corner of the U", 16, 30);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		String[] notes = {"Rounded corner like the photo, in cream limestone, the ground floor rusticated",
				"Canopy round the corner on branching brass columns; the doors under it",
				"A tall arched window of iron lattice up the corner, the sun lounge behind it",
				"Arched windows on 3, iron balconies with dead plants, the slate mansard with dormers",
				"A dome on the corner with the hotel's name in brass, strings of bulbs, nearly all dead"};
		for (int i = 0; i < notes.length; i++) {
			g.drawString(notes[i], 16, 54 + i * 18);
		}
		g.dispose();
		return img;
	}
}
