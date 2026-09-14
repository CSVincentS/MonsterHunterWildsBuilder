package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.Weapon;

/** Builds and Pareto-prunes the amulet and weapon choices offered to each evaluation. */
final class GearOptions {

  record AmuletOpt(AmuletRank rank, long pack) {}

  record WeaponOpt(Weapon weapon, long innatePack, long slotValue, int[] counts) {}

  private final Map<Integer, Integer> skillPos;
  private final int n;

  GearOptions(Map<Integer, Integer> skillPos) {
    this.skillPos = skillPos;
    this.n = skillPos.size();
  }

  List<AmuletOpt> amuletOptions(List<AmuletRank> amulets) {
    List<AmuletOpt> opts = new ArrayList<>();

    for (AmuletRank a : amulets) {
      opts.add(new AmuletOpt(a, SkillLevels.packOf(a.skills(), skillPos)));
    }

    return pruneAmulets(opts);
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

    return strict;
  }

  List<WeaponOpt> weaponOptions(List<Weapon> weapons) {
    List<WeaponOpt> opts = new ArrayList<>();

    for (Weapon w : weapons) {
      int[] counts = new int[3];

      for (int s : w.slots()) {
        if (s >= 1 && s <= 3) {
          counts[s - 1]++;
        }
      }

      opts.add(
        new WeaponOpt(
          w,
          SkillLevels.packOf(w.skills(), skillPos),
          SlotScores.score(w.slots()),
          counts
        )
      );
    }

    return pruneWeapons(opts);
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
