package com.memento3d.inventory.domain.model;

public enum InventoryTransactionType {
    STOCK_IN,
    STOCK_OUT,
    RESERVE,
    RELEASE,
    ADJUST_INCREASE,
    ADJUST_DECREASE,
    TRANSFER_IN,
    TRANSFER_OUT,
    RETURN_IN,
    DAMAGED,
    WASTE,
    RECEIPT,
    ISSUE,
    RESERVATION_HOLD,
    RESERVATION_RELEASE,
    PRINT_ISSUE,
    PRINT_RETURN
}
