package rauk.com.entity.combat;

/**
 * Where on Rauk's body a blow lands. Nothing hurts it, but where it is struck matters: a blow to
 * the face, or to the hand that grips, can make it let go of what it holds.
 */
public enum Region {
	HEAD, TORSO, ARM_L, ARM_R, LEG_L, LEG_R, TAIL;

	public boolean leg() {
		return this == LEG_L || this == LEG_R;
	}

	public boolean arm() {
		return this == ARM_L || this == ARM_R;
	}

	/** The region a rig part belongs to, by its name. */
	public static Region ofPart(String name) {
		if (name.startsWith("head") || name.startsWith("jaw") || name.startsWith("skull_") || name.startsWith("drool_")
				|| name.startsWith("eye_")) {
			return HEAD;
		}
		if (name.startsWith("arm_l") || name.startsWith("hand_l") || name.startsWith("finger_l") || name.startsWith("thumb_l")
				|| name.startsWith("scapula_l")) {
			return ARM_L;
		}
		if (name.startsWith("arm_r") || name.startsWith("hand_r") || name.startsWith("finger_r") || name.startsWith("thumb_r")
				|| name.startsWith("scapula_r")) {
			return ARM_R;
		}
		if (name.startsWith("leg_l") || name.startsWith("toe_l") || name.startsWith("dewclaw_l")) {
			return LEG_L;
		}
		if (name.startsWith("leg_r") || name.startsWith("toe_r") || name.startsWith("dewclaw_r")) {
			return LEG_R;
		}
		if (name.startsWith("tail_")) {
			return TAIL;
		}
		return TORSO;
	}

	public static Region byId(int id) {
		Region[] all = values();
		return all[Math.clamp(id, 0, all.length - 1)];
	}
}
