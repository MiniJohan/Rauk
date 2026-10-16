package rauk.com.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rauk.com.network.RaukDebugPayload;

/**
 * Draws Rauk's mind in the world ({@code /rauk debug}): where its eyes are, what it believes about
 * each player (yellow, the box as big as its uncertainty; green while it can see them), what it
 * has heard (cyan), the scent trails around it (green, darker with age; red where it is smelling),
 * what it has noticed changed (magenta), the path it is on (white) and what it is doing.
 */
public final class RaukDebugRenderer {
	private static RaukDebugPayload latest;
	private static long received;

	private RaukDebugRenderer() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(RaukDebugPayload.TYPE, (payload, context) -> {
			latest = payload;
			received = System.currentTimeMillis();
		});
		WorldRenderEvents.AFTER_TRANSLUCENT.register(RaukDebugRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		RaukDebugPayload d = latest;
		if (d == null || System.currentTimeMillis() - received > 1500) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		Vec3 cam = ctx.camera().getPosition();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());

		box(pose, lines, d.eye(), 0.15, 1, 1, 1);
		for (RaukDebugPayload.Guess g : d.beliefs()) {
			float r = g.seen() ? 0.2f : 1f, gr = 1f, b = g.seen() ? 0.2f : 0.1f;
			double size = Math.min(g.uncertainty(), 4);
			box(pose, lines, g.pos().add(0, 0.9, 0), size, r, gr, b);
			line(pose, lines, d.eye(), g.pos().add(0, 0.9, 0), r, gr, b, 0.6f);
		}
		for (RaukDebugPayload.Noise n : d.noises()) {
			float fade = Math.max(0.2f, 1 - n.age() / 20f);
			box(pose, lines, n.pos(), 0.2 + 0.4 * n.loudness(), 0.2f * fade, fade, fade);
		}
		Vec3 last = null;
		for (RaukDebugPayload.Scent s : d.scent()) {
			if (s.pos().y < -1000) {
				last = null;
				continue;
			}
			float fresh = Math.max(0.15f, 1 - s.ageMinutes() / 40f);
			if (s.smelled()) {
				box(pose, lines, s.pos(), 0.35, 1, 0.1f, 0.1f);
			} else {
				box(pose, lines, s.pos(), 0.08, 0.1f, fresh, 0.1f);
			}
			if (last != null && last.distanceTo(s.pos()) < 2.5) {
				line(pose, lines, last, s.pos(), 0.1f, fresh, 0.1f, 0.7f);
			}
			last = s.pos();
		}
		for (RaukDebugPayload.Mark m : d.anomalies()) {
			LevelRenderer.renderLineBox(pose, lines, new AABB(m.pos()).inflate(0.05), 1, 0.2f, 1, 1);
		}
		for (int i = 0; i + 1 < d.path().size(); i++) {
			line(pose, lines, d.path().get(i).add(0, 0.1, 0), d.path().get(i + 1).add(0, 0.1, 0), 1, 1, 1, 0.9f);
		}
		buffers.endBatch(RenderType.lines());
		pose.popPose();

		text(pose, buffers, d.status(), d.eye().add(0, 1.2, 0), 0xFFFFFF);
		for (RaukDebugPayload.Guess g : d.beliefs()) {
			text(pose, buffers, String.format("%s %d%% %s %.1fs", g.name(), Math.round(g.confidence() * 100), g.seen() ? "SEEN" : g.sense(),
					g.age()), g.pos().add(0, 2.2, 0), g.seen() ? 0x66FF66 : 0xFFFF33);
		}
		for (RaukDebugPayload.Noise n : d.noises()) {
			if (n.age() < 8) {
				text(pose, buffers, n.label(), n.pos().add(0, 0.6, 0), 0x66FFFF);
			}
		}
		for (RaukDebugPayload.Mark m : d.anomalies()) {
			text(pose, buffers, m.label(), Vec3.atCenterOf(m.pos()).add(0, 0.9, 0), 0xFF66FF);
		}
		buffers.endBatch();
	}

	private static void box(PoseStack pose, VertexConsumer lines, Vec3 c, double size, float r, float g, float b) {
		double h = size / 2;
		LevelRenderer.renderLineBox(pose, lines, c.x - h, c.y - h, c.z - h, c.x + h, c.y + h, c.z + h, r, g, b, 1);
	}

	private static void line(PoseStack pose, VertexConsumer lines, Vec3 a, Vec3 b, float r, float g, float bl, float alpha) {
		Vec3 n = b.subtract(a).normalize();
		PoseStack.Pose last = pose.last();
		lines.addVertex(last, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha)
				.setNormal(last, (float) n.x, (float) n.y, (float) n.z);
		lines.addVertex(last, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha)
				.setNormal(last, (float) n.x, (float) n.y, (float) n.z);
	}

	private static void text(PoseStack pose, MultiBufferSource buffers, String text, Vec3 at, int color) {
		DebugRenderer.renderFloatingText(pose, buffers, text, at.x, at.y, at.z, color, 0.025f, true, 0, true);
	}
}
