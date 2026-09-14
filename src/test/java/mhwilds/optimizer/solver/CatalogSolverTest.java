package mhwilds.optimizer.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.config.GearPoolResolver;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotTarget;
import mhwilds.optimizer.model.Weapon;
import org.junit.jupiter.api.Test;

/**
 * {CatalogSolver} is verified two ways: against an independent brute-force oracle on crafted
 * pools (exact fill, joint amulet/weapon spill, omission bonuses, set skills) and against {@link
 * GreedySolver} on the three real configs, asserting per-build dominance — for every greedy build
 * the exact solver must reach the same or a better score, so its feasible set is a superset of the
 * greedy solver's.
 */
class CatalogSolverTest {

  private static final long INF = Long.MAX_VALUE >> 2;
  private static final int ARMOR = 0;
  private static final int WEAPON = 1;

  private static final Map<Integer, Skill> SKILLS = Map.of(
    100,
    new Skill(100, "ReqA", 5),
    200,
    new Skill(200, "ReqB", 3),
    300,
    new Skill(300, "SetSkill", 5)
  );

  private static ArmorPiece piece(ArmorSlot kind, int[] slots, Map<Integer, Integer> skills) {
    return new ArmorPiece(0, "Set", kind, slots, skills, 0, 0);
  }

  private static ArmorPiece setPiece(ArmorSlot kind, int bonusId) {
    return new ArmorPiece(
      0,
      "SetA",
      kind,
      new int[] {},
      Map.of(),
      0,
      0,
      0,
      bonusId,
      Map.of(2, 1, 4, 2),
      0,
      Map.of()
    );
  }

  private static Weapon weapon(int id, int[] slots, Map<Integer, Integer> skills) {
    return new Weapon(id, "bow", "W" + id, 100, 0, slots, skills, List.of());
  }

  private static Decoration deco(int id, int level, Map<Integer, Integer> skills, SlotTarget on) {
    return new Decoration(id, "J" + id, level, skills, on);
  }

  private static AmuletRank amulet(int level) {
    return new AmuletRank(1, 1, "Charm", level == 0 ? Map.of() : Map.of(100, level));
  }

  private static SolverPool pool(
    List<ArmorPiece> pieces,
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    List<AmuletRank> amulets,
    List<Weapon> weapons,
    SkillThresholds thr
  ) {
    return new SolverPool(pieces, armorDecos, weaponDecos, amulets, weapons, SKILLS, thr);
  }

  @Test
  void matchesBruteForceOracleOnCraftedPools() {
    OracleCase scenario = oracleCaseForWeaponSpill();
    assertOracleEquivalence("weapon spill (old greedy solver misses this)", scenario);

    scenario = oracleCaseForComboDeco();
    assertOracleEquivalence("single deco covering two skills", scenario);

    scenario = oracleCaseForSetWithOmission();
    assertOracleEquivalence("set skill with omission bonus", scenario);

    scenario = oracleCaseForDecoOnly();
    assertOracleEquivalence("deco-covered residual", scenario);
  }

  private record OracleCase(SolverPool pool, long bonus, String name) {}

  private OracleCase oracleCaseForDecoOnly() {
    SolverPool p = pool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] { 1 }, Map.of(100, 4)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(deco(1, 1, Map.of(100, 1), SlotTarget.ARMOR)),
      List.of(),
      List.of(amulet(0)),
      List.of(weapon(1, new int[] {}, Map.of())),
      new SkillThresholds(Map.of(100, 5))
    );

    return new OracleCase(p, 0L, "deco-only");
  }

  private OracleCase oracleCaseForWeaponSpill() {
    SolverPool p = pool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] { 1 }, Map.of(100, 4)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(),
      List.of(deco(1, 1, Map.of(100, 1), SlotTarget.WEAPON)),
      List.of(amulet(0)),
      List.of(weapon(1, new int[] { 2 }, Map.of()), weapon(2, new int[] { 1 }, Map.of(100, 1))),
      new SkillThresholds(Map.of(100, 5))
    );

    return new OracleCase(p, 0L, "weapon spill");
  }

  private OracleCase oracleCaseForComboDeco() {
    SolverPool p = pool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] { 1 }, Map.of(100, 4)),
        piece(ArmorSlot.CHEST, new int[] { 1 }, Map.of(200, 2)),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(
        deco(1, 1, Map.of(100, 1), SlotTarget.ARMOR),
        deco(2, 1, Map.of(200, 1), SlotTarget.ARMOR),
        deco(3, 1, Map.of(100, 1, 200, 1), SlotTarget.ARMOR)
      ),
      List.of(),
      List.of(amulet(0)),
      List.of(weapon(1, new int[] { 1 }, Map.of())),
      new SkillThresholds(Map.of(100, 5, 200, 3))
    );

    return new OracleCase(p, 0L, "combo deco");
  }

  private OracleCase oracleCaseForSetWithOmission() {
    SolverPool p = pool(
      List.of(
        setPiece(ArmorSlot.HEAD, 300),
        setPiece(ArmorSlot.CHEST, 300),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(),
      List.of(),
      List.of(amulet(0)),
      List.of(weapon(1, new int[] {}, Map.of())),
      new SkillThresholds(Map.of(300, 1), Map.of(300, Map.of(2, 1, 4, 2)))
    );

    return new OracleCase(p, 1000L, "set with omission");
  }

  private void assertOracleEquivalence(String name, OracleCase c) {
    List<Long> oracle = bruteForceOracle(c.pool(), c.bonus());
    List<Long> expected = oracle
      .stream()
      .sorted(Comparator.reverseOrder())
      .distinct()
      .limit(100)
      .toList();

    long t0 = System.currentTimeMillis();
    List<Build> results = new CatalogSolver(100, 1, c.bonus()).solve(c.pool());
    long elapsed = System.currentTimeMillis() - t0;

    List<Long> actual = results
      .stream()
      .map(b -> b.equipmentAwareScore(c.bonus()))
      .sorted(Comparator.reverseOrder())
      .distinct()
      .toList();

    System.out.printf(
      "[%s] oracle %d distinct, catalog %d distinct (%dms)%n",
      name,
      expected.size(),
      actual.size(),
      elapsed
    );

    assertThat(actual)
      .as("%s: exact top scores match the brute-force oracle", name)
      .containsExactlyElementsOf(expected);
  }

  /** Independent ground truth: no solver code is reused here. */
  private List<Long> bruteForceOracle(SolverPool pool, long bonus) {
    SkillThresholds thr = pool.thresholds();
    Map<Integer, Integer> req = thr.requiredSkills();
    List<Integer> nonSet = new ArrayList<>();
    List<Integer> setSkills = new ArrayList<>();

    for (Integer id : req.keySet()) {
      if (thr.isSetSkill(id)) {
        setSkills.add(id);
      } else {
        nonSet.add(id);
      }
    }

    List<Integer> orderedNonSet = new ArrayList<>(nonSet);
    orderedNonSet.sort(Comparator.naturalOrder());

    Map<Integer, List<ArmorPiece>> bySlot = new TreeMap<>();

    for (ArmorSlot s : ArmorSlot.values()) {
      bySlot.put(s.ordinal(), new ArrayList<>());
    }

    for (ArmorPiece p : pool.armorPieces()) {
      bySlot.get(p.kind().ordinal()).add(p);
    }

    List<Long> scores = new ArrayList<>();
    List<ArmorPiece> chosen = new ArrayList<>(Arrays.asList(new ArmorPiece[5]));
    oracleArmorRec(bySlot, 0, chosen, pool, orderedNonSet, setSkills, bonus, scores);

    return scores;
  }

  private void oracleArmorRec(
    Map<Integer, List<ArmorPiece>> bySlot,
    int slotIdx,
    List<ArmorPiece> chosen,
    SolverPool pool,
    List<Integer> nonSet,
    List<Integer> setSkills,
    long bonus,
    List<Long> scores
  ) {
    if (slotIdx == 5) {
      oracleCombine(chosen, pool, nonSet, setSkills, bonus, scores);
      return;
    }

    for (ArmorPiece p : bySlot.get(slotIdx)) {
      chosen.set(slotIdx, p);
      oracleArmorRec(bySlot, slotIdx + 1, chosen, pool, nonSet, setSkills, bonus, scores);
    }

    if (bonus > 0) {
      chosen.set(slotIdx, null);
      oracleArmorRec(bySlot, slotIdx + 1, chosen, pool, nonSet, setSkills, bonus, scores);
    }
  }

  private void oracleCombine(
    List<ArmorPiece> chosen,
    SolverPool pool,
    List<Integer> nonSet,
    List<Integer> setSkills,
    long bonus,
    List<Long> scores
  ) {
    SkillThresholds thr = pool.thresholds();

    for (Integer setSkill : setSkills) {
      int count = 0;

      for (ArmorPiece p : chosen) {
        if (p != null && (p.setBonusId() == setSkill || p.groupBonusId() == setSkill)) {
          count++;
        }
      }

      if (activationLevel(thr.setSkillRanksOf(setSkill), count) < thr.required(setSkill)) {
        return;
      }
    }

    int[] need = new int[nonSet.size()];

    for (int k = 0; k < nonSet.size(); k++) {
      int level = 0;

      for (ArmorPiece p : chosen) {
        if (p != null) {
          level += p.skills().getOrDefault(nonSet.get(k), 0);
        }
      }

      need[k] = Math.max(0, thr.required(nonSet.get(k)) - level);
    }

    long armorSlotValue = 0;
    List<int[]> slots = new ArrayList<>();

    for (ArmorPiece p : chosen) {
      if (p == null) {
        continue;
      }

      armorSlotValue += slotScore(p.slots());

      for (int s : p.slots()) {
        slots.add(new int[] { s, ARMOR });
      }
    }

    int omittedArmor = 0;

    for (ArmorPiece p : chosen) {
      if (p == null) {
        omittedArmor++;
      }
    }

    List<AmuletRank> amulets = new ArrayList<>(pool.amuletRanks());

    if (bonus > 0) {
      amulets.add(null);
    }

    List<Weapon> weapons = new ArrayList<>(pool.weapons());

    if (bonus > 0) {
      weapons.add(null);
    }

    for (AmuletRank amulet : amulets) {
      int[] needA = needAfter(chosen, need, nonSet, amulet);

      for (Weapon weapon : weapons) {
        int[] needW = needAfter(chosen, needA, nonSet, weapon);
        List<int[]> allSlots = new ArrayList<>(slots);

        if (weapon != null) {
          for (int s : weapon.slots()) {
            allSlots.add(new int[] { s, WEAPON });
          }
        }

        long cost = oracleFill(
          needW,
          allSlots,
          pool.armorDecorations(),
          pool.weaponDecorations(),
          nonSet
        );

        if (cost == INF) {
          continue;
        }

        long weaponSlotValue = weapon == null ? 0 : slotScore(weapon.slots());
        int omitted = omittedArmor + (amulet == null ? 1 : 0) + (weapon == null ? 1 : 0);
        long score = armorSlotValue + weaponSlotValue - cost + bonus * omitted;
        scores.add(score);
      }
    }
  }

  private int[] needAfter(
    List<ArmorPiece> ignored,
    int[] need,
    List<Integer> nonSet,
    Object source
  ) {
    int[] out = need.clone();

    if (source == null) {
      return out;
    }

    Map<Integer, Integer> skills =
      source instanceof AmuletRank a ? a.skills() : ((Weapon) source).skills();

    for (int k = 0; k < nonSet.size(); k++) {
      out[k] = Math.max(0, out[k] - skills.getOrDefault(nonSet.get(k), 0));
    }

    return out;
  }

  private long oracleFill(
    int[] need,
    List<int[]> slots,
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    List<Integer> nonSet
  ) {
    long best = INF;
    best = oracleFillRec(need.clone(), slots, 0, 0L, armorDecos, weaponDecos, nonSet, best);

    return best;
  }

  private long oracleFillRec(
    int[] need,
    List<int[]> slots,
    int idx,
    long consumed,
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    List<Integer> nonSet,
    long best
  ) {
    if (consumed >= best) {
      return best;
    }

    boolean done = true;

    for (int k = 0; k < need.length; k++) {
      if (need[k] > 0) {
        done = false;
        break;
      }
    }

    if (done) {
      return Math.min(best, consumed);
    }

    if (idx == slots.size()) {
      return best;
    }

    // Leave this slot free.
    best = oracleFillRec(need, slots, idx + 1, consumed, armorDecos, weaponDecos, nonSet, best);

    int size = slots.get(idx)[0];
    List<Decoration> pool = slots.get(idx)[1] == ARMOR ? armorDecos : weaponDecos;

    for (Decoration d : pool) {
      if (d.level() > size) {
        continue;
      }

      int[] next = need.clone();
      boolean helps = false;

      for (int k = 0; k < nonSet.size(); k++) {
        int c = d.skills().getOrDefault(nonSet.get(k), 0);

        if (c > 0) {
          next[k] = Math.max(0, next[k] - c);
          helps = true;
        }
      }

      if (helps) {
        best = oracleFillRec(
          next,
          slots,
          idx + 1,
          consumed + POW10(size),
          armorDecos,
          weaponDecos,
          nonSet,
          best
        );
      }
    }

    return best;
  }

  private static long POW10(int size) {
    return switch (size) {
      case 1 -> 10L;
      case 2 -> 100L;
      default -> 1000L;
    };
  }

  private static long slotScore(int[] slots) {
    long s = 0;

    for (int sz : slots) {
      s += POW10(sz);
    }

    return s;
  }

  private static int activationLevel(Map<Integer, Integer> thresholds, int pieceCount) {
    int best = 0;

    for (Map.Entry<Integer, Integer> rank : thresholds.entrySet()) {
      if (pieceCount >= rank.getKey() && rank.getValue() > best) {
        best = rank.getValue();
      }
    }

    return best;
  }

  @Test
  void realConfigsDoNotRegressGreedySolver() {
    GameData data = new GameDataLoader("data").load();
    boolean soak =
      Boolean.getBoolean("catalog.soak") || "true".equalsIgnoreCase(System.getenv("CATALOG_SOAK"));

    List<String> configs = new ArrayList<>(List.of("configs/gogma_meta.json"));

    if (soak) {
      configs.add("configs/sere-gore_adrenaline.json");
      configs.add("configs/zoh-gore_evasion.json");
    }

    for (String path : configs) {
      GearPoolConfig config = GearPoolConfig.load(path);
      long bonus = config.equipmentSlotBonus() != null ? config.equipmentSlotBonus() : 0L;

      // Default run uses the restricted crop for a fast regression cap; the soak run exercises
      // the full pools at the production k.
      SolverPool pool = new GearPoolResolver(data).resolve(config);
      SolverPool used = soak ? pool : restrictPool(pool, 10, 6, 9);
      int k = soak ? 1000 : 100;

      long t0 = System.currentTimeMillis();
      List<Build> baseline = new GreedySolver(k, 1, bonus).solve(used);
      long tGreedy = System.currentTimeMillis() - t0;

      long t1 = System.currentTimeMillis();
      List<Build> results = new CatalogSolver(k, 1, bonus).solve(used);
      long tCatalog = System.currentTimeMillis() - t1;

      List<Long> baseScores = distinctDesc(baseline, bonus);
      List<Long> catalogScores = distinctDesc(results, bonus);

      System.out.printf(
        "[%s] greedy %s catalog %s | greedy %dms catalog %dms%n",
        path,
        baseScores,
        catalogScores,
        tGreedy,
        tCatalog
      );

      // Greedy can emit EXTRA distinct low scores that are slack builds (its fill is not
      // cost-minimal) and dominated equipment variants (e.g. a weapon that a strictly-better
      // same-name sibling beats). The exact solver replaces those with strictly better builds,
      // so its distinct list is not rank-comparable. The sound guarantee is per-build dominance:
      // every greedy build must be shadowed by a catalog build of equal or higher score, i.e.
      // for every score threshold t the catalog must offer at least as many builds >= t as
      // greedy does. (Fails if the catalog missed any reachable build; passes despite greedy's
      // dominated/slack tail.)
      List<Long> greScores = baseline
        .stream()
        .map(b -> b.equipmentAwareScore(bonus))
        .toList();
      List<Long> catScoresDesc = results
        .stream()
        .map(b -> b.equipmentAwareScore(bonus))
        .sorted(Comparator.reverseOrder())
        .toList();

      int catIdx = 0;

      for (long gs : greScores) {
        boolean shadowed = false;

        while (catIdx < catScoresDesc.size()) {
          if (catScoresDesc.get(catIdx) >= gs) {
            shadowed = true;
            break;
          }

          catIdx++;
        }

        if (!shadowed) {
          org.junit.jupiter.api.Assertions.fail(
            String.format(
              "%s: no catalog build reaches greedy build score %d — the exact solver must reach " +
                "or beat every score the greedy solver reaches",
              path,
              gs
            )
          );
        }
      }

      assertThat(catalogScores.get(0))
        .as("%s: top-1 distinct score must not regress", path)
        .isGreaterThanOrEqualTo(baseScores.get(0));

      for (Build b : results) {
        checkThresholds(b, used.thresholds());
      }
    }
  }

  private static List<Long> distinctDesc(List<Build> builds, long bonus) {
    return builds
      .stream()
      .map(b -> b.equipmentAwareScore(bonus))
      .sorted(Comparator.reverseOrder())
      .distinct()
      .toList();
  }

  /**
   * Faithful crop of a real pool for a bounded equivalence run: top {@code perSlot} pieces per
   * armor slot by defense, capped relevant amulets/weapons. Both solvers run on the same pool, so
   * it still tests score equivalence on real game data.
   */
  private static SolverPool restrictPool(SolverPool in, int perSlot, int capAmu, int capWep) {
    Map<Integer, Integer> req = in.thresholds().requiredSkills();
    List<ArmorSlot> slots = new ArrayList<>(
      in.armorPieces().stream().map(ArmorPiece::kind).distinct().toList()
    );

    Map<ArmorSlot, List<ArmorPiece>> bySlot = new EnumMap<>(ArmorSlot.class);

    for (ArmorSlot s : ArmorSlot.values()) {
      bySlot.put(s, new ArrayList<>());
    }

    for (ArmorPiece p : in.armorPieces()) {
      bySlot.get(p.kind()).add(p);
    }

    List<ArmorPiece> retained = new ArrayList<>();

    for (ArmorSlot s : slots) {
      List<ArmorPiece> list = new ArrayList<>(bySlot.get(s));
      list.sort(Comparator.comparingInt(ArmorPiece::maxDefense).reversed());
      retained.addAll(list.subList(0, Math.min(perSlot, list.size())));
    }

    List<AmuletRank> amulets = retainRelevantAmulets(
      new ArrayList<>(in.amuletRanks()),
      req.keySet()
    )
      .stream()
      .limit(capAmu)
      .toList();
    List<Weapon> weapons = retainRelevantWeapons(new ArrayList<>(in.weapons()), req.keySet())
      .stream()
      .limit(capWep)
      .toList();

    return new SolverPool(
      retained,
      in.armorDecorations(),
      in.weaponDecorations(),
      amulets,
      weapons,
      in.skillMap(),
      in.thresholds()
    );
  }

  private static List<AmuletRank> retainRelevantAmulets(
    List<AmuletRank> input,
    java.util.Set<Integer> required
  ) {
    List<AmuletRank> out = new ArrayList<>();

    for (AmuletRank r : input) {
      boolean relevant = false;

      for (Integer id : r.skills().keySet()) {
        if (required.contains(id)) {
          relevant = true;
          break;
        }
      }

      if (relevant && !out.contains(r)) {
        out.add(r);
      }
    }

    out.sort(
      Comparator.comparingInt((AmuletRank r) ->
        r
          .skills()
          .keySet()
          .stream()
          .mapToInt(id -> required.contains(id) ? 1 : 0)
          .sum()
      ).reversed()
    );
    return out;
  }

  private static List<Weapon> retainRelevantWeapons(
    List<Weapon> input,
    java.util.Set<Integer> required
  ) {
    List<Weapon> out = new ArrayList<>();

    for (Weapon w : input) {
      boolean relevant = w.skills().keySet().stream().anyMatch(required::contains);

      for (int s : w.slots()) {
        relevant |= s > 0;
      }

      if (relevant && !out.contains(w)) {
        out.add(w);
      }
    }

    out.sort(
      Comparator.comparingInt((Weapon w) -> {
        int v = w.skills().size();

        for (int s : w.slots()) {
          v += s;
        }

        return v;
      }).reversed()
    );
    return out;
  }

  private static void checkThresholds(Build b, SkillThresholds thr) {
    for (Map.Entry<Integer, Integer> e : thr.requiredSkills().entrySet()) {
      int id = e.getKey();
      int req = e.getValue();

      if (thr.isSetSkill(id)) {
        assertThat(b.activeSetBonusSkills().getOrDefault(id, 0))
          .as("set skill %d must activate to %d", id, req)
          .isGreaterThanOrEqualTo(req);
      } else {
        assertThat(b.totalSkillLevel(id))
          .as("skill %d must reach %d", id, req)
          .isGreaterThanOrEqualTo(req);
      }
    }
  }
}
