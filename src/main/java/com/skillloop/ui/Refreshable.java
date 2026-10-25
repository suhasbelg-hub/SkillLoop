package com.skillloop.ui;

/** Implemented by every screen that reloads its data from the database. */
public interface Refreshable {
    /** Loads (or reloads) the data shown by this screen. */
    void refresh();
}
