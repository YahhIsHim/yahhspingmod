package com.yahhsping;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.phys.Vec3;

/**
 * Draws ping markers on the HUD: the pinger's head, "<Name>'s Marker", and a diamond on the pinged spot.
 * The position is projected onto the screen using your view direction and FOV. If it's off-screen (or behind you)
 * the marker sticks to the edge of the screen in the right direction.
 */
final class PingHud {
	// Keep room above the marker for the label and head, and below for the diamond.
	private static final int MARGIN_X = 40;
	private static final int MARGIN_TOP = 44;
	private static final int MARGIN_BOTTOM = 16;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int DIAMOND = 0xFFE8F4E8;
	private static final int OUTLINE = 0xFF1A1A1A;

	private PingHud() {
	}

	static void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.level == null) return;

		// Pings from another dimension are never shown here (they only appear in chat).
		List<PingMarkers.Marker> markers = PingMarkers.active(PingMarkers.dimensionName(mc.level));
		if (markers.isEmpty()) return;

		Vec3 eye = mc.player.getEyePosition();
		Vec3 forward = mc.player.getLookAngle();
		Vec3 right = forward.cross(new Vec3(0, 1, 0));

		if (right.lengthSqr() < 1.0E-6) return; // looking straight up or down, skip this frame

		right = right.normalize();
		Vec3 up = right.cross(forward);

		double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		int w = g.width();
		int h = g.height();
		double aspect = (double) w / h;
		Font font = mc.font;

		for (PingMarkers.Marker m : markers) {
			Vec3 d = new Vec3(m.x(), m.y(), m.z()).subtract(eye);
			double depth = d.dot(forward);
			double dx = d.dot(right);
			double dy = d.dot(up);

			double sx = 0;
			double sy = 0;
			boolean onScreen = false;

			if (depth > 0.05) {
				sx = w / 2.0 * (1 + dx / depth / (tanHalfFov * aspect));
				sy = h / 2.0 * (1 - dy / depth / tanHalfFov);
				onScreen = sx >= MARGIN_X && sx <= w - MARGIN_X && sy >= MARGIN_TOP && sy <= h - MARGIN_BOTTOM;
			}

			if (!onScreen) {
				// Push the marker out to the screen edge in the direction of the target.
				double vx = dx;
				double vy = -dy;
				double len = Math.hypot(vx, vy);

				if (len < 1.0E-6) {
					vx = 0;
					vy = 1;
					len = 1;
				}

				vx /= len;
				vy /= len;

				double maxX = w / 2.0 - MARGIN_X;
				double maxY = vy > 0 ? h / 2.0 - MARGIN_BOTTOM : h / 2.0 - MARGIN_TOP;
				double scale = Math.min(maxX / Math.max(Math.abs(vx), 1.0E-6), maxY / Math.max(Math.abs(vy), 1.0E-6));

				sx = w / 2.0 + vx * scale;
				sy = h / 2.0 + vy * scale;
			}

			drawMarker(g, font, m.user(), (int) sx, (int) sy, w);
		}
	}

	private static void drawMarker(Gfx g, Font font, String user, int cx, int cy, int screenWidth) {
		// Diamond with a dark outline, centred on the target.
		diamond(g, cx, cy, 5, OUTLINE);
		diamond(g, cx, cy, 4, DIAMOND);

		// "<Name>'s Marker" just above the diamond.
		String label = user + "'s Marker";
		int labelWidth = font.width(label);
		int labelX = Math.max(2, Math.min(cx - labelWidth / 2, screenWidth - labelWidth - 2));
		int labelY = cy - 18;
		g.text(font, label, labelX, labelY, WHITE);

		// The pinger's head above the label.
		int size = 12;
		int headX = Math.max(2, Math.min(cx - size / 2, screenWidth - size - 2));
		g.head(user, headX, labelY - size - 3, size);
	}

	private static void diamond(Gfx g, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int halfWidth = radius - Math.abs(dy);
			g.fill(cx - halfWidth, cy + dy, cx + halfWidth + 1, cy + dy + 1, color);
		}
	}
}
