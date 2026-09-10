package mhwilds.optimizer.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearPoolConfigRoundTripTest {

  @TempDir
  Path tmp;

  @Test
  void saveThenLoadPreservesEverythingFromDefaultConfig() throws Exception {
    GearPoolConfig original = GearPoolConfig.load("src/main/resources/default-config.json");
    Path out = tmp.resolve("saved.json");
    original.save(out.toString());

    GearPoolConfig loaded = GearPoolConfig.load(out.toString());
    assertThat(loaded).isEqualTo(original);
  }

  @Test
  void saveThenLoadPreservesRarityAndNameKeys() throws Exception {
    Path in = tmp.resolve("in.json");
    Files.writeString(
      in,
      """
      {
        "required_skills": { "constitution": 3, "burst": 1 },
        "weapon_type": "bow",
        "armor": { "exclude_sets": ["Orion"], "exclude_skills": [], "min_rarity": 5, "max_rarity": 8 },
        "decorations": { "exclude_ids": [1], "min_rarity": 4 },
        "amulets": { "exclude_families": [2], "max_rarity": 7 },
        "weapons": { "include_ids": [3], "min_rarity": 6, "max_rarity": 8 },
        "ignored_skills": [1050520384, 1160639488],
        "ranking": "free_slots"
      }
      """
    );
    GearPoolConfig original = GearPoolConfig.load(in.toString());
    Path out = tmp.resolve("saved.json");
    original.save(out.toString());

    GearPoolConfig loaded = GearPoolConfig.load(out.toString());
    assertThat(loaded).isEqualTo(original);
  }
}
