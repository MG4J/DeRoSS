# DeRoSS — **De**lay-**Ro**bustne**SS**

DeRoSS is an [Eclipse Modeling Framework (EMF)](https://eclipse.dev/emf/) plugin that uses the in-place model transformation language [Eclipse Henshin](https://eclipse.dev/henshin/) to derive delay-robust Timed Graph Transformation System (TGTS) rules from non-delay-robust ones.


DeRoSS implements the techniques presented in:

- M. Ghani, S. Schneider, M. Maximova, and H. Giese. [Deriving Delay-Robust Timed Graph Transformation System Models](https://link.springer.com/chapter/10.1007/978-3-031-64285-2_9). In: Graph Transformation (International Conference on Graph Transformation, ICGT 2024), Springer, 2024, pp. 158–179.

- M. Ghani and H. Giese. *Modeling and Analyzing Planning-Aware Distributed Cyber-Physical Systems with Timed Graph Transformation Systems.* To appear in [FASE 2026, ETAPS 2026](https://etaps.org/2026/conferences/fase/).

- M. Ghani and H. Giese. *Towards Delay-Robust Models for Engineering Smart Systems of Systems: Challenges, Innovations, and Future Directions.* To appear in: B. Tekinerdogan and K. Drira (eds.), [System of Systems Engineering: Innovations, Challenges, and Future Directions](https://easychair.org/cfp/SoSE-2025). Elsevier (Academic Press), 2026.

### Running Example

The tool uses an **autonomous shuttle transportation system** as its domain. Multiple shuttles coordinate their movement on a shared track topology to avoid collisions while maximizing throughput. Each shuttle operates in one of three modes (`DRIVE`, `BRAKE`, `STOP`) and maintains a chain of marker nodes representing its planned future track positions.

## Plugin Functionalities

The plugin provides three core rule-transformation operations, selectable via a mode dropdown in the Eclipse view.

### 1. Planning-Aware Modification

Extends a TGTS rule to create planning-aware variants. Given a non-planning-aware rule (e.g., `driveD`), this mode computes a planning horizon and adds the corresponding marker nodes to the rule. Additionally, the plugin can transform a planning-aware TGTS rule that relies on a purely time-based planning horizon into a context-aware planning-aware TGTS rule by incorporating topology information via the SURE (Shortest Unreserved Route Extension) algorithm, which extends the planning horizon to cover the shortest feasible path that avoids currently reserved locations.


**Required inputs:**
- Node type (selected from auto-discovered compatible types via *Load Types*)
- Guard value (integer, `1 < g`, default: `3`)
- Invariant value (integer, `g < n <= 15`, default: `4`)
- Topology

**Output:** `<RuleName>_MOD.henshin` in `outputRules/`

### 2. Delta-Shift Operation

Implements a temporal robustness transformation that moves *passive* shuttles backward on the track topology. A passive shuttle is one whose `at` edge points to the **same** track in both LHS and RHS (it does not move in the original rule).


**Required inputs:**
- Backward steps (integer, `0-10`, default: `0`)

**Output:** `<RuleName>_MOD.henshin` in `outputRules/`


### 3. Rule-Split

Splits a single TGTS rule into two separate rules, implementing an observe-then-execute pattern:


**Marker node types** are discovered automatically from the metamodel. A type qualifies if: (a) `Shuttle` references it, (b) it has a reference back to `Shuttle`, and (c) `Model` has a containment reference to it. In the provided metamodel, `Meta1`–`Meta5` qualify. Marker selection is optional — choosing `(No marker node)` produces a split without the handshake mechanism.

**Required inputs:**
- Marker node type (optional, selected from auto-discovered types via *Load Marker Types*)

**Output:** `<RuleName>_Obs.henshin` and `<RuleName>_Exe.henshin` in `outputRules/`

### 4. PTGTS Simulator Integration (Optional)


## Project Structure

```
DeRoSS/
├── META-INF/
│   └── MANIFEST.MF                    # OSGi bundle manifest
├── models/
│   └── ecoreModel.ecore               # Shuttle/Track domain metamodel (AmEm package)
├── inputRules/
│   ├── ruleSet.henshin                # Full rule set
│   ├── shuttles2.henshin             # Large shuttle model rules
│   ├── PluginDrive.henshin           # Example drive rule
│   ├── TwoShuttleDrive.henshin      # Two-shuttle variant
│   └── example.henshin              # Small example rule
├── outputRules/
│   ├── *_MOD.henshin                 # Modified rule outputs
│   ├── *_Obs.henshin                 # Rule-split observation outputs
│   ├── *_Exe.henshin                 # Rule-split execution outputs
│   ├── *_SPLIT.henshin              # Combined split output
│   └── timing.properties            # Generated timing configuration
├── src/henshin/
│   ├── modifier/
│   │   ├── HenshinRuleModifier.java  # Core transformation engine (~1400 lines)
│   │   ├── SampleAlgorithm.java      # Immutable configuration record
│   │   └── Algorithm.java            # Strategy interface
│   └── plugin/views/
│       ├── HenshinPluginView.java    # Eclipse ViewPart UI (~700 lines)
│       ├── SimulatorLauncher.java    # External PTGTS simulator orchestration
│       ├── Activator.java           # Plugin activator (registers .ecore factory)
│       ├── OpenHenshinViewHandler.java  # Command handler to open the view
│       └── Startup.java             # Early plugin loading via IStartup
├── plugin.xml                        # Eclipse extension points (view + startup)
├── build.properties                  # PDE build descriptor
└── .classpath                        # JavaSE-11, PDE required plugins
```



## Prerequisites

- **Java 11** or later
- **Eclipse IDE for Eclipse Committers** (or similar) with:
  - [Eclipse Modeling Framework (EMF)](https://eclipse.dev/modeling/emf/)
  - [Henshin](https://eclipse.dev/henshin/) — Eclipse-based graph transformation framework
  - Eclipse Plugin Development Environment (PDE) — typically included in the Eclipse Committers package
- *(Optional, for simulation only)* PTGTS Simulator installed locally

## Installation and Usage

### 1. Clone and Import

```bash
git clone https://github.com/MG4J/DeRoSS.git
```

In Eclipse: **File > Import > General > Existing Projects into Workspace** > select the cloned `DeRoSS` directory > **Finish**.

### 2. Launch the Runtime Workbench

Right-click the project > **Run As > Eclipse Application**.

This starts a second Eclipse instance (the *runtime workbench*) with the DeRoSS plugin loaded.

### 3. Open the Plugin View

In the runtime workbench: **Window > Show View > Other > Henshin > ZZZ Henshin Rule Modifier**.

### 4. Use the Plugin

#### Planning-Aware Modification

1. Enter or browse to a `.henshin` input file (e.g., `inputRules/PluginDrive.henshin`).
2. Click **Load Rules** to populate the rule dropdown.
3. Select a rule from the dropdown (e.g., `ODrive`).
4. Set the mode to **Planning-Aware**.
5. Click **Load Types** to discover compatible planning node types from the metamodel.
6. Select a node type (e.g., `RsX1 (via sx1)`).
7. Enter the **Guard** (e.g., `3`) and **Invariant** (e.g., `4`) values. These must satisfy: `1 < guard < invariant <= 15`.
8. Click **Modify Rule**. The planning horizon *k* is computed automatically (e.g., *k* = 2 for guard=3, invariant=4). The modified rule is saved to `outputRules/ODrive_MOD.henshin`.

#### Delta-Shift Operation

1. Load a `.henshin` file and select a rule (steps 1-3 above).
2. Set the mode to **delta-Shift-Operation**.
3. Enter the number of **Backward Steps** (e.g., `2`).
4. Click **Modify Rule**. Passive shuttles in the rule are moved backward by the specified number of track positions.

#### Rule-Split

1. Load a `.henshin` file and select a rule (steps 1-3 above).
2. Set the mode to **Rule-Split**.
3. Click **Load Marker Types** to discover compatible marker types.
4. Select a marker type (e.g., `Meta1 (via m1)`) or choose `(No marker node)` for a marker-less split.
5. Click **Modify Rule**. Two output files are created: `<Rule>_Obs.henshin` and `<Rule>_Exe.henshin`.

#### Running a Simulation (Optional)

1. Check the **Simulate after modification** checkbox to reveal the simulation section.
2. Configure the simulator root directory (path to the PTGTS simulator installation).
3. Set timing parameters: clock declarations, guard/invariant nodes, resets, probability, and priority.
4. Specify an input model: `fixed(2:5)` (2 shuttles, 5 tracks each), `random(10:3)`, or browse to an `.xmi` file.
5. Set the number of simulation steps (default: `5000`).
6. Click **Modify & Simulate**. The plugin modifies the rule, writes `timing.properties`, and launches the simulator pipeline as a background job. Output is streamed to the Eclipse Console.

### 5. Inspect Output

Modified rules are saved as standard Henshin `.henshin` (XMI) files in the `outputRules/` directory. Each modified rule's `description` field contains a human-readable summary of the applied transformations. The output files can be:
- Opened in the Henshin graphical editor for visual inspection
- Used as input for state-space generation with Henshin
- Fed into the PTGTS simulator for execution
- Analyzed with PRISM for formal verification of safety and real-time properties

### Troubleshooting

- **Changes not reflected after editing source**: Run **Project > Clean** and restart the runtime workbench.
- **Edge source/target null errors**: The plugin includes an automatic workaround (`repairEdgeReferences`) for a known Henshin XMI loader issue. If you encounter edge-related errors, ensure you are using the plugin's loading methods rather than loading `.henshin` files manually.
- **Simulator not found**: Verify that the simulator root directory contains the expected project subdirectories (`de.mdelab.ptgtssimulation.generator/`, `de.mdelab.ptgtssimulation.simulator/`, etc.) and that they are compiled (`bin/` directories exist).

## Technologies

| Technology | Role |
|---|---|
| [Java 11](https://openjdk.org/projects/jdk/11/) | Implementation language |
| [Eclipse PDE](https://eclipse.dev/pde/) / OSGi | Plugin lifecycle, bundle management, lazy activation |
| [Eclipse Modeling Framework (EMF)](https://eclipse.dev/modeling/emf/) | Metamodel infrastructure (EPackage, EClass, EReference, XMI serialization) |
| [Henshin](https://eclipse.dev/henshin/) | Graph transformation rules, modules, nodes, edges, and LHS/RHS mappings |
| [SWT](https://eclipse.dev/swt/) / [JFace](https://wiki.eclipse.org/JFace) | UI widgets and dialogs |
| [PRISM](https://www.prismmodelchecker.org/) | Probabilistic model checker (used in evaluation for formal safety verification) |

## References

- M. Ghani, S. Schneider, M. Maximova, and H. Giese. [Deriving Delay-Robust Timed Graph Transformation System Models](https://link.springer.com/chapter/10.1007/978-3-031-64285-2_9). In: Graph Transformation (International Conference on Graph Transformation, ICGT 2024), Springer, 2024, pp. 158–179.

- M. Ghani and H. Giese. *Modeling and Analyzing Planning-Aware Distributed Cyber-Physical Systems with Timed Graph Transformation Systems.* To appear in [FASE 2026, ETAPS 2026](https://etaps.org/2026/conferences/fase/).

- M. Ghani and H. Giese. *Towards Delay-Robust Models for Engineering Smart Systems of Systems: Challenges, Innovations, and Future Directions.* To appear in: B. Tekinerdogan and K. Drira (eds.), [System of Systems Engineering: Innovations, Challenges, and Future Directions](https://easychair.org/cfp/SoSE-2025). Elsevier (Academic Press), 2026.


