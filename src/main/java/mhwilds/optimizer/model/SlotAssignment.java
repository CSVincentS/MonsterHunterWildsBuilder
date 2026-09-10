package mhwilds.optimizer.model;

public record SlotAssignment(
  Decoration decoration,
  ArmorSlot targetPiece,
  int slotIndexWithinPiece
) {}
