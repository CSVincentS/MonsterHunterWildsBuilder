package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.Test;

class ResultsTableModelTest {

  private ArmorPiece piece(ArmorSlot slot, String setName) {
    return new ArmorPiece(1, setName, slot, new int[] { 2 }, Map.of(), 10, 20, 6);
  }

  private Build build(String headSet) {
    return new Build(
      new ArmorPiece[] { piece(ArmorSlot.HEAD, headSet), null, null, null, null },
      List.of(),
      List.of(),
      new AmuletRank(1, 1, "Sheathe Charm I", Map.of()),
      new Weapon(2, "bow", "Calamitous Angel", 100, 0, new int[] { 2 }, Map.of(), List.of(), 7)
    );
  }

  @Test
  void exposesNineColumns() {
    ResultsTableModel m = new ResultsTableModel(List.of());

    assertThat(m.getColumnCount()).isEqualTo(9);
    assertThat(m.getColumnName(2)).isEqualTo("Head");
    assertThat(m.getColumnName(7)).isEqualTo("Amulet");
    assertThat(m.getColumnName(8)).isEqualTo("Weapon");
  }

  @Test
  void rendersArmorNamesAndSummaryColumns() {
    ResultsTableModel m = new ResultsTableModel(List.of(build("Orion α")));

    assertThat(m.getRowCount()).isEqualTo(1);
    assertThat(m.getValueAt(0, 0)).isEqualTo(1);
    assertThat(m.getValueAt(0, 1)).isEqualTo("200");
    assertThat(m.getValueAt(0, 2)).isEqualTo("Orion α");
    assertThat(m.getValueAt(0, 7)).isEqualTo("Sheathe Charm I");
    assertThat(m.getValueAt(0, 8)).isEqualTo("Calamitous Angel");
  }

  @Test
  void scoreColumnRespectsConfiguredMetric() {
    Build b = build("Orion α");
    ResultsTableModel m = new ResultsTableModel(List.of(b));

    assertThat(m.getValueAt(0, 1)).isEqualTo("200");

    m.setScoreFunc(v -> v.equipmentAwareScore(1000));
    assertThat(m.getValueAt(0, 1)).isEqualTo(String.valueOf(b.equipmentAwareScore(1000)));
  }
}
