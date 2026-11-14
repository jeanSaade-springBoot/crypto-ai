package com.crypto.local;

import org.springframework.stereotype.Component;

/** Isolated solely so shutdown behaviour can be replaced in tests. */
@Component
public class LocalProcessExit {
    public void exit(int status) { System.exit(status); }
}
