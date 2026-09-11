package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.Test;

class BuildScoreTest {

  private Build leanBuild() {
    return new Build(
      new ArmorPiece[] {
        new ArmorPiece(1, "Orion α", ArmorSlot.HEAD, new int[] { 3 }, Map.of(), 10, 20, 6),
        null,
        null,
        null,
        null,
      },
      List.of(),
      List.of(),
      null,
      null
    );
  }

  @Test
  void freeSlotsRankingShowsFreeSlotScore() {
    BuildScore score = BuildScore.of(leanBuild(), "free_slots", 3000L);

    assertThat(score.total()).isEqualTo(1000);
    assertThat(score.equipmentAware()).isFalse();
    assertThat(score.headline()).isEqualTo("Free-slot score: 1000");
  }

  @Test
  void equipmentRankingShowsAwardedScoreWithBreakdown() {
    Build build = leanBuild();
    BuildScore score = BuildScore.of(build, "free_equipment_slots", 3000L);

    assertThat(score.total()).isEqualTo(build.equipmentAwareScore(3000));
    assertThat(score.equipmentAware()).isTrue();
    assertThat(score.headline()).isEqualTo(
      "Equipment-slot score: 19000  (free slots 1000, 6 omitted × 3000 bonus)"
    );
  }
}
