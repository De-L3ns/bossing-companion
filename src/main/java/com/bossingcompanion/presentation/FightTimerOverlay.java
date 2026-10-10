package com.bossingcompanion.presentation;

import com.bossingcompanion.application.BossIcons;
import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.FightTimerSnapshot;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public final class FightTimerOverlay extends Overlay
{
	private final BossIcons icons;
	private final LongSupplier nanos;
	private final TooltipManager tooltips;
	private volatile FightTimerSnapshot snapshot;
	private volatile BufferedImage icon;
	private volatile boolean visible;
	private volatile boolean closed;

	public FightTimerOverlay(Plugin plugin, BossIcons icons, LongSupplier nanos, TooltipManager tooltips)
	{
		super(plugin); this.icons = icons; this.nanos = nanos; this.tooltips = tooltips;
		setPosition(OverlayPosition.TOP_LEFT); setLayer(OverlayLayer.ABOVE_WIDGETS);
		setMovable(true); setSnappable(true); setResizable(false);
	}
	public void showSnapshot(FightTimerSnapshot snapshot, boolean visible)
	{
		if (closed) { return; }
		Boss previous = this.snapshot == null ? null : this.snapshot.getBoss();
		this.snapshot = snapshot; this.visible = visible;
		if (previous != snapshot.getBoss())
		{
			icon = null; Boss boss = snapshot.getBoss();
			if (boss != null) { SwingUtilities.invokeLater(() ->
			{
				if (!closed) { icons.load(boss, image ->
				{
					if (!closed && this.snapshot.getBoss() == boss) { icon = image; }
				}); }
			}); }
		}
	}
	@Override public Dimension render(Graphics2D graphics)
	{
		FightTimerSnapshot value = snapshot;
		return closed || !visible || value == null ? null : FightTimerPainter.paint(graphics, value, nanos.getAsLong(), icon);
	}
	@Override public void onMouseOver()
	{
		FightTimerSnapshot value = snapshot;
		if (!closed && visible && value != null) { tooltips.add(new Tooltip(FightTimerPainter.provenance(value))); }
	}
	public void close()
	{
		closed = true; visible = false; icon = null;
		SwingUtilities.invokeLater(icons::close);
	}
}
