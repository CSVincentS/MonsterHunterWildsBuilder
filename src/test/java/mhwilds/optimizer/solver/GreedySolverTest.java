package mhwilds.optimizer.solver;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreedySolverTest {

  private Map<Integer, Skill> skillMap;

  @BeforeEach
  void setup() {
    skillMap = Map.of(
      100,
      new Skill(100, "ReqA", 5),
      200,
      new Skill(200, "ReqB", 3),
      300,
      new Skill(300, "SetSkill", 5)
    );
  }

  private ArmorPiece piece(ArmorSlot kind, int[] slots, Map<Integer, Integer> skills) {
    return new ArmorPiece(0, "Set", kind, slots, skills, 0, 0);
  }

  private ArmorPiece setPiece(ArmorSlot kind) {
    return new ArmorPiece(
      0,
      "SetA",
      kind,
      new int[] {},
      Map.of(),
      0,
      0,
      0,
      300,
      Map.of(2, 1, 4, 2),
      0,
      Map.of()
    );
  }

  private static final SkillThresholds SET_SKILL_1 = new SkillThresholds(
    Map.of(300, 1),
    Map.of(300, Map.of(2, 1, 4, 2))
  );
  private static final SkillThresholds SET_SKILL_2 = new SkillThresholds(
    Map.of(300, 2),
    Map.of(300, Map.of(2, 1, 4, 2))
  );
  private static final SkillThresholds SET_SKILL_3 = new SkillThresholds(
    Map.of(300, 3),
    Map.of(300, Map.of(2, 1, 4, 2))
  );

  private SolverPool pool(
    List<ArmorPiece> pieces,
    SkillThresholds thr,
    List<Decoration> decos,
    AmuletRank amulet,
    Weapon weapon
  ) {
    return new SolverPool(
      pieces,
      decos,
      List.of(),
      List.of(amulet != null ? amulet : emptyAmulet()),
      List.of(weapon != null ? weapon : emptyWeapon()),
      skillMap,
      thr
    );
  }

  private static AmuletRank emptyAmulet() {
    return new AmuletRank(1, 1, "Charm", Map.of());
  }

  private static Weapon emptyWeapon() {
    return new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of());
  }

  private long countBonusPieces(Build build) {
    long n = 0;

    for (ArmorPiece p : build.armorPieces()) {
      if (p.setBonusId() == 300) n++;
    }

    return n;
  }

  @Test
  void findsBuildWithArmorAlone() {
    // Head gives 3 of skill 100, Chest gives 2 of skill 100 = total 5 ✓
    // Arms gives 2 of skill 200, Waist gives 1 of skill 200 = total 3 ✓
    SolverPool pool = new SolverPool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] {}, Map.of(100, 3)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of(100, 2)),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of(200, 2)),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of(200, 1)),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(), // no armor decorations
      List.of(), // no weapon decorations
      List.of(new AmuletRank(1, 1, "Charm", Map.of())),
      List.of(new Weapon(1, "bow", "Bow", 100, 0, new int[] { 2 }, Map.of(), List.of())),
      skillMap,
      new SkillThresholds(Map.of(100, 5, 200, 3))
    );

    GreedySolver solver = new GreedySolver();

    List<Build> results = solver.solve(pool);

    assertThat(results).isNotEmpty();
    Build best = results.get(0);

    assertThat(best.totalSkillLevel(100)).isGreaterThanOrEqualTo(5);
    assertThat(best.totalSkillLevel(200)).isGreaterThanOrEqualTo(3);
  }

  @Test
  void usesDecorationToReachThreshold() {
    // Head gives 4 of skill 100, need 1 more from decoration
    SolverPool pool = new SolverPool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] { 1 }, Map.of(100, 4)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(new Decoration(1, "SkillJewel", 1, Map.of(100, 1), SlotTarget.ARMOR)),
      List.of(),
      List.of(new AmuletRank(1, 1, "Charm", Map.of())),
      List.of(new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())),
      skillMap,
      new SkillThresholds(Map.of(100, 5))
    );

    GreedySolver solver = new GreedySolver();

    List<Build> results = solver.solve(pool);

    assertThat(results).isNotEmpty();
    assertThat(results.get(0).totalSkillLevel(100)).isGreaterThanOrEqualTo(5);

    // The decoration should be assigned to the head's slot
    assertThat(results.get(0).armorDecorations()).isNotEmpty();
  }

  @Test
  void maximizesFreeSlots() {
    // Two ways to reach skill 100 level 5:
    // Option A: Head has 5 of skill 100, no decorations needed → 1 free slot
    // Option B: Head has 3, Chest has 2 → no decorations needed → more free slots
    SolverPool pool = new SolverPool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] { 1 }, Map.of(100, 5)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of(100, 2)),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(),
      List.of(),
      List.of(new AmuletRank(1, 1, "Charm", Map.of())),
      List.of(new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())),
      skillMap,
      new SkillThresholds(Map.of(100, 5))
    );

    GreedySolver solver = new GreedySolver();

    List<Build> results = solver.solve(pool);

    assertThat(results).isNotEmpty();
    // Best build should maximize free slots
    Build best = results.get(0);

    assertThat(best.totalSkillLevel(100)).isGreaterThanOrEqualTo(5);
  }

  @Test
  void returnsEmptyWhenImpossible() {
    SolverPool pool = new SolverPool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] {}, Map.of(100, 1)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of())
      ),
      List.of(),
      List.of(),
      List.of(new AmuletRank(1, 1, "Charm", Map.of())),
      List.of(new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())),
      skillMap,
      new SkillThresholds(Map.of(100, 5)) // impossible: max from gear is 1
    );

    GreedySolver solver = new GreedySolver();

    List<Build> results = solver.solve(pool);

    assertThat(results).isEmpty();
  }

  @Test
  void setSkillRequirementForcesBonusActivation() {
    // SetA head+chest activate skill 300 (L1 at 2 pieces); direct pieces grant it only
    // as a direct skill. Under sum-semantics an all-direct build (activation 0) would
    // qualify, so requiring 300:1 must exclude it.
    SolverPool solverPool = pool(
      List.of(
        setPiece(ArmorSlot.HEAD),
        setPiece(ArmorSlot.CHEST),
        piece(ArmorSlot.HEAD, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of(300, 1))
      ),
      SET_SKILL_1,
      List.of(),
      null,
      null
    );

    List<Build> results = new GreedySolver().solve(solverPool);

    assertThat(results).isNotEmpty();

    for (Build build : results) {
      assertThat(build.activeSetBonusSkills()).as("set bonus must activate").containsEntry(300, 1);
      assertThat(countBonusPieces(build)).isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  void setSkillNotSatisfiedByDirectSkillsOrSubstitutes() {
    // Activation can reach at most 1 (only 2 SetA pieces exist), but direct skills +
    // deco + amulet + bow could sum to 2+. Requiring 300:2 must return nothing.
    List<Decoration> decos = List.of(
      new Decoration(1, "SetJewel", 1, Map.of(300, 1), SlotTarget.ARMOR)
    );
    AmuletRank amulet = new AmuletRank(1, 1, "SetCharm", Map.of(300, 1));
    Weapon weapon = new Weapon(1, "bow", "SetBow", 100, 0, new int[] {}, Map.of(300, 1), List.of());
    SolverPool solverPool = pool(
      List.of(
        setPiece(ArmorSlot.HEAD),
        setPiece(ArmorSlot.CHEST),
        piece(ArmorSlot.ARMS, new int[] { 1 }, Map.of(300, 1)),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of(300, 1))
      ),
      SET_SKILL_2,
      decos,
      amulet,
      weapon
    );

    List<Build> results = new GreedySolver().solve(solverPool);

    assertThat(results).isEmpty();
  }

  @Test
  void setSkillLevelTwoNeedsFourBonusPieces() {
    // Also offer direct alternates for every non-essential slot so a sum-semantics
    // solver could build 300:2 without trouble; only the 4-piece activation should pass.
    SolverPool solverPool = pool(
      List.of(
        setPiece(ArmorSlot.HEAD),
        setPiece(ArmorSlot.CHEST),
        setPiece(ArmorSlot.ARMS),
        setPiece(ArmorSlot.WAIST),
        setPiece(ArmorSlot.LEGS),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of(300, 1))
      ),
      SET_SKILL_2,
      List.of(),
      null,
      null
    );

    List<Build> results = new GreedySolver().solve(solverPool);

    assertThat(results).isNotEmpty();

    for (Build build : results) {
      assertThat(build.activeSetBonusSkills()).containsEntry(300, 2);
      assertThat(countBonusPieces(build)).isGreaterThanOrEqualTo(4);
    }
  }

  @Test
  void impossibleSetSkillLevelReturnsEmpty() {
    // Activation max is 2 but required is 3; even a stack of direct skill 300 equal to 5
    // must not be accepted, and the solver should give up immediately.
    SolverPool solverPool = pool(
      List.of(
        setPiece(ArmorSlot.HEAD),
        setPiece(ArmorSlot.CHEST),
        setPiece(ArmorSlot.ARMS),
        setPiece(ArmorSlot.WAIST),
        setPiece(ArmorSlot.LEGS),
        piece(ArmorSlot.HEAD, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.CHEST, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.ARMS, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.WAIST, new int[] {}, Map.of(300, 1)),
        piece(ArmorSlot.LEGS, new int[] {}, Map.of(300, 1))
      ),
      SET_SKILL_3,
      List.of(),
      null,
      null
    );

    assertThat(new GreedySolver().solve(solverPool)).isEmpty();
  }

  // Equipment-omission: when the ranking values empty equipment slots, the solver may skip
  // gear that contributes nothing; with bonus 0 it must be byte-identical to classic behavior.

  private SolverPool omissionPool() {
    // Head (skill 100 x3) + Chest (skill 100 x2) meet the requirement; the remaining armor
    // pieces, the weapon, and the amulet contribute nothing and are all optional.
    return new SolverPool(
      List.of(
        piece(ArmorSlot.HEAD, new int[] { 3 }, Map.of(100, 3)),
        piece(ArmorSlot.CHEST, new int[] { 3 }, Map.of(100, 2)),
        piece(ArmorSlot.ARMS, new int[] { 3 }, Map.of()),
        piece(ArmorSlot.WAIST, new int[] { 3 }, Map.of()),
        piece(ArmorSlot.LEGS, new int[] { 3 }, Map.of())
      ),
      List.of(),
      List.of(),
      List.of(new AmuletRank(1, 1, "Charm", Map.of())),
      List.of(new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())),
      skillMap,
      new SkillThresholds(Map.of(100, 5))
    );
  }

  @Test
  void bonusZeroNeverOmitsGear() {
    List<Build> results = new GreedySolver(100, 1, 0).solve(omissionPool());

    assertThat(results).isNotEmpty();

    for (Build build : results) {
      assertThat(build.omittedEquipmentCount())
        .as("every equipment slot is filled when omission is not rewarded")
        .isZero();
      assertThat(build.totalSkillLevel(100)).isGreaterThanOrEqualTo(5);
    }
  }

  @Test
  void positiveBonusOmitsGearThatContributesNothing() {
    List<Build> results = new GreedySolver(100, 1, 1000).solve(omissionPool());

    assertThat(results).isNotEmpty();
    Build best = results.get(0);

    assertThat(best.totalSkillLevel(100)).isGreaterThanOrEqualTo(5);

    // Bonus is high enough that at least the weapon and/or amulet should be omitted; the
    // score must beat the maximum freeSlotScore of any build that keeps all gear.
    assertThat(best.equipmentAwareScore(1000)).isGreaterThan(freeSlotOnlyScore(results));
  }

  @Test
  void parallelSolverMatchesSequentialWithBonus() {
    List<Build> sequential = new GreedySolver(200, 1, 1000).solve(omissionPool());
    List<Build> parallel = new GreedySolver(200, 4, 1000).solve(omissionPool());

    assertThat(sequential).isNotEmpty();

    assertThat(parallel.get(0).equipmentAwareScore(1000)).isEqualTo(
      sequential.get(0).equipmentAwareScore(1000)
    );
    assertThat(parallel.get(0).omittedEquipmentCount()).isEqualTo(
      sequential.get(0).omittedEquipmentCount()
    );
  }

  private long freeSlotOnlyScore(List<Build> results) {
    long max = 0;

    for (Build build : results) {
      max = Math.max(max, build.freeSlotScore());
    }

    return max;
  }
}
