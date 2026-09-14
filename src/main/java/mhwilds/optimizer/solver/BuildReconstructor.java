package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.List;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.solver.ArmorAggregates.Aggregate;
import mhwilds.optimizer.solver.DecoFill.Step;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

/** Turns a chosen aggregate and decoration-fill steps into a concrete {@link Build}. */
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

    for (Step step : steps) {
      if (step.kind() == DecoFill.ARMOR) {
        boolean placed = false;

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
          throw new IllegalStateException("no armor slot of size " + step.size());
        }
      } else {
        boolean placed = false;

        for (int si = 0; si < weaponOccupied.length; si++) {
          if (weapon.weapon().slots()[si] == step.size() && !weaponOccupied[si]) {
            weaponOccupied[si] = true;
            weaponAssign.add(new SlotAssignment(step.deco(), null, si));
            placed = true;
            break;
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
      weapon == null ? null : weapon.weapon()
    );
  }
}
