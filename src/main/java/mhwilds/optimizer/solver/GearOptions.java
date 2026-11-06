package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.SlotScore;
import mhwilds.optimizer.model.Weapon;
import mhwilds.optimizer.model.WeaponRoll;

final class GearOptions {

  record AmuletOpt(
    AmuletRank rank,
    long pack,
    int[] armorCounts,
    int[] weaponCounts,
    long slotValue
  ) {}

  record WeaponOpt(Weapon weapon, long innatePack, long slotValue, int[] counts, WeaponRoll roll) {
    boolean contributesTo(int skillId) {
      return weapon.gogmazios() && roll.contributesTo(skillId);
    }

    long rollBonus() {
      return weapon.gogmazios() ? roll.emptyBonus() : 0L;
    }
  }

  private final Map<Integer, Integer> skillPos;
  private final int n;

  GearOptions(Map<Integer, Integer> skillPos) {
    this.skillPos = skillPos;
    this.n = skillPos.size();
  }

  List<AmuletOpt> amuletOptions(List<AmuletRank> amulets) {
    List<AmuletOpt> opts = new ArrayList<>();

    for (AmuletRank a : amulets) {
      opts.add(
        new AmuletOpt(
          a,
          SkillLevels.packOf(a.skills(), skillPos),
          slotCounts(a.armorSlots()),
          slotCounts(a.weaponSlots()),
          a.slotValue()
        )
      );
    }

    return pruneAmulets(opts);
  }

  private static int[] slotCounts(int[] slots) {
    int[] counts = new int[3];

    for (int s : slots) {
      if (s >= 1 && s <= 3) {
        counts[s - 1]++;
      }
    }

    return counts;
  }

  private List<AmuletOpt> pruneAmulets(List<AmuletOpt> opts) {
    List<AmuletOpt> out = new ArrayList<>();

    outer: for (AmuletOpt a : opts) {
      for (AmuletOpt b : opts) {
        if (b == a) {
          continue;
        }

        if (dominatesAmulet(b, a)) {
          continue outer;
        }
      }

      out.add(a);
    }

    return out;
  }

  private boolean dominatesAmulet(AmuletOpt b, AmuletOpt a) {
    boolean strict = false;

    for (int i = 0; i < n; i++) {
      int bv = SkillLevels.levelAt(b.pack(), i);
      int av = SkillLevels.levelAt(a.pack(), i);

      if (bv < av) {
        return false;
      }

      if (bv > av) {
        strict = true;
      }
    }

    for (int s = 0; s < 3; s++) {
      if (b.armorCounts()[s] < a.armorCounts()[s] || b.weaponCounts()[s] < a.weaponCounts()[s]) {
        return false;
      }

      if (b.armorCounts()[s] > a.armorCounts()[s] || b.weaponCounts()[s] > a.weaponCounts()[s]) {
        strict = true;
      }
    }

    return strict;
  }

  List<WeaponOpt> weaponOptions(
    List<Weapon> weapons,
    GogmaziosWeapons gogmazios,
    SkillThresholds thresholds
  ) {
    List<WeaponOpt> opts = new ArrayList<>();
    int[] setChoices = rollChoices(gogmazios.setBonusSkillIds(), thresholds);
    int[] groupChoices = rollChoices(gogmazios.groupBonusSkillIds(), thresholds);

    for (Weapon w : weapons) {
      int[] counts = new int[3];

      for (int s : w.slots()) {
        if (s >= 1 && s <= 3) {
          counts[s - 1]++;
        }
      }

      long innatePack = SkillLevels.packOf(w.skills(), skillPos);
      long slotValue = SlotScore.score(w.slots());

      if (!gogmazios.enabled() || !w.gogmazios()) {
        opts.add(new WeaponOpt(w, innatePack, slotValue, counts, WeaponRoll.NONE));
        continue;
      }

      for (int setBonusSkillId : setChoices) {
        for (int groupBonusSkillId : groupChoices) {
          // One weapon lends one piece per bonus skill, so a skill can never be counted twice.
          if (groupBonusSkillId != 0 && groupBonusSkillId == setBonusSkillId) {
            continue;
          }

          opts.add(
            new WeaponOpt(
              w,
              innatePack,
              slotValue,
              counts,
              new WeaponRoll(
                setBonusSkillId,
                gogmazios.skillRanks().getOrDefault(setBonusSkillId, Map.of()),
                groupBonusSkillId,
                gogmazios.skillRanks().getOrDefault(groupBonusSkillId, Map.of())
              )
            )
          );
        }
      }
    }

    return pruneWeapons(opts);
  }

  private int[] rollChoices(Set<Integer> pool, SkillThresholds thresholds) {
    Set<Integer> choices = new TreeSet<>();

    choices.add(0);

    for (int skillId : pool) {
      if (thresholds.required(skillId) > 0) {
        choices.add(skillId);
      }
    }

    int[] result = new int[choices.size()];
    int i = 0;

    for (int id : choices) {
      result[i++] = id;
    }

    return result;
  }

  private List<WeaponOpt> pruneWeapons(List<WeaponOpt> opts) {
    List<WeaponOpt> out = new ArrayList<>();

    outer: for (WeaponOpt a : opts) {
      for (WeaponOpt b : opts) {
        if (b == a) {
          continue;
        }

        if (dominatesWeapon(b, a)) {
          continue outer;
        }
      }

      out.add(a);
    }

    return out;
  }

  private boolean dominatesWeapon(WeaponOpt b, WeaponOpt a) {
    boolean strict = false;

    for (int s = 0; s < 3; s++) {
      if (b.counts()[s] < a.counts()[s]) {
        return false;
      }

      if (b.counts()[s] > a.counts()[s]) {
        strict = true;
      }
    }

    for (int i = 0; i < n; i++) {
      int bv = SkillLevels.levelAt(b.innatePack(), i);
      int av = SkillLevels.levelAt(a.innatePack(), i);

      if (bv < av) {
        return false;
      }

      if (bv > av) {
        strict = true;
      }
    }

    return strict;
  }
}
