package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.List;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.model.WeaponRoll;
import mhwilds.optimizer.solver.ArmorAggregates.Aggregate;
import mhwilds.optimizer.solver.DecoFill.Step;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

final class BuildReconstructor {

  private BuildReconstructor() {}

  static Build from(Aggregate aggregate, List<Step> steps, AmuletOpt amulet, WeaponOpt weapon) {
    ArmorPiece[] worn = aggregate.pieces().clone();
    boolean[][] occupied = new boolean[ArmorAggregates.SLOT_COUNT][];

    for (int i = 0; i < ArmorAggregates.SLOT_COUNT; i++) {
      occupied[i] = new boolean[worn[i] == null ? 0 : worn[i].slotCount()];
    }

    List<SlotAssignment> armorAssign = new ArrayList<>();
    boolean[] weaponOccupied = weapon == null ? null : new boolean[weapon.weapon().slotCount()];
    List<SlotAssignment> weaponAssign = new ArrayList<>();

    int[] amuletArmorSlots = amulet == null ? new int[0] : amulet.rank().armorSlots();
    int[] amuletWeaponSlots = amulet == null ? new int[0] : amulet.rank().weaponSlots();
    boolean[] amuletArmorOccupied = new boolean[amuletArmorSlots.length];
    boolean[] amuletWeaponOccupied = new boolean[amuletWeaponSlots.length];
    List<SlotAssignment> amuletArmorAssign = new ArrayList<>();
    List<SlotAssignment> amuletWeaponAssign = new ArrayList<>();

    for (Step step : steps) {
      boolean placed = false;

      if (step.kind() == DecoFill.ARMOR) {
        for (int i = 0; i < worn.length && !placed; i++) {
          ArmorPiece piece = worn[i];

          if (piece == null) {
            continue;
          }

          for (int si = 0; si < piece.slotCount(); si++) {
            if (piece.slots()[si] == step.size() && !occupied[i][si]) {
              occupied[i][si] = true;
              armorAssign.add(new SlotAssignment(step.deco(), piece.kind(), si));
              placed = true;
              break;
            }
          }
        }

        if (!placed) {
          for (int si = 0; si < amuletArmorSlots.length && !placed; si++) {
            if (amuletArmorSlots[si] == step.size() && !amuletArmorOccupied[si]) {
              amuletArmorOccupied[si] = true;
              amuletArmorAssign.add(new SlotAssignment(step.deco(), null, si));
              placed = true;
            }
          }
        }

        if (!placed) {
          throw new IllegalStateException("no armor slot of size " + step.size());
        }
      } else {
        if (weaponOccupied != null) {
          for (int si = 0; si < weaponOccupied.length && !placed; si++) {
            if (weapon.weapon().slots()[si] == step.size() && !weaponOccupied[si]) {
              weaponOccupied[si] = true;
              weaponAssign.add(new SlotAssignment(step.deco(), null, si));
              placed = true;
            }
          }
        }

        if (!placed) {
          for (int si = 0; si < amuletWeaponSlots.length && !placed; si++) {
            if (amuletWeaponSlots[si] == step.size() && !amuletWeaponOccupied[si]) {
              amuletWeaponOccupied[si] = true;
              amuletWeaponAssign.add(new SlotAssignment(step.deco(), null, si));
              placed = true;
            }
          }
        }

        if (!placed) {
          throw new IllegalStateException("no weapon slot of size " + step.size());
        }
      }
    }

    return new Build(
      worn,
      armorAssign,
      weaponAssign,
      amulet == null ? null : amulet.rank(),
      weapon == null ? null : weapon.weapon(),
      weapon == null ? WeaponRoll.NONE : weapon.roll(),
      amuletArmorAssign,
      amuletWeaponAssign
    );
  }
}
