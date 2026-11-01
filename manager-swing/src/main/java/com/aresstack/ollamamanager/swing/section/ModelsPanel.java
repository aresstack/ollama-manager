package com.aresstack.ollamamanager.swing.section;

import com.aresstack.askai.java8.ui.ManagerModelCard;
import com.aresstack.ollamamanager.application.model.ModelEntry;
import com.aresstack.ollamamanager.application.model.ModelPlacement;
import com.aresstack.ollamamanager.domain.model.InstalledModel;
import com.aresstack.ollamamanager.domain.model.LoadedModel;
import com.aresstack.ollamamanager.swing.component.ActionBar;
import com.aresstack.ollamamanager.swing.component.Dialogs;
import com.aresstack.ollamamanager.swing.component.EmptyState;
import com.aresstack.ollamamanager.swing.component.JobProgressPanel;
import com.aresstack.ollamamanager.swing.component.MessageBanner;
import com.aresstack.ollamamanager.swing.component.SectionPanel;
import com.aresstack.ollamamanager.swing.component.Spacing;
import com.aresstack.ollamamanager.swing.component.ScrollColumn;
import com.aresstack.ollamamanager.swing.component.StatusBadge;
import com.aresstack.ollamamanager.swing.component.Theme;
import com.aresstack.ollamamanager.swing.job.Job;
import com.aresstack.ollamamanager.swing.shell.Section;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.Locale;

/**
 * Model library as one list of model cards, ported from the askai-java8 models view. Running models (from
 * {@code /api/ps}) come first with a green wash and a badge; the rest follow by name. Every card carries its
 * own actions.
 */
public final class ModelsPanel extends SectionPanel implements ModelsView {

    private static final long serialVersionUID = 1L;

    private final ModelsPresenter presenter;
    private final JLabel notice = new JLabel(" ");
    private final MessageBanner banner = new MessageBanner();
    private final EmptyState empty = new EmptyState("No models on this server",
        "Install a model in the Install models section to get started.");
    private final CardLayout listCards = new CardLayout();
    private final JPanel list = new JPanel(listCards);
    private final ScrollColumn column = new ScrollColumn(new java.awt.FlowLayout());
    private final JobProgressPanel progress = new JobProgressPanel();
    private final java.util.Map<String, List<ManagerModelCard>> cardsByName =
        new java.util.HashMap<String, List<ManagerModelCard>>();
    private boolean actionsEnabled = true;
    private boolean mutationsAllowed;

    public ModelsPanel(ModelsPresenter presenter) {
        super(Section.MODELS.title(), Section.MODELS.description());
        this.presenter = presenter;

        JButton refresh = new JButton("Refresh");
        refresh.setMnemonic('R');
        refresh.addActionListener(event -> presenter.refresh());
        addToolbarItem(refresh);

        JPanel south = new JPanel(new BorderLayout(0, Spacing.GAP));
        south.setOpaque(false);
        south.add(progress, BorderLayout.SOUTH);

        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        list.setOpaque(false);
        list.add(ScrollColumn.wrap(column), "cards");
        list.add(empty, "empty");

        JPanel north = new JPanel(new BorderLayout(0, Spacing.GAP));
        north.setOpaque(false);
        north.add(banner, BorderLayout.NORTH);
        notice.setForeground(Theme.mutedForeground());
        north.add(notice, BorderLayout.CENTER);
        JPanel body = new JPanel(new BorderLayout(0, Spacing.GAP));
        body.setOpaque(false);
        body.add(north, BorderLayout.NORTH);
        body.add(list, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);
        showList(false);
        updateButtons();
        presenter.bind(this);
        // Load when the section becomes visible, so a server started meanwhile is picked up.
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                presenter.refresh();
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (notice != null) {
            notice.setForeground(Theme.mutedForeground());
        }
    }

    @Override
    public void showModels(List<ModelEntry> entries) {
        column.removeAll();
        cardsByName.clear();
        int loaded = 0;
        int installed = 0;
        for (ModelEntry entry : entries) {
            loaded += entry.isLoaded() ? 1 : 0;
            installed += entry.isInstalled() ? 1 : 0;
        }
        // One list: running models first, each with a green wash and badge, then the rest by name.
        List<ModelEntry> ordered = new java.util.ArrayList<ModelEntry>(entries);
        java.util.Collections.sort(ordered, (a, b) -> a.isLoaded() != b.isLoaded() ? (a.isLoaded() ? -1 : 1)
            : a.identity().name().compareToIgnoreCase(b.identity().name()));
        for (ModelEntry entry : ordered) {
            addCard(entry.isLoaded() ? runningCard(entry) : installedCard(entry));
        }
        column.add(Box.createVerticalGlue());
        notice.setText(entries.isEmpty() ? " " : installed + " installed, " + loaded + " running");
        empty.setText("No models on this server", "Install a model in the Install models section to get started.");
        showList(!entries.isEmpty());
        updateButtons();
        column.revalidate();
        column.repaint();
    }

    @Override
    public void showSource(String text) {
        String counts = notice.getText().trim();
        notice.setText(counts.isEmpty() ? text : counts + "  ·  " + text);
    }

    @Override
    public void setMutationsAllowed(boolean allowed, String reason) {
        mutationsAllowed = allowed;
        if (allowed || reason == null) {
            banner.clear();
        } else {
            banner.warning(reason);
        }
        updateButtons();
    }

    @Override
    public void showUnavailable(String reason) {
        mutationsAllowed = false;
        banner.clear();
        notice.setText(" ");
        empty.setText("Model library unavailable", reason);
        column.removeAll();
        cardsByName.clear();
        showList(false);
        updateButtons();
    }

    /** Show the cards, or the empty state while there is nothing to list. */
    private void showList(boolean hasRows) {
        listCards.show(list, hasRows ? "cards" : "empty");
    }

    private JPanel runningCard(ModelEntry entry) {
        LoadedModel model = entry.loaded();
        String placement = entry.placement().name().toLowerCase(Locale.ROOT);
        String details = ManagerModelCard.join(entry.isInstalled() ? entry.installed().family() : null,
            entry.isInstalled() ? entry.installed().parameterSize() : null, entry.identity().quantization(),
            "on " + placement);
        String meta = ManagerModelCard.join("RAM " + ManagerModelCard.formatBytes(model.sizeBytes()),
            "VRAM " + ManagerModelCard.formatBytes(model.vramBytes()),
            model.contextLength() == null ? null : "context " + model.contextLength(),
            model.expiresAt() == null ? null : "expires " + ManagerModelCard.shortDate(model.expiresAt()));
        final String name = entry.identity().name();
        return register(name, new ManagerModelCard(name, details, meta, true,
            ManagerModelCard.Action.normal("Unload", "Remove the model from memory. The files stay on disk.",
                () -> presenter.unload(name)),
            entry.isInstalled() ? ManagerModelCard.Action.destructive("Delete", "Delete the model files from"
                + " disk. You must type the model name to confirm.", () -> presenter.delete(name)) : null));
    }

    private JPanel installedCard(ModelEntry entry) {
        InstalledModel model = entry.installed();
        String details = ManagerModelCard.join(model.family(), model.parameterSize(), entry.identity().quantization());
        String meta = ManagerModelCard.join(ManagerModelCard.formatBytes(model.sizeBytes()),
            ManagerModelCard.shortDate(model.modifiedAt()), ManagerModelCard.shortDigest(entry.identity().digest()));
        final String name = entry.identity().name();
        return register(name, new ManagerModelCard(name, details, meta, false,
            ManagerModelCard.Action.primary("Load",
                "Load the model into memory now and show whether it runs on GPU, CPU or split.",
                () -> presenter.load(name)),
            ManagerModelCard.Action.destructive("Delete", "Delete the model files from disk. You must type the"
                + " model name to confirm.", () -> presenter.delete(name))));
    }

    private ManagerModelCard register(String name, ManagerModelCard card) {
        List<ManagerModelCard> list = cardsByName.get(name);
        if (list == null) {
            list = new java.util.ArrayList<ManagerModelCard>();
            cardsByName.put(name, list);
        }
        list.add(card);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    @Override
    public void showCapabilities(java.util.Map<String, List<String>> capabilities) {
        for (java.util.Map.Entry<String, List<String>> entry : capabilities.entrySet()) {
            List<ManagerModelCard> cards = cardsByName.get(entry.getKey());
            if (cards != null) {
                for (ManagerModelCard card : cards) {
                    card.setCapabilities(entry.getValue());
                }
            }
        }
    }

    private void addCard(JPanel card) {
        column.add(card);
        column.add(Box.createVerticalStrut(Spacing.GAP));
    }


    static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    static String shortDate(String value) {
        return value == null || value.isEmpty() ? "" : value.substring(0, Math.min(19, value.length())).replace('T', ' ');
    }

    static String shortDigest(String digest) {
        return digest == null || digest.isEmpty() ? ""
            : "digest " + (digest.length() <= 18 ? digest : digest.substring(0, 18) + "…");
    }

    static Theme.Tone placementTone(Object value) {
        if ("gpu".equals(value)) {
            return Theme.Tone.SUCCESS;
        }
        if ("split".equals(value)) {
            return Theme.Tone.WARNING;
        }
        return "cpu".equals(value) ? Theme.Tone.ERROR : Theme.Tone.NEUTRAL;
    }

    @Override
    public void setActionsEnabled(boolean enabled) {
        actionsEnabled = enabled;
        updateButtons();
    }

    @Override
    public void showFailure(String action, Exception error) {
        Dialogs.showError(this, action, error);
    }

    @Override
    public void trackJob(Job job) {
        progress.attach(job);
    }

    @Override
    public String confirmDelete(String modelName) {
        Object typed = JOptionPane.showInputDialog(this,
            "Deleting removes the model files from disk. Clients using " + modelName + " will fail.\n"
                + "Type the model name to confirm:", "Delete model", JOptionPane.WARNING_MESSAGE);
        return typed == null ? null : typed.toString();
    }

    private void updateButtons() {
        boolean enabled = actionsEnabled && mutationsAllowed;
        for (List<ManagerModelCard> cards : cardsByName.values()) {
            for (ManagerModelCard card : cards) {
                card.setActionsEnabled(enabled);
            }
        }
    }
}
