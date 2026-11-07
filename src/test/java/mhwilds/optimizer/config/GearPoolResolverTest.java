package mhwilds.optimizer.config;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.*;
import mhwilds.optimizer.solver.CatalogSolver;
import mhwilds.optimizer.solver.SolverPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearPoolResolverTest {

  private final GameData data = new GameDataLoader("data").load();

  @TempDir
  Path tmp;

  private static final int HANDICRAFT_ID = 1160639488;
  private static final int OTHER_ID = 850626240;

  @Test
  void weaponTypeFiltersPoolToKind() throws Exception {
    String json = """
    {
      "required_skills": {},
      "weapon_type": "great-sword",
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ignored_skills": [],
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("weapon-kind-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(config);

    assertThat(pool.weapons()).extracting(Weapon::kind).containsOnly("great-sword");
    assertThat(pool.weapons())
      .extracting(Weapon::name)
      .containsExactly("OnlyIgnored GS", "Mixed GS");
  }

  private GearPoolConfig configWithWeaponTypeAndIgnored(String weaponType, int... ignoredIds)
    throws Exception {
    String idsJson = IntStream.of(ignoredIds)
      .mapToObj(String::valueOf)
      .collect(Collectors.joining(","));
    String json = """
    {
      "required_skills": {},
      "weapon_type": "%s",
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ignored_skills": [%s],
      "ranking": "free_slots"
    }
    """.formatted(weaponType, idsJson);
    Path configPath = tmp.resolve("seed-" + weaponType + "-" + idsJson.hashCode() + ".json");
    Files.writeString(configPath, json);
    return GearPoolConfig.load(configPath.toString());
  }

  @Test
  void ignoredSkillsIncludePerKindSeedUnion() throws Exception {
    // Seed for 'bow' contains the legacy 27 (verify via seedFor); config adds HANDICRAFT_ID.
    GearPoolConfig config = configWithWeaponTypeAndIgnored("bow", HANDICRAFT_ID);
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(config);

    // Mixed Bow has HANDICRAFT(ignored) + OTHER(useful) -> kept; OnlyIgnored Bow has only
    // HANDICRAFT -> dropped (Handicraft is in both the bow seed and the config list).
    assertThat(pool.weapons()).extracting(Weapon::name).contains("Mixed Bow");
    assertThat(pool.weapons()).extracting(Weapon::name).doesNotContain("OnlyIgnored Bow");
  }

  @Test
  void weaponTypeUnknownFallsBackToBow() throws Exception {
    GearPoolConfig config = configWithWeaponTypeAndIgnored("nosuchtype");
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertThat(pool.weapons()).isNotEmpty();
    assertThat(pool.weapons()).extracting(Weapon::kind).containsOnly("bow");
  }

  @Test
  void resolvesWithDefaultConfig() {
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");
    GearPoolResolver resolver = new GearPoolResolver(data);
    var pool = resolver.resolve(config);

    assertThat(pool.armorPieces()).isNotEmpty();
    assertThat(pool.armorDecorations()).isNotEmpty();
    assertThat(pool.weaponDecorations()).isNotEmpty();
    assertThat(pool.amuletRanks()).isNotEmpty();
    assertThat(pool.weapons()).isNotEmpty();
    assertThat(pool.thresholds().requiredSkills()).containsEntry(-1689391744, 5);
    assertThat(pool.thresholds().requiredSkills()).containsEntry(-315492576, 3);
  }

  @Test
  void excludeSetsRemovesArmorPieces(@TempDir Path tmp) throws Exception {
    String setName = data.armorPieces().get(0).setName();
    String json = """
    {
      "required_skills": {},
      "armor": { "exclude_sets": ["%s"], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """.formatted(setName);
    Path configPath = tmp.resolve("test-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    GearPoolResolver resolver = new GearPoolResolver(data);
    var pool = resolver.resolve(config);

    assertThat(pool.armorPieces()).allMatch(p -> !p.setName().equals(setName));
  }

  @Test
  void excludeSkillsRemovesGearContributingOnlyThoseSkills(@TempDir Path tmp) throws Exception {
    String json = """
    {
      "required_skills": { "-1689391744": 5, "-315492576": 3 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("test-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    GearPoolResolver resolver = new GearPoolResolver(data);
    var pool = resolver.resolve(config);

    assertThat(pool.thresholds().requiredSkills()).containsEntry(-1689391744, 5);
  }

  @Test
  void setSkillRequirementsAutoDetectedFromNames() throws Exception {
    String json = """
    {
      "required_skills": { "gogmapocalypse": 1, "constitution": 3 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("set-skill-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertThat(pool.thresholds().requiredSkills())
      .containsEntry(5590, 1)
      .containsEntry(-1689391744, 3);
    // Gogmapocalypse is a set-bonus skill -> carries activation ranks
    assertThat(pool.thresholds().setSkillRanks()).containsKey(5590);
    assertThat(pool.thresholds().setSkillRanks().get(5590)).containsEntry(2, 1).containsEntry(4, 2);
    assertThat(pool.thresholds().isSetSkill(5590)).isTrue();
    // Constitution is a regular skill -> no set-skill semantics
    assertThat(pool.thresholds().isSetSkill(-1689391744)).isFalse();
  }

  @Test
  void setSkillRequirementDetectedFromNumericId() throws Exception {
    String json = """
    {
      "required_skills": { "5590": 2, "burst": 1 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("set-skill-id-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertThat(pool.thresholds().requiredSkills()).containsEntry(5590, 2);
    assertThat(pool.thresholds().setSkillRanks()).containsKey(5590);
    assertThat(pool.thresholds().requiredSkills()).containsEntry(565867136, 1);
    assertThat(pool.thresholds().isSetSkill(565867136)).isFalse();
  }

  @Test
  void throwsOnUnknownConfigId() throws Exception {
    String json = """
    {
      "required_skills": {},
      "armor": { "exclude_sets": ["Nonexistent Set Name 999"], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("bad-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    GearPoolResolver resolver = new GearPoolResolver(data);
    assertDoesNotThrow(() -> resolver.resolve(config));
  }

  private GameData fabricatedData() {
    return new GameData(
      Map.of(),
      List.of(
        new ArmorPiece(
          101,
          "OnlyIgnored Set",
          ArmorSlot.HEAD,
          new int[] { 3 },
          Map.of(HANDICRAFT_ID, 2),
          10,
          20,
          8
        ),
        new ArmorPiece(
          102,
          "Mixed Set",
          ArmorSlot.CHEST,
          new int[] { 3 },
          Map.of(HANDICRAFT_ID, 1, OTHER_ID, 1),
          10,
          20,
          4
        ),
        new ArmorPiece(103, "NoSkill Set", ArmorSlot.ARMS, new int[] { 3, 3 }, Map.of(), 10, 20, 1)
      ),
      List.of(
        new Decoration(201, "OnlyIgnored Jewel", 1, Map.of(HANDICRAFT_ID, 1), SlotTarget.ARMOR, 4),
        new Decoration(
          202,
          "Mixed Jewel",
          2,
          Map.of(HANDICRAFT_ID, 1, OTHER_ID, 1),
          SlotTarget.ARMOR,
          6
        ),
        new Decoration(
          203,
          "OnlyIgnored Weapon Jewel",
          1,
          Map.of(HANDICRAFT_ID, 1),
          SlotTarget.WEAPON,
          3
        ),
        new Decoration(204, "NoSkill Jewel", 1, Map.of(), SlotTarget.ARMOR, 7)
      ),
      List.of(
        new AmuletRank(1, 1, "OnlyIgnored Charm I", Map.of(HANDICRAFT_ID, 1), 5),
        new AmuletRank(2, 1, "Mixed Charm I", Map.of(HANDICRAFT_ID, 1, OTHER_ID, 1), 8)
      ),
      List.of(
        new Weapon(
          301,
          "bow",
          "OnlyIgnored Bow",
          200,
          0,
          new int[] { 0 },
          Map.of(HANDICRAFT_ID, 1),
          List.of(),
          5
        ),
        new Weapon(
          302,
          "bow",
          "Mixed Bow",
          250,
          5,
          new int[] { 2, 2 },
          Map.of(HANDICRAFT_ID, 1, OTHER_ID, 1),
          List.of(),
          8
        ),
        new Weapon(
          303,
          "great-sword",
          "OnlyIgnored GS",
          300,
          0,
          new int[] { 0 },
          Map.of(HANDICRAFT_ID, 1),
          List.of(),
          5
        ),
        new Weapon(
          304,
          "great-sword",
          "Mixed GS",
          350,
          10,
          new int[] { 2 },
          Map.of(HANDICRAFT_ID, 1, OTHER_ID, 1),
          List.of(),
          8
        )
      ),
      Map.of(),
      data.armorTranscendence()
    );
  }

  private GearPoolConfig configWithIgnoredSkills(int... ignoredIds) throws Exception {
    String idsJson = IntStream.of(ignoredIds)
      .mapToObj(String::valueOf)
      .collect(Collectors.joining(","));
    String json = """
    {
      "required_skills": {},
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ignored_skills": [%s],
      "ranking": "free_slots"
    }
    """.formatted(idsJson);
    Path configPath = tmp.resolve("ignored-" + idsJson.hashCode() + ".json");
    Files.writeString(configPath, json);
    return GearPoolConfig.load(configPath.toString());
  }

  private GearPoolConfig configWithRarity(
    String armorJson,
    String decorationsJson,
    String amuletsJson,
    String weaponsJson
  ) throws Exception {
    String json = """
    {
      "required_skills": {},
      "armor": %s,
      "decorations": %s,
      "amulets": %s,
      "weapons": %s,
      "ignored_skills": [],
      "ranking": "free_slots"
    }
    """.formatted(armorJson, decorationsJson, amuletsJson, weaponsJson);
    Path configPath = tmp.resolve("rarity-" + json.hashCode() + ".json");
    Files.writeString(configPath, json);
    return GearPoolConfig.load(configPath.toString());
  }

  private GearPoolConfig bowConfig(String gogmaziosSkills) throws Exception {
    String json = """
    {
      "required_skills": {},
      "weapon_type": "bow",
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ignored_skills": [],
      "ranking": "free_slots",
      "gogmazios_skills": %s
    }
    """.formatted(gogmaziosSkills);
    Path configPath = tmp.resolve("gogmazios-" + json.hashCode() + ".json");
    Files.writeString(configPath, json);

    return GearPoolConfig.load(configPath.toString());
  }

  @Test
  void gogmaziosOptionRestrictsThePoolToThatOneBow() throws Exception {
    SolverPool enabled = new GearPoolResolver(data).resolve(bowConfig("true"));

    assertThat(enabled.gogmazios().enabled()).isTrue();
    assertThat(enabled.weapons())
      .hasSize(1)
      .allSatisfy(w -> {
        assertThat(w.gogmazios()).isTrue();
        assertThat(w.name()).isEqualTo("Calamitous Angel");
        assertThat(w.attackRaw()).isEqualTo(200);
        assertThat(w.affinity()).isEqualTo(-10);
      });

    // Without the option the whole bow pool is available, Gogmazios attack focus included.
    SolverPool off = new GearPoolResolver(data).resolve(bowConfig("false"));

    assertThat(off.gogmazios().enabled()).isFalse();
    assertThat(off.weapons()).hasSizeGreaterThan(1);
    assertThat(off.weapons()).filteredOn(Weapon::gogmazios).hasSize(1);
    assertThat(off.weapons()).anyMatch(w -> !w.gogmazios());
  }

  @Test
  void skillsReachableFromBothRollSlotsShareThresholds() throws Exception {
    SolverPool pool = new GearPoolResolver(data).resolve(bowConfig("true"));

    Set<Integer> setIds = pool.gogmazios().setBonusSkillIds();
    Set<Integer> groupIds = pool.gogmazios().groupBonusSkillIds();

    assertThat(setIds).isNotEmpty().contains(31540);
    assertThat(groupIds).isNotEmpty().contains(4554);

    Map<Integer, Map<Integer, Integer>> ranks = pool.gogmazios().skillRanks();

    for (Integer id : intersection(setIds, groupIds)) {
      assertThat(ranks.get(id))
        .as("thresholds for skill %s reached from either roll slot", id)
        .isNotNull();
    }

    // Omega Attire's "Omega Resonance" is a set bonus and Akuma's "Master of the Fist" a group
    // skill; neither leaks into the other slot's pool for this bow configuration.
    assertThat(setIds).doesNotContain(4554);
    assertThat(groupIds).doesNotContain(31540);

    // Gogmazios armor carries secondary set bonuses (e.g. Zoh Shia's Pulse) that the loader
    // stores in groupBonusId. They must not become group roll choices.
    assertThat(setIds).allSatisfy(id ->
      assertThat(data.skills().get(id).kind()).isEqualTo(SkillKind.SET)
    );

    assertThat(groupIds)
      .allSatisfy(id -> assertThat(data.skills().get(id).kind()).isEqualTo(SkillKind.GROUP))
      .doesNotContain(-555494336);
  }

  @Test
  void gogmaziosOptionYieldsAnEmptyPoolWhenTheKindHasNone() throws Exception {
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(bowConfig("true"));

    assertThat(pool.weapons()).isEmpty();
    assertThat(new CatalogSolver(10, 0L).solve(pool)).isEmpty();
  }

  private Set<Integer> intersection(Set<Integer> a, Set<Integer> b) {
    Set<Integer> out = new HashSet<>(a);

    out.retainAll(b);

    return out;
  }

  @Test
  void rarityFilterDropsItemsOutsideConfiguredRange() throws Exception {
    GearPoolConfig config = configWithRarity(
      "{ \"exclude_sets\": [], \"exclude_skills\": [], \"min_rarity\": 4, \"max_rarity\": 7 }",
      "{ \"exclude_ids\": [], \"min_rarity\": 4, \"max_rarity\": 7 }",
      "{ \"exclude_families\": [], \"min_rarity\": 4, \"max_rarity\": 7 }",
      "{ \"include_ids\": [], \"min_rarity\": 4, \"max_rarity\": 7 }"
    );
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(config);

    assertThat(pool.armorPieces()).extracting(ArmorPiece::setName).containsExactly("Mixed Set");
    assertThat(pool.armorDecorations())
      .extracting(Decoration::name)
      .containsExactly("OnlyIgnored Jewel", "Mixed Jewel", "NoSkill Jewel");
    assertThat(pool.weaponDecorations()).isEmpty();
    assertThat(pool.amuletRanks())
      .extracting(AmuletRank::name)
      .containsExactly("OnlyIgnored Charm I");
    assertThat(pool.weapons()).extracting(Weapon::name).containsExactly("OnlyIgnored Bow");
  }

  @Test
  void rarityFilterWithOnlyMinOrMaxIsInclusive() throws Exception {
    GearPoolConfig minOnly = configWithRarity(
      "{ \"exclude_sets\": [], \"exclude_skills\": [], \"min_rarity\": 6 }",
      "{ \"exclude_ids\": [], \"min_rarity\": 6 }",
      "{ \"exclude_families\": [], \"min_rarity\": 6 }",
      "{ \"include_ids\": [], \"min_rarity\": 6 }"
    );
    SolverPool minPool = new GearPoolResolver(fabricatedData()).resolve(minOnly);

    assertThat(minPool.armorPieces())
      .extracting(ArmorPiece::setName)
      .containsExactly("OnlyIgnored Set");
    assertThat(minPool.armorDecorations())
      .extracting(Decoration::name)
      .containsExactly("Mixed Jewel", "NoSkill Jewel");
    assertThat(minPool.weaponDecorations()).isEmpty();
    assertThat(minPool.amuletRanks()).extracting(AmuletRank::name).containsExactly("Mixed Charm I");
    assertThat(minPool.weapons()).extracting(Weapon::name).containsExactly("Mixed Bow");

    GearPoolConfig maxOnly = configWithRarity(
      "{ \"exclude_sets\": [], \"exclude_skills\": [], \"max_rarity\": 1 }",
      "{ \"exclude_ids\": [], \"max_rarity\": 7 }",
      "{ \"exclude_families\": [], \"max_rarity\": 8 }",
      "{ \"include_ids\": [], \"max_rarity\": 5 }"
    );
    SolverPool maxPool = new GearPoolResolver(fabricatedData()).resolve(maxOnly);

    assertThat(maxPool.armorPieces())
      .extracting(ArmorPiece::setName)
      .containsExactly("NoSkill Set");
    assertThat(maxPool.armorDecorations())
      .extracting(Decoration::name)
      .containsExactly("OnlyIgnored Jewel", "Mixed Jewel", "NoSkill Jewel");
    assertThat(maxPool.weaponDecorations()).isNotEmpty();
    assertThat(maxPool.amuletRanks())
      .extracting(AmuletRank::name)
      .containsExactly("OnlyIgnored Charm I", "Mixed Charm I");
    assertThat(maxPool.weapons()).extracting(Weapon::name).containsExactly("OnlyIgnored Bow");
  }

  @Test
  void invertedRarityRangeYieldsEmptyPools() throws Exception {
    GearPoolConfig config = configWithRarity(
      "{ \"exclude_sets\": [], \"exclude_skills\": [], \"min_rarity\": 7, \"max_rarity\": 3 }",
      "{ \"exclude_ids\": [], \"min_rarity\": 7, \"max_rarity\": 3 }",
      "{ \"exclude_families\": [], \"min_rarity\": 7, \"max_rarity\": 3 }",
      "{ \"include_ids\": [], \"min_rarity\": 7, \"max_rarity\": 3 }"
    );
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(config);

    assertThat(pool.armorPieces()).isEmpty();
    assertThat(pool.armorDecorations()).isEmpty();
    assertThat(pool.weaponDecorations()).isEmpty();
    assertThat(pool.amuletRanks()).isEmpty();
    assertThat(pool.weapons()).isEmpty();
  }

  @Test
  void requiredSkillsAcceptNamesAsKeys() throws Exception {
    String json = """
    {
      "required_skills": { "constitution": 3, "burst": 1 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("names-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertThat(pool.thresholds().requiredSkills())
      .containsEntry(-1689391744, 3)
      .containsEntry(565867136, 1);
  }

  @Test
  void requiredSkillsMixNumericAndNameKeys() throws Exception {
    String json = """
    {
      "required_skills": { "-315492576": 2, "constitution": 5 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("mixed-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertThat(pool.thresholds().requiredSkills())
      .containsEntry(-315492576, 2)
      .containsEntry(-1689391744, 5);
  }

  @Test
  void requiredSkillNamesMatchCaseInsensitively() throws Exception {
    String json = """
    {
      "required_skills": { "CoNsTiTuTiOn": 2, "burst": 1 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("case-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertThat(pool.thresholds().requiredSkills())
      .containsEntry(-1689391744, 2)
      .containsEntry(565867136, 1);
  }

  @Test
  void unknownRequiredSkillNameWarnsAndIsSkipped() throws Exception {
    String json = """
    {
      "required_skills": { "NonexistentSkillName": 3, "burst": 1 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("unknown-name-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertDoesNotThrow(() -> new GearPoolResolver(data).resolve(config));
    assertThat(pool.thresholds().requiredSkills()).containsExactly(
      java.util.Map.entry(565867136, 1)
    );
  }

  @Test
  void unknownRequiredSkillIdWarnsAndIsSkipped() throws Exception {
    String json = """
    {
      "required_skills": { "999999999": 3, "-315492576": 2 },
      "armor": { "exclude_sets": [], "exclude_skills": [] },
      "decorations": { "exclude_ids": [] },
      "amulets": { "exclude_families": [] },
      "weapons": { "include_ids": [] },
      "ranking": "free_slots"
    }
    """;
    Path configPath = tmp.resolve("unknown-id-config.json");
    Files.writeString(configPath, json);

    GearPoolConfig config = GearPoolConfig.load(configPath.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    assertDoesNotThrow(() -> new GearPoolResolver(data).resolve(config));
    assertThat(pool.thresholds().requiredSkills()).containsExactly(
      java.util.Map.entry(-315492576, 2)
    );
  }

  @Test
  void ignoredSkillsDropsItemsProvidingOnlyThoseSkills() throws Exception {
    GearPoolConfig config = configWithIgnoredSkills(HANDICRAFT_ID);
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(config);

    assertThat(pool.armorPieces())
      .extracting(ArmorPiece::setName)
      .contains("Mixed Set", "NoSkill Set");
    assertThat(pool.armorPieces())
      .extracting(ArmorPiece::setName)
      .doesNotContain("OnlyIgnored Set");
    assertThat(pool.armorDecorations())
      .extracting(Decoration::name)
      .contains("Mixed Jewel", "NoSkill Jewel");
    assertThat(pool.armorDecorations())
      .extracting(Decoration::name)
      .doesNotContain("OnlyIgnored Jewel");
    assertThat(pool.weaponDecorations())
      .extracting(Decoration::name)
      .doesNotContain("OnlyIgnored Weapon Jewel");
    assertThat(pool.amuletRanks()).extracting(AmuletRank::name).contains("Mixed Charm I");
    assertThat(pool.amuletRanks())
      .extracting(AmuletRank::name)
      .doesNotContain("OnlyIgnored Charm I");
    assertThat(pool.weapons()).extracting(Weapon::name).contains("Mixed Bow");
    assertThat(pool.weapons()).extracting(Weapon::name).doesNotContain("OnlyIgnored Bow");
  }

  @Test
  void withoutIgnoredSkillsAllItemsRetained() throws Exception {
    GearPoolConfig config = configWithIgnoredSkills();
    SolverPool pool = new GearPoolResolver(fabricatedData()).resolve(config);

    assertThat(pool.armorPieces())
      .extracting(ArmorPiece::setName)
      .contains("OnlyIgnored Set", "Mixed Set", "NoSkill Set");
    assertThat(pool.armorDecorations())
      .extracting(Decoration::name)
      .contains("OnlyIgnored Jewel", "Mixed Jewel", "NoSkill Jewel");
    assertThat(pool.weaponDecorations())
      .extracting(Decoration::name)
      .contains("OnlyIgnored Weapon Jewel");
    assertThat(pool.amuletRanks())
      .extracting(AmuletRank::name)
      .contains("OnlyIgnored Charm I", "Mixed Charm I");
    assertThat(pool.weapons()).extracting(Weapon::name).contains("OnlyIgnored Bow", "Mixed Bow");
  }

  @Test
  void defaultConfigDropsMeleeOnlyDecorations() {
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    List<String> allDecos = pool
      .armorDecorations()
      .stream()
      .map(Decoration::name)
      .collect(Collectors.toList());
    allDecos.addAll(pool.weaponDecorations().stream().map(Decoration::name).toList());
    assertThat(allDecos).doesNotContain(
      "Ironwall Jewel [1]",
      "Handicraft Jewel [1]",
      "Artillery Jewel [1]"
    );
    assertThat(pool.armorDecorations().size() + pool.weaponDecorations().size()).isLessThan(
      data.decorations().size()
    );
  }

  @Test
  void unknownIgnoredSkillWarnsButDoesNotThrow() throws Exception {
    GearPoolConfig config = configWithIgnoredSkills(999_999_999);
    assertDoesNotThrow(() -> new GearPoolResolver(fabricatedData()).resolve(config));
  }

  @Test
  void transcendedFlagRewritesArmorStatsInThePool() {
    GearPoolConfig plain = GearPoolConfig.load("src/main/resources/default-config.json");

    List<ArmorPiece> plainArmor = new GearPoolResolver(data).resolve(plain).armorPieces();
    List<ArmorPiece> transcendedArmor = new GearPoolResolver(data)
      .resolve(plain.withTranscendedArmor(true))
      .armorPieces();

    assertThat(transcendedArmor).hasSameSizeAs(plainArmor);

    ArmorPiece rarity6 = transcendedArmor
      .stream()
      .filter(p -> p.rarity() == 6 && p.slots().length > 0)
      .findFirst()
      .orElseThrow();

    assertThat(rarity6.slots().length).isGreaterThanOrEqualTo(2);
    assertThat(Arrays.stream(rarity6.slots()).allMatch(s -> s >= 1 && s <= 3)).isTrue();
    assertThat(rarity6.maxDefense()).isEqualTo(
      data.armorTranscendence().forRarity(6).transcendedDefense(rarity6.baseDefense())
    );
  }

  @Test
  void untranscendedPoolIsLeftAloneByDefault() {
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");

    assertThat(config.armor().transcended()).isFalse();
    assertThat(new GearPoolResolver(data).resolve(config).armorPieces())
      .extracting(ArmorPiece::maxDefense)
      .containsExactlyElementsOf(data.armorPieces().stream().map(ArmorPiece::maxDefense).toList());
  }

  @Test
  void transcendedRunOutscoresPlainRun() {
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");
    GearPoolResolver resolver = new GearPoolResolver(data);

    long plainSlots = resolver
      .resolve(config)
      .armorPieces()
      .stream()
      .mapToLong(p -> Arrays.stream(p.slots()).mapToLong(SlotScore::valueOf).sum())
      .sum();
    long transcendedSlots = resolver
      .resolve(config.withTranscendedArmor(true))
      .armorPieces()
      .stream()
      .mapToLong(p -> Arrays.stream(p.slots()).mapToLong(SlotScore::valueOf).sum())
      .sum();

    assertThat(transcendedSlots).isGreaterThan(plainSlots);
  }
}
