package henshin.modifier;

public class SampleAlgorithm {
    private final int k;
    private final int backwardSteps;
    private final String nodeTypeName;  // e.g., "RsX1" - the planning node type

    public SampleAlgorithm(int k, int backwardSteps, String nodeTypeName) {
        if (k < 0) throw new IllegalArgumentException("k must be >= 0");
        if (backwardSteps < 0) throw new IllegalArgumentException("backwardSteps must be >= 0");
        this.k = k;
        this.backwardSteps = backwardSteps;
        this.nodeTypeName = nodeTypeName;
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
}
