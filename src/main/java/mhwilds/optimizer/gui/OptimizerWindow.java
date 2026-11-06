package mhwilds.optimizer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.text.DefaultCaret;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.mhshub.MhWildsHubLink;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.Weapon;
import mhwilds.optimizer.ranking.RankingFactory;

public class OptimizerWindow extends JFrame {

  private final GameData data;
  private final SolverService service;
  private GearPoolConfig baseConfig;
  private final AppraisedTalismanStore talismanStore;

  private final SkillRequirementsTableModel skillModel = new SkillRequirementsTableModel();
  private final ResultsTableModel resultsModel = new ResultsTableModel(List.of());

  private static final int ARMOR_MIN = 0;
  private static final int ARMOR_MAX = 1;
  private static final int DECO_MIN = 2;
  private static final int DECO_MAX = 3;
  private static final int AMULET_MIN = 4;
  private static final int AMULET_MAX = 5;
  private static final int WEAPON_MIN = 6;
  private static final int WEAPON_MAX = 7;
  private static final int RARITY_FIELD_COUNT = 8;

  private final JSpinner[] raritySpinners = createRaritySpinners();
  private final String[] rarityLabels = {
    "Armor min",
    "Armor max",
    "Deco min",
    "Deco max",
    "Amulet min",
    "Amulet max",
    "Weapon min",
    "Weapon max",
  };
  private final JSpinner topNSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, 100_000, 1));
  private final JSpinner showSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100_000, 1));
  private final JComboBox<String> rankingCombo = new JComboBox<>(new String[] {
    RankingFactory.FREE_SLOTS,
    RankingFactory.FREE_EQUIPMENT_SLOTS,
  });
  private final JSpinner equipmentBonusSpinner = new JSpinner(
    new SpinnerNumberModel(1000, 0, 1_000_000, 100)
  );
  private final JComboBox<String> weaponCombo;
  private final JCheckBox transcendedBox = new JCheckBox(
    "Transcended armor (max level, extra defense + deco slots)"
  );
  private final JCheckBox gogmaziosBox = new JCheckBox(
    "Gogmazios weapons only (with bonus skill rolls)"
  );
  private final JCheckBox appraisedBox = new JCheckBox("Use appraised talismans");
  private final JButton manageTalismansButton = new JButton("Manage appraised talismans…");

  private final JButton solveButton = new JButton("Solve");
  private final JTextArea detailArea = new JTextArea();
  private final JLabel statusLabel = new JLabel("Ready");

  private Map<Integer, Skill> lastSkillMap = Map.of();
  private String lastRanking = RankingFactory.FREE_SLOTS;
  private long lastBonus;

  public OptimizerWindow(GameData data, GearPoolConfig config) {
    super("MH Wilds Loadout Optimizer");

    this.data = data;
    this.service = new SolverService(data);
    this.baseConfig = config;
    this.talismanStore = AppraisedTalismanStore.loadDefault(data);
    this.weaponCombo = new JComboBox<>(
      data
        .weapons()
        .stream()
        .map(Weapon::kind)
        .distinct()
        .sorted()
        .toArray(String[]::new)
    );

    if (config.weaponType() != null) {
      weaponCombo.setSelectedItem(config.weaponType());
    }

    if (config.ranking() != null) {
      rankingCombo.setSelectedItem(config.ranking());
    }

    Long bonus = config.equipmentSlotBonus();
    equipmentBonusSpinner.setValue(bonus != null ? Math.max(0, bonus) : 1000);
    equipmentBonusSpinner.setEnabled(RankingFactory.FREE_EQUIPMENT_SLOTS.equals(config.ranking()));
    transcendedBox.setSelected(config.armor().transcended());
    gogmaziosBox.setSelected(Boolean.TRUE.equals(config.gogmaziosSkills()));
    appraisedBox.setSelected(talismanStore.isEnabled());
    appraisedBox.addActionListener(e -> {
      talismanStore.setEnabled(appraisedBox.isSelected());

      try {
        talismanStore.saveDefault();
      } catch (RuntimeException ex) {
        status("Could not save " + AppraisedTalismanStore.DEFAULT_FILE_NAME, true);
      }
    });
    manageTalismansButton.addActionListener(e -> manageTalismans());

    seedSkillsFrom(config);

    seedRarityFrom(config);

    initComponents();
  }

  private static JSpinner[] createRaritySpinners() {
    JSpinner[] spinners = new JSpinner[RARITY_FIELD_COUNT];

    for (int i = 0; i < spinners.length; i++) {
      spinners[i] = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
    }

    return spinners;
  }

  private static void addOptionRow(JPanel panel, int row, String label, JComponent component) {
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = row;
    gc.anchor = GridBagConstraints.WEST;
    gc.insets = new Insets(2, 0, 2, 8);
    gc.gridx = 0;
    panel.add(new JLabel(label), gc);
    gc.gridx = 1;
    panel.add(component, gc);
  }

  private static void addFullWidthOptionRow(JPanel panel, int row, JComponent component) {
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridy = row;
    gc.gridx = 0;
    gc.gridwidth = 2;
    gc.anchor = GridBagConstraints.WEST;
    gc.insets = new Insets(2, 0, 2, 8);
    panel.add(component, gc);
  }

  private void seedSkillsFrom(GearPoolConfig config) {
    clearSkillRows();

    for (Map.Entry<String, Integer> entry : config.requiredSkills().entrySet()) {
      skillModel.addRow();
      int row = skillModel.getRowCount() - 1;
      skillModel.setValueAt(displayName(entry.getKey()), row, 0);
      skillModel.setValueAt(entry.getValue(), row, 1);
    }
  }

  private String displayName(String key) {
    try {
      Skill skill = data.skills().get(Integer.parseInt(key.trim()));

      if (skill != null) {
        return skill.name();
      }
    } catch (NumberFormatException ignored) {}
    return key;
  }

  private void clearSkillRows() {
    while (skillModel.getRowCount() > 0) {
      skillModel.removeRow(0);
    }
  }

  private void seedRarityFrom(GearPoolConfig config) {
    Integer[] values = new Integer[RARITY_FIELD_COUNT];
    values[ARMOR_MIN] = config.armor().minRarity();
    values[ARMOR_MAX] = config.armor().maxRarity();
    values[DECO_MIN] = config.decorations().minRarity();
    values[DECO_MAX] = config.decorations().maxRarity();
    values[AMULET_MIN] = config.amulets().minRarity();
    values[AMULET_MAX] = config.amulets().maxRarity();
    values[WEAPON_MIN] = config.weapons().minRarity();
    values[WEAPON_MAX] = config.weapons().maxRarity();

    for (int i = 0; i < raritySpinners.length; i++) {
      raritySpinners[i].setValue(values[i] != null ? values[i] : 0);
    }
  }

  private void initComponents() {
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLayout(new BorderLayout());

    initMenuBar();

    initLeftPanel();

    JPanel rightPanel = initRightPanel();

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel(), rightPanel);
    split.setResizeWeight(0.35);
    split.setDividerLocation(380);

    add(split, BorderLayout.CENTER);

    statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    add(statusLabel, BorderLayout.SOUTH);

    setSize(1280, 800);
  }

  private void initMenuBar() {
    JMenuBar menuBar = new JMenuBar();
    JMenu file = new JMenu("File");
    JMenuItem load = new JMenuItem("Load Config…");
    load.addActionListener(e -> loadConfig());
    JMenuItem save = new JMenuItem("Save Config…");
    save.addActionListener(e -> saveConfig());
    JMenuItem exit = new JMenuItem("Exit");
    exit.addActionListener(e -> dispose());
    file.add(load);
    file.add(save);
    file.addSeparator();
    file.add(exit);
    menuBar.add(file);
    setJMenuBar(menuBar);
  }

  private JPanel leftPanel;

  private void initLeftPanel() {
    leftPanel = new JPanel(new BorderLayout(8, 8));
    leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JPanel skillPanel = new JPanel(new BorderLayout(0, 4));
    skillPanel.setBorder(BorderFactory.createTitledBorder("Required skills"));
    JTable skillTable = new JTable(skillModel);
    skillTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    JScrollPane skillScroll = new JScrollPane(skillTable);
    skillScroll.setPreferredSize(new java.awt.Dimension(320, 180));
    skillPanel.add(skillScroll, BorderLayout.CENTER);

    JButton addButton = new JButton("+");
    addButton.setToolTipText("Add skill requirement");
    addButton.addActionListener(e -> skillModel.addRow());
    JButton removeButton = new JButton("−");
    removeButton.setToolTipText("Remove selected skill requirement");
    removeButton.addActionListener(e -> {
      int row = skillTable.getSelectedRow();
      if (row >= 0) {
        skillModel.removeRow(row);
      }
    });
    JPanel skillButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    skillButtons.add(addButton);
    skillButtons.add(removeButton);
    skillPanel.add(skillButtons, BorderLayout.SOUTH);

    JPanel rarityPanel = new JPanel(new GridLayout(4, 4, 8, 4));
    rarityPanel.setBorder(BorderFactory.createTitledBorder("Rarity bounds (0 = unset)"));
    for (int i = 0; i < raritySpinners.length; i++) {
      rarityPanel.add(new JLabel(rarityLabels[i]));
      rarityPanel.add(raritySpinners[i]);
    }

    JPanel optionsPanel = new JPanel(new GridBagLayout());
    optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
    addOptionRow(optionsPanel, 0, "Weapon type:", weaponCombo);
    addOptionRow(optionsPanel, 1, "Compute top N:", topNSpinner);
    addOptionRow(optionsPanel, 2, "Show top M:", showSpinner);
    addOptionRow(optionsPanel, 3, "Ranking:", rankingCombo);
    addOptionRow(optionsPanel, 4, "Equipment slot bonus:", equipmentBonusSpinner);
    addFullWidthOptionRow(optionsPanel, 5, transcendedBox);
    addFullWidthOptionRow(optionsPanel, 6, gogmaziosBox);
    addFullWidthOptionRow(optionsPanel, 7, appraisedBox);
    addFullWidthOptionRow(optionsPanel, 8, manageTalismansButton);

    rankingCombo.addActionListener(e ->
      equipmentBonusSpinner.setEnabled(
        RankingFactory.FREE_EQUIPMENT_SLOTS.equals(rankingCombo.getSelectedItem())
      )
    );

    solveButton.addActionListener(e -> solve());

    leftPanel.add(skillPanel, BorderLayout.NORTH);

    JPanel middle = new JPanel(new BorderLayout(0, 8));
    middle.add(rarityPanel, BorderLayout.NORTH);
    middle.add(optionsPanel, BorderLayout.CENTER);

    leftPanel.add(middle, BorderLayout.CENTER);

    leftPanel.add(solveButton, BorderLayout.SOUTH);
  }

  private JPanel leftPanel() {
    return leftPanel;
  }

  private JPanel initRightPanel() {
    JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
    rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

    JTable resultsTable = new JTable(resultsModel);
    resultsTable.setFillsViewportHeight(true);
    resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    resultsTable.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      int row = resultsTable.getSelectedRow();
      detailArea.setText(
        row >= 0
          ? BuildDetailFormatter.detailText(
              resultsModel.buildAt(row),
              BuildScore.of(resultsModel.buildAt(row), lastRanking, lastBonus),
              lastSkillMap,
              data.setSkillRanks()
            )
          : ""
      );
    });

    detailArea.setEditable(false);
    detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    detailArea.setLineWrap(false);
    DefaultCaret caret = (DefaultCaret) detailArea.getCaret();
    caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    JScrollPane detailScroll = new JScrollPane(detailArea);
    detailScroll.setBorder(BorderFactory.createTitledBorder("Detail"));

    JButton hubButton = new JButton("Open in MH Wilds Hub");
    hubButton.setToolTipText("Open the selected build in the mhwildshub builder");
    hubButton.setEnabled(false);
    hubButton.addActionListener(e -> {
      int row = resultsTable.getSelectedRow();

      if (row < 0) {
        return;
      }

      openInHub(resultsModel.buildAt(row));
    });
    resultsTable.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        hubButton.setEnabled(resultsTable.getSelectedRow() >= 0);
      }
    });
    JPanel hubPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    hubPanel.add(hubButton);

    rightPanel.add(hubPanel, BorderLayout.NORTH);

    JSplitPane verticalSplit = new JSplitPane(
      JSplitPane.VERTICAL_SPLIT,
      new JScrollPane(resultsTable),
      detailScroll
    );
    verticalSplit.setResizeWeight(0.7);
    verticalSplit.setDividerLocation(400);
    verticalSplit.setOneTouchExpandable(true);

    rightPanel.add(verticalSplit, BorderLayout.CENTER);

    return rightPanel;
  }

  private void solve() {
    List<SkillRequirement> requirements = skillModel.requirements();
    RarityBounds bounds = rarityBounds();
    List<String> errors = service.validate(requirements, bounds);
    if (!errors.isEmpty()) {
      status(errors.get(0), true);
      return;
    }

    GearPoolConfig config = service.buildConfig(
      baseConfig,
      String.valueOf(weaponCombo.getSelectedItem()),
      requirements,
      bounds,
      String.valueOf(rankingCombo.getSelectedItem()),
      ((Number) equipmentBonusSpinner.getValue()).longValue(),
      transcendedBox.isSelected(),
      gogmaziosBox.isSelected()
    );

    int computeTopN = (Integer) topNSpinner.getValue();
    int showTopN = (Integer) showSpinner.getValue();
    solveButton.setEnabled(false);

    lastRanking = String.valueOf(rankingCombo.getSelectedItem());
    lastBonus = ((Number) equipmentBonusSpinner.getValue()).longValue();
    resultsModel.setScoreFunc(b -> BuildScore.of(b, lastRanking, lastBonus).total());
    detailArea.setText("");

    List<AmuletRank> extraAmulets = appraisedBox.isSelected()
      ? talismanStore.toAmuletRanks()
      : List.of();

    status("Solving…", false);

    new SolveWorker(service, config, computeTopN, showTopN, extraAmulets).execute();
  }

  private RarityBounds rarityBounds() {
    Integer[] values = new Integer[RARITY_FIELD_COUNT];

    for (int i = 0; i < raritySpinners.length; i++) {
      int v = (Integer) raritySpinners[i].getValue();
      values[i] = v == 0 ? null : v;
    }

    return new RarityBounds(
      values[ARMOR_MIN],
      values[ARMOR_MAX],
      values[DECO_MIN],
      values[DECO_MAX],
      values[AMULET_MIN],
      values[AMULET_MAX],
      values[WEAPON_MIN],
      values[WEAPON_MAX]
    );
  }

  private String chooseConfigFile(boolean save) {
    JFileChooser fc = new JFileChooser();
    File configsDir = new File("configs");

    if (configsDir.isDirectory()) {
      fc.setCurrentDirectory(configsDir);
    }

    int result = save ? fc.showSaveDialog(this) : fc.showOpenDialog(this);

    return result == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().toString() : null;
  }

  private void loadConfig() {
    String path = chooseConfigFile(false);

    if (path == null) {
      return;
    }

    try {
      GearPoolConfig loaded = GearPoolConfig.load(path);
      baseConfig = loaded;
      seedSkillsFrom(loaded);
      seedRarityFrom(loaded);

      if (loaded.weaponType() != null) {
        weaponCombo.setSelectedItem(loaded.weaponType());
      }

      transcendedBox.setSelected(loaded.armor().transcended());
      gogmaziosBox.setSelected(Boolean.TRUE.equals(loaded.gogmaziosSkills()));

      resultsModel.setBuilds(List.of());
      detailArea.setText("");
      status("Loaded " + new File(path).getName(), false);
    } catch (RuntimeException e) {
      status("Load failed: " + e.getMessage(), true);
    }
  }

  private void saveConfig() {
    String path = chooseConfigFile(true);

    if (path == null) {
      return;
    }

    try {
      GearPoolConfig out = service.buildConfig(
        baseConfig,
        String.valueOf(weaponCombo.getSelectedItem()),
        skillModel.requirements(),
        rarityBounds(),
        String.valueOf(rankingCombo.getSelectedItem()),
        ((Number) equipmentBonusSpinner.getValue()).longValue(),
        transcendedBox.isSelected(),
        gogmaziosBox.isSelected()
      );
      out.save(path);
      status("Saved " + new File(path).getName(), false);
    } catch (RuntimeException e) {
      status("Save failed: " + e.getMessage(), true);
    }
  }

  private void manageTalismans() {
    boolean saved = AppraisedTalismanListDialog.showDialog(this, data, talismanStore);

    if (saved) {
      status(
        "Saved " +
          talismanStore.talismans().size() +
          " appraised talisman(s) to " +
          AppraisedTalismanStore.DEFAULT_FILE_NAME,
        false
      );
    }
  }

  private void status(String text, boolean error) {
    statusLabel.setText(text);
    statusLabel.setForeground(error ? Color.RED : new Color(0, 120, 0));
  }

  private void openInHub(Build build) {
    String url;

    try {
      url = MhWildsHubLink.shareUrl(build);
    } catch (IllegalStateException e) {
      status(e.getMessage(), true);

      return;
    }

    try {
      if (openWithDesktop(url) || openWithProcess(url)) {
        status("Opened build in MH Wilds Hub", false);
        return;
      }
    } catch (Exception ignored) {}

    status("Could not open a browser automatically; link: " + url, true);
  }

  private static boolean openWithDesktop(String url) throws Exception {
    if (!Desktop.isDesktopSupported()) {
      return false;
    }

    Desktop desktop = Desktop.getDesktop();

    if (!desktop.isSupported(Desktop.Action.BROWSE)) {
      return false;
    }

    desktop.browse(new URI(url));

    return true;
  }

  private static boolean openWithProcess(String url) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    if (os.contains("win")) {
      return launch(List.of("rundll32", "url.dll,FileProtocolHandler", url));
    }

    if (os.contains("mac")) {
      return launch(List.of("open", url));
    }

    for (String tool : List.of("xdg-open", "gnome-open", "kde-open")) {
      if (launch(List.of(tool, url))) {
        return true;
      }
    }

    return launch(List.of("gio", "open", url));
  }

  private static boolean launch(List<String> command) {
    try {
      Process process = new ProcessBuilder(command).start();
      return process.waitFor(3, TimeUnit.SECONDS) ? process.exitValue() == 0 : true;
    } catch (Exception e) {
      return false;
    }
  }

  private final class SolveWorker extends SwingWorker<SolverService.SolveResult, Void> {

    private final SolverService solverService;
    private final GearPoolConfig config;
    private final int computeTopN;
    private final int showTopN;
    private final List<AmuletRank> extraAmulets;

    SolveWorker(
      SolverService solverService,
      GearPoolConfig config,
      int computeTopN,
      int showTopN,
      List<AmuletRank> extraAmulets
    ) {
      this.solverService = solverService;
      this.config = config;
      this.computeTopN = computeTopN;
      this.showTopN = showTopN;
      this.extraAmulets = extraAmulets;
    }

    @Override
    protected SolverService.SolveResult doInBackground() {
      return solverService.solve(config, computeTopN, showTopN, extraAmulets);
    }

    @Override
    protected void done() {
      try {
        SolverService.SolveResult result = get();
        lastSkillMap = result.skillMap();

        resultsModel.setBuilds(result.builds());

        detailArea.setText("");

        status(
          "Found " +
            result.computedCount() +
            " builds, showing " +
            result.builds().size() +
            " in " +
            result.elapsedMillis() +
            " ms",
          false
        );
      } catch (Exception e) {
        status("Solve failed: " + e.getMessage(), true);
      } finally {
        solveButton.setEnabled(true);
      }
    }
  }
}
