package henshin.modifier;

public class SampleAlgorithm {
    private final int k;
    private final int backwardSteps;
    private final String nodeTypeName;  // e.g., "RsX1" - the planning node type
    private final boolean splitRule;
    private final String markerTypeName; // null = no marker node

    public SampleAlgorithm(int k, int backwardSteps, String nodeTypeName) {
        this(k, backwardSteps, nodeTypeName, false, null);
    }

    public SampleAlgorithm(int k, int backwardSteps, String nodeTypeName, boolean splitRule, String markerTypeName) {
        if (k < 0) throw new IllegalArgumentException("k must be >= 0");
        if (backwardSteps < 0) throw new IllegalArgumentException("backwardSteps must be >= 0");
        this.k = k;
        this.backwardSteps = backwardSteps;
        this.nodeTypeName = nodeTypeName;
        this.splitRule = splitRule;
        this.markerTypeName = markerTypeName;
    }

    public int getK() {
        return k;
    }

    public int getBackwardSteps() {
        return backwardSteps;
    }

    public String getNodeTypeName() {
        return nodeTypeName;
    }

    public boolean isSplitRule() {
        return splitRule;
    }

    public String getMarkerTypeName() {
        return markerTypeName;
    }
}
