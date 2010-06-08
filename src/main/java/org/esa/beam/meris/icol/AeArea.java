package org.esa.beam.meris.icol;


public enum AeArea {
    // 1. apply ICOL over ocean only, in coastal region only [cosat only selected]
    COSTAL_OCEAN(true, false, "Costal regions over the ocean"),
    // 2. apply ICOL everywhere over ocean [no selection]
    OCEAN(false, false, "Everywhere over the ocean"),
    // 3. apply ICOL over ocean and land, in coastal region only [both selected]
    COSTAL_ZONE(true, true, "Eostal regions over ocean and land"),
    // 4. apply ICOL everywhere over ocean and land [over land selected]
    EVERYWHERE(false, true, "Everywhere");

    private final boolean correctCostalArea;
    private final boolean correctOverLand;
    private final String label;

    private AeArea(boolean correctCostalArea, boolean correctOverLand, String label) {
        this.correctCostalArea = correctCostalArea;
        this.correctOverLand = correctOverLand;
        this.label = label;
    }

    public boolean correctCostalArea() {
        return correctCostalArea;
    }

    public boolean correctOverLand() {
        return correctOverLand;
    }

    public String getLabel() {
        return label;
    }
}
