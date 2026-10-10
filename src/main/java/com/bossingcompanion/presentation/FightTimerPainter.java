package com.bossingcompanion.presentation;

import com.bossingcompanion.domain.FightTimerSnapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Native compact widget. No state caption; raw geometry is shared by the actual overlay and render tests. */
public final class FightTimerPainter
{
	private FightTimerPainter() { }
	public static Dimension paint(Graphics2D target, FightTimerSnapshot snapshot, long nowNanos, BufferedImage icon)
	{
		Graphics2D graphics = (Graphics2D) target.create();
		try
		{
			Font titleFont = FontManager.getRunescapeFont();
			Font clockFont = FontManager.getRunescapeBoldFont().deriveFont(22f);
			String name = snapshot.getBoss() == null ? "" : snapshot.getBoss().getDisplayName();
			String clock = (snapshot.isApproximate() ? "~" : "") + FightTimerSnapshot.format(snapshot.elapsedAt(nowNanos));
			FontMetrics titleMetrics = graphics.getFontMetrics(titleFont), clockMetrics = graphics.getFontMetrics(clockFont);
			int iconHeight = name.isEmpty() || icon == null ? 0 : Math.min(20, icon.getHeight());
			int iconWidth = iconHeight == 0 ? 0 : Math.max(1, icon.getWidth() * iconHeight / icon.getHeight());
			int titleHeight = name.isEmpty() ? 0 : Math.max(titleMetrics.getHeight(), iconHeight);
			int width = Math.max(164, Math.max(titleMetrics.stringWidth(name) + iconWidth + (iconWidth == 0 ? 0 : 4), clockMetrics.stringWidth(clock)) + 14);
			int height = 10 + titleHeight + (titleHeight == 0 ? 0 : 2) + clockMetrics.getHeight();
			graphics.setColor(ColorScheme.DARKER_GRAY_COLOR); graphics.fillRect(0, 0, width, height);
			graphics.setColor(ColorScheme.MEDIUM_GRAY_COLOR); graphics.drawRect(0, 0, width - 1, height - 1);
			if (!name.isEmpty())
			{
				if (iconWidth > 0) { graphics.drawImage(icon, 7, 5 + (titleHeight - iconHeight) / 2, iconWidth, iconHeight, null); }
				graphics.setFont(titleFont);
				text(graphics, name, 7 + (iconWidth == 0 ? 0 : iconWidth + 4), 5 + (titleHeight - titleMetrics.getHeight()) / 2 + titleMetrics.getAscent(), ColorScheme.BRAND_ORANGE);
			}
			graphics.setFont(clockFont);
			text(graphics, clock, 7, 5 + titleHeight + (titleHeight == 0 ? 0 : 2) + clockMetrics.getAscent(), Color.YELLOW);
			return new Dimension(width, height);
		}
		finally { graphics.dispose(); }
	}
	private static void text(Graphics2D graphics, String text, int x, int y, Color color)
	{
		graphics.setColor(Color.BLACK); graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(color); graphics.drawString(text, x, y);
	}
	public static String provenance(FightTimerSnapshot snapshot)
	{
		return snapshot.isOfficial() ? "Game-reported completed fight duration."
			: "Observed elapsed time from your first qualifying boss hit. Values marked ~ may differ from official encounter time.";
	}
}
