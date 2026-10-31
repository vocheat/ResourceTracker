package net.fabricmc.resourcetracker.client.gui;

/** Session-only table state, retained while a child screen is open or the window is resized. */
final class ResourceViewState {
    public String query = "";
    public String selectedItemId;
    public int page;
}
