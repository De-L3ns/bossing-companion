package com.bossingcompanion.presentation;

import com.bossingcompanion.application.BossIcons;
import com.bossingcompanion.domain.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Isolated Swing rendering only: no RuneLite client, game input, or network. */
public class BossingPanelTest
{
	@Test public void actualSessionAndCollectionRowsUseFullWidthWithoutOverlap() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BossIcons icons = new BossIcons()
			{
				public void load(Boss boss, Consumer<BufferedImage> consumer) { consumer.accept(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)); }
				public void close() { }
			};
			BossingPanel panel = new BossingPanel((id, tile) -> {}, icons, List.of(Boss.ABYSSAL_SIRE, Boss.VORKATH),
				Clock.fixed(Instant.EPOCH.plusSeconds(35), ZoneOffset.UTC), boss -> {}, () -> {}, boss -> {}, () -> {});
			try
			{
				panel.showSnapshot(new SessionSnapshot(Boss.ABYSSAL_SIRE, Instant.EPOCH, null, 0, 0, 0, null, null, null, List.of()), true, true);
				layout(panel); checkRows(panel);
				assertEquals(209, findText(panel, "Loot observed").getParent().getWidth());
				assertTrue(findText(panel, "Loot observed").getBounds().getMaxX() <= findText(panel, "0 / 0 kills").getX());
				render(panel, "session");
				List<BossProgress.Slot> slots = new ArrayList<>();
				for (int id = 1; id <= 9; id++) { slots.add(new BossProgress.Slot(new CollectionItem(id, "Abyssal item " + id), null)); }
				DropComponent rate = new DropComponent("Unsired", "26/128", 1, "1", false, "", "Unsired", "26/123");
				panel.showProgress(new BossProgress(Boss.ABYSSAL_SIRE, 1234L, slots), new WikiRates(WikiRates.State.READY,
					new DropTable(Boss.ABYSSAL_SIRE, "https://oldschool.runescape.wiki/w/Abyssal_Sire", Instant.EPOCH,
						Map.of(1, new DropEntry(1, List.of(rate), List.of())))));
				findButton(panel, "Collection log").doClick(); layout(panel); checkRows(panel);
				assertEquals(209, findText(panel, "Total kills").getParent().getWidth());
				findTooltip(panel, "Abyssal item 1").doClick(); layout(panel); checkRows(panel);
				assertNotNull(findTextContaining(panel, "per Unsired"));
				render(panel, "collection");
				panel.showProgress(new BossProgress(Boss.ABYSSAL_SIRE, 1234L, slots), new WikiRates(WikiRates.State.UNAVAILABLE, null));
				layout(panel); assertTrue(findButton(panel, "Retry").getWidth() >= 200); render(panel, "collection-unavailable");
			}
			finally { panel.close(); }
		});
	}
	private static void layout(BossingPanel panel) { panel.setSize(225, panel.getPreferredSize().height); layoutTree(panel); }
	private static void layoutTree(Container container)
	{
		container.doLayout(); for (Component component : container.getComponents()) { if (component instanceof Container) { layoutTree((Container) component); } }
	}
	private static void checkRows(Container container)
	{
		if (container.getLayout() instanceof net.runelite.client.ui.DynamicGridLayout)
		{
			int bottom = 0;
			for (Component component : container.getComponents())
			{
				assertEquals("Row inset caused the reported half-width layout", 0, component.getX());
				assertEquals(container.getWidth(), component.getWidth()); assertTrue(component.getY() >= bottom);
				bottom = component.getY() + component.getHeight();
			}
		}
		for (Component child : container.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText().startsWith("<html>"))
			{
				assertTrue("Wrapped text clipped: " + ((JLabel) child).getText(), child.getHeight() >= child.getPreferredSize().height);
				assertTrue("Wrapped text wider than sidebar: " + child.getWidth() + "/" + child.getPreferredSize().width + " " + ((JLabel) child).getText(), child.getWidth() >= child.getPreferredSize().width);
			}
			if (child instanceof Container) { checkRows((Container) child); }
		}
	}
	private static JLabel findText(Container parent, String text)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel && text.equals(((JLabel) child).getText())) { return (JLabel) child; }
			if (child instanceof Container) { JLabel found = findText((Container) child, text); if (found != null) { return found; } }
		}
		return null;
	}
	private static JLabel findTextContaining(Container parent, String text)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText().contains(text)) { return (JLabel) child; }
			if (child instanceof Container) { JLabel found = findTextContaining((Container) child, text); if (found != null) { return found; } }
		}
		return null;
	}
	private static JButton findButton(Container parent, String text) { return findButton(parent, text, false); }
	private static JButton findTooltip(Container parent, String text) { return findButton(parent, text, true); }
	private static JButton findButton(Container parent, String text, boolean tooltip)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JButton && text.equals(tooltip ? ((JButton) child).getToolTipText() : ((JButton) child).getText())) { return (JButton) child; }
			if (child instanceof Container) { JButton found = findButton((Container) child, text, tooltip); if (found != null) { return found; } }
		}
		return null;
	}
	private static void render(BossingPanel panel, String name)
	{
		try
		{
			Path directory = Path.of("build", "ui-verification"); Files.createDirectories(directory);
			for (int scale = 1; scale <= 2; scale++)
			{
				BufferedImage image = new BufferedImage(panel.getWidth() * scale, panel.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				try { graphics.scale(scale, scale); panel.printAll(graphics); } finally { graphics.dispose(); }
				ImageIO.write(image, "png", directory.resolve(name + "-" + scale + "x.png").toFile());
			}
		}
		catch (Exception ex) { throw new AssertionError(ex); }
	}
}
