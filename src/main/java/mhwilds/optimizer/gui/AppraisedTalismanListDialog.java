package mhwilds.optimizer.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.model.AppraisedTalisman;
import mhwilds.optimizer.model.Skill;

public final class AppraisedTalismanListDialog extends JDialog {

  private final Frame parent;
  private final GameData data;
  private final AppraisedTalismanStore store;

  private final DefaultListModel<AppraisedTalisman> model = new DefaultListModel<>();
  private final JList<AppraisedTalisman> list = new JList<>(model);

  private boolean saved;

  private AppraisedTalismanListDialog(Frame parent, GameData data, AppraisedTalismanStore store) {
    super(parent, "Appraised talismans", true);
    this.parent = parent;
    this.data = data;
    this.store = store;

    for (AppraisedTalisman talisman : store.talismans()) {
      model.addElement(talisman);
    }

    initComponents();
  }

  public static boolean showDialog(Frame parent, GameData data, AppraisedTalismanStore store) {
    AppraisedTalismanListDialog dialog = new AppraisedTalismanListDialog(parent, data, store);
    dialog.setVisible(true);

    return dialog.saved;
  }

  private void initComponents() {
    setLayout(new BorderLayout(8, 8));

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new TalismanRenderer());
    add(new JScrollPane(list), BorderLayout.CENTER);
    list.setVisibleRowCount(10);

    JButton addButton = new JButton("Add…");
    addButton.addActionListener(e -> onAdd());
    JButton editButton = new JButton("Edit…");
    editButton.addActionListener(e -> onEdit());
    JButton removeButton = new JButton("Remove");
    removeButton.addActionListener(e -> onRemove());

    list.addMouseListener(
      new java.awt.event.MouseAdapter() {
        @Override
        public void mouseClicked(java.awt.event.MouseEvent e) {
          if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0) {
            onEdit();
          }
        }
      }
    );

    JPanel left = new JPanel();
    left.add(addButton);
    left.add(editButton);
    left.add(removeButton);
    add(left, BorderLayout.NORTH);

    JButton ok = new JButton("OK");
    ok.addActionListener(e -> onOk());
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(e -> dispose());

    JPanel buttons = new JPanel();
    buttons.add(ok);
    buttons.add(cancel);
    add(buttons, BorderLayout.SOUTH);

    getRootPane().setDefaultButton(ok);
    setSize(560, 420);
    setLocationRelativeTo(getParent());
  }

  private void onAdd() {
    AppraisedTalisman created = AppraisedTalismanDialog.showDialog(parent, data, null);

    if (created != null) {
      model.addElement(created);
      list.setSelectedIndex(model.size() - 1);
    }
  }

  private void onEdit() {
    int index = list.getSelectedIndex();

    if (index < 0) {
      return;
    }

    AppraisedTalisman updated = AppraisedTalismanDialog.showDialog(parent, data, model.get(index));

    if (updated != null) {
      model.set(index, updated);
    }
  }

  private void onRemove() {
    int index = list.getSelectedIndex();

    if (index < 0) {
      return;
    }

    int answer = JOptionPane.showConfirmDialog(
      this,
      "Remove '" + model.get(index).name() + "'?",
      "Remove talisman",
      JOptionPane.YES_NO_OPTION
    );

    if (answer == JOptionPane.YES_OPTION) {
      model.remove(index);
    }
  }

  private void onOk() {
    List<AppraisedTalisman> updated = new ArrayList<>();

    for (int i = 0; i < model.size(); i++) {
      updated.add(model.get(i));
    }

    store.clear();

    for (AppraisedTalisman talisman : updated) {
      store.add(talisman);
    }

    try {
      store.saveDefault();
    } catch (RuntimeException e) {
      JOptionPane.showMessageDialog(
        this,
        "Could not save " + AppraisedTalismanStore.DEFAULT_FILE_NAME + ": " + e.getMessage(),
        "Save failed",
        JOptionPane.ERROR_MESSAGE
      );

      return;
    }

    saved = true;
    dispose();
  }

  private final class TalismanRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(
      JList<?> list,
      Object value,
      int index,
      boolean isSelected,
      boolean cellHasFocus
    ) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof AppraisedTalisman talisman) {
        setText(describe(talisman));
      }

      return this;
    }

    private String describe(AppraisedTalisman talisman) {
      String skills = talisman
        .skills()
        .entrySet()
        .stream()
        .sorted(Comparator.comparing(entry -> skillName(entry.getKey())))
        .map(entry -> skillName(entry.getKey()) + " Lv" + entry.getValue())
        .collect(Collectors.joining(", "));

      return (
        talisman.name() +
        " [R" +
        talisman.rarity() +
        "] " +
        skills +
        " · A:" +
        slotList(talisman.armorSlots()) +
        " W:" +
        slotList(talisman.weaponSlots())
      );
    }

    private String slotList(int[] slots) {
      if (slots.length == 0) {
        return "—";
      }

      return java.util.Arrays.stream(slots)
        .mapToObj(String::valueOf)
        .collect(Collectors.joining(","));
    }

    private String skillName(int skillId) {
      Skill skill = data.skills().get(skillId);

      return skill != null ? skill.name() : String.valueOf(skillId);
    }
  }
}
