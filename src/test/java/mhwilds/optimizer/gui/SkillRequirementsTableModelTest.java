package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkillRequirementsTableModelTest {

  @Test
  void startsEmpty() {
    SkillRequirementsTableModel m = new SkillRequirementsTableModel();
    assertThat(m.getRowCount()).isZero();
    assertThat(m.getColumnCount()).isEqualTo(2);
    assertThat(m.getColumnName(0)).isEqualTo("Skill");
    assertThat(m.getColumnName(1)).isEqualTo("Min level");
  }

  @Test
  void addRemoveRowsAndEditCells() {
    SkillRequirementsTableModel m = new SkillRequirementsTableModel();
    m.addRow();
    m.addRow();

    assertThat(m.getRowCount()).isEqualTo(2);

    m.setValueAt("constitution", 0, 0);
    m.setValueAt(3, 0, 1);
    m.setValueAt("burst", 1, 0);
    m.setValueAt(1, 1, 1);

    assertThat(m.requirements()).containsExactly(
      new SkillRequirement("constitution", 3),
      new SkillRequirement("burst", 1)
    );

    m.removeRow(0);
    assertThat(m.requirements()).containsExactly(new SkillRequirement("burst", 1));

    m.removeRow(0);
    assertThat(m.getRowCount()).isZero();
  }

  @Test
  void removeRowOnEmptyIsNoOp() {
    SkillRequirementsTableModel m = new SkillRequirementsTableModel();
    m.removeRow(0);
    assertThat(m.getRowCount()).isZero();
  }
}
