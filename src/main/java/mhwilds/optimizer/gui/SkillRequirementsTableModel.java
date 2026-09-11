package mhwilds.optimizer.gui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Editable two-column table: skill name (String) and minimum level (Integer). */
public class SkillRequirementsTableModel extends AbstractTableModel {

  private static final String[] COLUMNS = { "Skill", "Min level" };

  private final List<SkillRequirement> rows = new ArrayList<>();

  public void addRow() {
    rows.add(new SkillRequirement("constitution", 1));
    fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
  }

  public void removeRow(int index) {
    if (index < 0 || index >= rows.size()) {
      return;
    }

    rows.remove(index);
    fireTableRowsDeleted(index, index);
  }

  public List<SkillRequirement> requirements() {
    return List.copyOf(rows);
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMNS.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLUMNS[column];
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return column == 1 ? Integer.class : String.class;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return true;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    SkillRequirement r = rows.get(rowIndex);
    return columnIndex == 0 ? r.skillName() : r.minLevel();
  }

  @Override
  public void setValueAt(Object value, int rowIndex, int columnIndex) {
    SkillRequirement old = rows.get(rowIndex);

    try {
      if (columnIndex == 0) {
        rows.set(rowIndex, new SkillRequirement(String.valueOf(value), old.minLevel()));
      } else {
        rows.set(
          rowIndex,
          new SkillRequirement(old.skillName(), Integer.parseInt(String.valueOf(value)))
        );
      }
    } catch (IllegalArgumentException ignored) {
      return; // keep the previous value on bad input (non-numeric, blank name, minLevel < 1)
    }

    fireTableCellUpdated(rowIndex, columnIndex);
  }
}
