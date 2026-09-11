package mhwilds.optimizer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.Weapon;
import mhwilds.optimizer.ranking.RankingFactory;

/** Main Swing window: edit requirements/rarity, solve, and browse ranked builds. */
public class OptimizerWindow extends JFrame {

  private final GameData data;
  private final SolverService service;
  private GearPoolConfig baseConfig;

  private final SkillRequirementsTableModel skillModel = new SkillRequirementsTableModel();
  private final ResultsTableModel resultsModel = new ResultsTableModel(List.of());

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

    seedSkillsFrom(config);

    seedRarityFrom(config);

    initComponents();
  }

  private static JSpinner[] createRaritySpinners() {
    JSpinner[] spinners = new JSpinner[8];

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
    } catch (NumberFormatException ignored) {
      // name key — pass through
    }
    return key;
  }

  private void clearSkillRows() {
    while (skillModel.getRowCount() > 0) {
      skillModel.removeRow(0);
    }
  }

  private void seedRarityFrom(GearPoolConfig config) {
    Integer[] values = {
      config.armor().minRarity(),
      config.armor().maxRarity(),
      config.decorations().minRarity(),
      config.decorations().maxRarity(),
      config.amulets().minRarity(),
      config.amulets().maxRarity(),
      config.weapons().minRarity(),
      config.weapons().maxRarity(),
    };

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
      ((Number) equipmentBonusSpinner.getValue()).longValue()
    );

    int computeTopN = (Integer) topNSpinner.getValue();
    int showTopN = (Integer) showSpinner.getValue();
    solveButton.setEnabled(false);

    lastRanking = String.valueOf(rankingCombo.getSelectedItem());
    lastBonus = ((Number) equipmentBonusSpinner.getValue()).longValue();
    resultsModel.setScoreFunc(b -> BuildScore.of(b, lastRanking, lastBonus).total());
    detailArea.setText("");

    status("Solving…", false);

    new SolveWorker(service, config, computeTopN, showTopN).execute();
  }

  private RarityBounds rarityBounds() {
    Integer[] values = new Integer[raritySpinners.length];

    for (int i = 0; i < raritySpinners.length; i++) {
      int v = (Integer) raritySpinners[i].getValue();
      values[i] = v == 0 ? null : v;
    }

    return new RarityBounds(
      values[0],
      values[1],
      values[2],
      values[3],
      values[4],
      values[5],
      values[6],
      values[7]
    );
  }

  private void loadConfig() {
    JFileChooser fc = new JFileChooser();
    File configsDir = new File("configs");

    if (configsDir.isDirectory()) {
      fc.setCurrentDirectory(configsDir);
    }

    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        GearPoolConfig loaded = GearPoolConfig.load(fc.getSelectedFile().toString());
        baseConfig = loaded;
        seedSkillsFrom(loaded);
        seedRarityFrom(loaded);

        if (loaded.weaponType() != null) {
          weaponCombo.setSelectedItem(loaded.weaponType());
        }

        resultsModel.setBuilds(List.of());
        detailArea.setText("");
        status("Loaded " + fc.getSelectedFile().getName(), false);
      } catch (RuntimeException e) {
        status("Load failed: " + e.getMessage(), true);
      }
    }
  }

  private void saveConfig() {
    JFileChooser fc = new JFileChooser();
    File configsDir = new File("configs");

    if (configsDir.isDirectory()) {
      fc.setCurrentDirectory(configsDir);
    }

    if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        GearPoolConfig out = service.buildConfig(
          baseConfig,
          String.valueOf(weaponCombo.getSelectedItem()),
          skillModel.requirements(),
          rarityBounds(),
          String.valueOf(rankingCombo.getSelectedItem()),
          ((Number) equipmentBonusSpinner.getValue()).longValue()
        );
        out.save(fc.getSelectedFile().toString());
        status("Saved " + fc.getSelectedFile().getName(), false);
      } catch (RuntimeException e) {
        status("Save failed: " + e.getMessage(), true);
      }
    }
  }

  private void status(String text, boolean error) {
    statusLabel.setText(text);
    statusLabel.setForeground(error ? Color.RED : new Color(0, 120, 0));
  }

  private final class SolveWorker extends SwingWorker<SolverService.SolveResult, Void> {

    private final SolverService solverService;
    private final GearPoolConfig config;
    private final int computeTopN;
    private final int showTopN;

    SolveWorker(SolverService solverService, GearPoolConfig config, int computeTopN, int showTopN) {
      this.solverService = solverService;
      this.config = config;
      this.computeTopN = computeTopN;
      this.showTopN = showTopN;
    }

    @Override
    protected SolverService.SolveResult doInBackground() {
      return solverService.solve(config, computeTopN, showTopN);
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
