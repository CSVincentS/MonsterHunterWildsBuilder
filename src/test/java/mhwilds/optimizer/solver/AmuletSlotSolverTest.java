package mhwilds.optimizer.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotTarget;
import mhwilds.optimizer.model.Weapon;
import mhwilds.optimizer.validity.BuildValidator;
import org.junit.jupiter.api.Test;

class AmuletSlotSolverTest {

  private static final Map<Integer, Skill> SKILLS = Map.of(
    100,
    new Skill(100, "ReqA", 5),
    200,
    new Skill(200, "ReqB", 3)
  );

  private static ArmorPiece plainPiece(ArmorSlot kind) {
    return new ArmorPiece(0, "Set", kind, new int[] {}, Map.of(), 0, 0);
  }

  private static List<ArmorPiece> plainArmor() {
    return List.of(
      plainPiece(ArmorSlot.HEAD),
      plainPiece(ArmorSlot.CHEST),
      plainPiece(ArmorSlot.ARMS),
      plainPiece(ArmorSlot.WAIST),
      plainPiece(ArmorSlot.LEGS)
    );
  }

  private static Weapon plainWeapon() {
    return new Weapon(1, "bow", "W1", 100, 0, new int[] {}, Map.of(), List.of());
  }

  private static SolverPool pool(
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    List<AmuletRank> amulets,
    SkillThresholds thresholds
  ) {
    return new SolverPool(
      plainArmor(),
      armorDecos,
      weaponDecos,
      amulets,
      List.of(plainWeapon()),
      SKILLS,
      thresholds
    );
  }

  @Test
  void armorSlotsOnAmuletFillRequiredSkills() {
    Decoration armorDeco = new Decoration(1, "J1", 1, Map.of(100, 1), SlotTarget.ARMOR);
    AmuletRank appraised = new AmuletRank(
      2_000_000_000,
      1,
      "Appraised Charm",
      Map.of(),
      8,
      new int[] { 1, 1 },
      new int[0]
    );

    List<Build> builds = new CatalogSolver().solve(
      pool(List.of(armorDeco), List.of(), List.of(appraised), new SkillThresholds(Map.of(100, 2)))
    );

    assertThat(builds).isNotEmpty();

    Build build = builds.get(0);

    assertThat(build.amuletRank().name()).isEqualTo("Appraised Charm");
    assertThat(build.amuletArmorDecorations()).hasSize(2);
    assertThat(build.totalSkillLevel(100)).isEqualTo(2);
    assertThat(new BuildValidator().validate(build)).isEmpty();
  }

  @Test
  void weaponSlotOnAmuletFillsWeaponSkills() {
    Decoration weaponDeco = new Decoration(2, "J2", 1, Map.of(200, 1), SlotTarget.WEAPON);
    AmuletRank appraised = new AmuletRank(
      2_000_000_000,
      1,
      "Golden Age Charm",
      Map.of(),
      8,
      new int[0],
      new int[] { 1 }
    );

    List<Build> builds = new CatalogSolver().solve(
      pool(List.of(), List.of(weaponDeco), List.of(appraised), new SkillThresholds(Map.of(200, 1)))
    );

    assertThat(builds).isNotEmpty();

    Build build = builds.get(0);

    assertThat(build.amuletWeaponDecorations()).hasSize(1);
    assertThat(build.totalSkillLevel(200)).isEqualTo(1);
    assertThat(new BuildValidator().validate(build)).isEmpty();
  }

  @Test
  void unsolvableWithoutAmuletSlots() {
    Decoration armorDeco = new Decoration(1, "J1", 1, Map.of(100, 1), SlotTarget.ARMOR);
    AmuletRank plain = new AmuletRank(7, 1, "Charm", Map.of(), 5);

    List<Build> builds = new CatalogSolver().solve(
      pool(List.of(armorDeco), List.of(), List.of(plain), new SkillThresholds(Map.of(100, 2)))
    );

    assertThat(builds).isEmpty();
  }

  @Test
  void slotlessAmuletLeavesNoAmuletDecorations() {
    Decoration armorDeco = new Decoration(1, "J1", 1, Map.of(100, 1), SlotTarget.ARMOR);
    ArmorPiece slotted = new ArmorPiece(0, "Set", ArmorSlot.HEAD, new int[] { 1 }, Map.of(), 0, 0);
    AmuletRank plain = new AmuletRank(7, 1, "Charm", Map.of(), 5);

    SolverPool custom = new SolverPool(
      List.of(
        slotted,
        plainPiece(ArmorSlot.CHEST),
        plainPiece(ArmorSlot.ARMS),
        plainPiece(ArmorSlot.WAIST),
        plainPiece(ArmorSlot.LEGS)
      ),
      List.of(armorDeco),
      List.of(),
      List.of(plain),
      List.of(plainWeapon()),
      SKILLS,
      new SkillThresholds(Map.of(100, 1))
    );

    List<Build> builds = new CatalogSolver().solve(custom);

    assertThat(builds).isNotEmpty();
    assertThat(builds.get(0).amuletArmorDecorations()).isEmpty();
    assertThat(builds.get(0).amuletWeaponDecorations()).isEmpty();
  }

  @Test
  void slotsBreakSkillTiesWhenPruningAmulets() {
    GearOptions gear = new GearOptions(Map.of(100, 0));
    AmuletRank bare = new AmuletRank(1, 1, "Bare", Map.of(100, 1), 5);
    AmuletRank slotted = new AmuletRank(
      2,
      1,
      "Slotted",
      Map.of(100, 1),
      8,
      new int[] { 1 },
      new int[0]
    );

    assertThat(gear.amuletOptions(List.of(bare, slotted)))
      .extracting(opt -> opt.rank().name())
      .containsExactly("Slotted");

    AmuletRank stronger = new AmuletRank(3, 1, "Stronger", Map.of(100, 2), 5);

    assertThat(gear.amuletOptions(List.of(stronger, slotted))).hasSize(2);
  }
}
