package mhwilds.optimizer.config;

import static org.assertj.core.api.Assertions.*;

import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import org.junit.jupiter.api.Test;

class WeaponIgnoredSkillSeedsTest {

  private final GameData data = new GameDataLoader("data").load();

  @Test
  void bowSeedPreservesLegacy27() {
    assertThat(WeaponIgnoredSkillSeeds.defaultOnClasspath().seedFor("bow")).hasSize(27);
  }

  @Test
  void everySeedIdExistsInSkillJson() {
    for (var entry : WeaponIgnoredSkillSeeds.defaultOnClasspath().all().entrySet()) {
      for (int id : entry.getValue()) {
        assertThat(data.skills()).as("kind %s seed id %d", entry.getKey(), id).containsKey(id);
      }
    }
  }

  @Test
  void everyKindInDataHasASeed() {
    WeaponIgnoredSkillSeeds seeds = WeaponIgnoredSkillSeeds.defaultOnClasspath();

    for (String kind : data
      .weapons()
      .stream()
      .map(w -> w.kind())
      .distinct()
      .toList()) {
      assertThat(seeds.all()).as("kind %s has seed", kind).containsKey(kind);
    }
  }

  @Test
  void unknownKindReturnsEmpty() {
    assertThat(WeaponIgnoredSkillSeeds.defaultOnClasspath().seedFor("nonexistent-kind")).isEmpty();
  }
}
