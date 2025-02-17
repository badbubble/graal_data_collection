/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.phases.common.inlining.policy;

import static jdk.graal.compiler.core.common.GraalOptions.InlineEverything;
import static jdk.graal.compiler.core.common.GraalOptions.LimitInlinedInvokes;
import static jdk.graal.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static jdk.graal.compiler.core.common.GraalOptions.MaximumInliningSize;
import static jdk.graal.compiler.core.common.GraalOptions.SmallCompiledLowLevelGraphSize;
import static jdk.graal.compiler.core.common.GraalOptions.TraceInlining;
import static jdk.graal.compiler.core.common.GraalOptions.TrivialInliningSize;

import java.util.Map;

import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.inlining.info.InlineInfo;
import jdk.graal.compiler.phases.common.inlining.walker.MethodInvocation;

public class GreedyInliningPolicy extends AbstractInliningPolicy {

    private static String SavePath = "/root/Projects/data.csv";

    private static final CounterKey inliningStoppedByMaxDesiredSizeCounter = DebugContext.counter("InliningStoppedByMaxDesiredSize");

    public GreedyInliningPolicy(Map<Invoke, Double> hints) {
        super(hints);
    }

    @Override
    public boolean continueInlining(StructuredGraph currentGraph) {
        if (InliningUtil.getNodeCount(currentGraph) >= MaximumDesiredSize.getValue(currentGraph.getOptions())) {
            DebugContext debug = currentGraph.getDebug();
            InliningUtil.logInliningDecision(debug, "inlining is cut off by MaximumDesiredSize");
            inliningStoppedByMaxDesiredSizeCounter.increment(debug);
            return false;
        }
        return true;
    }

    protected static boolean hasSubstitution(Replacements replacements, InlineInfo info) {
        for (int i = 0; i < info.numberOfMethods(); i++) {
            if (replacements.hasSubstitution(info.methodAt(i), info.graph().getOptions())) {
                return true;
            }
        }
        return false;
    }

    //@Override
    public Decision isWorthInliningDefault(Replacements replacements, MethodInvocation invocation, InlineInfo calleeInfo, int inliningDepth, boolean fullyProcessed) {
        OptionValues options = calleeInfo.graph().getOptions();
        final boolean isTracing = TraceInlining.getValue(options) || calleeInfo.graph().getDebug().hasCompilationListener();
        final InlineInfo info = invocation.callee();
        final double probability = invocation.probability();
        final double relevance = invocation.relevance();

        if (InlineEverything.getValue(options)) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "inline everything");
            return InliningPolicy.Decision.YES.withReason(isTracing, "inline everything");
        }

        if (isIntrinsic(replacements, info)) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "intrinsic");
            return InliningPolicy.Decision.YES.withReason(isTracing, "intrinsic");
        }

        if (info.shouldInline()) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "forced inlining");
            return InliningPolicy.Decision.YES.withReason(isTracing, "forced inlining");
        }

        double inliningBonus = getInliningBonus(info);
        int nodes = info.determineNodeCount();
        int lowLevelGraphSize = previousLowLevelGraphSize(info);

        if (SmallCompiledLowLevelGraphSize.getValue(options) > 0 && lowLevelGraphSize > SmallCompiledLowLevelGraphSize.getValue(options) * inliningBonus && !hasSubstitution(replacements, info)) {
            InliningUtil.traceNotInlinedMethod(info, inliningDepth, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)", lowLevelGraphSize,
                            relevance, probability, inliningBonus, nodes);
            return InliningPolicy.Decision.NO.withReason(isTracing, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)", lowLevelGraphSize,
                            relevance, probability, inliningBonus, nodes);
        }

        if (nodes < TrivialInliningSize.getValue(options) * inliningBonus) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
        }

        /*
         * TODO (chaeubl): invoked methods that are on important paths but not yet compiled -> will
         * be compiled anyways and it is likely that we are the only caller... might be useful to
         * inline those methods but increases bootstrap time (maybe those methods are also getting
         * queued in the compilation queue concurrently)
         */
        double invokes = determineInvokeProbability(info);
        if (LimitInlinedInvokes.getValue(options) > 0 && fullyProcessed && invokes > LimitInlinedInvokes.getValue(options) * inliningBonus) {
            InliningUtil.traceNotInlinedMethod(info, inliningDepth, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                            probability, inliningBonus, nodes);
            return InliningPolicy.Decision.NO.withReason(isTracing, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                            probability, inliningBonus, nodes);
        }

        double maximumNodes = computeMaximumSize(relevance, (int) (MaximumInliningSize.getValue(options) * inliningBonus));
        if (nodes <= maximumNodes) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability, inliningBonus,
                            nodes, maximumNodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability, inliningBonus,
                            nodes, maximumNodes);
        }

        InliningUtil.traceNotInlinedMethod(info, inliningDepth, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes, maximumNodes);
        return InliningPolicy.Decision.NO.withReason(isTracing, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes, maximumNodes);
    }

    @Override
    public Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, InlineInfo calleeInfo, int inliningDepth, boolean fullyProcessed) {
        OptionValues options = calleeInfo.graph().getOptions();
        final boolean isTracing = TraceInlining.getValue(options) || calleeInfo.graph().getDebug().hasCompilationListener();
        final InlineInfo info = invocation.callee();
        final double probability = invocation.probability();
        final double relevance = invocation.relevance();

        double inliningBonus = getInliningBonus(info);
        int nodes = info.determineNodeCount();
        int lowLevelGraphSize = previousLowLevelGraphSize(info);
        double invokes = determineInvokeProbability(info);



        // Initialize FeatureVector
        FeatureVector feature = new FeatureVector();
        // Set the function name
        feature.setFuncName(invocation.toString());
        // Populate Numerical Features
        feature.setProbability(probability);
        feature.setRelevance(relevance);
        feature.setInliningBonus(inliningBonus);
        feature.setNodes(nodes);
        feature.setLowLevelGraphSize(lowLevelGraphSize);
        feature.setInvokes(invokes);
        feature.setInliningDepth(inliningDepth);

        // Populate Categorical Features
        feature.setIsIntrinsic(isIntrinsic(replacements, info) ? 1 : 0);
        feature.setShouldInline(info.shouldInline() ? 1 : 0);
        feature.setHasSubstitution(hasSubstitution(replacements, info) ? 1 : 0);
        feature.setInlineEverything(InlineEverything.getValue(options) ? 1 : 0);
        feature.setIsTracing(isTracing ? 1 : 0);
        feature.setFullyProcessed(fullyProcessed ? 1 : 0);

        // Populate Derived Features
        double maximumNodes = computeMaximumSize(relevance, (int) (MaximumInliningSize.getValue(options) * inliningBonus));
        feature.setNodesToMaximumNodesRatio(nodes / maximumNodes);

        double smallGraphThreshold = SmallCompiledLowLevelGraphSize.getValue(options) * inliningBonus;
        feature.setLowLevelGraphSizeToThresholdRatio(lowLevelGraphSize / smallGraphThreshold);

        double limitInvokes = LimitInlinedInvokes.getValue(options) * inliningBonus;
        feature.setInvokesToLimitRatio(invokes / limitInvokes);

        // Populate Interaction Features
        feature.setProbabilityRelevanceInteraction(probability * relevance);
        feature.setNodesInliningBonusInteraction(nodes * inliningBonus);



        // Existing Inlining Decision Logic
        if (InlineEverything.getValue(options)) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "inline everything");
            feature.setDecision(1);
            feature.saveToFile(SavePath);
            return InliningPolicy.Decision.YES.withReason(isTracing, "inline everything");
        }

        if (isIntrinsic(replacements, info)) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "intrinsic");
            feature.setDecision(1);
            feature.saveToFile(SavePath);
            return InliningPolicy.Decision.YES.withReason(isTracing, "intrinsic");
        }

        if (info.shouldInline()) {
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "forced inlining");
            feature.setDecision(1);
            feature.saveToFile(SavePath);
            return InliningPolicy.Decision.YES.withReason(isTracing, "forced inlining");
        }

        if (SmallCompiledLowLevelGraphSize.getValue(options) > 0 && lowLevelGraphSize > SmallCompiledLowLevelGraphSize.getValue(options) * inliningBonus && !hasSubstitution(replacements, info)) {
            feature.setDecision(0);
            feature.saveToFile(SavePath);

            InliningUtil.traceNotInlinedMethod(info, inliningDepth, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)", lowLevelGraphSize,
                    relevance, probability, inliningBonus, nodes);

            return InliningPolicy.Decision.NO.withReason(isTracing, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)", lowLevelGraphSize,
                    relevance, probability, inliningBonus, nodes);
        }

        if (nodes < TrivialInliningSize.getValue(options) * inliningBonus) {
            feature.setDecision(1);
            feature.saveToFile(SavePath);
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus,
                    nodes);
        }

        if (LimitInlinedInvokes.getValue(options) > 0 && fullyProcessed && invokes > LimitInlinedInvokes.getValue(options) * inliningBonus) {
            feature.setDecision(0);
            feature.saveToFile(SavePath);

            InliningUtil.traceNotInlinedMethod(info, inliningDepth, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                    probability, inliningBonus, nodes);
            return InliningPolicy.Decision.NO.withReason(isTracing, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes, relevance,
                    probability, inliningBonus, nodes);
        }

        if (nodes <= maximumNodes) {
            feature.setDecision(1);
            feature.saveToFile(SavePath);
            InliningUtil.traceInlinedMethod(info, inliningDepth, fullyProcessed, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability, inliningBonus,
                    nodes, maximumNodes);
            return InliningPolicy.Decision.YES.withReason(isTracing, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability, inliningBonus,
                    nodes, maximumNodes);
        }

        feature.setDecision(0);
        feature.saveToFile(SavePath);

        InliningUtil.traceNotInlinedMethod(info, inliningDepth, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes, maximumNodes);
        return InliningPolicy.Decision.NO.withReason(isTracing, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes, maximumNodes);
    }

}
