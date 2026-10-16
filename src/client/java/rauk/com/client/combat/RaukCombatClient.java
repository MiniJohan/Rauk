package rauk.com.client.combat;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.model.RaukRig.V;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.combat.RaukCombat;
import rauk.com.network.RaukHitPayload;

/**
 * The player's side of a fight with Rauk: swings aimed at its actual body, and being held at its
 * face with the camera pulled toward it (design doc 7.2).
 */
public final class RaukCombatClient {
	private static long lastFrame;

	private RaukCombatClient() {
	}

	public static void init() {
		ClientPreAttackCallback.EVENT.register((client, player, clicks) -> clicks > 0 && attack(client, player));
		ClientTickEvents.END_CLIENT_TICK.register(RaukCombatClient::hold);
		WorldRenderEvents.START.register(context -> pullCamera(context.tickCounter().getGameTimeDeltaPartialTick(true)));
	}

	/** The player's swing, tested against Rauk's posed body. Returns true when it hit Rauk. */
	public static boolean attack(Minecraft client, LocalPlayer player) {
		if (player.isSpectator() || client.level == null) {
			return false;
		}
		Vec3 eye = player.getEyePosition(1f);
		Vec3 look = player.getViewVector(1f);
		RaukHitTest.Hit best = null;
		for (Entity e : client.level.entitiesForRendering()) {
			if (e instanceof RaukEntity rauk && rauk.isAlive() && rauk.distanceTo(player) < 16) {
				RaukHitTest.Hit hit = RaukHitTest.ray(rauk, eye, look, player.entityInteractionRange());
				if (hit != null && (best == null || hit.distance() < best.distance())) {
					best = hit;
				}
			}
		}
		if (best == null) {
			return false;
		}
		// A block or another creature in the way, closer: that's what the swing hits.
		HitResult vanilla = client.hitResult;
		if (vanilla != null && vanilla.getType() != HitResult.Type.MISS) {
			boolean isRauk = vanilla instanceof EntityHitResult eh && eh.getEntity() instanceof RaukEntity;
			if (!isRauk && vanilla.getLocation().distanceTo(eye) < best.distance()) {
				return false;
			}
		}
		ClientPlayNetworking.send(new RaukHitPayload(best.rauk().getId(), best.region().ordinal(), best.at()));
		player.swing(InteractionHand.MAIN_HAND);
		player.resetAttackStrengthTicker();
		return true;
	}

	/** The Rauk holding this player, if one is. */
	@Nullable
	public static RaukEntity holder(Minecraft client, Entity player) {
		if (client.level == null) {
			return null;
		}
		for (Entity e : client.level.entitiesForRendering()) {
			if (e instanceof RaukEntity rauk && rauk.grabbed() == player && rauk.grabPhase() != RaukCombat.PHASE_NONE
					&& rauk.grabPhase() != RaukCombat.PHASE_RECOIL) {
				return rauk;
			}
		}
		return null;
	}

	/** Held: the player is kept up at its face, whatever they press. */
	private static void hold(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		RaukEntity rauk = holder(client, player);
		if (rauk == null) {
			return;
		}
		Vec3 feet = rauk.holdPoint().subtract(0, player.getEyeHeight(), 0);
		double ease = rauk.grabPhase() == RaukCombat.PHASE_LIFT ? 0.3 : 0.6;
		Vec3 at = player.position().lerp(feet, ease);
		player.setPos(at.x, at.y, at.z);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}

	/** Every frame while held, the camera is pulled toward its face. You can fight it, a little. */
	private static void pullCamera(float partialTick) {
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1e9, 0.1);
		lastFrame = now;
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || dt <= 0) {
			return;
		}
		RaukEntity rauk = holder(client, player);
		if (rauk == null) {
			return;
		}
		V face = RaukAnimator.of(rauk).faceWorld;
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 to = new Vec3(face.x(), face.y(), face.z()).subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
		float k = (float) (1 - Math.exp(-dt * 7));
		float newYaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * k;
		float newPitch = player.getXRot() + (pitch - player.getXRot()) * k;
		player.setYRot(newYaw);
		player.setXRot(newPitch);
		player.yRotO = newYaw;
		player.xRotO = newPitch;
		player.setYHeadRot(newYaw);
	}
}
