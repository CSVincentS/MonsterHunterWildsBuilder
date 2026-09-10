package mhwilds.optimizer.solver;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.*;

public record SolverPool(
  List<ArmorPiece> armorPieces,
  List<Decoration> armorDecorations,
  List<Decoration> weaponDecorations,
  List<AmuletRank> amuletRanks,
  List<Weapon> weapons,
  Map<Integer, Skill> skillMap,
  SkillThresholds thresholds
) {}
