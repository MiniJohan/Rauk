package rauk.com.client.anim;

import rauk.com.client.model.RaukRig.V;

/** A rotation as a unit quaternion. */
public record Quat(double w, double x, double y, double z) {
	public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

	public static Quat axisAngle(V axis, double angle) {
		V a = axis.norm();
		double s = Math.sin(angle / 2);
		return new Quat(Math.cos(angle / 2), a.x() * s, a.y() * s, a.z() * s);
	}

	/** The rotation whose matrix has these columns (an orthonormal, right-handed basis). */
	public static Quat fromAxes(V cx, V cy, V cz) {
		double m00 = cx.x(), m01 = cy.x(), m02 = cz.x();
		double m10 = cx.y(), m11 = cy.y(), m12 = cz.y();
		double m20 = cx.z(), m21 = cy.z(), m22 = cz.z();
		double tr = m00 + m11 + m22;
		Quat q;
		if (tr > 0) {
			double s = Math.sqrt(tr + 1) * 2;
			q = new Quat(0.25 * s, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s);
		} else if (m00 > m11 && m00 > m22) {
			double s = Math.sqrt(1 + m00 - m11 - m22) * 2;
			q = new Quat((m21 - m12) / s, 0.25 * s, (m01 + m10) / s, (m02 + m20) / s);
		} else if (m11 > m22) {
			double s = Math.sqrt(1 + m11 - m00 - m22) * 2;
			q = new Quat((m02 - m20) / s, (m01 + m10) / s, 0.25 * s, (m12 + m21) / s);
		} else {
			double s = Math.sqrt(1 + m22 - m00 - m11) * 2;
			q = new Quat((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, 0.25 * s);
		}
		return q.norm();
	}

	/** The shortest rotation taking direction a to direction b. */
	public static Quat fromTo(V a, V b) {
		V u = a.norm(), v = b.norm();
		double d = u.dot(v);
		if (d < -0.999999) {
			V axis = V.X.cross(u);
			if (axis.len() < 1e-6) {
				axis = V.Y.cross(u);
			}
			return axisAngle(axis, Math.PI);
		}
		V c = u.cross(v);
		return new Quat(1 + d, c.x(), c.y(), c.z()).norm();
	}

	public Quat mul(Quat o) {
		return new Quat(
				w * o.w - x * o.x - y * o.y - z * o.z,
				w * o.x + x * o.w + y * o.z - z * o.y,
				w * o.y - x * o.z + y * o.w + z * o.x,
				w * o.z + x * o.y - y * o.x + z * o.w);
	}

	public Quat conj() {
		return new Quat(w, -x, -y, -z);
	}

	public Quat norm() {
		double l = Math.sqrt(w * w + x * x + y * y + z * z);
		return l < 1e-12 ? IDENTITY : new Quat(w / l, x / l, y / l, z / l);
	}

	public V rotate(V v) {
		double tx = 2 * (y * v.z() - z * v.y()), ty = 2 * (z * v.x() - x * v.z()), tz = 2 * (x * v.y() - y * v.x());
		return new V(v.x() + w * tx + (y * tz - z * ty), v.y() + w * ty + (z * tx - x * tz), v.z() + w * tz + (x * ty - y * tx));
	}

	/** The rotation angle, 0..pi. */
	public double angle() {
		return 2 * Math.acos(Math.min(1, Math.abs(w)));
	}

	/** The same rotation scaled to a fraction of its angle (about the same axis). */
	public Quat scale(double k) {
		double a = angle();
		if (a < 1e-9) {
			return IDENTITY;
		}
		double sign = w < 0 ? -1 : 1;
		V axis = new V(x * sign, y * sign, z * sign);
		return axisAngle(axis, a * k);
	}

	/** This rotation with its angle limited to max radians. */
	public Quat clamp(double max) {
		double a = angle();
		return a <= max ? this : scale(max / a);
	}

	public static Quat slerp(Quat a, Quat b, double t) {
		return a.mul(a.conj().mul(b).scale(t));
	}
}
