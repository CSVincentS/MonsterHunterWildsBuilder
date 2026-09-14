package mhwilds.optimizer.gui;

import java.util.List;
import java.util.function.ToLongFunction;
import javax.swing.table.AbstractTableModel;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;

public class ResultsTableModel extends AbstractTableModel {

  private static final String[] COLUMNS = {
    "#",
    "Score",
    "Head",
    "Chest",
    "Arms",
    "Waist",
    "Legs",
    "Amulet",
    "Weapon",
  };

  private List<Build> builds;
  private ToLongFunction<Build> scoreFunc = Build::freeSlotScore;

  public ResultsTableModel(List<Build> builds) {
    this.builds = List.copyOf(builds);
  }

  public void setScoreFunc(ToLongFunction<Build> scoreFunc) {
    this.scoreFunc = scoreFunc;
    fireTableDataChanged();
  }

  public void setBuilds(List<Build> builds) {
    this.builds = List.copyOf(builds);
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return builds.size();
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
  public Object getValueAt(int rowIndex, int columnIndex) {
    Build b = builds.get(rowIndex);

    return switch (columnIndex) {
      case 0 -> rowIndex + 1;
      case 1 -> String.valueOf(scoreFunc.applyAsLong(b));
      case 2 -> armorName(b, ArmorSlot.HEAD);
      case 3 -> armorName(b, ArmorSlot.CHEST);
      case 4 -> armorName(b, ArmorSlot.ARMS);
      case 5 -> armorName(b, ArmorSlot.WAIST);
      case 6 -> armorName(b, ArmorSlot.LEGS);
      case 7 -> b.amuletRank() != null ? b.amuletRank().name() : "none";
      case 8 -> b.weapon() != null ? b.weapon().name() : "none";
      default -> "";
    };
  }

  public Build buildAt(int rowIndex) {
    return builds.get(rowIndex);
  }

  private static String armorName(Build b, ArmorSlot slot) {
    ArmorPiece piece = b.armorPiece(slot);

    return piece != null ? piece.setName() : "none";
  }
}
