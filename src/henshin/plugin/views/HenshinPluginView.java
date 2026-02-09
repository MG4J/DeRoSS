package henshin.plugin.views;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import henshin.modifier.HenshinRuleModifier;
import henshin.modifier.SampleAlgorithm;

public class HenshinPluginView extends ViewPart {

    public static final String ID = "henshin.plugin.view";

    private Text inputText;
    private Text ruleText;
    private Text outputText;
    private Combo modeSelectorCombo;
    private Label guardLabel;
    private Text guardText;
    private Label invariantLabel;
    private Text invariantText;
    private Label nodeTypeLabel;
    private Combo nodeTypeCombo;
    private Button loadTypesButton;
    private Label backwardStepsLabel;
    private Text backwardStepsText;

    // Stores the compatible node type info: [typeName, shuttleRefName, selfRefName, trackRefName]
    private List<String[]> compatibleNodeTypes = new ArrayList<>();

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(3, false));

        // Input field (spans 2 columns for the text)
        new Label(parent, SWT.NONE).setText("Input .henshin:");
        inputText = new Text(parent, SWT.BORDER);
        inputText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        inputText.setText("/Users/mg/Desktop/WorkSpaceEclipseMoD/HenshinRuleModifierPlugin/inputRules/PluginDrive.henshin");
        new Label(parent, SWT.NONE); // empty cell

        // Rule name
        new Label(parent, SWT.NONE).setText("Rule name:");
        ruleText = new Text(parent, SWT.BORDER);
        ruleText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        ruleText.setText("PluginDrive_MOD");
        new Label(parent, SWT.NONE); // empty cell

        // Output field
        new Label(parent, SWT.NONE).setText("Output .henshin:");
        outputText = new Text(parent, SWT.BORDER);
        outputText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        outputText.setText("/Users/mg/Desktop/WorkSpaceEclipseMoD/HenshinRuleModifierPlugin/outputRules/out.henshin");
        new Label(parent, SWT.NONE); // empty cell

        // Mode selector
        new Label(parent, SWT.NONE).setText("Mode:");
        modeSelectorCombo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        modeSelectorCombo.setItems(new String[] {
            "Node Extension",
            "Delta-Shift Operation"
        });
        modeSelectorCombo.select(0);
        modeSelectorCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        new Label(parent, SWT.NONE); // empty cell

        // Node Type selector (for Node Extension mode)
        nodeTypeLabel = new Label(parent, SWT.NONE);
        nodeTypeLabel.setText("Node Type:");
        nodeTypeCombo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
        nodeTypeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        nodeTypeCombo.setItems(new String[] {"(Click 'Load Types' first)"});
        nodeTypeCombo.select(0);
        loadTypesButton = new Button(parent, SWT.PUSH);
        loadTypesButton.setText("Load Types");
        loadTypesButton.addListener(SWT.Selection, e -> loadCompatibleNodeTypes());

        // Guard
        guardLabel = new Label(parent, SWT.NONE);
        guardLabel.setText("Guard:");
        guardText = new Text(parent, SWT.BORDER);
        guardText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        guardText.setText("3");
        new Label(parent, SWT.NONE); // empty cell

        // Invariant
        invariantLabel = new Label(parent, SWT.NONE);
        invariantLabel.setText("Invariant:");
        invariantText = new Text(parent, SWT.BORDER);
        invariantText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        invariantText.setText("4");
        new Label(parent, SWT.NONE); // empty cell

        // Backward Steps (for Delta-Shift mode)
        backwardStepsLabel = new Label(parent, SWT.NONE);
        backwardStepsLabel.setText("Backward Steps:");
        backwardStepsText = new Text(parent, SWT.BORDER);
        backwardStepsText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        backwardStepsText.setText("0");
        backwardStepsLabel.setVisible(false);
        backwardStepsText.setVisible(false);
        new Label(parent, SWT.NONE); // empty cell

        modeSelectorCombo.addListener(SWT.Selection, e -> updateModeVisibility());

        // Run button
        Button runButton = new Button(parent, SWT.PUSH);
        runButton.setText("Modify Rule");
        GridData gd = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        gd.horizontalSpan = 3;
        runButton.setLayoutData(gd);

        runButton.addListener(SWT.Selection, e -> runModification());

        System.out.println("[henshin.plugin] View created");
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

    private void runModification() {
        try {
            // 1) Input finden (absolut ODER workspace/projekt-relativ)
            File input = resolveInputFileSmart(inputText.getText().trim());

            // 2) Output auf Projekt-Root auflösen (gleicher Projekt-Root wie Input)
            File output = resolveOutputFileSmart(outputText.getText().trim(), input);

            String ruleName = ruleText.getText().trim();
            if (ruleName.isEmpty()) {
                throw new IllegalArgumentException("Rule name must not be empty.");
            }

            ensureParentFolderExists(output);

            int selectedModeIndex = modeSelectorCombo.getSelectionIndex();

            if (selectedModeIndex == 0) {
                // Node Extension mode
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
                        ruleName,
                        new SampleAlgorithm(k, 0, nodeTypeName),
                        output
                );

                MessageDialog.openInformation(
                        getSite().getShell(),
                        "Success",
                        "Node Extension applied:\n" +
                        "  Node type: " + nodeTypeName + "\n" +
                        "  k = " + k + "\n" +
                        "\nInput:\n" + input.getAbsolutePath() +
                        "\nOutput:\n" + output.getAbsolutePath()
                );

            } else if (selectedModeIndex == 1) {
                // Delta-Shift Operation mode
                int backwardSteps = parseIntInRange(backwardStepsText.getText().trim(), 0, 10, "Backward Steps");

                HenshinRuleModifier.modifyRuleInModule(
                        input,
                        ruleName,
                        new SampleAlgorithm(0, backwardSteps, null),
                        output
                );

                MessageDialog.openInformation(
                        getSite().getShell(),
                        "Success",
                        "Delta-Shift Operation applied with backward steps = " + backwardSteps
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

    private void updateModeVisibility() {
        int selectedModeIndex = modeSelectorCombo.getSelectionIndex();
        boolean isNodeExtension = (selectedModeIndex == 0);

        // Node Extension mode fields
        nodeTypeLabel.setVisible(isNodeExtension);
        nodeTypeCombo.setVisible(isNodeExtension);
        loadTypesButton.setVisible(isNodeExtension);
        guardLabel.setVisible(isNodeExtension);
        guardText.setVisible(isNodeExtension);
        invariantLabel.setVisible(isNodeExtension);
        invariantText.setVisible(isNodeExtension);

        // Delta-Shift mode fields
        backwardStepsLabel.setVisible(!isNodeExtension);
        backwardStepsText.setVisible(!isNodeExtension);

        // Force layout refresh
        guardLabel.getParent().layout(true, true);
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
