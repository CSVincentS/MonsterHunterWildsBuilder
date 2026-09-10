package mhwilds.optimizer.config;

import java.util.*;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.model.*;
import mhwilds.optimizer.solver.SkillThresholds;
import mhwilds.optimizer.solver.SolverPool;

public class GearPoolResolver {

  private static final WeaponIgnoredSkillSeeds IGNORED_SEEDS =
    WeaponIgnoredSkillSeeds.defaultOnClasspath();

  private final GameData data;

  public GearPoolResolver(GameData data) {
    this.data = data;
  }

  public SolverPool resolve(GearPoolConfig config) {
    String weaponKind = resolveWeaponType(config);
    Set<Integer> ignoredSkillIds = resolveIgnoredSkills(config, weaponKind);

    List<ArmorPiece> armorPieces = dropIgnoredSkillOnly(
      filterArmor(config),
      ignoredSkillIds,
      ArmorPiece::skills
    );

    List<Decoration> allDecorations = dropIgnoredSkillOnly(
      filterDecorations(config),
      ignoredSkillIds,
      Decoration::skills
    );

    List<AmuletRank> amuletRanks = dropIgnoredSkillOnly(
      filterAmulets(config),
      ignoredSkillIds,
      AmuletRank::skills
    );

    List<Weapon> weapons = dropIgnoredSkillOnly(
      filterWeapons(config, weaponKind),
      ignoredSkillIds,
      Weapon::skills
    );

    warnIfInvertedRarity("armor", config.armor().minRarity(), config.armor().maxRarity());
    warnIfInvertedRarity(
      "decorations",
      config.decorations().minRarity(),
      config.decorations().maxRarity()
    );
    warnIfInvertedRarity("amulets", config.amulets().minRarity(), config.amulets().maxRarity());
    warnIfInvertedRarity("weapons", config.weapons().minRarity(), config.weapons().maxRarity());

    armorPieces = applyRarityFilter(
      armorPieces,
      config.armor().minRarity(),
      config.armor().maxRarity(),
      ArmorPiece::rarity
    );

    allDecorations = applyRarityFilter(
      allDecorations,
      config.decorations().minRarity(),
      config.decorations().maxRarity(),
      Decoration::rarity
    );

    amuletRanks = applyRarityFilter(
      amuletRanks,
      config.amulets().minRarity(),
      config.amulets().maxRarity(),
      AmuletRank::rarity
    );

    weapons = applyRarityFilter(
      weapons,
      config.weapons().minRarity(),
      config.weapons().maxRarity(),
      Weapon::rarity
    );

    SkillThresholds thresholds = buildThresholds(config);

    List<Decoration> armorDecorations = allDecorations
      .stream()
      .filter(d -> d.allowedOn() == SlotTarget.ARMOR)
      .toList();

    List<Decoration> weaponDecorations = allDecorations
      .stream()
      .filter(d -> d.allowedOn() == SlotTarget.WEAPON)
      .toList();

    return new SolverPool(
      armorPieces,
      armorDecorations,
      weaponDecorations,
      amuletRanks,
      weapons,
      data.skills(),
      thresholds
    );
  }

  private String resolveWeaponType(GearPoolConfig config) {
    String candidate = config.weaponType();
    boolean known = data
      .weapons()
      .stream()
      .anyMatch(w -> w.kind().equals(candidate));

    if (known) {
      return candidate;
    }

    System.err.println("Warning: unknown weapon_type '" + candidate + "', falling back to 'bow'");

    return "bow";
  }

  private Set<Integer> resolveIgnoredSkills(GearPoolConfig config, String weaponKind) {
    Set<Integer> ids = new HashSet<>(config.ignoredSkills());

    for (int id : ids) {
      if (!data.skills().containsKey(id)) {
        System.err.println("Warning: unknown skill ID in ignored_skills: " + id);
      }
    }

    for (int id : IGNORED_SEEDS.seedFor(weaponKind)) {
      if (!data.skills().containsKey(id)) {
        System.err.println(
          "Warning: seed ignored skill " +
            id +
            " for weapon_type '" +
            weaponKind +
            "' not in Skill.json"
        );
      } else {
        ids.add(id);
      }
    }

    return ids;
  }

  // ignored_skills is a pool-shrinker: an item is dropped only when all of its skills are
  // ignored, so empty or mixed skill sets are kept.
  private boolean hasOnlyIgnoredSkills(Map<Integer, Integer> skills, Set<Integer> ignoredSkillIds) {
    if (skills == null || skills.isEmpty()) {
      return false;
    }

    return skills.keySet().stream().allMatch(ignoredSkillIds::contains);
  }

  private <T> List<T> dropIgnoredSkillOnly(
    List<T> items,
    Set<Integer> ignored,
    Function<T, Map<Integer, Integer>> skillsFn
  ) {
    return items
      .stream()
      .filter(i -> !hasOnlyIgnoredSkills(skillsFn.apply(i), ignored))
      .toList();
  }

  private <T> List<T> applyRarityFilter(
    List<T> items,
    Integer min,
    Integer max,
    ToIntFunction<T> rarityFn
  ) {
    return items
      .stream()
      .filter(i -> min == null || rarityFn.applyAsInt(i) >= min)
      .filter(i -> max == null || rarityFn.applyAsInt(i) <= max)
      .toList();
  }

  private void warnIfInvertedRarity(String category, Integer min, Integer max) {
    if (min != null && max != null && min > max) {
      System.err.println(
        "Warning: " +
          category +
          " min_rarity (" +
          min +
          ") is greater than max_rarity (" +
          max +
          "): no " +
          category +
          " items will be included"
      );
    }
  }

  private List<ArmorPiece> filterArmor(GearPoolConfig config) {
    Set<String> excludeSets = new HashSet<>(config.armor().excludeSets());
    Set<String> excludeSkillNames = new HashSet<>(config.armor().excludeSkills());

    Map<Integer, String> idToName = data
      .skills()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));

    Map<String, Integer> nameToId = data
      .skills()
      .values()
      .stream()
      .collect(Collectors.toMap(Skill::name, Skill::gameId));

    Set<Integer> excludeSkillIds = new HashSet<>();

    for (String name : excludeSkillNames) {
      Integer id = nameToId.get(name);

      if (id == null) {
        System.err.println("Warning: unknown skill name in exclude_skills: " + name);
      } else {
        excludeSkillIds.add(id);
      }
    }

    return data
      .armorPieces()
      .stream()
      .filter(p -> {
        if (excludeSets.contains(p.setName())) {
          return false;
        }
        if (excludeSkillIds.isEmpty()) {
          return true;
        }

        boolean allSkillsExcluded =
          !p.skills().isEmpty() && p.skills().keySet().stream().allMatch(excludeSkillIds::contains);

        return !allSkillsExcluded;
      })
      .toList();
  }

  private List<Decoration> filterDecorations(GearPoolConfig config) {
    Set<Integer> excludeIds = new HashSet<>(config.decorations().excludeIds());

    for (int id : excludeIds) {
      boolean found = data
        .decorations()
        .stream()
        .anyMatch(d -> d.gameId() == id);

      if (!found) {
        System.err.println("Warning: unknown decoration ID in exclude_ids: " + id);
      }
    }

    return data
      .decorations()
      .stream()
      .filter(d -> !excludeIds.contains(d.gameId()))
      .toList();
  }

  private List<AmuletRank> filterAmulets(GearPoolConfig config) {
    Set<Integer> excludeFamilies = new HashSet<>(config.amulets().excludeFamilies());

    for (int fam : excludeFamilies) {
      boolean found = data
        .amuletRanks()
        .stream()
        .anyMatch(r -> r.familyGameId() == fam);

      if (!found) {
        System.err.println("Warning: unknown amulet family ID in exclude_families: " + fam);
      }
    }

    return data
      .amuletRanks()
      .stream()
      .filter(r -> !excludeFamilies.contains(r.familyGameId()))
      .toList();
  }

  private List<Weapon> filterWeapons(GearPoolConfig config, String kind) {
    List<Weapon> kindWeapons = data
      .weapons()
      .stream()
      .filter(w -> w.kind().equals(kind))
      .toList();

    List<Integer> includeIds = config.weapons().includeIds();

    if (includeIds.isEmpty()) {
      return kindWeapons;
    }

    Set<Integer> includeSet = new HashSet<>(includeIds);
    List<Weapon> result = kindWeapons
      .stream()
      .filter(w -> includeSet.contains(w.gameId()))
      .toList();

    for (int id : includeIds) {
      boolean found = kindWeapons.stream().anyMatch(w -> w.gameId() == id);

      if (!found) {
        System.err.println("Warning: unknown weapon ID in weapons.include_ids: " + id);
      }
    }

    return result;
  }

  private SkillThresholds buildThresholds(GearPoolConfig config) {
    Map<String, Integer> skillNameToId = new HashMap<>();

    for (Skill skill : data.skills().values()) {
      skillNameToId.put(skill.name().toLowerCase(Locale.ROOT), skill.gameId());
    }

    // Keys are strings: a numeric game_id or an English skill name, resolved case-insensitively.
    Map<Integer, Integer> required = new HashMap<>();

    for (Map.Entry<String, Integer> entry : config.requiredSkills().entrySet()) {
      String key = entry.getKey();
      int skillId;

      try {
        skillId = Integer.parseInt(key);
      } catch (NumberFormatException e) {
        Integer byName = skillNameToId.get(key.toLowerCase(Locale.ROOT).trim());

        if (byName == null) {
          System.err.println("Warning: unknown skill in required_skills: " + key);
          continue;
        }

        skillId = byName;
      }

      if (!data.skills().containsKey(skillId)) {
        System.err.println("Warning: unknown skill in required_skills: " + key);
        continue;
      }

      required.put(skillId, entry.getValue());
    }

    Map<Integer, Map<Integer, Integer>> setSkillRanks = new HashMap<>();

    for (int skillId : required.keySet()) {
      if (data.setSkillRanks().containsKey(skillId)) {
        setSkillRanks.put(skillId, data.setSkillRanks().get(skillId));
      }
    }

    return new SkillThresholds(Map.copyOf(required), Map.copyOf(setSkillRanks));
  }
}
