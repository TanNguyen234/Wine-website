package com.strongwine.strongwine.service;

/**
 * Outcome of an admin order deletion request.
 * Only an unpaid, pending order without payment or shipment records can be removed.
 */
public enum OrderDeletionResult {
    DELETED,
    NOT_FOUND,
    CONFLICT
}
