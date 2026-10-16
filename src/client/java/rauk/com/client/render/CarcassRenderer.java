package rauk.com.client.render;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.client.anim.Quat;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.model.RaukRig.V;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.mind.RaukMind;

/**
 * Draws what Rauk has killed (design doc 6.7, 9): hanging limp from its jaws by the shoulders,
 * the rest trailing down and dragging along the floor, or, while it feeds, lying on its back on
 * the floor in front of it. The body is the creature's own model (a player keeps their skin).
 */
final class CarcassRenderer {
	private static final Map<RaukEntity, LivingEntity> bodies = new WeakHashMap<>();
	private static final Map<RaukEntity, CompoundTag> made = new WeakHashMap<>();

	private CarcassRenderer() {
	}

	static void render(RaukEntity rauk, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		CompoundTag tag = rauk.carcass();
		if (tag.isEmpty()) {
			bodies.remove(rauk);
			made.remove(rauk);
			return;
		}
		LivingEntity body = body(rauk, tag);
		if (body == null) {
			return;
		}
		RaukAnimator animator = RaukAnimator.of(rauk);
		V origin = new V(Mth.lerp(partialTick, rauk.xo, rauk.getX()), Mth.lerp(partialTick, rauk.yo, rauk.getY()),
				Mth.lerp(partialTick, rauk.zo, rauk.getZ()));
		double h = body.getBbHeight();
		V feet, up, face;
		if (rauk.action() == RaukMind.ACTION_FEED) {
			// On its back on the floor, head toward Rauk.
			Vec3 at = rauk.actionPoint();
			V kill = new V(at.x, at.y, at.z);
			V toward = new V(origin.x() - kill.x(), 0, origin.z() - kill.z());
			toward = toward.len() < 1e-3 ? V.Z : toward.norm();
			up = toward;
			face = V.Y;
			feet = kill.sub(toward.mul(h * 0.45)).add(new V(0, body.getBbWidth() * 0.45, 0));
		} else {
			// Held by the shoulders in its teeth, hanging and trailing back along the floor.
			V grip = animator.jawWorld().point(new V(0, -0.3, 0.36));
			V fwd = animator.jawWorld().z();
			fwd = new V(fwd.x(), 0, fwd.z());
			fwd = fwd.len() < 1e-3 ? V.Z : fwd.norm();
			double reach = h * 0.8;
			double floor = floorBelow(rauk, grip);
			double drop = grip.y() - floor;
			V back = fwd.mul(-1);
			if (drop < reach) {
				// It doesn't hang free: the legs drag behind on the floor.
				double along = Math.sqrt(reach * reach - drop * drop);
				feet = new V(grip.x(), floor + 0.05, grip.z()).add(back.mul(along));
			} else {
				feet = grip.add(back.mul(0.25)).sub(new V(0, reach, 0));
			}
			up = grip.sub(feet).norm();
			// Its face turned to one side, limp.
			V side = up.cross(fwd);
			face = side.len() < 1e-3 ? V.X : side.norm();
			feet = grip.sub(up.mul(reach));
		}
		V x = up.cross(face);
		if (x.len() < 1e-3) {
			return;
		}
		Quat q = Quat.fromAxes(x.norm(), up, x.norm().cross(up).norm());
		pose.pushPose();
		pose.translate(feet.x() - origin.x(), feet.y() - origin.y(), feet.z() - origin.z());
		pose.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
		Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body).render(body, 0, partialTick, pose, buffers, light);
		pose.popPose();
	}

	/** The body for this kill, made once: the same kind of creature, or the player with their skin. */
	@Nullable
	private static LivingEntity body(RaukEntity rauk, CompoundTag tag) {
		LivingEntity body = bodies.get(rauk);
		if (body != null && tag.equals(made.get(rauk))) {
			return body;
		}
		body = null;
		if (rauk.level() instanceof ClientLevel level) {
			if (tag.hasUUID("player")) {
				UUID id = tag.getUUID("player");
				var connection = Minecraft.getInstance().getConnection();
				PlayerInfo info = connection != null ? connection.getPlayerInfo(id) : null;
				body = new RemotePlayer(level, info != null ? info.getProfile() : new GameProfile(id, tag.getString("name")));
			} else if (EntityType.byString(tag.getString("type")).orElse(null) instanceof EntityType<?> type
					&& type.create(level) instanceof LivingEntity living) {
				body = living;
			}
		}
		if (body != null) {
			// Far below the world, so nothing draws a name or a shadow for it.
			body.setPos(0, -10000, 0);
			body.setYRot(0);
			body.yRotO = 0;
			body.yBodyRot = body.yBodyRotO = 0;
			body.yHeadRot = body.yHeadRotO = 0;
			body.setXRot(0);
			body.xRotO = 0;
		}
		bodies.put(rauk, body);
		made.put(rauk, tag.copy());
		return body;
	}

	private static double floorBelow(RaukEntity rauk, V p) {
		Vec3 from = new Vec3(p.x(), p.y(), p.z());
		var hit = rauk.level().clip(new ClipContext(from, from.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
		return hit.getType() == HitResult.Type.MISS ? p.y() - 6 : hit.getLocation().y;
	}
}
