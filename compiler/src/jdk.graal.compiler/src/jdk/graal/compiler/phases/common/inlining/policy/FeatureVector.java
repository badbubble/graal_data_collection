package jdk.graal.compiler.phases.common.inlining.policy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// FeatureVector.java
public class FeatureVector {
    private String funcName;
    // Numerical Features
    private double probability;
    private double relevance;
    private double inliningBonus;
    private int nodes;
    private int lowLevelGraphSize;
    private double invokes;
    private int inliningDepth;

    // Categorical Features (Binary: 0 or 1)
    private int isIntrinsic;
    private int shouldInline;
    private int hasSubstitution;
    private int inlineEverything;
    private int isTracing;
    private int fullyProcessed;

    // Derived Features
    private double nodesToMaximumNodesRatio;
    private double lowLevelGraphSizeToThresholdRatio;
    private double invokesToLimitRatio;

    // Interaction Features
    private double probabilityRelevanceInteraction;
    private double nodesInliningBonusInteraction;

    private int decision;

    // Additional Contextual Features (Optional)
    // Add any additional features here

    // Constructors, Getters, and Setters
    static Map<String, double[]> data = new HashMap<>();
    static Map<String, Integer> result = new HashMap<>();

    public FeatureVector() {}

    // Getters and Setters for all fields
    // Example:
    public double getProbability() {
        return probability;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public void setDecision(int decision) {
        this.decision = decision;
    }

    // Repeat for all other fields...

    // Optionally, override toString() for easy logging
    @Override
    public String toString() {
//        return "{" +
//                "probability=" + probability +
//                ", relevance=" + relevance +
//                ", inliningBonus=" + inliningBonus +
//                ", nodes=" + nodes +
//                ", lowLevelGraphSize=" + lowLevelGraphSize +
//                ", invokes=" + invokes +
//                ", inliningDepth=" + inliningDepth +
//                ", isIntrinsic=" + isIntrinsic +
//                ", shouldInline=" + shouldInline +
//                ", hasSubstitution=" + hasSubstitution +
//                ", inlineEverything=" + inlineEverything +
//                ", isTracing=" + isTracing +
//                ", fullyProcessed=" + fullyProcessed +
//                ", nodesToMaximumNodesRatio=" + nodesToMaximumNodesRatio +
//                ", lowLevelGraphSizeToThresholdRatio=" + lowLevelGraphSizeToThresholdRatio +
//                ", invokesToLimitRatio=" + invokesToLimitRatio +
//                ", probabilityRelevanceInteraction=" + probabilityRelevanceInteraction +
//                ", nodesInliningBonusInteraction=" + nodesInliningBonusInteraction +
//                '}';
//    }

        return
                funcName + "," +probability +
                        "," + relevance +
                        "," + inliningBonus +
                        "," + nodes +
                        "," + lowLevelGraphSize +
                        "," + invokes +
                        "," + inliningDepth +
                        "," + isIntrinsic +
                        "," + shouldInline +
                        "," + hasSubstitution +
                        "," + inlineEverything +
                        "," + isTracing +
                        "," + fullyProcessed +
                        "," + nodesToMaximumNodesRatio +
                        "," + lowLevelGraphSizeToThresholdRatio +
                        "," + invokesToLimitRatio +
                        "," + probabilityRelevanceInteraction +
                        "," + nodesInliningBonusInteraction + "," + decision + '\n';
    }

    public void setRelevance(double relevance) {
        this.relevance = relevance;
    }

    public void setInliningBonus(double inliningBonus) {
        this.inliningBonus = inliningBonus;
    }

    public void setNodes(int nodes) {
        this.nodes = nodes;
    }

    public void setLowLevelGraphSize(int lowLevelGraphSize) {
        this.lowLevelGraphSize = lowLevelGraphSize;
    }

    public void setInvokes(double invokes) {
        this.invokes = invokes;
    }

    public void setInliningDepth(int inliningDepth) {
        this.inliningDepth = inliningDepth;
    }

    public void setIsIntrinsic(int i) {
        this.isIntrinsic = i;
    }

    public void setShouldInline(int i) {
        this.shouldInline = i;
    }

    public void setHasSubstitution(int i) {
        this.hasSubstitution = i;
    }

    public void setInlineEverything(int i) {
        this.inlineEverything = i;
    }

    public void setIsTracing(int i) {
        this.isTracing = i;
    }

    public void setFullyProcessed(int i) {
        this.fullyProcessed = i;
    }

    public void setNodesToMaximumNodesRatio(double v) {
        this.nodesToMaximumNodesRatio = v;
    }

    public void setLowLevelGraphSizeToThresholdRatio(double v) {
        this.lowLevelGraphSizeToThresholdRatio = v;
    }

    public void setInvokesToLimitRatio(double v) {
        this.invokesToLimitRatio = v;
    }

    public void setProbabilityRelevanceInteraction(double v) {
        this.probabilityRelevanceInteraction = v;
    }

    public void setNodesInliningBonusInteraction(double v) {
        this.nodesInliningBonusInteraction = v;
    }

    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    public void saveToFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(this.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public double[] ToMLData() {
        return new double[]{probability,relevance,inliningBonus,nodes,lowLevelGraphSize,invokes,inliningDepth,isIntrinsic,shouldInline,hasSubstitution,inlineEverything,isTracing,fullyProcessed,nodesToMaximumNodesRatio, lowLevelGraphSizeToThresholdRatio,invokesToLimitRatio,probabilityRelevanceInteraction, nodesInliningBonusInteraction};
    }

}
