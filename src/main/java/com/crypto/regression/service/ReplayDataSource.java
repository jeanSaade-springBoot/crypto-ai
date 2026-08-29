package com.crypto.regression.service;

/** FIX-11H replay-only input mechanism selector. Never used by Production. */
public enum ReplayDataSource {
    DATABASE,
    DATASET
}
