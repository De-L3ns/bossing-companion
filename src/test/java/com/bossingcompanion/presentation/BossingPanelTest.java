package com.bossingcompanion.presentation;

import com.bossingcompanion.application.BossIcons;
import com.bossingcompanion.application.SessionTracker;
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
	@Test public void inlineDropInformationPreservesSessionAndUsesFullWidthWithoutOverlap() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BossIcons icons = new BossIcons()
			{
				public void load(Boss boss, Consumer<BufferedImage> consumer) { consumer.accept(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)); }
				public void close() { }
			};
			BossingPanel panel = new BossingPanel((id, tile) -> {}, icons, List.of(Boss.ABYSSAL_SIRE, Boss.VORKATH),
				Clock.fixed(Instant.EPOCH.plusSeconds(35), ZoneOffset.UTC), boss -> {}, () -> {});
			try
			{
				panel.showSnapshot(new SessionSnapshot(Boss.ABYSSAL_SIRE, Instant.EPOCH, null, 0, 0, 0, null, null, null, List.of()), true, true);
				layout(panel); checkRows(panel);
				assertEquals(16f, findText(panel, "Bossing Companion").getFont().getSize2D(), 0f);
				assertEquals(209, findText(panel, "Loot observed").getParent().getWidth());
				assertTrue(findText(panel, "Loot observed").getBounds().getMaxX() <= findText(panel, "0 / 0 kills").getX());
				render(panel, "session");
				assertNull(findButton(panel, "Session"));
				assertNull(findButton(panel, "Collection log"));
				assertFalse(findDropsToggle(panel).isSelected());
				List<BossProgress.Slot> slots = new ArrayList<>();
				for (int id = 1; id <= 9; id++) { slots.add(new BossProgress.Slot(new CollectionItem(id, "Abyssal item " + id), null)); }
				DropComponent rate = new DropComponent("Unsired", "26/128", 1, "1", false, "", "Unsired", "26/123");
				panel.showProgress(new BossProgress(Boss.ABYSSAL_SIRE, 1234L, slots), new DropRates(DropRates.State.READY,
					new DropTable(Boss.ABYSSAL_SIRE, "https://oldschool.runescape.wiki/w/Abyssal_Sire", Instant.EPOCH,
						Map.of(1, new DropEntry(1, List.of(rate), List.of())))));
				assertNull(findText(panel, "Total kills"));
				findDropsToggle(panel).doClick(); layout(panel); checkRows(panel);
				assertNotNull(findText(panel, "Loot observed"));
				assertNotNull(findButton(panel, "End"));
				assertEquals(209, findText(panel, "Total kills").getParent().getWidth());
				findTooltip(panel, "Abyssal item 1").doClick(); layout(panel); checkRows(panel);
				assertNotNull(findTextContaining(panel, "per Unsired"));
				render(panel, "drop-information");
				findDropsToggle(panel).doClick(); layout(panel);
				assertNull(findText(panel, "Total kills"));
				assertNotNull(findText(panel, "Loot observed"));
				panel.showSnapshot(new SessionSnapshot(Boss.ABYSSAL_SIRE, Instant.EPOCH, null, 1, 0, 0, null, null, null, List.of()), true, true);
				panel.showProgress(new BossProgress(Boss.ABYSSAL_SIRE, 1235L, slots), new DropRates(DropRates.State.UNAVAILABLE, null));
				assertFalse(findDropsToggle(panel).isSelected());
				assertNull(findText(panel, "Total kills"));
				findDropsToggle(panel).doClick(); layout(panel); checkRows(panel);
				assertNotNull(findText(panel, "1 kills"));
				assertNotNull(findTextContaining(panel, "Abyssal item 1"));
				assertNotNull(findText(panel, "1,235"));
				panel.showProgress(new BossProgress(Boss.ABYSSAL_SIRE, 1234L, slots), new DropRates(DropRates.State.UNAVAILABLE, null));
				layout(panel); assertTrue(findDropsToggle(panel).isSelected());
				assertNull(findButton(panel, "Retry")); assertNotNull(findText(panel, "Drop data unavailable.")); render(panel, "drop-information-unavailable");
				panel.showSnapshot(new SessionSnapshot(Boss.ABYSSAL_SIRE, Instant.EPOCH, Instant.EPOCH.plusSeconds(35), 1, 0, 0, null, null, null, List.of()), true, true);
				layout(panel); checkRows(panel);
				assertTrue(findDropsToggle(panel).isSelected()); assertEquals(0, countChoosers(panel));
				panel.showSnapshot(null, true, true);
				layout(panel); checkRows(panel);
				assertNotNull(findButton(panel, "New session"));
				assertNull(findDropsToggle(panel));
				assertEquals(0, countChoosers(panel));
				panel.showProgress(new BossProgress(Boss.VORKATH, null, List.of(new BossProgress.Slot(new CollectionItem(1, "Vorkath item"), null))),
					new DropRates(DropRates.State.NEED_ITEMS, null));
				layout(panel); checkRows(panel);
				assertNull(findTextContaining(panel, "Abyssal item 1"));
				render(panel, "drop-information-idle");
				panel.showSnapshot(null, false, true);
				assertNotNull(findText(panel, "Log in to start a session."));
			}
			finally { panel.close(); }
		});
	}
	@Test public void informationRequiresStartedSessionAndRejectsPreviousBossData() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Clock clock = Clock.fixed(Instant.EPOCH.plusSeconds(35), ZoneOffset.UTC);
			SessionTracker tracker = new SessionTracker(clock);
			List<Consumer<BufferedImage>> callbacks = new ArrayList<>();
			BossIcons icons = new BossIcons()
			{
				public void load(Boss boss, Consumer<BufferedImage> callback) { callbacks.add(callback); }
				public void close() { }
			};
			BossingPanel panel = new BossingPanel((id, tile) -> {}, icons, List.of(Boss.ABYSSAL_SIRE, Boss.VORKATH), clock, tracker::start, tracker::end);
			BossProgress sire = new BossProgress(Boss.ABYSSAL_SIRE, 10L, List.of(new BossProgress.Slot(new CollectionItem(1, "Sire unique"), null)));
			DropRates vorkathRate = new DropRates(DropRates.State.READY, new DropTable(Boss.VORKATH, "Wiki", Instant.EPOCH,
				Map.of(1, new DropEntry(1, List.of(new DropComponent("Vorkath", "1/100", 1, "1", false, "")), List.of()))));
			try
			{
				panel.showSnapshot(null, true, false); panel.showProgress(sire, vorkathRate);
				assertNull(findDropsToggle(panel)); assertEquals(0, callbacks.size());
				findButton(panel, "New session").doClick();
				assertEquals(1, countChoosers(panel)); findChooser(panel).setSelectedItem(Boss.ABYSSAL_SIRE);
				assertNull(findDropsToggle(panel)); assertNull(tracker.snapshot());
				layout(panel); checkRows(panel); render(panel, "session-boss-picker");
				findButton(panel, "Start").doClick(); panel.showSnapshot(tracker.snapshot(), true, false);
				assertNotNull(findDropsToggle(panel)); assertEquals(0, countChoosers(panel));
				findDropsToggle(panel).doClick();
				panel.showProgress(new BossProgress(Boss.VORKATH, 99L, List.of(new BossProgress.Slot(new CollectionItem(1, "Wrong boss unique"), null))), vorkathRate);
				assertNull(findTooltip(panel, "Wrong boss unique")); assertNull(findText(panel, "Total kills"));
				panel.showProgress(sire, vorkathRate); findTooltip(panel, "Sire unique").doClick();
				assertNull(findTextContaining(panel, "1/100")); assertNotNull(findText(panel, "Loading drop data…"));
				tracker.end(); panel.showSnapshot(tracker.snapshot(), true, false);
				assertNotNull(findDropsToggle(panel)); assertEquals(0, countChoosers(panel));
				layout(panel); checkRows(panel);
				assertTrue(SwingUtilities.convertPoint(findButton(panel, "New session").getParent(), findButton(panel, "New session").getLocation(), panel).y
					< SwingUtilities.convertPoint(findText(panel, "0 kills").getParent(), findText(panel, "0 kills").getLocation(), panel).y);
				render(panel, "session-ended"); findButton(panel, "New session").doClick();
				assertEquals(Boss.ABYSSAL_SIRE, findChooser(panel).getSelectedItem());
				assertNotNull(findText(panel, "Next session boss"));
				findChooser(panel).setSelectedItem(Boss.VORKATH);
				assertEquals(Boss.ABYSSAL_SIRE, tracker.snapshot().getBoss());
				layout(panel); checkRows(panel);
				assertTrue(findButton(panel, "Start new session").getWidth() >= findButton(panel, "Start new session").getPreferredSize().width);
				render(panel, "session-next-picker");
				findButton(panel, "Cancel").doClick(); assertEquals(Boss.ABYSSAL_SIRE, tracker.snapshot().getBoss());
				findButton(panel, "New session").doClick(); findChooser(panel).setSelectedItem(Boss.VORKATH);
				findButton(panel, "Start new session").doClick(); panel.showSnapshot(tracker.snapshot(), true, false);
				assertEquals(Boss.VORKATH, tracker.snapshot().getBoss()); assertEquals(0, countChoosers(panel));
				List<Consumer<BufferedImage>> staleIcons = new ArrayList<>(callbacks);
				tracker.clear(); panel.showSnapshot(null, true, true); panel.showProgress(sire, vorkathRate);
				assertNull(findDropsToggle(panel));
				tracker.setAutomatic(true); tracker.creditedKill(Boss.VORKATH, 100, 10); panel.showSnapshot(tracker.snapshot(), true, true);
				assertNotNull(findDropsToggle(panel)); assertFalse(findDropsToggle(panel).isSelected());
				staleIcons.forEach(callback -> callback.accept(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)));
				assertNull(findTextContaining(panel, "Vorkath").getIcon());
				findDropsToggle(panel).doClick(); panel.showProgress(sire, vorkathRate);
				assertNull(findTooltip(panel, "Sire unique"));
				panel.showProgress(new BossProgress(Boss.VORKATH, 100L, List.of(new BossProgress.Slot(new CollectionItem(1, "Vorkath unique"), null))), vorkathRate);
				assertNull(findTextContaining(panel, "Vorkath unique")); // Prior selection cannot select the same ID on the new boss.
				assertNotNull(findTooltip(panel, "Vorkath unique"));
				callbacks.get(callbacks.size() - 1).accept(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
				assertNotNull(findTextContaining(panel, "Vorkath").getIcon());
				layout(panel); checkRows(panel); render(panel, "session-boss-information");
				panel.showSnapshot(null, false, true); panel.showProgress(sire, vorkathRate);
				assertNull(findDropsToggle(panel)); assertNull(findText(panel, "Total kills"));
			}
			finally { panel.close(); }
		});
	}
	private static JComboBox<?> findChooser(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JComboBox) { return (JComboBox<?>) child; }
			if (child instanceof Container) { JComboBox<?> found = findChooser((Container) child); if (found != null) { return found; } }
		}
		return null;
	}
	private static JToggleButton findDropsToggle(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JToggleButton && ((JToggleButton) child).getText().contains("Drop Information")) { return (JToggleButton) child; }
			if (child instanceof Container) { JToggleButton found = findDropsToggle((Container) child); if (found != null) { return found; } }
		}
		return null;
	}
	private static int countChoosers(Container parent)
	{
		int count = 0;
		for (Component child : parent.getComponents())
		{
			if (child instanceof JComboBox) { count++; }
			else if (child instanceof Container) { count += countChoosers((Container) child); }
		}
		return count;
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
