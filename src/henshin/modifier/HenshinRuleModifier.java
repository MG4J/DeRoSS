package henshin.modifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.henshin.model.Edge;
import org.eclipse.emf.henshin.model.Graph;
import org.eclipse.emf.henshin.model.HenshinFactory;
import org.eclipse.emf.henshin.model.HenshinPackage;
import org.eclipse.emf.henshin.model.Mapping;
import org.eclipse.emf.henshin.model.Module;
import org.eclipse.emf.henshin.model.Node;
import org.eclipse.emf.henshin.model.Rule;
import org.eclipse.emf.henshin.model.Unit;
import org.eclipse.emf.henshin.model.resource.HenshinResourceFactory;
import org.eclipse.emf.henshin.model.resource.HenshinResourceSet;

public class HenshinRuleModifier {

    // ============================================================
    // PUBLIC ENTRY POINT
    // ============================================================

    public static void modifyRuleInModule(
            File inputHenshinFile,
            String selectedRuleName,
            String outputRuleName,
            SampleAlgorithm algorithm,
            File outputHenshinFile
    ) throws IOException {

        if (inputHenshinFile == null || !inputHenshinFile.exists()) {
            throw new IllegalArgumentException("Input henshin file not found: " + inputHenshinFile);
        }
        if (outputHenshinFile == null) {
            throw new IllegalArgumentException("Output henshin file is null");
        }
        if (outputRuleName == null || outputRuleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Output rule name must not be empty.");
        }

        // ------------------------------------------------------------
        // 1) Load input module
        // ------------------------------------------------------------

        HenshinPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("henshin", new HenshinResourceFactory());

        String baseDir = inputHenshinFile.getParentFile().getAbsolutePath();
        HenshinResourceSet rs = new HenshinResourceSet(baseDir);

        URI inUri = URI.createFileURI(inputHenshinFile.getAbsolutePath());
        Resource inRes = rs.getResource(inUri, true);

        if (inRes.getContents().isEmpty() || !(inRes.getContents().get(0) instanceof Module)) {
            throw new IllegalStateException("No henshin:Module found in " + inputHenshinFile);
        }

        Module inModule = (Module) inRes.getContents().get(0);

        // resolve imports/proxies
        EcoreUtil.resolveAll(rs);

        // Repair edge source/target references that the XMI loader leaves null
        repairEdgeReferences(inRes, inputHenshinFile);

        Rule inRule = findRulePreferName(inModule, selectedRuleName);
        if (inRule == null) {
            throw new IllegalStateException("No Rule found in input module (expected '" + selectedRuleName + "' or at least one rule).");
        }

        System.out.println("[HenshinRuleModifier] Loaded rule '" + inRule.getName()
                + "' LHS edges=" + inRule.getLhs().getEdges().size()
                + " RHS edges=" + inRule.getRhs().getEdges().size());

        // Verify input edges after repair
        verifyEdges("INPUT LHS", inRule.getLhs());
        verifyEdges("INPUT RHS", inRule.getRhs());

        // ------------------------------------------------------------
        // 2) Copy rule into new output module
        // ------------------------------------------------------------

        Module outModule = EcoreUtil.copy(inModule);

        Rule outRule = findRulePreferName(outModule, selectedRuleName);
        if (outRule == null) {
            throw new IllegalStateException("Rule '" + selectedRuleName + "' not found in copied module.");
        }
        outRule.setName(outputRuleName);

        System.out.println("[HenshinRuleModifier] Copied rule '" + outRule.getName()
                + "' LHS edges=" + outRule.getLhs().getEdges().size()
                + " RHS edges=" + outRule.getRhs().getEdges().size());

        verifyEdges("COPY LHS", outRule.getLhs());
        verifyEdges("COPY RHS", outRule.getRhs());

        // ------------------------------------------------------------
        // 3) Apply Planning-Aware OR δ-Shift-Operation
        // ------------------------------------------------------------
        int k = algorithm != null ? algorithm.getK() : 0;
        int backwardSteps = algorithm != null ? algorithm.getBackwardSteps() : 0;
        String nodeTypeName = algorithm != null ? algorithm.getNodeTypeName() : null;

        StringBuilder modifications = new StringBuilder();
        modifications.append("=== HenshinRuleModifier Output ===\n");
        modifications.append("Source rule: ").append(inRule.getName()).append("\n\n");

        if (k > 0 && nodeTypeName != null) {
            String extDesc = applyNodeExtension(outModule, outRule, k, nodeTypeName);
            modifications.append(extDesc);
        }

        if (backwardSteps > 0) {
            String shiftDesc = applyPassiveShuttleBackwardMovement(outModule, outRule, backwardSteps);
            modifications.append(shiftDesc);
        }

        if (k == 0 && backwardSteps == 0) {
            modifications.append("No modifications applied (k=0, backwardSteps=0).\n");
        }

        // Set description on the modified rule
        outRule.setDescription(modifications.toString());

        // ------------------------------------------------------------
        // 4) Save output
        // ------------------------------------------------------------
        System.out.println("[HenshinRuleModifier] Output rule: " + outRule.getName());

        Resource outRes = rs.createResource(URI.createFileURI(outputHenshinFile.getAbsolutePath()));
        outRes.getContents().add(outModule);
        outRes.save(null);

        System.out.println("[HenshinRuleModifier] Saved output to " + outputHenshinFile.getAbsolutePath());
    }

    // ============================================================
    // RULE-SPLIT: produce two separate output files (Obs + Exe)
    // ============================================================

    /**
     * Split a rule into two separate .henshin files:
     * <ul>
     *   <li>Obs file: observation rule (RHS = copy of LHS, no side effects, optionally creates marker)</li>
     *   <li>Exe file: execution rule (original side effects, optionally requires+deletes marker)</li>
     * </ul>
     */
    public static void splitRuleToFiles(
            File inputHenshinFile,
            String selectedRuleName,
            String markerTypeName,
            File obsOutputFile,
            File exeOutputFile
    ) throws IOException {

        if (inputHenshinFile == null || !inputHenshinFile.exists()) {
            throw new IllegalArgumentException("Input henshin file not found: " + inputHenshinFile);
        }

        // ---- Load input ----
        HenshinPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("henshin", new HenshinResourceFactory());

        String baseDir = inputHenshinFile.getParentFile().getAbsolutePath();
        HenshinResourceSet rs = new HenshinResourceSet(baseDir);

        URI inUri = URI.createFileURI(inputHenshinFile.getAbsolutePath());
        Resource inRes = rs.getResource(inUri, true);

        if (inRes.getContents().isEmpty() || !(inRes.getContents().get(0) instanceof Module)) {
            throw new IllegalStateException("No henshin:Module found in " + inputHenshinFile);
        }

        Module inModule = (Module) inRes.getContents().get(0);
        EcoreUtil.resolveAll(rs);
        repairEdgeReferences(inRes, inputHenshinFile);

        Rule inRule = findRulePreferName(inModule, selectedRuleName);
        if (inRule == null) {
            throw new IllegalStateException("No Rule found (expected '" + selectedRuleName + "').");
        }

        System.out.println("[RuleSplit] Loaded rule '" + inRule.getName()
                + "' LHS nodes=" + inRule.getLhs().getNodes().size()
                + " RHS nodes=" + inRule.getRhs().getNodes().size());

        // ---- Create two independent module copies ----
        Module obsModule = EcoreUtil.copy(inModule);
        Module exeModule = EcoreUtil.copy(inModule);

        Rule obsRule = findRulePreferName(obsModule, selectedRuleName);
        Rule exeRule = findRulePreferName(exeModule, selectedRuleName);

        // ---- Resolve metamodel references for marker (if any) ----
        EClass markerCls = null;
        EReference shuttleToMarkerRef = null;
        EReference modelContainmentRef = null;
        EClass shuttleCls = null;
        EReference shuttle_at = null;

        if (markerTypeName != null) {
            EPackage pkg = resolveMetamodelPackage(obsModule);
            if (pkg == null) throw new IllegalStateException("Could not resolve metamodel EPackage.");

            markerCls = (EClass) pkg.getEClassifier(markerTypeName);
            if (markerCls == null) throw new IllegalStateException("Marker type '" + markerTypeName + "' not found.");

            shuttleCls = (EClass) pkg.getEClassifier("Shuttle");
            EClass modelCls = (EClass) pkg.getEClassifier("Model");
            shuttle_at = (EReference) shuttleCls.getEStructuralFeature("at");

            for (EReference ref : shuttleCls.getEAllReferences()) {
                if (sameEClassName(ref.getEReferenceType(), markerCls)) {
                    shuttleToMarkerRef = ref;
                    break;
                }
            }
            if (shuttleToMarkerRef == null) throw new IllegalStateException("No reference from Shuttle to " + markerTypeName);

            for (EReference ref : modelCls.getEAllReferences()) {
                if (ref.isContainment() && sameEClassName(ref.getEReferenceType(), markerCls)) {
                    modelContainmentRef = ref;
                    break;
                }
            }
            if (modelContainmentRef == null) throw new IllegalStateException("No containment from Model to " + markerTypeName);
        }

        // ---- Transform Obs rule ----
        // RHS becomes a copy of LHS (no side effects)
        String obsName = selectedRuleName + "_Obs";
        {
            Graph lhs = obsRule.getLhs();
            Graph newRhs = EcoreUtil.copy(lhs);
            newRhs.setName("Rhs");
            obsRule.setRhs(newRhs);

            // Identity mappings
            obsRule.getMappings().clear();
            List<Node> lhsNodes = lhs.getNodes();
            List<Node> rhsNodes = newRhs.getNodes();
            for (int i = 0; i < lhsNodes.size() && i < rhsNodes.size(); i++) {
                Mapping m = HenshinFactory.eINSTANCE.createMapping();
                m.setOrigin(lhsNodes.get(i));
                m.setImage(rhsNodes.get(i));
                obsRule.getMappings().add(m);
            }

            // Optionally add marker node to Obs RHS (created when rule fires)
            if (markerCls != null) {
                Node marker = HenshinFactory.eINSTANCE.createNode();
                marker.setType(markerCls);
                marker.setName("M1");
                newRhs.getNodes().add(marker);

                Node rhsShuttle = findActiveShuttleInGraph(newRhs, obsRule, shuttle_at, shuttleCls, true);
                Node rhsRoot = findNodeByNameOrType(newRhs, "root", "Model");

                if (rhsShuttle != null) {
                    addEdgeToGraph(newRhs, makeEdge(rhsShuttle, marker, shuttleToMarkerRef));
                }
                if (rhsRoot != null) {
                    addEdgeToGraph(newRhs, makeEdge(rhsRoot, marker, modelContainmentRef));
                }
            }

            obsRule.setName(obsName);
            obsRule.setDescription("[Rule-Split] Obs rule for " + selectedRuleName
                    + " — observation only, no side effects"
                    + (markerTypeName != null ? ", creates marker " + markerTypeName : ""));
        }

        // ---- Transform Exe rule ----
        // Keep original LHS and RHS (all side effects preserved)
        String exeName = selectedRuleName + "_Exe";
        {
            // Optionally add marker node to Exe LHS only (deleted when rule fires)
            if (markerCls != null) {
                // Need to resolve marker type from the exe module's own metamodel
                EPackage exePkg = resolveMetamodelPackage(exeModule);
                EClass exeMarkerCls = (EClass) exePkg.getEClassifier(markerTypeName);
                EClass exeShuttleCls = (EClass) exePkg.getEClassifier("Shuttle");
                EReference exeShuttleAt = (EReference) exeShuttleCls.getEStructuralFeature("at");

                EReference exeShuttleToMarker = null;
                for (EReference ref : exeShuttleCls.getEAllReferences()) {
                    if (sameEClassName(ref.getEReferenceType(), exeMarkerCls)) {
                        exeShuttleToMarker = ref;
                        break;
                    }
                }
                EClass exeModelCls = (EClass) exePkg.getEClassifier("Model");
                EReference exeModelContainment = null;
                for (EReference ref : exeModelCls.getEAllReferences()) {
                    if (ref.isContainment() && sameEClassName(ref.getEReferenceType(), exeMarkerCls)) {
                        exeModelContainment = ref;
                        break;
                    }
                }

                Graph exeLhs = exeRule.getLhs();
                Node marker = HenshinFactory.eINSTANCE.createNode();
                marker.setType(exeMarkerCls);
                marker.setName("M1");
                exeLhs.getNodes().add(marker);

                Node lhsShuttle = findActiveShuttleInGraph(exeLhs, exeRule, exeShuttleAt, exeShuttleCls, false);
                Node lhsRoot = findNodeByNameOrType(exeLhs, "root", "Model");

                if (lhsShuttle != null && exeShuttleToMarker != null) {
                    addEdgeToGraph(exeLhs, makeEdge(lhsShuttle, marker, exeShuttleToMarker));
                }
                if (lhsRoot != null && exeModelContainment != null) {
                    addEdgeToGraph(exeLhs, makeEdge(lhsRoot, marker, exeModelContainment));
                }
            }

            exeRule.setName(exeName);
            exeRule.setDescription("[Rule-Split] Exe rule for " + selectedRuleName
                    + " — original side effects"
                    + (markerTypeName != null ? ", requires+deletes marker " + markerTypeName : ""));
        }

        // ---- Save Obs file ----
        Resource obsRes = rs.createResource(URI.createFileURI(obsOutputFile.getAbsolutePath()));
        obsRes.getContents().add(obsModule);
        obsRes.save(null);
        System.out.println("[RuleSplit] Saved Obs rule to " + obsOutputFile.getAbsolutePath());

        // ---- Save Exe file ----
        Resource exeRes = rs.createResource(URI.createFileURI(exeOutputFile.getAbsolutePath()));
        exeRes.getContents().add(exeModule);
        exeRes.save(null);
        System.out.println("[RuleSplit] Saved Exe rule to " + exeOutputFile.getAbsolutePath());
    }

    /**
     * Find the active shuttle node in a graph. For RHS graphs (isRhs=true), finds
     * the shuttle that moves to a different track. For LHS graphs (isRhs=false),
     * finds the corresponding LHS shuttle via the rule's mappings.
     */
    private static Node findActiveShuttleInGraph(Graph graph, Rule rule,
            EReference shuttleAtRef, EClass shuttleCls, boolean isRhs) {

        if (shuttleAtRef == null || shuttleCls == null) return null;

        // Try via active shuttle detection on the rule
        Node activeRhs = findActiveShuttleRhs(rule, shuttleAtRef, shuttleCls);
        if (activeRhs != null) {
            if (isRhs) {
                // For Obs RHS (which is a copy of LHS), find by name
                return findNodeByName(graph, activeRhs.getName());
            } else {
                // For Exe LHS, find via mapping
                for (Mapping m : rule.getMappings()) {
                    if (m.getImage() == activeRhs) {
                        return m.getOrigin();
                    }
                }
                // Fallback: find by name
                return findNodeByName(graph, activeRhs.getName());
            }
        }

        // Fallback: return first shuttle in graph
        for (Node n : graph.getNodes()) {
            if (n.getType() != null && "Shuttle".equals(n.getType().getName())) {
                return n;
            }
        }
        return null;
    }

    // ============================================================
    // LIST RULE NAMES FROM A HENSHIN FILE
    // ============================================================

    /**
     * Load a .henshin file and return the names of all Rule units in the module.
     */
    public static List<String> listRuleNames(File henshinFile) throws IOException {
        List<String> names = new ArrayList<>();

        if (henshinFile == null || !henshinFile.exists()) {
            throw new IllegalArgumentException("Henshin file not found: " + henshinFile);
        }

        HenshinPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("henshin", new HenshinResourceFactory());

        String baseDir = henshinFile.getParentFile().getAbsolutePath();
        HenshinResourceSet rs = new HenshinResourceSet(baseDir);

        URI inUri = URI.createFileURI(henshinFile.getAbsolutePath());
        Resource inRes = rs.getResource(inUri, true);

        if (inRes.getContents().isEmpty() || !(inRes.getContents().get(0) instanceof Module)) {
            throw new IllegalStateException("No henshin:Module found in " + henshinFile);
        }

        Module module = (Module) inRes.getContents().get(0);
        for (Unit u : module.getUnits()) {
            if (u instanceof Rule) {
                String name = ((Rule) u).getName();
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name);
                }
            }
        }

        return names;
    }

    // ============================================================
    // FIND COMPATIBLE NODE TYPES FOR UI
    // ============================================================

    /**
     * Find node types in the metamodel that are compatible with Shuttle for planning.
     * A type is compatible if:
     * 1. Shuttle has a reference to it
     * 2. It has a self-reference (for chaining multiple instances)
     * 3. It has a reference to Track (for planning)
     *
     * @return List of String arrays: [typeName, shuttleRefName, selfRefName, trackRefName]
     */
    public static List<String[]> findCompatibleNodeTypes(File inputHenshinFile) throws IOException {
        List<String[]> result = new ArrayList<>();

        if (inputHenshinFile == null || !inputHenshinFile.exists()) {
            throw new IllegalArgumentException("Input henshin file not found: " + inputHenshinFile);
        }

        // Load the module to get the metamodel
        HenshinPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("henshin", new HenshinResourceFactory());

        String baseDir = inputHenshinFile.getParentFile().getAbsolutePath();
        HenshinResourceSet rs = new HenshinResourceSet(baseDir);

        URI inUri = URI.createFileURI(inputHenshinFile.getAbsolutePath());
        Resource inRes = rs.getResource(inUri, true);

        if (inRes.getContents().isEmpty() || !(inRes.getContents().get(0) instanceof Module)) {
            throw new IllegalStateException("No henshin:Module found in " + inputHenshinFile);
        }

        Module inModule = (Module) inRes.getContents().get(0);
        EcoreUtil.resolveAll(rs);

        EPackage pkg = resolveMetamodelPackage(inModule);
        if (pkg == null) {
            throw new IllegalStateException("Could not resolve metamodel EPackage.");
        }

        EClass shuttleCls = (EClass) pkg.getEClassifier("Shuttle");
        EClass trackCls = (EClass) pkg.getEClassifier("Track");

        if (shuttleCls == null || trackCls == null) {
            throw new IllegalStateException("Metamodel must have Shuttle and Track classes.");
        }

        // Iterate through all references from Shuttle
        for (EReference shuttleRef : shuttleCls.getEAllReferences()) {
            EClass targetType = shuttleRef.getEReferenceType();
            if (targetType == null) continue;

            // Skip if target is Track or Shuttle itself
            if (sameEClassName(targetType, trackCls) || sameEClassName(targetType, shuttleCls)) {
                continue;
            }

            // Check if target type has a self-reference (for chaining)
            EReference selfRef = null;
            for (EReference ref : targetType.getEAllReferences()) {
                if (sameEClassName(ref.getEReferenceType(), targetType)) {
                    selfRef = ref;
                    break;
                }
            }
            if (selfRef == null) continue;

            // Check if target type has a reference to Track (for planning)
            EReference trackRef = null;
            for (EReference ref : targetType.getEAllReferences()) {
                if (sameEClassName(ref.getEReferenceType(), trackCls)) {
                    trackRef = ref;
                    break;
                }
            }
            if (trackRef == null) continue;

            // This type is compatible!
            result.add(new String[] {
                targetType.getName(),
                shuttleRef.getName(),
                selfRef.getName(),
                trackRef.getName()
            });

            System.out.println("[findCompatibleNodeTypes] Found: " + targetType.getName()
                + " (Shuttle." + shuttleRef.getName()
                + " -> " + targetType.getName() + "." + selfRef.getName()
                + " -> Track via " + trackRef.getName() + ")");
        }

        return result;
    }

    // ============================================================
    // FIND MARKER NODE TYPES FOR RULE-SPLIT
    // ============================================================

    /**
     * Find node types in the metamodel that can serve as marking nodes for rule splitting.
     * A type is a valid marker if:
     * 1. Shuttle has a reference to it
     * 2. Model has a containment reference to it
     *
     * @return List of String arrays: [typeName, shuttleRefName, modelContainmentRefName]
     */
    public static List<String[]> findMarkerNodeTypes(File inputHenshinFile) throws IOException {
        List<String[]> result = new ArrayList<>();

        if (inputHenshinFile == null || !inputHenshinFile.exists()) {
            throw new IllegalArgumentException("Input henshin file not found: " + inputHenshinFile);
        }

        HenshinPackage.eINSTANCE.eClass();
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("henshin", new HenshinResourceFactory());

        String baseDir = inputHenshinFile.getParentFile().getAbsolutePath();
        HenshinResourceSet rs = new HenshinResourceSet(baseDir);

        URI inUri = URI.createFileURI(inputHenshinFile.getAbsolutePath());
        Resource inRes = rs.getResource(inUri, true);

        if (inRes.getContents().isEmpty() || !(inRes.getContents().get(0) instanceof Module)) {
            throw new IllegalStateException("No henshin:Module found in " + inputHenshinFile);
        }

        Module inModule = (Module) inRes.getContents().get(0);
        EcoreUtil.resolveAll(rs);

        EPackage pkg = resolveMetamodelPackage(inModule);
        if (pkg == null) {
            throw new IllegalStateException("Could not resolve metamodel EPackage.");
        }

        EClass shuttleCls = (EClass) pkg.getEClassifier("Shuttle");
        EClass modelCls = (EClass) pkg.getEClassifier("Model");
        EClass trackCls = (EClass) pkg.getEClassifier("Track");

        if (shuttleCls == null || modelCls == null) {
            throw new IllegalStateException("Metamodel must have Shuttle and Model classes.");
        }

        // Iterate through all references from Shuttle
        for (EReference shuttleRef : shuttleCls.getEAllReferences()) {
            EClass targetType = shuttleRef.getEReferenceType();
            if (targetType == null) continue;

            // Skip infrastructure types: Shuttle, Model, Track
            if (sameEClassName(targetType, shuttleCls) || sameEClassName(targetType, modelCls)) {
                continue;
            }
            if (trackCls != null && sameEClassName(targetType, trackCls)) {
                continue;
            }

            // Require the type to have a reference back to Shuttle.
            // This distinguishes pure marker types (Meta1-Meta5 have s0 → Shuttle)
            // from planning types (RsX2-5, RsY2-5 have no reference to Shuttle).
            boolean hasRefToShuttle = false;
            for (EReference ref : targetType.getEAllReferences()) {
                if (sameEClassName(ref.getEReferenceType(), shuttleCls)) {
                    hasRefToShuttle = true;
                    break;
                }
            }
            if (!hasRefToShuttle) continue;

            // Check if Model has a containment reference to this type
            EReference modelContainmentRef = null;
            for (EReference ref : modelCls.getEAllReferences()) {
                if (ref.isContainment() && sameEClassName(ref.getEReferenceType(), targetType)) {
                    modelContainmentRef = ref;
                    break;
                }
            }
            if (modelContainmentRef == null) continue;

            // This type can serve as a marker node
            result.add(new String[] {
                targetType.getName(),
                shuttleRef.getName(),
                modelContainmentRef.getName()
            });

            System.out.println("[findMarkerNodeTypes] Found: " + targetType.getName()
                + " (Shuttle." + shuttleRef.getName()
                + ", Model." + modelContainmentRef.getName() + ")");
        }

        return result;
    }

    // ============================================================
    // POST-LOAD REPAIR: fix Edge source/target from raw XMI IDREFs
    // ============================================================

    /**
     * The Henshin XMI loader sometimes fails to resolve Edge.source and
     * Edge.target IDREF attributes, leaving them null.  This method parses
     * the raw XML to extract those ID references and wires them up manually
     * using the resource's xmi:id &rarr; EObject mapping.
     */
    private static void repairEdgeReferences(Resource resource, File xmlFile) {
        // 1) Build a map: xmi:id -> loaded EObject
        Map<String, EObject> idMap = new HashMap<>();

        if (resource instanceof XMIResource) {
            XMIResource xmiRes = (XMIResource) resource;
            TreeIterator<EObject> it = resource.getAllContents();
            while (it.hasNext()) {
                EObject obj = it.next();
                String id = xmiRes.getID(obj);
                if (id != null && !id.isEmpty()) {
                    idMap.put(id, obj);
                }
            }
        }

        if (idMap.isEmpty()) {
            System.out.println("[repairEdgeRefs] No xmi:id entries found in resource — cannot repair.");
            return;
        }
        System.out.println("[repairEdgeRefs] ID map has " + idMap.size() + " entries.");

        // 2) Parse the raw XML to get edge source/target attribute values
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            // non-namespace-aware so we can use simple attribute names
            dbf.setNamespaceAware(false);
            org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(xmlFile);

            org.w3c.dom.NodeList edgeElements = doc.getElementsByTagName("edges");
            int repaired = 0;

            for (int i = 0; i < edgeElements.getLength(); i++) {
                org.w3c.dom.Element edgeEl = (org.w3c.dom.Element) edgeElements.item(i);
                String edgeId   = edgeEl.getAttribute("xmi:id");
                String sourceId = edgeEl.getAttribute("source");
                String targetId = edgeEl.getAttribute("target");

                if (edgeId == null || edgeId.isEmpty()) continue;

                EObject edgeObj = idMap.get(edgeId);
                if (!(edgeObj instanceof Edge)) continue;
                Edge edge = (Edge) edgeObj;

                if (edge.getSource() == null && sourceId != null && !sourceId.isEmpty()) {
                    EObject srcObj = idMap.get(sourceId);
                    if (srcObj instanceof Node) {
                        edge.setSource((Node) srcObj);
                        repaired++;
                    }
                }

                if (edge.getTarget() == null && targetId != null && !targetId.isEmpty()) {
                    EObject tgtObj = idMap.get(targetId);
                    if (tgtObj instanceof Node) {
                        edge.setTarget((Node) tgtObj);
                        repaired++;
                    }
                }
            }

            System.out.println("[repairEdgeRefs] Repaired " + repaired + " edge->node references.");

        } catch (Exception ex) {
            System.out.println("[repairEdgeRefs] XML parse error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ============================================================
    // CORE LOGIC: add planning nodes on RHS (generic for any compatible type)
    // ============================================================

    private static String applyNodeExtension(Module module, Rule rule, int k, String nodeTypeName) {
        StringBuilder desc = new StringBuilder();
        desc.append("[Planning-Aware]\n");
        desc.append("  Node type: ").append(nodeTypeName).append("\n");
        desc.append("  Planning horizon k = ").append(k).append("\n");

        Graph lhs = rule.getLhs();
        Graph rhs = rule.getRhs();
        if (lhs == null || rhs == null) throw new IllegalStateException("Rule has no LHS/RHS.");

        EPackage pkg = resolveMetamodelPackage(module);
        if (pkg == null) {
            throw new IllegalStateException("Could not resolve metamodel EPackage.");
        }

        EClass modelCls   = (EClass) pkg.getEClassifier("Model");
        EClass shuttleCls = (EClass) pkg.getEClassifier("Shuttle");
        EClass trackCls   = (EClass) pkg.getEClassifier("Track");
        EClass nodeTypeCls = (EClass) pkg.getEClassifier(nodeTypeName);

        if (nodeTypeCls == null) {
            throw new IllegalStateException("Node type '" + nodeTypeName + "' not found in metamodel.");
        }

        // Find references dynamically
        EReference shuttle_at = (EReference) shuttleCls.getEStructuralFeature("at");
        EReference track_next = (EReference) trackCls.getEStructuralFeature("next");

        // Find reference from Shuttle to the selected node type
        EReference shuttle_to_nodeType = findReferenceToType(shuttleCls, nodeTypeCls);
        if (shuttle_to_nodeType == null) {
            throw new IllegalStateException("No reference from Shuttle to " + nodeTypeName + " found.");
        }

        // Find self-reference on the node type (for chaining)
        EReference nodeType_chain = findSelfReference(nodeTypeCls);
        if (nodeType_chain == null) {
            throw new IllegalStateException("No self-reference (chaining) found on " + nodeTypeName + ".");
        }

        // Find reference from node type to Track (for planning)
        EReference nodeType_plan = findReferenceToType(nodeTypeCls, trackCls);
        if (nodeType_plan == null) {
            throw new IllegalStateException("No reference from " + nodeTypeName + " to Track found.");
        }

        // Find reference from Model to the node type (for containment)
        EReference model_to_nodeType = findReferenceToType(modelCls, nodeTypeCls);

        desc.append("  References used:\n");
        desc.append("    - Shuttle." + shuttle_to_nodeType.getName() + " -> " + nodeTypeName + "\n");
        desc.append("    - " + nodeTypeName + "." + nodeType_chain.getName() + " -> " + nodeTypeName + " (chain)\n");
        desc.append("    - " + nodeTypeName + "." + nodeType_plan.getName() + " -> Track (plan)\n");
        if (model_to_nodeType != null) {
            desc.append("    - Model." + model_to_nodeType.getName() + " -> " + nodeTypeName + " (containment)\n");
        }

        Node rhsRoot = findNodeByNameOrType(rhs, "root", "Model");
        if (rhsRoot == null) throw new IllegalStateException("RHS root Model node not found.");

        Node activeShuttle = findActiveShuttleRhs(rule, shuttle_at, shuttleCls);
        if (activeShuttle == null) {
            throw new IllegalStateException("Could not detect active shuttle");
        }
        System.out.println("[NodeExtension] Active shuttle: " + safeNode(activeShuttle));
        desc.append("  Active shuttle: ").append(safeNode(activeShuttle)).append("\n");

        Node tAct = getSingleTarget(rhs, activeShuttle, shuttle_at);
        if (tAct == null) throw new IllegalStateException("Active shuttle has no RHS 'at' edge.");
        desc.append("  Shuttle position (RHS): ").append(safeNode(tAct)).append("\n");

        // Create k nodes of the selected type
        String nodePrefix = nodeTypeName.toLowerCase();
        List<Node> createdNodes = new ArrayList<>(k);
        List<String> createdNodeNames = new ArrayList<>();

        for (int i = 1; i <= k; i++) {
            Node n = HenshinFactory.eINSTANCE.createNode();
            n.setType(nodeTypeCls);
            n.setName(nodePrefix + "_" + i);
            rhs.getNodes().add(n);

            // Add containment edge from Model if reference exists
            if (model_to_nodeType != null) {
                addEdgeToGraph(rhs, makeEdge(rhsRoot, n, model_to_nodeType));
            }
            createdNodes.add(n);
            createdNodeNames.add(nodePrefix + "_" + i);
        }

        // Connect shuttle to first node
        addEdgeToGraph(rhs, makeEdge(activeShuttle, createdNodes.get(0), shuttle_to_nodeType));

        // Chain the nodes together
        for (int i = 0; i < createdNodes.size() - 1; i++) {
            addEdgeToGraph(rhs, makeEdge(createdNodes.get(i), createdNodes.get(i + 1), nodeType_chain));
        }

        desc.append("  Created nodes (RHS only): ").append(String.join(", ", createdNodeNames)).append("\n");
        desc.append("  Edges added:\n");
        desc.append("    - ").append(safeNode(activeShuttle)).append(" --").append(shuttle_to_nodeType.getName())
            .append("--> ").append(nodePrefix).append("_1\n");
        for (int i = 0; i < createdNodes.size() - 1; i++) {
            desc.append("    - ").append(nodePrefix).append("_").append(i + 1)
                .append(" --").append(nodeType_chain.getName()).append("--> ")
                .append(nodePrefix).append("_").append(i + 2).append("\n");
        }

        // Add plan edges to tracks
        for (int i = 1; i <= createdNodes.size(); i++) {
            Node target = advanceByNext(rhs, tAct, track_next, i);
            if (target != null) {
                addEdgeToGraph(rhs, makeEdge(createdNodes.get(i - 1), target, nodeType_plan));
                desc.append("    - ").append(nodePrefix).append("_").append(i)
                    .append(" --").append(nodeType_plan.getName()).append("--> ")
                    .append(safeNode(target)).append("\n");
            }
        }

        desc.append("\n");
        return desc.toString();
    }

    /**
     * Find a reference from sourceClass to targetClass.
     */
    private static EReference findReferenceToType(EClass sourceClass, EClass targetClass) {
        for (EReference ref : sourceClass.getEAllReferences()) {
            if (sameEClassName(ref.getEReferenceType(), targetClass)) {
                return ref;
            }
        }
        return null;
    }

    /**
     * Find a self-reference on the given class (reference whose type is the class itself).
     */
    private static EReference findSelfReference(EClass cls) {
        for (EReference ref : cls.getEAllReferences()) {
            if (sameEClassName(ref.getEReferenceType(), cls)) {
                return ref;
            }
        }
        return null;
    }

    // ============================================================
    // δ-SHIFT OPERATION: PASSIVE SHUTTLE BACKWARD MOVEMENT
    // ============================================================

    private static int generatedTrackCounter = 0;

    private static String applyPassiveShuttleBackwardMovement(Module module, Rule rule, int backwardSteps) {
        generatedTrackCounter = 0;
        StringBuilder desc = new StringBuilder();
        desc.append("[δ-Shift-Operation]\n");
        desc.append("  Backward steps = ").append(backwardSteps).append("\n");

        Graph lhs = rule.getLhs();
        Graph rhs = rule.getRhs();
        if (lhs == null || rhs == null) throw new IllegalStateException("Rule has no LHS/RHS.");

        EPackage pkg = resolveMetamodelPackage(module);
        if (pkg == null) {
            throw new IllegalStateException("Could not resolve AmEm EPackage.");
        }

        EClass modelCls   = (EClass) pkg.getEClassifier("Model");
        EClass shuttleCls = (EClass) pkg.getEClassifier("Shuttle");
        EClass trackCls   = (EClass) pkg.getEClassifier("Track");
        EClass rsx1Cls    = (EClass) pkg.getEClassifier("RsX1");

        EReference model_tracks = (EReference) modelCls.getEStructuralFeature("tracks");
        EReference shuttle_at   = (EReference) shuttleCls.getEStructuralFeature("at");
        EReference shuttle_sx1  = (EReference) shuttleCls.getEStructuralFeature("sx1");
        EReference track_next   = (EReference) trackCls.getEStructuralFeature("next");
        EReference rsx1_plan    = (EReference) rsx1Cls.getEStructuralFeature("plan");
        EReference rsx1_btx     = (EReference) rsx1Cls.getEStructuralFeature("btx");

        Node lhsRoot = findNodeByNameOrType(lhs, "root", "Model");
        Node rhsRoot = findNodeByNameOrType(rhs, "root", "Model");
        if (lhsRoot == null || rhsRoot == null) {
            throw new IllegalStateException("LHS/RHS root Model node not found.");
        }

        // Find passive shuttles
        List<Node[]> passiveShuttles = findPassiveShuttles(rule, shuttle_at, shuttleCls);
        if (passiveShuttles.isEmpty()) {
            System.out.println("[δ-Shift] No passive shuttles found - nothing to move.");
            desc.append("  No passive shuttles found - no changes made.\n\n");
            return desc.toString();
        }

        System.out.println("[δ-Shift] Found " + passiveShuttles.size() + " passive shuttle(s). Moving backward by " + backwardSteps + " steps.");
        desc.append("  Found ").append(passiveShuttles.size()).append(" passive shuttle(s)\n");

        for (Node[] pair : passiveShuttles) {
            Node lhsShuttle = pair[0];
            Node rhsShuttle = pair[1];

            String originalShuttleName = lhsShuttle.getName();
            System.out.println("[δ-Shift] Processing passive shuttle: " + safeNode(rhsShuttle));
            desc.append("\n  Processing: ").append(safeNode(rhsShuttle)).append("\n");

            // Get current track
            Node currentLhsTrack = getSingleTarget(lhs, lhsShuttle, shuttle_at);
            Node currentRhsTrack = getSingleTarget(rhs, rhsShuttle, shuttle_at);

            if (currentLhsTrack == null || currentRhsTrack == null) {
                System.out.println("[δ-Shift] Passive shuttle has no 'at' edge - skipping.");
                desc.append("    Skipped (no 'at' edge)\n");
                continue;
            }

            String originalTrackName = currentLhsTrack.getName();
            System.out.println("[δ-Shift] Current track: LHS=" + safeNode(currentLhsTrack) + ", RHS=" + safeNode(currentRhsTrack));
            desc.append("    Original position: ").append(safeNode(currentLhsTrack)).append("\n");

            List<String> createdTrackNames = new ArrayList<>();
            List<Node[]> createdTrackNodes = new ArrayList<>();  // Store for adding descriptions later

            // Navigate backward
            for (int i = 1; i <= backwardSteps; i++) {
                Node prevLhs = findPreviousTrack(lhs, currentLhsTrack, track_next);
                Node prevRhs = findPreviousTrack(rhs, currentRhsTrack, track_next);

                if (prevLhs == null || prevRhs == null) {
                    // Need to create new track
                    generatedTrackCounter++;
                    String newTrackName = "t_back_" + generatedTrackCounter;

                    System.out.println("[δ-Shift] Creating new track: " + newTrackName);
                    createdTrackNames.add(newTrackName);

                    Node[] newTracks = createTrackWithMapping(rule, newTrackName, trackCls, model_tracks, lhsRoot, rhsRoot);
                    Node newLhsTrack = newTracks[0];
                    Node newRhsTrack = newTracks[1];

                    createdTrackNodes.add(newTracks);

                    // Add next edges: new track -> current track
                    addEdgeToGraph(lhs, makeEdge(newLhsTrack, currentLhsTrack, track_next));
                    addEdgeToGraph(rhs, makeEdge(newRhsTrack, currentRhsTrack, track_next));

                    prevLhs = newLhsTrack;
                    prevRhs = newRhsTrack;
                }

                currentLhsTrack = prevLhs;
                currentRhsTrack = prevRhs;
            }

            String newTrackName = currentLhsTrack.getName();
            System.out.println("[δ-Shift] New track position: LHS=" + safeNode(currentLhsTrack) + ", RHS=" + safeNode(currentRhsTrack));
            desc.append("    New position: ").append(safeNode(currentLhsTrack)).append("\n");

            if (!createdTrackNames.isEmpty()) {
                desc.append("    Created tracks (LHS+RHS with mapping): ").append(String.join(", ", createdTrackNames)).append("\n");
            }

            // Update shuttle's at edge
            removeEdge(lhs, lhsShuttle, shuttle_at);
            removeEdge(rhs, rhsShuttle, shuttle_at);
            addEdgeToGraph(lhs, makeEdge(lhsShuttle, currentLhsTrack, shuttle_at));
            addEdgeToGraph(rhs, makeEdge(rhsShuttle, currentRhsTrack, shuttle_at));

            // ============================================================
            // ADD DESCRIPTIONS TO NODES FOR EASY IDENTIFICATION OF CHANGES
            // ============================================================

            // 1. Rename shuttle nodes to indicate they were shifted
            String shiftedName = originalShuttleName + "_SHIFTED";
            lhsShuttle.setName(shiftedName);
            rhsShuttle.setName(shiftedName);

            // 2. Add description to shuttle nodes
            String shuttleDesc = "[DELTA-SHIFT] This shuttle was moved backward by " + backwardSteps + " step(s). "
                    + "Original position: " + originalTrackName + " -> New position: " + newTrackName;
            lhsShuttle.setDescription(shuttleDesc);
            rhsShuttle.setDescription(shuttleDesc);

            // 3. Add descriptions to newly created track nodes
            int trackIndex = createdTrackNodes.size();
            for (Node[] trackPair : createdTrackNodes) {
                String trackDesc = "[DELTA-SHIFT] Generated track #" + trackIndex + " for shuttle '" + originalShuttleName + "' "
                        + "(shifted back " + backwardSteps + " steps)";
                trackPair[0].setDescription(trackDesc);  // LHS track
                trackPair[1].setDescription(trackDesc);  // RHS track
                trackIndex--;
            }

            desc.append("    Renamed shuttle: ").append(originalShuttleName).append(" -> ").append(shiftedName).append("\n");
            desc.append("    Updated edges:\n");
            desc.append("      - LHS: ").append(shiftedName).append(" --at--> ").append(newTrackName).append("\n");
            desc.append("      - RHS: ").append(shiftedName).append(" --at--> ").append(newTrackName).append("\n");

            // Handle existing RsX1 nodes (if any)
            boolean hasRsX1 = getSingleTarget(rhs, rhsShuttle, shuttle_sx1) != null;
            if (hasRsX1) {
                desc.append("    Adjusted existing RsX1 plan edges\n");
            }
            adjustRsX1PlanEdgesForPassiveShuttle(rhs, rhsShuttle, currentRhsTrack,
                    shuttle_sx1, rsx1_btx, rsx1_plan, track_next);
        }

        desc.append("\n");
        return desc.toString();
    }

    /**
     * Find passive shuttles: shuttles where 'at' edge points to the same track in LHS and RHS.
     * Returns list of [lhsShuttle, rhsShuttle] pairs.
     */
    private static List<Node[]> findPassiveShuttles(Rule rule, EReference shuttleAtRef, EClass shuttleCls) {
        Graph lhs = rule.getLhs();
        Graph rhs = rule.getRhs();
        List<Node[]> result = new ArrayList<>();

        for (Mapping m : rule.getMappings()) {
            Node l = m.getOrigin();
            Node r = m.getImage();
            if (l == null || r == null) continue;

            if (!sameEClassName(r.getType(), shuttleCls)) continue;

            Node lhsAt = getSingleTarget(lhs, l, shuttleAtRef);
            Node rhsAt = getSingleTarget(rhs, r, shuttleAtRef);

            if (lhsAt != null && rhsAt != null) {
                // Check if they point to the same track (via mapping)
                Node mappedLhsAt = findMappedNode(rule, lhsAt);
                if (mappedLhsAt == rhsAt) {
                    // Same track = passive shuttle
                    result.add(new Node[]{l, r});
                }
            }
        }

        return result;
    }

    /**
     * Find the RHS node that the given LHS node maps to.
     */
    private static Node findMappedNode(Rule rule, Node lhsNode) {
        for (Mapping m : rule.getMappings()) {
            if (m.getOrigin() == lhsNode) {
                return m.getImage();
            }
        }
        return null;
    }

    /**
     * Find the track whose 'next' edge points to the given track (i.e., the previous track).
     */
    private static Node findPreviousTrack(Graph g, Node currentTrack, EReference trackNextRef) {
        for (Edge e : getAllEdges(g)) {
            if (sameFeature(e.getType(), trackNextRef) && e.getTarget() == currentTrack) {
                return e.getSource();
            }
        }
        return null;
    }

    /**
     * Create a new track node in both LHS and RHS with a mapping between them.
     * Returns [lhsNode, rhsNode].
     */
    private static Node[] createTrackWithMapping(Rule rule, String trackName, EClass trackCls,
            EReference modelTracksRef, Node lhsRoot, Node rhsRoot) {

        Graph lhs = rule.getLhs();
        Graph rhs = rule.getRhs();

        // Create LHS track
        Node lhsTrack = HenshinFactory.eINSTANCE.createNode();
        lhsTrack.setType(trackCls);
        lhsTrack.setName(trackName);
        lhs.getNodes().add(lhsTrack);

        // Create RHS track
        Node rhsTrack = HenshinFactory.eINSTANCE.createNode();
        rhsTrack.setType(trackCls);
        rhsTrack.setName(trackName);
        rhs.getNodes().add(rhsTrack);

        // Add model --tracks--> track edges
        addEdgeToGraph(lhs, makeEdge(lhsRoot, lhsTrack, modelTracksRef));
        addEdgeToGraph(rhs, makeEdge(rhsRoot, rhsTrack, modelTracksRef));

        // Create mapping
        Mapping mapping = HenshinFactory.eINSTANCE.createMapping();
        mapping.setOrigin(lhsTrack);
        mapping.setImage(rhsTrack);
        rule.getMappings().add(mapping);

        return new Node[]{lhsTrack, rhsTrack};
    }

    /**
     * Remove an edge from a graph. Properly disconnects bidirectional references.
     */
    private static void removeEdge(Graph g, Node source, EReference type) {
        Edge toRemove = null;
        for (Edge e : getAllEdges(g)) {
            if (e.getSource() == source && sameFeature(e.getType(), type)) {
                toRemove = e;
                break;
            }
        }
        if (toRemove != null) {
            // Must disconnect source/target first to break bidirectional refs
            // (Node.outgoing <-> Edge.source, Node.incoming <-> Edge.target)
            toRemove.setSource(null);
            toRemove.setTarget(null);
            g.getEdges().remove(toRemove);
        }
    }

    /**
     * Adjust existing RsX1 nodes' plan edges when the passive shuttle moves backward.
     * Each RsX1 in the chain points N steps ahead of the shuttle's current position.
     */
    private static void adjustRsX1PlanEdgesForPassiveShuttle(Graph rhs, Node passiveShuttle,
            Node newShuttleTrack, EReference sx1Ref, EReference btxRef,
            EReference planRef, EReference nextRef) {

        // Get the RsX1 chain starting from shuttle.sx1
        Node rsx1 = getSingleTarget(rhs, passiveShuttle, sx1Ref);
        if (rsx1 == null) {
            System.out.println("[δ-Shift] No RsX1 nodes on passive shuttle - nothing to adjust.");
            return;
        }

        System.out.println("[δ-Shift] Adjusting RsX1 plan edges for passive shuttle.");

        // Walk the RsX1 chain and update plan edges
        int planStep = 1;  // First RsX1 points 1 step ahead, second 2 steps, etc.
        while (rsx1 != null) {
            // Remove old plan edge
            removeEdge(rhs, rsx1, planRef);

            // Calculate new plan target: advance planStep from new shuttle position
            Node newPlanTarget = advanceByNext(rhs, newShuttleTrack, nextRef, planStep);
            if (newPlanTarget != null) {
                addEdgeToGraph(rhs, makeEdge(rsx1, newPlanTarget, planRef));
                System.out.println("[δ-Shift] RsX1 plan edge updated: " + safeNode(rsx1)
                        + " --plan--> " + safeNode(newPlanTarget));
            } else {
                System.out.println("[δ-Shift] Warning: Could not find plan target for step " + planStep);
            }

            // Move to next RsX1 in chain
            rsx1 = getSingleTarget(rhs, rsx1, btxRef);
            planStep++;
        }
    }


    // ============================================================
    // ACTIVE SHUTTLE DETECTION
    // ============================================================

    private static Node findActiveShuttleRhs(Rule rule, EReference shuttleAtRef, EClass shuttleCls) {
        Graph lhs = rule.getLhs();
        Graph rhs = rule.getRhs();

        for (Mapping m : rule.getMappings()) {
            Node l = m.getOrigin();
            Node r = m.getImage();
            if (l == null || r == null) continue;

            if (!sameEClassName(r.getType(), shuttleCls)) continue;

            Node lhsAt = getSingleTarget(lhs, l, shuttleAtRef);
            Node rhsAt = getSingleTarget(rhs, r, shuttleAtRef);

            if (lhsAt != null && rhsAt != null && lhsAt != rhsAt) {
                return r;
            }
        }

        for (Node r : rhs.getNodes()) {
            if (!sameEClassName(r.getType(), shuttleCls)) continue;
            if (r.getName() == null) continue;

            Node l = findNodeByName(lhs, r.getName());
            if (l == null) continue;

            Node lhsAt = getSingleTarget(lhs, l, shuttleAtRef);
            Node rhsAt = getSingleTarget(rhs, r, shuttleAtRef);

            if (lhsAt != null && rhsAt != null && lhsAt != rhsAt) {
                return r;
            }
        }

        return null;
    }

    // ============================================================
    // METAMODEL RESOLUTION
    // ============================================================

    private static EPackage resolveMetamodelPackage(Module module) {
        if (module == null || module.getImports() == null) return null;

        for (Object imp : module.getImports()) {
            if (imp instanceof EPackage) {
                EPackage p = (EPackage) imp;
                if (p.getEClassifier("Model") != null &&
                    p.getEClassifier("Shuttle") != null &&
                    p.getEClassifier("Track") != null &&
                    p.getEClassifier("RsX1") != null) {
                    return p;
                }
            } else if (imp instanceof EObject) {
                EObject resolved = EcoreUtil.resolve((EObject) imp, module);
                if (resolved instanceof EPackage) {
                    EPackage p = (EPackage) resolved;
                    if (p.getEClassifier("Model") != null &&
                        p.getEClassifier("Shuttle") != null &&
                        p.getEClassifier("Track") != null &&
                        p.getEClassifier("RsX1") != null) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    // ============================================================
    // EDGE HANDLING
    // ============================================================

    private static void addEdgeToGraph(Graph g, Edge e) {
        if (g == null || e == null) return;
        g.getEdges().add(e);
    }

    private static List<Edge> getAllEdges(Graph g) {
        List<Edge> res = new ArrayList<>();
        if (g == null) return res;

        try {
            res.addAll(g.getEdges());
        } catch (Exception ignore) {}

        TreeIterator<EObject> it = g.eAllContents();
        while (it.hasNext()) {
            EObject eo = it.next();
            if (eo instanceof Edge) {
                Edge e = (Edge) eo;
                if (!res.contains(e)) res.add(e);
            }
        }
        return res;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static boolean safeEq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean sameEClassName(EClass a, EClass b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return safeEq(a.getName(), b.getName());
    }

    private static boolean sameFeature(Object a, EReference b) {
        if (a == b) return true;
        if (!(a instanceof EReference) || b == null) return false;

        EReference ar = (EReference) a;

        if (!safeEq(ar.getName(), b.getName())) return false;

        EClass ac = ar.getEContainingClass();
        EClass bc = b.getEContainingClass();
        if (ac == null || bc == null) return true;
        return safeEq(ac.getName(), bc.getName());
    }

    private static Node getSingleTarget(Graph g, Node src, EReference ref) {
        if (g == null || src == null || ref == null) return null;
        for (Edge e : getAllEdges(g)) {
            if (e.getSource() == src && sameFeature(e.getType(), ref)) {
                return e.getTarget();
            }
        }
        return null;
    }

    private static Node advanceByNext(Graph g, Node start, EReference nextRef, int steps) {
        Node cur = start;
        for (int i = 0; i < steps; i++) {
            if (cur == null) return null;
            cur = getSingleTarget(g, cur, nextRef);
        }
        return cur;
    }

    private static Edge makeEdge(Node s, Node t, EReference r) {
        Edge e = HenshinFactory.eINSTANCE.createEdge();
        e.setSource(s);
        e.setTarget(t);
        e.setType(r);
        return e;
    }

    private static Node findNodeByNameOrType(Graph g, String name, String typeName) {
        if (g == null) return null;

        for (Node n : g.getNodes()) {
            if (safeEq(name, n.getName())) return n;
        }
        for (Node n : g.getNodes()) {
            if (n.getType() != null && safeEq(typeName, n.getType().getName())) return n;
        }
        return null;
    }

    private static Node findNodeByName(Graph g, String name) {
        if (g == null || name == null) return null;
        for (Node n : g.getNodes()) {
            if (safeEq(name, n.getName())) return n;
        }
        return null;
    }

    private static Rule findRulePreferName(Module m, String preferredName) {
        Rule firstRule = null;
        for (Unit u : m.getUnits()) {
            if (u instanceof Rule) {
                Rule r = (Rule) u;
                if (firstRule == null) firstRule = r;
                if (preferredName != null && preferredName.equals(r.getName())) return r;
            }
        }
        return firstRule;
    }

    // ============================================================
    // VERIFICATION
    // ============================================================

    private static void verifyEdges(String label, Graph g) {
        if (g == null) return;
        int ok = 0, broken = 0;
        for (Edge e : getAllEdges(g)) {
            if (e.getSource() != null && e.getTarget() != null) {
                ok++;
            } else {
                broken++;
                System.out.println("[WARNING] " + label + " edge has null src/tgt: type="
                        + (e.getType() != null ? e.getType().getName() : "null"));
            }
        }
        System.out.println("[" + label + "] " + ok + " OK edges, " + broken + " broken edges.");
    }

    private static String safeNode(Node n) {
        if (n == null) return "<null-node>";
        String name = n.getName();
        String type = (n.getType() != null ? n.getType().getName() : "nullType");
        return (name != null ? name : "<noName>") + ":" + type;
    }
}
