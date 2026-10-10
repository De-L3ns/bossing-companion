package com.bossingcompanion.presentation;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.LootTotal;
import com.bossingcompanion.domain.SessionSnapshot;
import com.bossingcompanion.domain.BossProgress;
import com.bossingcompanion.domain.DropComponent;
import com.bossingcompanion.domain.DropEntry;
import com.bossingcompanion.domain.DropMechanic;
import com.bossingcompanion.domain.WikiRates;
import com.bossingcompanion.application.BossIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.LinkBrowser;

/** Minimal native sidebar. Persistent options belong to RuneLite's config panel. */
public final class BossingPanel extends PluginPanel
{
	private final BiConsumer<Integer, JButton> itemImages;
	private final BossIcons icons;
	private final List<Boss> catalogue;
	private final Consumer<Boss> start;
	private final Runnable end;
	private final Consumer<Boss> browse;
	private final Runnable retry;
	private final Clock clock;
	private final JPanel content = vertical();
	private final Timer timer;
	private SessionSnapshot snapshot;
	private boolean loggedIn;
	private boolean automatic;
	private boolean picking;
	private boolean closed;
	private boolean initialized;
	private Boss selection = Boss.VORKATH;
	private Integer selectedItem;
	private JLabel elapsed;
	private JLabel detail;
	private BossProgress progress;
	private WikiRates rates;
	private boolean collectionTab;
	private Integer selectedUnique;

	public BossingPanel(ItemManager items, BossIcons icons, List<Boss> catalogue, Clock clock, Consumer<Boss> start, Runnable end,
		Consumer<Boss> browse, Runnable retry)
	{
		this((id, tile) ->
		{
			AsyncBufferedImage image = items.getImage(id);
			if (image != null) { image.addTo(tile); }
		}, icons, catalogue, clock, start, end, browse, retry);
	}
	BossingPanel(BiConsumer<Integer, JButton> itemImages, BossIcons icons, List<Boss> catalogue, Clock clock, Consumer<Boss> start, Runnable end,
		Consumer<Boss> browse, Runnable retry)
	{
		super(false);
		this.itemImages = itemImages;
		this.icons = icons;
		this.catalogue = catalogue;
		this.clock = clock;
		this.start = start;
		this.end = end;
		this.browse = browse; this.retry = retry;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JPanel top = vertical();
		JLabel title = label("Bossing Companion", ColorScheme.TEXT_COLOR);
		title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		top.add(title);
		JPanel tabs = new JPanel(new GridLayout(1, 2, 3, 0));
		tabs.setOpaque(false);
		tabs.add(button("Session", () -> { collectionTab = false; rebuild(); }));
		tabs.add(button("Collection log", () -> { collectionTab = true; rebuild(); }));
		top.add(tabs);
		top.add(content);
		add(top, BorderLayout.NORTH);
		timer = new Timer(1000, e -> refreshElapsed());
	}
	public void showProgress(BossProgress progress, WikiRates rates)
	{
		if (closed || Objects.equals(this.progress, progress) && Objects.equals(this.rates, rates)) { return; }
		if (this.progress == null || this.progress.getBoss() != progress.getBoss()) { selectedUnique = null; }
		this.progress = progress; this.rates = rates;
		if (collectionTab) { rebuild(); }
	}

	public void showSnapshot(SessionSnapshot snapshot, boolean loggedIn, boolean automatic)
	{
		if (closed) { return; }
		if (initialized && Objects.equals(this.snapshot, snapshot) && this.loggedIn == loggedIn && this.automatic == automatic) { return; }
		initialized = true;
		if (snapshot != null && snapshot.isActive()) { picking = false; }
		if (this.snapshot == null || snapshot == null || this.snapshot.getBoss() != snapshot.getBoss()
			|| !this.snapshot.getStartedAt().equals(snapshot.getStartedAt())) { selectedItem = null; }
		this.snapshot = snapshot;
		this.loggedIn = loggedIn;
		this.automatic = automatic;
		rebuild();
	}

	private void rebuild()
	{
		content.removeAll();
		elapsed = null;
		if (collectionTab)
		{
			addCollection(); content.revalidate(); content.repaint(); return;
		}
		if (snapshot == null)
		{
			content.add(label(loggedIn ? "No active session." : "Log in to start a session.", ColorScheme.LIGHT_GRAY_COLOR));
			if (loggedIn && automatic) { content.add(label("Kill a boss to begin automatically.", ColorScheme.LIGHT_GRAY_COLOR)); }
		}
		else
		{
			JPanel header = new JPanel(new BorderLayout(4, 0));
			header.setOpaque(false);
			String name = snapshot.getBoss().getDisplayName();
			JLabel bossName = wrapped(name, Color.YELLOW, 120);
			bossName.setToolTipText(snapshot.getBoss().getDisplayName());
			header.add(bossName, BorderLayout.CENTER);
			Boss boss = snapshot.getBoss();
			icons.load(boss, image ->
			{
				if (!closed && snapshot != null && snapshot.getBoss() == boss && image != null)
				{
					bossName.setIcon(new ImageIcon(image));
				}
			});
			if (snapshot.isActive())
			{
				JButton stop = button("End", end);
				header.add(stop, BorderLayout.EAST);
			}
			content.add(header);
			content.add(label(!snapshot.isActive() ? "Ended" : snapshot.getKills() == 0 ? "Ready · first kill pending" : "Recording", ColorScheme.LIGHT_GRAY_COLOR));
			JPanel primary = new JPanel(new BorderLayout());
			primary.setOpaque(false);
			primary.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
			primary.add(label(snapshot.getKills() + " kills", ColorScheme.TEXT_COLOR), BorderLayout.WEST);
			elapsed = label("", ColorScheme.TEXT_COLOR);
			primary.add(elapsed, BorderLayout.EAST);
			content.add(primary);
			refreshElapsed();
			content.add(row("Last / best", time(snapshot.getLastTime()) + " / " + time(snapshot.getBestTime())));
			content.add(row("Average kill", time(snapshot.getAverageTime())));
			content.add(row("Kills timed", snapshot.getTimedKills() + " / " + snapshot.getKills()));
			addLoot();
		}
		if (snapshot == null || !snapshot.isActive()) { addStartControls(); }
		content.revalidate();
		content.repaint();
	}
	private void addCollection()
	{
		if (progress == null) { content.add(label("Loading collection definitions…", ColorScheme.LIGHT_GRAY_COLOR)); return; }
		Boss boss = progress.getBoss();
		JLabel header = wrapped(boss.getDisplayName(), Color.YELLOW, 145);
		header.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
		icons.load(boss, image ->
		{
			if (!closed && progress != null && progress.getBoss() == boss && image != null) { header.setIcon(new ImageIcon(image)); }
		});
		content.add(header);
		if (snapshot == null || !snapshot.isActive())
		{
			JComboBox<Boss> chooser = new JComboBox<>(catalogue.toArray(new Boss[0]));
			chooser.setSelectedItem(boss); chooser.setFont(FontManager.getRunescapeFont());
			chooser.addActionListener(e -> browse.accept((Boss) chooser.getSelectedItem()));
			content.add(chooser);
		}
		content.add(row("Total kills", progress.getTotalKills() == null ? "—" : number(progress.getTotalKills())));
		if (!loggedIn) { content.add(label("Log in to observe your collection log.", ColorScheme.LIGHT_GRAY_COLOR)); }
		else if (progress.getSlots().isEmpty()) { content.add(label("Collection definitions unavailable.", ColorScheme.LIGHT_GRAY_COLOR)); }
		else if (progress.getSlots().stream().anyMatch(s -> !s.isKnown()))
		{
			content.add(wrapped("Open your collection log to sync. Visit this boss page to confirm missing items."));
		}
		JPanel grid = new JPanel(new GridLayout(0, 5, 3, 3)); grid.setOpaque(false);
		for (BossProgress.Slot slot : progress.getSlots())
		{
			JButton tile = new CollectionButton(slot);
			tile.setPreferredSize(new Dimension(36, 36)); tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tile.setBorder(BorderFactory.createEmptyBorder()); tile.setToolTipText(slot.getItem().getName());
			tile.getAccessibleContext().setAccessibleName(slot.getItem().getName() + ", "
				+ (!slot.isKnown() ? "unknown" : !slot.isObtained() ? "not obtained" : "quantity " + slot.getQuantity()));
			itemImages.accept(slot.getItem().getItemId(), tile);
			tile.addActionListener(e -> { selectedUnique = slot.getItem().getItemId(); rebuild(); });
			grid.add(tile);
		}
		for (int i = 0, remaining = (5 - progress.getSlots().size() % 5) % 5; i < remaining; i++)
		{
			JPanel empty = new JPanel(); empty.setOpaque(false); grid.add(empty);
		}
		content.add(grid);
		if (rates != null)
		{
			switch (rates.getState())
			{
				case DISABLED: content.add(label("Wiki rates disabled in configuration.", ColorScheme.LIGHT_GRAY_COLOR)); break;
				case NEED_ITEMS: content.add(label("Waiting for collection definitions.", ColorScheme.LIGHT_GRAY_COLOR)); break;
				case LOADING: content.add(label("Loading Wiki rates…", ColorScheme.LIGHT_GRAY_COLOR)); break;
				case UNAVAILABLE:
					content.add(label("Wiki rates unavailable.", ColorScheme.LIGHT_GRAY_COLOR)); content.add(button("Retry", retry)); break;
				case UNSUPPORTED_WORLD: content.add(label("Standard rates unavailable on this world.", ColorScheme.LIGHT_GRAY_COLOR)); break;
				default: break;
			}
		}
		if (selectedUnique != null)
		{
			progress.getSlots().stream().filter(s -> s.getItem().getItemId() == selectedUnique).findFirst().ifPresent(slot ->
			{
				content.add(wrapped(slot.getItem().getName(), Color.YELLOW, 185));
				DropEntry entry = rates == null || rates.getTable() == null ? null : rates.getTable().getEntries().get(selectedUnique);
				if (entry == null)
				{
					if (rates != null && rates.getState() == WikiRates.State.READY) { content.add(label("Drop rate not available.", ColorScheme.LIGHT_GRAY_COLOR)); }
					return;
				}
				boolean conditional = entry.getMechanics().stream().anyMatch(m -> m.getKind() == DropMechanic.Kind.CONDITIONAL || m.getKind() == DropMechanic.Kind.UNMODELED || m.getKind() == DropMechanic.Kind.AVERAGE_ONLY);
				for (DropComponent component : entry.getComponents())
				{
					if (entry.getComponents().stream().map(DropComponent::getSourceVersion).distinct().count() > 1)
					{
						content.add(wrapped(component.getSourceVersion()));
					}
					String text = component.rarityLabel() + " · " + (component.isApproximate() ? "~" : "") + component.getRarity()
						+ (component.getRolls() == 1 ? " per " + component.getRollUnit() : " per roll · " + component.getRolls() + " rolls/" + component.getRollUnit())
						+ (conditional ? " · conditional" : "");
					content.add(wrapped(text, ColorScheme.TEXT_COLOR, 185));
					if (!component.getConditions().isEmpty()) { content.add(wrapped(component.getConditions())); }
					if (!component.getAlternativeRarity().isEmpty()) { content.add(wrapped("Alternative rate: " + component.getAlternativeRarity() + "; context required.")); }
				}
				for (DropMechanic mechanic : entry.getMechanics()) { content.add(wrapped(mechanic.getDescription())); }
				content.add(button("Wiki ↗", () -> LinkBrowser.browse(rates.getTable().getSource())));
			});
		}
	}
	private static JLabel wrapped(String text) { return wrapped(text, ColorScheme.LIGHT_GRAY_COLOR, 185); }
	private static JLabel wrapped(String text, Color color, int width)
	{
		JLabel label = new WrappedLabel(text, width);
		label.setFont(FontManager.getRunescapeFont()); label.setForeground(color); return label;
	}

	private void addLoot()
	{
		long value = snapshot.getLoot().stream().mapToLong(LootTotal::getKnownValue).sum();
		boolean partial = snapshot.getLoot().stream().anyMatch(LootTotal::isPartialValue);
		JPanel lootHeader = row("Loot", number(value) + " gp" + (partial ? " + ?" : "") + " · GE");
		lootHeader.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 5, 0),
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR)));
		content.add(lootHeader);
		JPanel grid = new JPanel(new GridLayout(0, 5, 3, 3));
		grid.setOpaque(false);
		for (LootTotal total : snapshot.getLoot())
		{
			JButton tile = new LootButton(total.getQuantity());
			tile.setPreferredSize(new Dimension(36, 36));
			tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tile.setBorder(BorderFactory.createEmptyBorder());
			tile.setFocusable(true);
			tile.setToolTipText(total.getName() + " × " + number(total.getQuantity()) + " · " + valueText(total));
			tile.getAccessibleContext().setAccessibleName(tile.getToolTipText());
			itemImages.accept(total.getItemId(), tile);
			tile.addActionListener(e -> { selectedItem = total.getItemId(); showDetail(total); });
			grid.add(tile);
		}
		if (!snapshot.getLoot().isEmpty())
		{
			int remaining = (5 - snapshot.getLoot().size() % 5) % 5;
			for (int i = 0; i < remaining; i++) { JPanel empty = new JPanel(); empty.setOpaque(false); grid.add(empty); }
			content.add(grid);
		}
		else { content.add(label("No attributed loot yet.", ColorScheme.LIGHT_GRAY_COLOR)); }
		content.add(row("Loot observed", snapshot.getLootKills() + " / " + snapshot.getKills() + " kills"));
		detail = label("", ColorScheme.TEXT_COLOR);
		detail.setBorder(BorderFactory.createEmptyBorder(5, 0, 4, 0));
		content.add(detail);
		if (selectedItem != null)
		{
			snapshot.getLoot().stream().filter(l -> l.getItemId() == selectedItem).findFirst().ifPresent(this::showDetail);
		}
	}

	private void showDetail(LootTotal total)
	{
		detail.setText("<html>" + escape(total.getName()) + " × " + number(total.getQuantity())
			+ "<br>" + valueText(total) + " · estimated GE value</html>");
		content.revalidate();
	}

	private void addStartControls()
	{
		if (!picking)
		{
			JButton newSession = button("New session", () -> { picking = true; rebuild(); });
			newSession.setEnabled(loggedIn);
			JPanel commands = new JPanel(new BorderLayout());
			commands.setOpaque(false);
			commands.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
			commands.add(newSession);
			content.add(commands);
			return;
		}
		JComboBox<Boss> chooser = new JComboBox<>(catalogue.toArray(new Boss[0]));
		chooser.setSelectedItem(selection);
		chooser.setFont(FontManager.getRunescapeFont());
		chooser.addActionListener(e -> selection = (Boss) chooser.getSelectedItem());
		content.add(label("Session boss", ColorScheme.LIGHT_GRAY_COLOR));
		content.add(chooser);
		JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
		actions.setOpaque(false);
		actions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		JButton begin = button("Start", () -> start.accept(selection));
		begin.setEnabled(loggedIn);
		actions.add(begin);
		actions.add(button("Cancel", () -> { picking = false; rebuild(); }));
		content.add(actions);
	}

	private void refreshElapsed()
	{
		if (elapsed != null && snapshot != null) { elapsed.setText(time(snapshot.elapsedAt(clock.instant())) + " elapsed"); }
	}
	@Override public void onActivate() { if (!closed) { timer.start(); refreshElapsed(); } }
	@Override public void onDeactivate() { timer.stop(); }
	public void close() { closed = true; timer.stop(); icons.close(); }

	private static JPanel vertical()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new DynamicGridLayout(0, 1, 0, 4));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
	}
	private static JPanel row(String name, String value)
	{
		JPanel panel = new JPanel(new BorderLayout(3, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		panel.add(label(name, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		panel.add(label(value, ColorScheme.TEXT_COLOR), BorderLayout.EAST);
		return panel;
	}
	private static JLabel label(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}
	private static JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeFont());
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.TEXT_COLOR);
		button.setMargin(new Insets(3, 5, 3, 5));
		button.addActionListener(e -> action.run());
		return button;
	}
	private static String valueText(LootTotal total) { return number(total.getKnownValue()) + " gp" + (total.isPartialValue() ? " + unknown" : ""); }
	private static String number(long value) { return QuantityFormatter.formatNumber(value); }
	private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
	public static String time(Duration duration)
	{
		if (duration == null) { return "—"; }
		long seconds = duration.getSeconds();
		return seconds >= 3600 ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
			: String.format("%02d:%02d", seconds / 60, seconds % 60);
	}
	private static final class LootButton extends JButton
	{
		private final long quantity;
		LootButton(long quantity) { this.quantity = quantity; }
		@Override protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics copy = graphics.create();
			try
			{
				String value = quantity >= 10000 ? quantity / 1000 + "K" : Long.toString(quantity);
				copy.setFont(FontManager.getRunescapeSmallFont());
				copy.setColor(Color.BLACK); copy.drawString(value, 2, 12);
				copy.setColor(Color.YELLOW); copy.drawString(value, 1, 11);
			}
			finally { copy.dispose(); }
		}
	}
	/** Measure HTML at the actual text width; CSS lengths otherwise scale with Swing font points. */
	private static final class WrappedLabel extends JLabel
	{
		private final int textWidth;
		WrappedLabel(String text, int textWidth) { super("<html>" + escape(text) + "</html>"); this.textWidth = textWidth; }
		@Override public Dimension getPreferredSize()
		{
			View view = (View) getClientProperty(BasicHTML.propertyKey);
			if (view == null || textWidth == 0) { return super.getPreferredSize(); }
			view.setSize(textWidth, 0);
			int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
			int iconHeight = getIcon() == null ? 0 : getIcon().getIconHeight();
			Insets insets = getInsets();
			return new Dimension(textWidth + iconWidth + insets.left + insets.right,
				Math.max(iconHeight, (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS))) + insets.top + insets.bottom);
		}
	}
	private static final class CollectionButton extends JButton
	{
		private final BossProgress.Slot slot;
		CollectionButton(BossProgress.Slot slot) { this.slot = slot; }
		@Override protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics copy = graphics.create();
			try
			{
				if (!slot.isObtained())
				{
					copy.setColor(new Color(20, 20, 20, 170)); copy.fillRect(0, 0, getWidth(), getHeight());
				}
				if (!slot.isKnown() || slot.isObtained())
				{
					String value = !slot.isKnown() ? "?" : slot.getQuantity() >= 10000 ? slot.getQuantity() / 1000 + "K" : slot.getQuantity().toString();
					copy.setFont(FontManager.getRunescapeSmallFont());
					copy.setColor(Color.BLACK); copy.drawString(value, 2, 12);
					copy.setColor(slot.isKnown() ? Color.YELLOW : Color.LIGHT_GRAY); copy.drawString(value, 1, 11);
				}
			}
			finally { copy.dispose(); }
		}
	}
}

