package mhwilds.optimizer.gui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.model.AppraisedTalisman;
import mhwilds.optimizer.model.Skill;

public final class AppraisedTalismanDialog extends JDialog {

  private static final String NONE = "(none)";
  private static final int SKILL_ROWS = 3;
  private static final int ARMOR_SLOT_INPUTS = 3;

  private final GameData data;
  private final List<Integer> skillIds;

  private final JTextField nameField = new JTextField(22);
  private final JSpinner raritySpinner = new JSpinner(
    new SpinnerNumberModel(
      AppraisedTalisman.MAX_RARITY,
      AppraisedTalisman.MIN_RARITY,
      AppraisedTalisman.MAX_RARITY,
      1
    )
  );
  private final List<JComboBox<String>> skillCombos = new ArrayList<>();
  private final List<JSpinner> levelSpinners = new ArrayList<>();
  private final List<JSpinner> armorSlotSpinners = new ArrayList<>();
  private final JSpinner weaponSlotSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));

  private AppraisedTalisman result;

  private AppraisedTalismanDialog(Frame parent, GameData data, AppraisedTalisman existing) {
    super(parent, existing == null ? "Add appraised talisman" : "Edit appraised talisman", true);
    this.data = data;
    this.skillIds = data
      .skills()
      .values()
      .stream()
      .sorted(Comparator.comparing(Skill::name))
      .map(Skill::gameId)
      .toList();

    initComponents(existing);
  }

  public static AppraisedTalisman showDialog(
    Frame parent,
    GameData data,
    AppraisedTalisman existing
  ) {
    AppraisedTalismanDialog dialog = new AppraisedTalismanDialog(parent, data, existing);
    dialog.setVisible(true);

    return dialog.result;
  }

  private void initComponents(AppraisedTalisman existing) {
    setLayout(new BorderLayout(8, 8));

    JPanel form = new JPanel(new GridBagLayout());
    int row = 0;

    form.add(new JLabel("Name:"), gc(0, row));
    form.add(nameField, gc(1, row));
    row++;

    form.add(new JLabel("Rarity (5-8):"), gc(0, row));
    form.add(raritySpinner, gc(1, row));
    row++;

    for (int i = 0; i < SKILL_ROWS; i++) {
      JComboBox<String> combo = new JComboBox<>(skillNames());
      JSpinner levels = new JSpinner(new SpinnerNumberModel(1, 1, 7, 1));
      combo.addActionListener(e -> syncLevelMax(combo, levels));
      skillCombos.add(combo);
      levelSpinners.add(levels);

      JPanel skillRow = new JPanel(new BorderLayout(4, 0));
      skillRow.add(combo, BorderLayout.CENTER);
      skillRow.add(levels, BorderLayout.EAST);

      form.add(new JLabel("Skill " + (i + 1) + ":"), gc(0, row));
      form.add(skillRow, gc(1, row));
      row++;
    }

    JPanel armorSlots = new JPanel();

    for (int i = 0; i < ARMOR_SLOT_INPUTS; i++) {
      JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 3, 1));
      armorSlotSpinners.add(spinner);
      armorSlots.add(new JLabel("S" + (i + 1) + ":"));
      armorSlots.add(spinner);
    }

    form.add(new JLabel("Armor slots (0 = none):"), gc(0, row));
    form.add(armorSlots, gc(1, row));
    row++;

    JPanel weaponSlots = new JPanel();
    weaponSlots.add(new JLabel("W1:"));
    weaponSlots.add(weaponSlotSpinner);
    form.add(new JLabel("Weapon slot (0 = none):"), gc(0, row));
    form.add(weaponSlots, gc(1, row));

    add(form, BorderLayout.CENTER);

    JButton ok = new JButton("OK");
    ok.addActionListener(e -> onOk());
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(e -> {
      result = null;
      dispose();
    });

    JPanel buttons = new JPanel();
    buttons.add(ok);
    buttons.add(cancel);
    add(buttons, BorderLayout.SOUTH);

    if (existing != null) {
      fillFrom(existing);
    }

    getRootPane().setDefaultButton(ok);
    pack();
    setLocationRelativeTo(getParent());
  }

  private String[] skillNames() {
    List<String> names = new ArrayList<>();
    names.add(NONE);

    for (int id : skillIds) {
      names.add(data.skills().get(id).name());
    }

    return names.toArray(String[]::new);
  }

  private void syncLevelMax(JComboBox<String> combo, JSpinner levels) {
    Integer id = selectedSkillId(combo);

    if (id == null) {
      return;
    }

    Skill skill = data.skills().get(id);
    SpinnerNumberModel model = (SpinnerNumberModel) levels.getModel();
    model.setMaximum(skill.maxRank());

    if ((Integer) model.getValue() > skill.maxRank()) {
      model.setValue(skill.maxRank());
    }
  }

  private Integer selectedSkillId(JComboBox<String> combo) {
    int index = combo.getSelectedIndex();

    if (index <= 0 || index > skillIds.size()) {
      return null;
    }

    return skillIds.get(index - 1);
  }

  private void fillFrom(AppraisedTalisman existing) {
    nameField.setText(existing.name());
    raritySpinner.setValue(existing.rarity());

    List<Integer> ids = new ArrayList<>(existing.skills().keySet());
    ids.sort(Comparator.comparing(id -> data.skills().get(id).name()));

    for (int i = 0; i < SKILL_ROWS && i < ids.size(); i++) {
      int id = ids.get(i);
      int comboIndex = skillIds.indexOf(id) + 1;
      skillCombos.get(i).setSelectedIndex(comboIndex);
      syncLevelMax(skillCombos.get(i), levelSpinners.get(i));
      levelSpinners.get(i).setValue(existing.skills().get(id));
    }

    for (int i = 0; i < ARMOR_SLOT_INPUTS && i < existing.armorSlots().length; i++) {
      armorSlotSpinners.get(i).setValue(existing.armorSlots()[i]);
    }

    if (existing.weaponSlots().length > 0) {
      weaponSlotSpinner.setValue(existing.weaponSlots()[0]);
    }
  }

  private void onOk() {
    String name = nameField.getText().trim();

    if (name.isEmpty()) {
      error("Enter a name for the talisman.");

      return;
    }

    java.util.Map<Integer, Integer> skills = new java.util.LinkedHashMap<>();

    for (int i = 0; i < SKILL_ROWS; i++) {
      Integer id = selectedSkillId(skillCombos.get(i));

      if (id == null) {
        continue;
      }

      if (skills.containsKey(id)) {
        error("Skill '" + data.skills().get(id).name() + "' is listed twice.");

        return;
      }

      int level = (Integer) levelSpinners.get(i).getValue();
      int max = data.skills().get(id).maxRank();

      if (level < 1 || level > max) {
        error("Level for '" + data.skills().get(id).name() + "' must be 1.." + max + ".");

        return;
      }

      skills.put(id, level);
    }

    if (skills.isEmpty()) {
      error("Pick at least one skill.");

      return;
    }

    int[] armorSlots = new int[ARMOR_SLOT_INPUTS];

    for (int i = 0; i < ARMOR_SLOT_INPUTS; i++) {
      armorSlots[i] = (Integer) armorSlotSpinners.get(i).getValue();
    }

    int weaponSlot = (Integer) weaponSlotSpinner.getValue();
    int[] weaponSlots = weaponSlot == 0 ? new int[0] : new int[] { weaponSlot };

    try {
      result = new AppraisedTalisman(
        name,
        (Integer) raritySpinner.getValue(),
        skills,
        armorSlots,
        weaponSlots
      );
    } catch (IllegalArgumentException e) {
      error(e.getMessage());

      return;
    }

    dispose();
  }

  private void error(String message) {
    JOptionPane.showMessageDialog(this, message, "Invalid talisman", JOptionPane.ERROR_MESSAGE);
  }

  private static GridBagConstraints gc(int x, int y) {
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = x;
    gc.gridy = y;
    gc.anchor = GridBagConstraints.WEST;
    gc.insets = new Insets(2, 4, 2, 4);

    return gc;
  }
}
