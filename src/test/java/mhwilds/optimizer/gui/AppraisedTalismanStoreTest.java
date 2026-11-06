package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.AppraisedTalisman;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppraisedTalismanStoreTest {

  private static final int SKILL_A = -1689391744;
  private static final int SKILL_B = -315492576;

  private static GameData data() {
    return new GameDataLoader("data").load();
  }

  @Test
  void roundTripPreservesTalismansAndEnabledFlag(@TempDir Path dir) {
    AppraisedTalismanStore store = new AppraisedTalismanStore();
    store.setEnabled(false);
    store.add(
      new AppraisedTalisman(
        "Golden Charm",
        8,
        Map.of(SKILL_A, 2, SKILL_B, 1),
        new int[] { 1, 1 },
        new int[] { 1 }
      )
    );
    store.add(
      new AppraisedTalisman("Old Charm", 5, Map.of(SKILL_A, 1), new int[] { 3 }, new int[0])
    );

    Path file = dir.resolve("talismans.json");
    store.save(file);

    assertThat(Files.isRegularFile(file)).isTrue();

    AppraisedTalismanStore loaded = AppraisedTalismanStore.load(file, data());

    assertThat(loaded.isEnabled()).isFalse();
    assertThat(loaded.talismans()).hasSize(2);
    assertThat(loaded.talismans().get(0).name()).isEqualTo("Golden Charm");
    assertThat(loaded.talismans().get(0).rarity()).isEqualTo(8);
    assertThat(loaded.talismans().get(0).skills()).containsExactlyInAnyOrderEntriesOf(
      Map.of(SKILL_A, 2, SKILL_B, 1)
    );
    assertThat(loaded.talismans().get(0).armorSlots()).containsExactly(1, 1);
    assertThat(loaded.talismans().get(0).weaponSlots()).containsExactly(1);
    assertThat(loaded.talismans().get(1).armorSlots()).containsExactly(3);
  }

  @Test
  void invalidEntriesAreSkippedWithTheRestLoaded(@TempDir Path dir) throws Exception {
    String json = """
    {"enabled": true, "talismans": [
      {"name": "Good", "rarity": 8, "skills": {"%d": 1}, "armor_slots": [1], "weapon_slots": []},
      {"name": "Bad skill", "rarity": 8, "skills": {"999999999": 1}, "armor_slots": [], "weapon_slots": []},
      {"name": "Bad level", "rarity": 8, "skills": {"%d": 99}, "armor_slots": [], "weapon_slots": []},
      {"name": "", "rarity": 8, "skills": {"%d": 1}, "armor_slots": [], "weapon_slots": []}
    ]}
    """.formatted(SKILL_A, SKILL_B, SKILL_A);

    Path file = dir.resolve("talismans.json");
    Files.writeString(file, json);

    AppraisedTalismanStore loaded = AppraisedTalismanStore.load(file, data());

    assertThat(loaded.talismans()).hasSize(1);
    assertThat(loaded.talismans().get(0).name()).isEqualTo("Good");
  }

  @Test
  void missingFileLoadsEmptyStore(@TempDir Path dir) {
    AppraisedTalismanStore loaded = AppraisedTalismanStore.load(dir.resolve("absent.json"), data());

    assertThat(loaded.isEnabled()).isTrue();
    assertThat(loaded.talismans()).isEmpty();
  }

  @Test
  void toAmuletRanksAssignsStableFamilyIds() {
    AppraisedTalismanStore store = new AppraisedTalismanStore();
    store.add(new AppraisedTalisman("One", 8, Map.of(SKILL_A, 1), new int[] { 1 }, new int[0]));
    store.add(new AppraisedTalisman("Two", 6, Map.of(SKILL_B, 2), new int[0], new int[0]));

    List<AmuletRank> ranks = store.toAmuletRanks();

    assertThat(ranks).hasSize(2);
    assertThat(ranks.get(0).familyGameId()).isEqualTo(AppraisedTalisman.familyIdForIndex(0));
    assertThat(ranks.get(1).familyGameId()).isEqualTo(AppraisedTalisman.familyIdForIndex(1));
    assertThat(ranks.get(0).armorSlots()).containsExactly(1);
  }
}
