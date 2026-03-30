package henshin.plugin.views;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import henshin.modifier.HenshinRuleModifier;
import henshin.modifier.SampleAlgorithm;

public class HenshinPluginView extends ViewPart {

    public static final String ID = "henshin.plugin.view";

    private Text inputText;
    private Button loadRulesButton;
    private Combo ruleSelectionCombo;
    private Text ruleText;
    private Text outputText;
    private Combo modeSelectorCombo;
    // Mode-specific sections (composites with GridData.exclude for clean show/hide)
    private Composite planningSection;
    private Combo nodeTypeCombo;
    private Button loadTypesButton;
    private Text guardText;
    private Text invariantText;
    private Composite deltaShiftSection;
    private Text backwardStepsText;
    private Composite ruleSplitSection;
    private Button runButton;

    // Simulation section
    private Button simulateCheckbox;
    private Composite simSection;
    private Text simulatorRootText;
    private Text clockDeclText;
    private Text guardNodeText;
    private Text invariantNodeText;
    private Text clockResetsText;
    private Text probabilityText;
    private Text priorityText;
    private Text inputModelText;
    private Text simStepsText;
    private Button modifyAndSimulateButton;

    // Marker node type UI (Rule-Split mode) - inside ruleSplitSection
    private Combo markerTypeCombo;
    private Button loadMarkerTypesButton;

    // Stores the compatible node type info: [typeName, shuttleRefName, selfRefName, trackRefName]
    private List<String[]> compatibleNodeTypes = new ArrayList<>();

    // Stores the marker node type info: [typeName, shuttleRefName, modelContainmentRefName]
    private List<String[]> markerNodeTypes = new ArrayList<>();

    // Stores the rule names loaded from the input file
    private List<String> loadedRuleNames = new ArrayList<>();

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(3, false));

        // 1) Input file path with "Load Rules" button
        new Label(parent, SWT.NONE).setText("Input .henshin:");
        inputText = new Text(parent, SWT.BORDER);
        inputText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        inputText.setText("/Users/mg/Desktop/WorkSpaceEclipseMoD/HenshinRuleModifierPlugin/inputRules/PluginDrive.henshin");
        loadRulesButton = new Button(parent, SWT.PUSH);
        loadRulesButton.setText("Load Rules");
        loadRulesButton.addListener(SWT.Selection, e -> loadRuleNames());

        // 2) Rule selection combo (populated after Load Rules)
        new Label(parent, SWT.NONE).setText("Select Rule:");
        ruleSelectionCombo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        ruleSelectionCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        ruleSelectionCombo.setItems(new String[] {"(Click 'Load Rules' first)"});
        ruleSelectionCombo.select(0);
        ruleSelectionCombo.setEnabled(false);
        ruleSelectionCombo.addListener(SWT.Selection, e -> onRuleSelected());
        new Label(parent, SWT.NONE); // empty cell

        // 3) Output file path (auto-filled when rule is selected)
        new Label(parent, SWT.NONE).setText("Output .henshin:");
        outputText = new Text(parent, SWT.BORDER);
        outputText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        outputText.setText("");
        outputText.setEnabled(false);
        new Label(parent, SWT.NONE); // empty cell

        // 4) Rule name for output (auto-filled: <selectedRule>_MOD)
        new Label(parent, SWT.NONE).setText("Output Rule name:");
        ruleText = new Text(parent, SWT.BORDER);
        ruleText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        ruleText.setText("");
        ruleText.setEnabled(false);
        new Label(parent, SWT.NONE); // empty cell

        // 5) Mode selector
        new Label(parent, SWT.NONE).setText("Mode:");
        modeSelectorCombo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        modeSelectorCombo.setItems(new String[] {
            "Planning-Aware",
            "δ-Shift-Operation",
            "Rule-Split"
        });
        modeSelectorCombo.select(0);
        modeSelectorCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        modeSelectorCombo.setEnabled(false);
        new Label(parent, SWT.NONE); // empty cell

        // 6a) Planning-Aware section (composite, shown by default)
        planningSection = new Composite(parent, SWT.NONE);
        planningSection.setLayout(new GridLayout(3, false));
        GridData planningGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        planningGd.horizontalSpan = 3;
        planningSection.setLayoutData(planningGd);

        new Label(planningSection, SWT.NONE).setText("Node Type:");
        nodeTypeCombo = new Combo(planningSection, SWT.DROP_DOWN | SWT.READ_ONLY);
        nodeTypeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        nodeTypeCombo.setItems(new String[] {"(Click 'Load Types' first)"});
        nodeTypeCombo.select(0);
        nodeTypeCombo.setEnabled(false);
        loadTypesButton = new Button(planningSection, SWT.PUSH);
        loadTypesButton.setText("Load Types");
        loadTypesButton.setEnabled(false);
        loadTypesButton.addListener(SWT.Selection, e -> loadCompatibleNodeTypes());

        new Label(planningSection, SWT.NONE).setText("Guard:");
        guardText = new Text(planningSection, SWT.BORDER);
        guardText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        guardText.setText("3");
        guardText.setEnabled(false);
        new Label(planningSection, SWT.NONE); // empty cell

        new Label(planningSection, SWT.NONE).setText("Invariant:");
        invariantText = new Text(planningSection, SWT.BORDER);
        invariantText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        invariantText.setText("4");
        invariantText.setEnabled(false);
        new Label(planningSection, SWT.NONE); // empty cell

        // 6b) δ-Shift section (composite, hidden by default)
        deltaShiftSection = new Composite(parent, SWT.NONE);
        deltaShiftSection.setLayout(new GridLayout(3, false));
        GridData deltaGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        deltaGd.horizontalSpan = 3;
        deltaGd.exclude = true;
        deltaShiftSection.setLayoutData(deltaGd);
        deltaShiftSection.setVisible(false);

        new Label(deltaShiftSection, SWT.NONE).setText("Backward Steps:");
        backwardStepsText = new Text(deltaShiftSection, SWT.BORDER);
        backwardStepsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        backwardStepsText.setText("0");
        new Label(deltaShiftSection, SWT.NONE); // empty cell

        // 6c) Rule-Split section (composite, hidden by default)
        ruleSplitSection = new Composite(parent, SWT.NONE);
        ruleSplitSection.setLayout(new GridLayout(3, false));
        GridData splitGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        splitGd.horizontalSpan = 3;
        splitGd.exclude = true;
        ruleSplitSection.setLayoutData(splitGd);
        ruleSplitSection.setVisible(false);

        new Label(ruleSplitSection, SWT.NONE).setText("Marker Node Type:");
        markerTypeCombo = new Combo(ruleSplitSection, SWT.DROP_DOWN | SWT.READ_ONLY);
        markerTypeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        markerTypeCombo.setItems(new String[] {"(No marker node)"});
        markerTypeCombo.select(0);
        markerTypeCombo.setEnabled(false);
        loadMarkerTypesButton = new Button(ruleSplitSection, SWT.PUSH);
        loadMarkerTypesButton.setText("Load Marker Types");
        loadMarkerTypesButton.setEnabled(false);
        loadMarkerTypesButton.addListener(SWT.Selection, e -> loadMarkerNodeTypes());

        modeSelectorCombo.addListener(SWT.Selection, e -> updateModeVisibility());

        // 7) Run button
        runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Modify Rule");
        GridData gd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        gd.horizontalSpan = 3;
        runButton.setLayoutData(gd);
        runButton.setEnabled(false);
        runButton.addListener(SWT.Selection, e -> runModification());

        // ========================================================
        // 8) Simulate after modification checkbox
        // ========================================================
        simulateCheckbox = new Button(parent, SWT.CHECK);
        simulateCheckbox.setText("Simulate after modification");
        GridData simCheckGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        simCheckGd.horizontalSpan = 3;
        simulateCheckbox.setLayoutData(simCheckGd);
        simulateCheckbox.setEnabled(true);

        // 9) Simulation fields section (hidden by default)
        simSection = new Composite(parent, SWT.NONE);
        simSection.setLayout(new GridLayout(3, false));
        GridData simSectionGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        simSectionGd.horizontalSpan = 3;
        simSection.setLayoutData(simSectionGd);

        // 9-pre) Simulator Root with Browse button
        new Label(simSection, SWT.NONE).setText("Simulator Root:");
        simulatorRootText = new Text(simSection, SWT.BORDER);
        simulatorRootText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        simulatorRootText.setText("/Users/mg/Desktop/ptgts-simulator-master");
        Button browseSimRootButton = new Button(simSection, SWT.PUSH);
        browseSimRootButton.setText("Browse");
        browseSimRootButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                org.eclipse.swt.widgets.DirectoryDialog dialog =
                        new org.eclipse.swt.widgets.DirectoryDialog(getSite().getShell(), SWT.OPEN);
                dialog.setText("Select ptgts-simulator-master directory");
                String path = dialog.open();
                if (path != null) {
                    simulatorRootText.setText(path);
                }
            }
        });

        // 9a) Clock Declarations
        new Label(simSection, SWT.NONE).setText("Clock Declarations:");
        clockDeclText = new Text(simSection, SWT.BORDER);
        clockDeclText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        clockDeclText.setText("Track.c");
        new Label(simSection, SWT.NONE); // empty cell

        // 9b) Guard Node
        new Label(simSection, SWT.NONE).setText("Guard Node:");
        guardNodeText = new Text(simSection, SWT.BORDER);
        guardNodeText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        guardNodeText.setText("t1");
        new Label(simSection, SWT.NONE); // empty cell

        // 9c) Invariant Node
        new Label(simSection, SWT.NONE).setText("Invariant Node:");
        invariantNodeText = new Text(simSection, SWT.BORDER);
        invariantNodeText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        invariantNodeText.setText("t1");
        new Label(simSection, SWT.NONE); // empty cell

        // 9d) Clock Resets
        new Label(simSection, SWT.NONE).setText("Clock Resets:");
        clockResetsText = new Text(simSection, SWT.BORDER);
        clockResetsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        clockResetsText.setText("t2.c'=0");
        new Label(simSection, SWT.NONE); // empty cell

        // 9e) Probability
        new Label(simSection, SWT.NONE).setText("Probability:");
        probabilityText = new Text(simSection, SWT.BORDER);
        probabilityText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        probabilityText.setText("1.0");
        new Label(simSection, SWT.NONE); // empty cell

        // 9f) Priority
        new Label(simSection, SWT.NONE).setText("Priority:");
        priorityText = new Text(simSection, SWT.BORDER);
        priorityText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        priorityText.setText("0");
        new Label(simSection, SWT.NONE); // empty cell

        // 9g) Input Model specification
        // Accepts: fixed(shuttles:tracksPerShuttle), random(tracks:shuttles), or a .xmi file path
        new Label(simSection, SWT.NONE).setText("Input Model:");
        inputModelText = new Text(simSection, SWT.BORDER);
        inputModelText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        inputModelText.setText("fixed(2:5)");
        inputModelText.setToolTipText(
            "Model specification for the simulator.\n" +
            "  fixed(shuttles:tracksPerShuttle) - auto-generated circle topology\n" +
            "  random(tracks:shuttles) - random circle topology\n" +
            "  /path/to/model.xmi - file must contain SimulatorInputModel root element");
        Button browseModelButton = new Button(simSection, SWT.PUSH);
        browseModelButton.setText("Browse");
        browseModelButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                FileDialog dialog = new FileDialog(getSite().getShell(), SWT.OPEN);
                dialog.setFilterExtensions(new String[] {"*.xmi", "*.*"});
                dialog.setText("Select Input Model (.xmi)");
                String path = dialog.open();
                if (path != null) {
                    inputModelText.setText(path);
                }
            }
        });

        // 9h) Simulation Steps
        new Label(simSection, SWT.NONE).setText("Simulation Steps:");
        simStepsText = new Text(simSection, SWT.BORDER);
        simStepsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        simStepsText.setText("5000");
        new Label(simSection, SWT.NONE); // empty cell

        // 10) Modify & Simulate button
        modifyAndSimulateButton = new Button(simSection, SWT.PUSH);
        modifyAndSimulateButton.setText("Modify && Simulate");
        GridData simBtnGd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        simBtnGd.horizontalSpan = 3;
        modifyAndSimulateButton.setLayoutData(simBtnGd);
        modifyAndSimulateButton.addListener(SWT.Selection, e -> runModifyAndSimulate());

        // Initially hide the simulation section
        simSection.setVisible(false);
        ((GridData) simSection.getLayoutData()).exclude = true;

        // Toggle simulation section visibility
        simulateCheckbox.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean show = simulateCheckbox.getSelection();
                simSection.setVisible(show);
                ((GridData) simSection.getLayoutData()).exclude = !show;
                parent.layout(true, true);
            }
        });

        System.out.println("[henshin.plugin] View created");
    }

    /**
     * Load rule names from the input .henshin file and populate the rule selection combo.
     */
    private void loadRuleNames() {
        try {
            File inputFile = resolveInputFileSmart(inputText.getText().trim());

            List<String> ruleNames = HenshinRuleModifier.listRuleNames(inputFile);

            loadedRuleNames.clear();
            loadedRuleNames.addAll(ruleNames);

            if (ruleNames.isEmpty()) {
                ruleSelectionCombo.setItems(new String[] {"(No rules found)"});
                ruleSelectionCombo.select(0);
                ruleSelectionCombo.setEnabled(false);
                setPostRuleSelectionEnabled(false);
                MessageDialog.openWarning(getSite().getShell(), "No Rules Found",
                    "No rules found in the input file.");
            } else {
                ruleSelectionCombo.setItems(ruleNames.toArray(new String[0]));
                ruleSelectionCombo.select(0);
                ruleSelectionCombo.setEnabled(true);
                onRuleSelected(); // auto-fill output path and rule name for the first rule
                MessageDialog.openInformation(getSite().getShell(), "Rules Loaded",
                    "Found " + ruleNames.size() + " rule(s):\n" +
                    String.join(", ", ruleNames));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(getSite().getShell(), "Error Loading Rules", ex.getMessage());
        }
    }

    /**
     * Called when a rule is selected from the combo. Auto-fills output path and rule name,
     * and enables the rest of the UI.
     */
    private void onRuleSelected() {
        int idx = ruleSelectionCombo.getSelectionIndex();
        if (idx < 0 || idx >= loadedRuleNames.size()) return;

        // Auto-fill output path and rule name based on current mode
        updateOutputNaming();

        // Enable the rest of the UI
        setPostRuleSelectionEnabled(true);
    }

    /**
     * Enable or disable all UI controls below the rule selection.
     */
    private void setPostRuleSelectionEnabled(boolean enabled) {
        outputText.setEnabled(enabled);
        ruleText.setEnabled(enabled);
        modeSelectorCombo.setEnabled(enabled);
        // Planning-Aware controls
        nodeTypeCombo.setEnabled(enabled);
        loadTypesButton.setEnabled(enabled);
        guardText.setEnabled(enabled);
        invariantText.setEnabled(enabled);
        // δ-Shift controls
        backwardStepsText.setEnabled(enabled);
        // Rule-Split controls
        markerTypeCombo.setEnabled(enabled);
        loadMarkerTypesButton.setEnabled(enabled);
        // Run button
        runButton.setEnabled(enabled);
    }

    /**
     * Load compatible node types from the input file's metamodel.
     * A node type is compatible if:
     * 1. Shuttle has a reference to it
     * 2. It has a self-reference (for chaining)
     * 3. It has a reference to Track (for planning)
     */
    private void loadCompatibleNodeTypes() {
        try {
            File inputFile = resolveInputFileSmart(inputText.getText().trim());

            // Use HenshinRuleModifier to find compatible types
            List<String[]> types = HenshinRuleModifier.findCompatibleNodeTypes(inputFile);

            compatibleNodeTypes.clear();
            compatibleNodeTypes.addAll(types);

            if (types.isEmpty()) {
                nodeTypeCombo.setItems(new String[] {"(No compatible types found)"});
                nodeTypeCombo.select(0);
                MessageDialog.openWarning(getSite().getShell(), "No Types Found",
                    "No compatible node types found in the metamodel.\n\n" +
                    "A compatible type must:\n" +
                    "- Be reachable from Shuttle via a reference\n" +
                    "- Have a self-reference (for chaining)\n" +
                    "- Have a reference to Track (for planning)");
            } else {
                String[] typeNames = new String[types.size()];
                for (int i = 0; i < types.size(); i++) {
                    String[] info = types.get(i);
                    // Format: "TypeName (shuttle.refName -> type.selfRef -> Track)"
                    typeNames[i] = info[0] + " (via " + info[1] + ")";
                }
                nodeTypeCombo.setItems(typeNames);
                nodeTypeCombo.select(0);

                MessageDialog.openInformation(getSite().getShell(), "Types Loaded",
                    "Found " + types.size() + " compatible node type(s):\n" +
                    String.join(", ", typeNames));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(getSite().getShell(), "Error Loading Types", ex.getMessage());
        }
    }

    /**
     * Load marker node types from the input file's metamodel.
     * A marker node type must be reachable from Shuttle and contained by Model.
     */
    private void loadMarkerNodeTypes() {
        try {
            File inputFile = resolveInputFileSmart(inputText.getText().trim());

            List<String[]> types = HenshinRuleModifier.findMarkerNodeTypes(inputFile);

            markerNodeTypes.clear();
            markerNodeTypes.addAll(types);

            String[] items = new String[types.size() + 1];
            items[0] = "(No marker node)";
            for (int i = 0; i < types.size(); i++) {
                items[i + 1] = types.get(i)[0] + " (via Shuttle." + types.get(i)[1] + ")";
            }
            markerTypeCombo.setItems(items);
            markerTypeCombo.select(0);

            if (types.isEmpty()) {
                MessageDialog.openWarning(getSite().getShell(), "No Marker Types Found",
                    "No suitable marker node types found in the metamodel.\n\n" +
                    "A marker type must:\n" +
                    "- Be reachable from Shuttle via a reference\n" +
                    "- Be contained by Model via a containment reference");
            } else {
                MessageDialog.openInformation(getSite().getShell(), "Marker Types Loaded",
                    "Found " + types.size() + " marker node type(s).");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(getSite().getShell(), "Error Loading Marker Types", ex.getMessage());
        }
    }

    /**
     * Update output file and rule name based on selected mode and rule.
     */
    private void updateOutputNaming() {
        int ruleIdx = ruleSelectionCombo.getSelectionIndex();
        if (ruleIdx < 0 || ruleIdx >= loadedRuleNames.size()) return;

        String selectedRule = loadedRuleNames.get(ruleIdx);
        int modeIdx = modeSelectorCombo.getSelectionIndex();

        File inputFile = new File(inputText.getText().trim());
        File projectRoot = findProjectRoot(inputFile);
        String outDir = (projectRoot != null)
                ? new File(projectRoot, "outputRules").getAbsolutePath()
                : "outputRules";

        if (modeIdx == 2) {
            // Rule-Split: two separate output files
            ruleText.setText(selectedRule + "_Obs  +  " + selectedRule + "_Exe");
            outputText.setText(outDir + "/" + selectedRule + "_Obs.henshin  +  "
                    + selectedRule + "_Exe.henshin");
        } else {
            ruleText.setText(selectedRule + "_MOD");
            outputText.setText(outDir + "/" + selectedRule + "_MOD.henshin");
        }
    }

    private void runModification() {
        try {
            // Validate rule selection
            int ruleIdx = ruleSelectionCombo.getSelectionIndex();
            if (ruleIdx < 0 || ruleIdx >= loadedRuleNames.size()) {
                throw new IllegalArgumentException("Please select a rule first (click 'Load Rules').");
            }
            String selectedRuleName = loadedRuleNames.get(ruleIdx);

            // 1) Input finden (absolut ODER workspace/projekt-relativ)
            File input = resolveInputFileSmart(inputText.getText().trim());

            int selectedModeIndex = modeSelectorCombo.getSelectionIndex();

            // Rule-Split has its own output path handling (two files)
            if (selectedModeIndex == 2) {
                runRuleSplit(input, selectedRuleName);
                return;
            }

            // 2) Output auf Projekt-Root auflösen (gleicher Projekt-Root wie Input)
            File output = resolveOutputFileSmart(outputText.getText().trim(), input);

            String ruleName = ruleText.getText().trim();
            if (ruleName.isEmpty()) {
                throw new IllegalArgumentException("Output rule name must not be empty.");
            }

            ensureParentFolderExists(output);

            if (selectedModeIndex == 0) {
                // Planning-Aware mode
                int guard = parseIntInRange(guardText.getText().trim(), 0, 15, "Guard");
                int invariant = parseIntInRange(invariantText.getText().trim(), 0, 15, "Invariant");

                validatePaperPreconditions(guard, invariant);

                int k = computePlanningHorizon(guard, invariant, 1);

                // Get selected node type
                int typeIndex = nodeTypeCombo.getSelectionIndex();
                if (typeIndex < 0 || compatibleNodeTypes.isEmpty()) {
                    throw new IllegalArgumentException("Please click 'Load Types' first to select a compatible node type.");
                }

                String[] typeInfo = compatibleNodeTypes.get(typeIndex);
                String nodeTypeName = typeInfo[0];

                HenshinRuleModifier.modifyRuleInModule(
                        input,
                        selectedRuleName,
                        ruleName,
                        new SampleAlgorithm(k, 0, nodeTypeName),
                        output
                );

                MessageDialog.openInformation(
                        getSite().getShell(),
                        "Success",
                        "Planning-Aware applied:\n" +
                        "  Source rule: " + selectedRuleName + "\n" +
                        "  Node type: " + nodeTypeName + "\n" +
                        "  k = " + k + "\n" +
                        "\nInput:\n" + input.getAbsolutePath() +
                        "\nOutput:\n" + output.getAbsolutePath()
                );

            } else if (selectedModeIndex == 1) {
                // δ-Shift-Operation mode
                int backwardSteps = parseIntInRange(backwardStepsText.getText().trim(), 0, 10, "Backward Steps");

                HenshinRuleModifier.modifyRuleInModule(
                        input,
                        selectedRuleName,
                        ruleName,
                        new SampleAlgorithm(0, backwardSteps, null),
                        output
                );

                MessageDialog.openInformation(
                        getSite().getShell(),
                        "Success",
                        "δ-Shift-Operation applied:\n" +
                        "  Source rule: " + selectedRuleName + "\n" +
                        "  Backward steps = " + backwardSteps
                        + "\nInput:\n" + input.getAbsolutePath()
                        + "\nOutput:\n" + output.getAbsolutePath()
                );

            } else {
                throw new IllegalStateException("Unknown mode selected: index=" + selectedModeIndex);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(getSite().getShell(), "Error", ex.getMessage());
        }
    }

    /**
     * Rule-Split: produces two separate .henshin output files (_Obs and _Exe).
     */
    private void runRuleSplit(File input, String selectedRuleName) throws Exception {
        int markerIdx = markerTypeCombo.getSelectionIndex();
        String markerTypeName = null;
        if (markerIdx > 0 && markerIdx <= markerNodeTypes.size()) {
            markerTypeName = markerNodeTypes.get(markerIdx - 1)[0];
        }

        // Derive output directory
        File outputDir = input.getParentFile();
        File projectRoot = findProjectRoot(input);
        if (projectRoot != null) {
            outputDir = new File(projectRoot, "outputRules");
        }
        ensureParentFolderExists(new File(outputDir, "dummy"));

        File obsFile = new File(outputDir, selectedRuleName + "_Obs.henshin");
        File exeFile = new File(outputDir, selectedRuleName + "_Exe.henshin");

        HenshinRuleModifier.splitRuleToFiles(
                input, selectedRuleName, markerTypeName, obsFile, exeFile);

        MessageDialog.openInformation(
                getSite().getShell(),
                "Success",
                "Rule-Split applied successfully!\n\n" +
                "  Source rule: " + selectedRuleName + "\n" +
                "  Marker type: " + (markerTypeName != null ? markerTypeName : "(none)") + "\n\n" +
                "  Output files:\n" +
                "    1) " + obsFile.getAbsolutePath() + "\n" +
                "    2) " + exeFile.getAbsolutePath()
        );
    }

    /**
     * Run modification and then launch the simulation pipeline.
     */
    private void runModifyAndSimulate() {
        try {
            // First, run the modification (same as runModification but without success dialog)
            int ruleIdx = ruleSelectionCombo.getSelectionIndex();
            if (ruleIdx < 0 || ruleIdx >= loadedRuleNames.size()) {
                throw new IllegalArgumentException("Please select a rule first (click 'Load Rules').");
            }
            String selectedRuleName = loadedRuleNames.get(ruleIdx);

            File input = resolveInputFileSmart(inputText.getText().trim());
            int selectedModeIndex = modeSelectorCombo.getSelectionIndex();

            File output;
            String ruleName;

            if (selectedModeIndex == 0) {
                // Planning-Aware mode
                output = resolveOutputFileSmart(outputText.getText().trim(), input);
                ruleName = ruleText.getText().trim();
                ensureParentFolderExists(output);

                int guard = parseIntInRange(guardText.getText().trim(), 0, 15, "Guard");
                int invariant = parseIntInRange(invariantText.getText().trim(), 0, 15, "Invariant");
                validatePaperPreconditions(guard, invariant);
                int k = computePlanningHorizon(guard, invariant, 1);

                int typeIndex = nodeTypeCombo.getSelectionIndex();
                if (typeIndex < 0 || compatibleNodeTypes.isEmpty()) {
                    throw new IllegalArgumentException("Please click 'Load Types' first to select a compatible node type.");
                }
                String nodeTypeName = compatibleNodeTypes.get(typeIndex)[0];

                HenshinRuleModifier.modifyRuleInModule(
                        input, selectedRuleName, ruleName,
                        new SampleAlgorithm(k, 0, nodeTypeName), output);

            } else if (selectedModeIndex == 1) {
                output = resolveOutputFileSmart(outputText.getText().trim(), input);
                ruleName = ruleText.getText().trim();
                ensureParentFolderExists(output);

                int backwardSteps = parseIntInRange(backwardStepsText.getText().trim(), 0, 10, "Backward Steps");
                HenshinRuleModifier.modifyRuleInModule(
                        input, selectedRuleName, ruleName,
                        new SampleAlgorithm(0, backwardSteps, null), output);

            } else if (selectedModeIndex == 2) {
                // Rule-Split mode — produces two separate output files
                int markerIdx = markerTypeCombo.getSelectionIndex();
                String markerTypeName = null;
                if (markerIdx > 0 && markerIdx <= markerNodeTypes.size()) {
                    markerTypeName = markerNodeTypes.get(markerIdx - 1)[0];
                }

                File outputDir = input.getParentFile();
                File projectRoot = findProjectRoot(input);
                if (projectRoot != null) {
                    outputDir = new File(projectRoot, "outputRules");
                }
                ensureParentFolderExists(new File(outputDir, "dummy"));

                File obsFile = new File(outputDir, selectedRuleName + "_Obs.henshin");
                File exeFile = new File(outputDir, selectedRuleName + "_Exe.henshin");

                HenshinRuleModifier.splitRuleToFiles(
                        input, selectedRuleName, markerTypeName, obsFile, exeFile);

                // For simulation, use the Obs file as the primary output
                output = obsFile;
                ruleName = selectedRuleName + "_Obs";
            } else {
                throw new IllegalStateException("Unknown mode selected: index=" + selectedModeIndex);
            }

            System.out.println("[henshin.plugin] Modification complete: " + output.getAbsolutePath());

            // Now generate timing.properties and launch simulation pipeline
            String simRoot = simulatorRootText.getText().trim();
            if (simRoot.isEmpty()) {
                throw new IllegalArgumentException("Simulator Root path must not be empty.");
            }
            if (!SimulatorLauncher.isSimulatorAvailable(simRoot)) {
                throw new IllegalArgumentException(
                    "Simulator projects not found at: " + simRoot
                    + "\nEnsure the path points to the ptgts-simulator-master directory.");
            }

            String inputModelSpec = inputModelText.getText().trim();
            // Model spec: "fixed(s:t)", "random(t:s)", or a file path

            int simSteps = parseIntInRange(simStepsText.getText().trim(), 1, 1000000, "Simulation Steps");

            // Write timing.properties
            File outputFolder = output.getParentFile();
            File propsFile = SimulatorLauncher.writeTimingProperties(
                    outputFolder, ruleName,
                    clockDeclText.getText().trim(),
                    guardNodeText.getText().trim(),
                    guardText.getText().trim(),
                    invariantNodeText.getText().trim(),
                    invariantText.getText().trim(),
                    clockResetsText.getText().trim(),
                    probabilityText.getText().trim(),
                    priorityText.getText().trim());

            System.out.println("[henshin.plugin] Timing properties written: " + propsFile.getAbsolutePath());

            // Launch simulation pipeline in background
            SimulatorLauncher.runPipeline(
                    simRoot,
                    output.getAbsolutePath(),
                    propsFile.getAbsolutePath(),
                    inputModelSpec,
                    simSteps);

            MessageDialog.openInformation(
                    getSite().getShell(),
                    "Simulation Launched",
                    "Modification complete. Simulation pipeline launched in background.\n\n" +
                    "Modified rule: " + output.getAbsolutePath() + "\n" +
                    "Timing properties: " + propsFile.getAbsolutePath() + "\n\n" +
                    "Check the Eclipse Console for progress.");

        } catch (Exception ex) {
            ex.printStackTrace();
            MessageDialog.openError(getSite().getShell(), "Error", ex.getMessage());
        }
    }

    private void updateModeVisibility() {
        int selectedModeIndex = modeSelectorCombo.getSelectionIndex();
        boolean isPlanningAware = (selectedModeIndex == 0);
        boolean isDeltaShift = (selectedModeIndex == 1);
        boolean isRuleSplit = (selectedModeIndex == 2);

        // Planning-Aware section
        planningSection.setVisible(isPlanningAware);
        ((GridData) planningSection.getLayoutData()).exclude = !isPlanningAware;

        // δ-Shift-Operation section
        deltaShiftSection.setVisible(isDeltaShift);
        ((GridData) deltaShiftSection.getLayoutData()).exclude = !isDeltaShift;

        // Rule-Split section
        ruleSplitSection.setVisible(isRuleSplit);
        ((GridData) ruleSplitSection.getLayoutData()).exclude = !isRuleSplit;

        // Update output naming when mode changes
        updateOutputNaming();

        // Force layout refresh
        modeSelectorCombo.getParent().layout(true, true);
    }

    @Override
    public void setFocus() {
        inputText.setFocus();
    }

    // -------------------------------------------------
    // Smart Path Resolution
    // -------------------------------------------------

    private static File resolveInputFileSmart(String userPath) {
        if (userPath == null || userPath.isBlank()) {
            throw new IllegalArgumentException("Input path must not be empty.");
        }

        // 1) Absolut?
        File f = new File(userPath);
        if (f.isAbsolute()) {
            if (!f.exists()) {
                throw new IllegalArgumentException("Input henshin file not found (absolute): " + f.getAbsolutePath());
            }
            return f;
        }

        // 2) Workspace root per Reflection holen (falls verfügbar)
        File wsRoot = tryGetWorkspaceRootByReflection();

        // Kandidaten sammeln (für gute Fehlermeldung)
        List<File> tried = new ArrayList<>();

        // 2a) Falls workspace gefunden: in allen Projekten suchen
        if (wsRoot != null && wsRoot.isDirectory()) {
            File[] projects = wsRoot.listFiles(File::isDirectory);
            if (projects != null) {
                for (File p : projects) {
                    File cand = new File(p, userPath);
                    tried.add(cand);
                    if (cand.exists()) return cand;
                }
            }
        }

        // 2b) Fallback: relativ zum current working dir (nur als letzter Versuch)
        File rel = new File(userPath).getAbsoluteFile();
        tried.add(rel);
        if (rel.exists()) return rel;

        // Nichts gefunden -> klare Fehlermeldung
        StringBuilder sb = new StringBuilder();
        sb.append("Input henshin file not found: ").append(userPath).append("\nTried:\n");
        for (File t : tried) {
            sb.append(" - ").append(t.getAbsolutePath()).append("\n");
        }
        throw new IllegalArgumentException(sb.toString().trim());
    }

    private static File resolveOutputFileSmart(String userPath, File inputFile) {
        if (userPath == null || userPath.isBlank()) {
            throw new IllegalArgumentException("Output path must not be empty.");
        }

        File out = new File(userPath);
        if (out.isAbsolute()) {
            return out;
        }

        // Output relativ zum Projekt-Root des Inputs (sicher!)
        File projectRoot = findProjectRoot(inputFile);
        if (projectRoot != null) {
            return new File(projectRoot, userPath);
        }

        // Fallback: workspace root
        File wsRoot = tryGetWorkspaceRootByReflection();
        if (wsRoot != null) {
            return new File(wsRoot, userPath);
        }

        // Letzter Fallback: working dir
        return new File(userPath).getAbsoluteFile();
    }

    private static File findProjectRoot(File anyFileInsideProject) {
        if (anyFileInsideProject == null) return null;

        File cur = anyFileInsideProject.isDirectory() ? anyFileInsideProject : anyFileInsideProject.getParentFile();
        while (cur != null) {
            File pluginXml = new File(cur, "plugin.xml");
            File dotProject = new File(cur, ".project");
            if (pluginXml.exists() || dotProject.exists()) {
                return cur;
            }
            cur = cur.getParentFile();
        }
        return null;
    }

    private static File tryGetWorkspaceRootByReflection() {
        try {
            Class<?> resourcesPlugin = Class.forName("org.eclipse.core.resources.ResourcesPlugin");
            Object workspace = resourcesPlugin.getMethod("getWorkspace").invoke(null);
            Object root = workspace.getClass().getMethod("getRoot").invoke(workspace);
            Object location = root.getClass().getMethod("getLocation").invoke(root);
            Object file = location.getClass().getMethod("toFile").invoke(location);
            if (file instanceof File) return (File) file;
        } catch (Throwable ignore) {
            // Workspace API nicht verfügbar -> ok, wir fallen zurück
        }
        return null;
    }

    private static void ensureParentFolderExists(File out) {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IllegalStateException("Could not create output folder: " + parent.getAbsolutePath());
            }
        }
    }

    // -------------------------------------------------
    // Guard / Invariant parsing + algorithm
    // -------------------------------------------------

    private static int parseIntInRange(String s, int min, int max, String label) {
        final int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer between " + min + " and " + max);
        }
        if (v < min || v > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
        }
        return v;
    }

    private static void validatePaperPreconditions(int guard, int invariant) {
        if (guard <= 1) {
            throw new IllegalArgumentException("Paper precondition violated: Guard must be > 1.");
        }
        if (invariant <= guard) {
            throw new IllegalArgumentException("Paper precondition violated: Invariant must be > Guard.");
        }
        if (invariant > 15) {
            throw new IllegalArgumentException("Paper precondition violated: Invariant must be <= 15.");
        }
    }

    private static int computePlanningHorizon(int guard, int invariant, int delta) {
        int temp = invariant - guard;

        if (temp == guard) {
            return delta + 1;
        } else if (temp < guard && temp == delta) {
            return delta + 1;
        } else if (temp > guard) {
            return (int) Math.ceil((double) temp / (double) guard);
        } else {
            return (int) Math.ceil((double) invariant / (double) guard);
        }
    }
}
