package com.bossingcompanion.presentation;

import com.bossingcompanion.BossingCompanionPlugin;
import com.bossingcompanion.application.BossIcons;
import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.FightTimerSnapshot;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual overlay renderer exercised off-screen, no client/game inputs. */
public class FightTimerOverlayTest
{
	private static final BossIcons NO_ICONS = new BossIcons()
	{
		public void load(Boss boss, Consumer<BufferedImage> consumer) { consumer.accept(null); }
		public void close() { }
	};
	@Test public void nativeOverlayIsMovableAndOptOutOrCloseStopsRendering() throws Exception
	{
		TooltipManager tooltips = new TooltipManager();
		FightTimerOverlay overlay = new FightTimerOverlay(new BossingCompanionPlugin(), NO_ICONS, () -> Duration.ofSeconds(64).toNanos(), tooltips);
		BufferedImage canvas = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB); Graphics2D graphics = canvas.createGraphics();
		try
		{
			assertTrue(overlay.isMovable()); assertEquals(OverlayPosition.TOP_LEFT, overlay.getPosition());
			assertNull(overlay.render(graphics));
			overlay.showSnapshot(new FightTimerSnapshot(Boss.VORKATH, true, 0, Duration.ZERO, false), true);
			SwingUtilities.invokeAndWait(() -> {});
			Dimension bounds = overlay.render(graphics); assertTrue(bounds.width < 164); assertTrue(bounds.height <= 32);
			overlay.onMouseOver(); assertTrue(tooltips.getTooltips().get(0).getText().contains("elapsed since your first detected hit"));
			overlay.showSnapshot(new FightTimerSnapshot(Boss.VORKATH, false, 0, Duration.ofMillis(64800), true), false);
			assertNull(overlay.render(graphics)); overlay.close();
			overlay.showSnapshot(new FightTimerSnapshot(Boss.VORKATH, false, 0, Duration.ofSeconds(30), true), true);
			assertNull(overlay.render(graphics));
		}
		finally { graphics.dispose(); overlay.close(); }
	}
	@Test public void actualClockPainterFitsNativeAndScaledBounds() throws Exception
	{
		Path output = Path.of("build", "ui-verification", "fight-timer"); Files.createDirectories(output);
		FightTimerSnapshot[] states = {
			new FightTimerSnapshot(null, false, 0, Duration.ZERO, false),
			new FightTimerSnapshot(Boss.VORKATH, false, 0, Duration.ZERO, false),
			new FightTimerSnapshot(Boss.VORKATH, true, 0, Duration.ZERO, false),
			new FightTimerSnapshot(Boss.VORKATH, false, 0, Duration.ofMillis(64800), true),
			new FightTimerSnapshot(Boss.BRYOPHYTA, false, 0, Duration.ofMinutes(125), false)
		};
		for (int state = 0; state < states.length; state++)
		{
			BufferedImage base = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = base.createGraphics(); Dimension bounds;
			try { bounds = FightTimerPainter.paint(graphics, states[state], Duration.ofMillis(64200).toNanos(), null); }
			finally { graphics.dispose(); }
			assertTrue(bounds.width < 140); assertTrue(bounds.height <= 32);
			for (int x = 0; x < base.getWidth(); x++)
			{
				for (int y = 0; y < base.getHeight(); y++)
				{
					if (x >= bounds.width || y >= bounds.height) { assertEquals("Drawing outside returned overlay bounds", 0, base.getRGB(x, y)); }
				}
			}
			for (int scale = 1; scale <= 2; scale++)
			{
				BufferedImage image = new BufferedImage(bounds.width * scale, bounds.height * scale, BufferedImage.TYPE_INT_ARGB);
				Graphics2D scaled = image.createGraphics();
				try { scaled.scale(scale, scale); FightTimerPainter.paint(scaled, states[state], Duration.ofMillis(64200).toNanos(), null); }
				finally { scaled.dispose(); }
				ImageIO.write(image, "png", output.resolve("state-" + state + "-" + scale + "x.png").toFile());
			}
		}
	}
	@Test public void officialVersusObservedIsMetadataAndNeverAStatusCaption()
	{
		FightTimerSnapshot exact = new FightTimerSnapshot(Boss.OBOR, false, 0, Duration.ofSeconds(30), true);
		assertFalse(exact.isApproximate()); assertTrue(FightTimerPainter.provenance(exact).contains("game-reported"));
		FightTimerSnapshot zero = new FightTimerSnapshot(null, false, 0, Duration.ZERO, false);
		assertFalse(zero.isApproximate());
		assertTrue(FightTimerPainter.provenance(zero).contains("no fight timed yet"));
		assertFalse(FightTimerPainter.provenance(zero).contains("elapsed since"));
		assertTrue(FightTimerPainter.provenance(new FightTimerSnapshot(Boss.VORKATH, false, 0, Duration.ofSeconds(30), false)).contains("frozen estimated"));
	}
	@Test public void iconAspectAndMissingAssetDoNotEnlargeClockFootprint()
	{
		BufferedImage canvas = new BufferedImage(240, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			FightTimerSnapshot value = new FightTimerSnapshot(Boss.VORKATH, false, 0, Duration.ofSeconds(30), false);
			Dimension missing = FightTimerPainter.paint(graphics, value, 0, null);
			assertEquals(missing, FightTimerPainter.paint(graphics, value, 0, new BufferedImage(100, 5, BufferedImage.TYPE_INT_ARGB)));
			assertEquals(missing, FightTimerPainter.paint(graphics, value, 0, new BufferedImage(5, 100, BufferedImage.TYPE_INT_ARGB)));
			assertEquals(missing, FightTimerPainter.paint(graphics,
				new FightTimerSnapshot(Boss.THERMONUCLEAR_SMOKE_DEVIL, false, 0, Duration.ofSeconds(30), false), 0, null));
		}
		finally { graphics.dispose(); }
	}
}
