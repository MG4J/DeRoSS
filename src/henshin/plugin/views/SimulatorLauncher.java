package henshin.plugin.views;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Launches the PTGTS simulator pipeline using ProcessBuilder.
 *
 * Constructs a classpath from:
 * 1. Simulator project bin/ directories (compiled classes)
 * 2. Simulator libs (log4j JARs)
 * 3. Eclipse installation plugins/ directory (EMF, Henshin, mlsdm, etc.)
 *
 * Prerequisites:
 * - Simulator projects must be built (bin/ folders populated)
 * - Eclipse installation must have mlsdm plugins installed
 *
 * The pipeline:
 * 1. Write timing.properties file
 * 2. Launch ModuleBasedRuleGenerator (generates .mlsdm Story Diagrams)
 * 3. Launch CommandLineSimulator (runs the simulation)
 */
public class SimulatorLauncher {

	private static final String GENERATOR_PROJECT = "de.mdelab.ptgtssimulation.generator";
	private static final String SIMULATOR_PROJECT = "de.mdelab.ptgtssimulation.simulator";
	private static final String GENERATOR_MAIN_CLASS =
			"de.mdelab.ptgtssimulation.generator.rules.ModuleBasedRuleGenerator";
	private static final String SIMULATOR_MAIN_CLASS =
			"de.ptgtssimulation.CommandLineSimulator";

	/**
	 * Check whether the simulator projects exist on the filesystem.
	 * Checks if the generator project directory exists at the given root.
	 */
	public static boolean isSimulatorAvailable(String simulatorRoot) {
		if (simulatorRoot == null || simulatorRoot.isBlank()) return false;
		File root = new File(simulatorRoot);
		return root.isDirectory()
				&& new File(root, GENERATOR_PROJECT).isDirectory()
				&& new File(root, SIMULATOR_PROJECT).isDirectory();
	}

	/**
	 * Check whether the simulator projects are built (bin/ folders exist).
	 */
	public static String checkBuildStatus(String simulatorRoot) {
		List<String> missing = new ArrayList<>();
		File genBin = new File(simulatorRoot, GENERATOR_PROJECT + "/bin");
		File simBin = new File(simulatorRoot, SIMULATOR_PROJECT + "/bin");
		if (!genBin.isDirectory()) missing.add(GENERATOR_PROJECT + "/bin");
		if (!simBin.isDirectory()) missing.add(SIMULATOR_PROJECT + "/bin");
		if (missing.isEmpty()) return null;
		return "Simulator projects not built. Missing:\n- "
				+ String.join("\n- ", missing)
				+ "\n\nPlease import the simulator projects into Eclipse and build them first.";
	}

	/**
	 * Find the Eclipse installation plugins directory.
	 * Uses system properties set by the running Eclipse/OSGi runtime.
	 */
	public static File findEclipsePluginsDir() {
		// Try osgi.syspath first (direct path to plugins dir)
		String syspath = System.getProperty("osgi.syspath");
		if (syspath != null) {
			File dir = new File(syspath);
			if (dir.isDirectory()) return dir;
		}

		// Try osgi.install.area (Eclipse home as URL)
		String installArea = System.getProperty("osgi.install.area");
		if (installArea != null) {
			try {
				File installDir = new File(new URI(installArea));
				File pluginsDir = new File(installDir, "plugins");
				if (pluginsDir.isDirectory()) return pluginsDir;
			} catch (Exception ignore) {
			}
		}

		// Try eclipse.home.location
		String homeLocation = System.getProperty("eclipse.home.location");
		if (homeLocation != null) {
			try {
				File homeDir = new File(new URI(homeLocation));
				File pluginsDir = new File(homeDir, "plugins");
				if (pluginsDir.isDirectory()) return pluginsDir;
			} catch (Exception ignore) {
			}
		}

		return null;
	}

	/**
	 * Find the Java executable.
	 */
	private static String findJavaExecutable() {
		String javaHome = System.getProperty("java.home");
		if (javaHome != null) {
			File javaBin = new File(javaHome, "bin/java");
			if (javaBin.exists()) return javaBin.getAbsolutePath();
		}
		return "java"; // fall back to PATH
	}

	/**
	 * Build the classpath for running simulator components.
	 *
	 * @param simulatorRoot path to the ptgts-simulator-master directory
	 * @return classpath string with all needed entries
	 */
	private static String buildClasspath(String simulatorRoot) {
		List<String> entries = new ArrayList<>();
		String sep = File.pathSeparator;

		// 1. Simulator project bin/ directories
		entries.add(new File(simulatorRoot, GENERATOR_PROJECT + "/bin").getAbsolutePath());
		entries.add(new File(simulatorRoot, SIMULATOR_PROJECT + "/bin").getAbsolutePath());

		// Also add other projects that might have compiled classes
		String[] otherProjects = {
			"de.mdelab.ptgtssimulation.basemodel",
			"de.ptgtssimulation.evaluation",
			"de.ptgtssimulation.rules"
		};
		for (String proj : otherProjects) {
			File bin = new File(simulatorRoot, proj + "/bin");
			if (bin.isDirectory()) {
				entries.add(bin.getAbsolutePath());
			}
		}

		// 2. Simulator libs (log4j JARs)
		File libsDir = new File(simulatorRoot, SIMULATOR_PROJECT + "/libs/log4j");
		if (libsDir.isDirectory()) {
			File[] jars = libsDir.listFiles((dir, name) -> name.endsWith(".jar"));
			if (jars != null) {
				for (File jar : jars) {
					entries.add(jar.getAbsolutePath());
				}
			}
		}

		// 3. Eclipse plugins directory (wildcard includes all JARs)
		File pluginsDir = findEclipsePluginsDir();
		if (pluginsDir != null) {
			// Java -cp wildcard: "dir/*" includes all JARs in dir
			entries.add(pluginsDir.getAbsolutePath() + "/*");
		}

		return String.join(sep, entries);
	}

	/**
	 * Write timing properties to a .properties file.
	 */
	public static File writeTimingProperties(
			File outputFolder, String ruleName,
			String clockDecl, String guardNode, String guardValue,
			String invariantNode, String invariantValue,
			String resets, String probability, String priority) throws IOException {

		Properties props = new Properties();

		if (clockDecl != null && !clockDecl.isBlank()) {
			props.setProperty("clockDeclarations", clockDecl);
		}

		// Capitalize first letter for property keys (PTDataParser decapitalizes when looking up)
		String capRuleName = Character.toUpperCase(ruleName.charAt(0)) + ruleName.substring(1);

		if (guardNode != null && !guardNode.isBlank() && guardValue != null && !guardValue.isBlank()) {
			props.setProperty("guard" + capRuleName, guardNode + ".c>=" + guardValue);
		}

		if (invariantNode != null && !invariantNode.isBlank()
				&& invariantValue != null && !invariantValue.isBlank()) {
			props.setProperty("invariant" + capRuleName,
					invariantNode + ".c>=0&" + invariantNode + ".c<=" + invariantValue);
		}

		if (resets != null && !resets.isBlank()) {
			props.setProperty("resets" + capRuleName, resets);
		}

		if (probability != null && !probability.isBlank()) {
			props.setProperty("prob" + capRuleName + "1", probability);
		}

		if (priority != null && !priority.isBlank()) {
			props.setProperty("priority" + capRuleName, priority);
		}

		File propsFile = new File(outputFolder, "timing.properties");
		try (FileOutputStream fos = new FileOutputStream(propsFile)) {
			props.store(fos, "Auto-generated timing properties for rule: " + ruleName);
		}
		return propsFile;
	}

	/**
	 * Run the full simulation pipeline as a background Eclipse Job.
	 *
	 * @param simulatorRoot  absolute path to the ptgts-simulator-master directory
	 * @param henshinPath    absolute path to the modified .henshin file
	 * @param propertiesPath absolute path to the timing.properties file
	 * @param inputModelSpec model specification: "fixed(s:t)", "random(t:s)", file path, or empty
	 * @param simulationSteps number of simulation steps
	 */
	public static void runPipeline(String simulatorRoot, String henshinPath,
			String propertiesPath, String inputModelSpec, int simulationSteps) {

		Job job = new Job("PTGTS Simulation Pipeline") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					monitor.beginTask("Running PTGTS simulation pipeline", 2);

					// Validate simulator root
					if (!isSimulatorAvailable(simulatorRoot)) {
						return new Status(IStatus.ERROR, "henshin.plugin",
								"Simulator root not found: " + simulatorRoot
								+ "\nEnsure the path points to the ptgts-simulator-master directory.");
					}

					String buildError = checkBuildStatus(simulatorRoot);
					if (buildError != null) {
						return new Status(IStatus.ERROR, "henshin.plugin", buildError);
					}

					String classpath = buildClasspath(simulatorRoot);
					String java = findJavaExecutable();

					// Derive system name from henshin file
					String henshinName = new File(henshinPath).getName();
					String systemName = henshinName.contains(".")
							? henshinName.substring(0, henshinName.lastIndexOf("."))
							: henshinName;

					// Step 1: Launch Rule Generator
					// Working dir = generator project, so ".." = simulatorRoot
					// args: <henshin> <properties> <simulatorRoot/>
					monitor.subTask("Launching Rule Generator...");
					System.out.println("[SimulatorLauncher] Launching ModuleBasedRuleGenerator...");

					File genWorkDir = new File(simulatorRoot, GENERATOR_PROJECT);
					int genExit = launchProcess(java, classpath, GENERATOR_MAIN_CLASS,
							new String[] { henshinPath, propertiesPath, simulatorRoot + "/" },
							genWorkDir);

					if (genExit != 0) {
						return new Status(IStatus.ERROR, "henshin.plugin",
								"RuleGenerator exited with code " + genExit
								+ ". Check the Eclipse Console for details.");
					}

					monitor.worked(1);
					if (monitor.isCanceled()) return Status.CANCEL_STATUS;

					// Step 2: Launch Simulator
					// Working dir = simulator project, so ".." = simulatorRoot
					// SimulationUtils.resolveLocalPath uses: new File(userDir).getParent() + "/" + path
					// So from CWD = <simRoot>/de.mdelab.ptgtssimulation.simulator,
					// parent = <simRoot>, and rule folder is workspace-relative
					monitor.subTask("Launching Simulator...");
					System.out.println("[SimulatorLauncher] Launching CommandLineSimulator...");

					String ruleFolderPath = GENERATOR_PROJECT + "/model-gen/" + systemName + "/";
					File simWorkDir = new File(simulatorRoot, SIMULATOR_PROJECT);

					// Build args for the simulator.
					// The CommandLineSimulator accepts model specs in 3 formats:
					//   fixed(shuttles:tracksPerShuttle) - programmatic circle topology
					//   random(tracks:shuttles) - programmatic random topology
					//   <relative-path>.xmi - file-based model (must contain SimulatorInputModel root)
					// File paths: SimulationUtils.resolveLocalPath prepends parent of user.dir
					// (= simulatorRoot), so absolute paths must be converted to relative.
					String[] simArgs;
					String modelArg = resolveModelArg(inputModelSpec, simulatorRoot);
					if (modelArg != null) {
						System.out.println("[SimulatorLauncher] Model spec: " + modelArg);
						simArgs = new String[] { ruleFolderPath, modelArg, String.valueOf(simulationSteps) };
					} else {
						// No model specified - let simulator use its own default
						simArgs = new String[] { ruleFolderPath };
					}
					int simExit = launchProcess(java, classpath, SIMULATOR_MAIN_CLASS,
							simArgs, simWorkDir);

					if (simExit != 0) {
						return new Status(IStatus.ERROR, "henshin.plugin",
								"Simulator exited with code " + simExit
								+ ". Check the Eclipse Console for details.");
					}

					monitor.worked(1);
					System.out.println("[SimulatorLauncher] Pipeline complete.");
					return Status.OK_STATUS;

				} catch (Exception e) {
					e.printStackTrace();
					return new Status(IStatus.ERROR, "henshin.plugin",
							"Simulation pipeline failed: " + e.getMessage(), e);
				} finally {
					monitor.done();
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/**
	 * Launch a Java process using ProcessBuilder.
	 * Redirects stdout/stderr to System.out/System.err (Eclipse Console).
	 *
	 * @return the process exit code
	 */
	private static int launchProcess(String java, String classpath, String mainClass,
			String[] args, File workingDir) throws IOException, InterruptedException {

		List<String> command = new ArrayList<>();
		command.add(java);
		command.add("-cp");
		command.add(classpath);
		command.add(mainClass);
		for (String arg : args) {
			command.add(arg);
		}

		System.out.println("[SimulatorLauncher] Command: " + mainClass);
		System.out.println("[SimulatorLauncher] Working dir: " + workingDir.getAbsolutePath());
		System.out.println("[SimulatorLauncher] Args: " + String.join(" ", args));

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(workingDir);
		pb.redirectErrorStream(true); // merge stderr into stdout

		Process process = pb.start();

		// Stream output to Eclipse Console
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println("[" + mainClass.substring(mainClass.lastIndexOf('.') + 1) + "] " + line);
			}
		}

		int exitCode = process.waitFor();
		System.out.println("[SimulatorLauncher] " + mainClass + " exited with code " + exitCode);
		return exitCode;
	}

	/**
	 * Resolve the model argument for the CommandLineSimulator.
	 *
	 * Accepts:
	 *   "fixed(s:t)" or "random(t:s)" - passed through directly
	 *   absolute file path - converted to relative path from simulatorRoot
	 *   relative file path - passed through directly
	 *   null/empty - returns null (caller decides what to do)
	 *
	 * @return the resolved model argument, or null if input was empty
	 */
	private static String resolveModelArg(String inputModelSpec, String simulatorRoot) {
		if (inputModelSpec == null || inputModelSpec.isBlank()) {
			return null;
		}

		String spec = inputModelSpec.trim();

		// Programmatic model specs: pass through directly
		if (spec.startsWith("fixed(") || spec.startsWith("random(")) {
			return spec;
		}

		// File path: convert absolute to relative for SimulationUtils.resolveLocalPath
		return toSimulatorRelativePath(spec, simulatorRoot);
	}

	/**
	 * Convert an absolute model path to a path relative to simulatorRoot.
	 * The CommandLineSimulator's resolveLocalPath() prepends the parent of user.dir
	 * (which is the simulatorRoot) to the path, so we must pass paths relative to it.
	 */
	private static String toSimulatorRelativePath(String path, String simulatorRoot) {
		try {
			File modelFile = new File(path).getCanonicalFile();
			File simRoot = new File(simulatorRoot).getCanonicalFile();
			String modelStr = modelFile.getPath();
			String rootStr = simRoot.getPath();
			if (!rootStr.endsWith(File.separator)) {
				rootStr = rootStr + File.separator;
			}
			if (modelStr.startsWith(rootStr)) {
				return modelStr.substring(rootStr.length());
			}
		} catch (Exception e) {
			System.out.println("[SimulatorLauncher] Warning: could not relativize model path: " + e.getMessage());
		}
		return path;
	}
}
