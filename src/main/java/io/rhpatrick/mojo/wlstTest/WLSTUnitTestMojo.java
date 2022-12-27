/*
 * WLSTUnitTestMojo.java - This class implements the test goal of the
 *     WLST Test Maven Plugin to allow unit tests to be written and
 *     executed as part of a normal Maven build.
 *
 * Copyright 2018 Robert Patrick <rhpatrick@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rhpatrick.mojo.wlstTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.Element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * This mojo allows WLST-based unit tests to be executed as part of a Maven build.  Any WLST source code
 * in the project can be tested by writing WLST unit tests and configuring this plugin to execute the
 * tests.
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class WLSTUnitTestMojo extends AbstractMojo {
    private static final String BUNDLE_NAME = "io.rhpatrick.mojo.wlstTest.WLSTTestBundle";
    private static final ResourceBundle WLST_TEST_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private static final String DEPENDENCY_PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String DEPENDENCY_PLUGIN_ARTIFACT_ID = "maven-dependency-plugin";
    private static final String DEPENDENCY_PLUGIN_PROPERTIES_GOAL = "properties";

    private static final String EXEC_PLUGIN_GROUP_ID = "org.codehaus.mojo";
    private static final String EXEC_PLUGIN_ARTIFACT_ID = "exec-maven-plugin";
    private static final String EXEC_PLUGIN_EXEC_GOAL = "exec";
    private static final String EXEC_PLUGIN_ENVIRONMENT_VARIABLES = "environmentVariables";
    private static final String EXEC_PLUGIN_ARGUMENT = "argument";
    private static final String EXEC_PLUGIN_ARGUMENTS = "arguments";
    private static final String EXEC_PLUGIN_WORKING_DIRECTORY = "workingDirectory";
    private static final String EXEC_PLUGIN_EXECUTABLE = "executable";
    private static final int EXEC_PLUGIN_ARGS_LENGTH = 4;

    private static final String RESOURCES_PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String RESOURCES_PLUGIN_ARTIFACT_ID = "maven-resources-plugin";
    private static final String RESOURCES_PLUGIN_COPY_RESOURCES_GOAL = "copy-resources";
    private static final String RESOURCES_PLUGIN_OUTPUT_DIRECTORY = "outputDirectory";
    private static final String RESOURCES_PLUGIN_RESOURCES = "resources";
    private static final String RESOURCES_PLUGIN_RESOURCE = "resource";
    private static final String RESOURCES_PLUGIN_DIRECTORY = "directory";
    private static final String RESOURCES_PLUGIN_INCLUDES = "includes";
    private static final String RESOURCES_PLUGIN_INCLUDE = "include";
    private static final String RESOURCES_PLUGIN_INCLUDE_VALUE = "**/*";

    private static final String WLST_TEST_BASE_DIRECTORY = "target/wlst-tests";
    private static final String WLST_TEST_MAIN_EXEC_DIR = WLST_TEST_BASE_DIRECTORY + "/main";
    private static final String WLST_TEST_TEST_EXEC_DIR = WLST_TEST_BASE_DIRECTORY + "/test";
    private static final String TEST_PY_FILE_ENDING = "test.py";

    private static final String ARTIFACT_PROPERTY_REGEX = "[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[a-zA-Z0-9]+";
    private static final Pattern ARTIFACT_PROPERTY_PATTERN = Pattern.compile(ARTIFACT_PROPERTY_REGEX);
    private static final String ARTIFACT_PROPERTY_CLASSPATH_FORMAT = "${%s}";
    private static final int BUF_SIZE = 1024;
    private static final String SKIP_MODULE_SCANNING_SWITCH = "-skipWLSModuleScanning";
    private static final String SYSTEM_PROPERTY_FORMAT = "-D%s=%s";
    private static final boolean WINDOWS = File.separatorChar == '\\';
    private static final String WLST_SCRIPT_NAME = WINDOWS ? "wlst.cmd" : "wlst.sh";
    private static final String RUN_ALL_TESTS_RESOURCE_NAME = "io/rhpatrick/mojo/wlstTest/_wlst_test_driver.py";
    private static final String RUN_ALL_TESTS_SCRIPT_NAME = "_wlst_test_driver.py";

    private static final String CLASSPATH_VARIABLE_NAME = "CLASSPATH";
    private static final String WLST_EXT_CLASSPATH_VARIABLE_NAME = "WLST_EXT_CLASSPATH";
    private static final String WLST_PROPERTIES_VARIABLE_NAME = "WLST_PROPERTIES";

    private static final String WLST_TEST_DEBUG_PROPERTY_NAME = "wlst.test.plugin.debug";

    private static final String WLST_DIR_NOT_SET = "NOT-SET";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    /**
     * The map of environment variable names and values to use when running the WLST shell script.
     */
    @Parameter(property = "environmentVariables")
    private Map<String, String> environmentVariables;

    /**
     * The version of the maven-dependency-plugin to use.
     */
    @Parameter(property = "mavenDependencyPluginVersion", defaultValue = "3.4.0")
    private String mavenDependencyPluginVersion;

    /**
     * The version of the exec-maven-plugin to use.
     */
    @Parameter(property = "execMavenPluginVersion", defaultValue = "3.1.0")
    private String execMavenPluginVersion;

    /**
     * The version of the maven-resources-plugin to use.
     */
    @Parameter(property = "mavenResourcesPluginVersion", defaultValue = "3.3.0")
    private String mavenResourcesPluginVersion;

    /**
     * Controls whether WLST skips module scanning at startup.
     */
    @Parameter(property = "skipModuleScanning", defaultValue = "true")
    private boolean skipModuleScanning;

    /**
     * The flag to use to skip running the tests.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    /**
     * The map of Java system property names and values to use when running the WLST shell script.
     */
    @Parameter(property = "systemProperties")
    private Map<String, String> systemProperties;

    /**
     * Additional JVM arguments that will be included as WLST_PROPERTIES to WLST.
     * For example, the argLine generated by the Jacoco plugin for code coverage.
     */
    @Parameter(property = "argLine")
    private String argLine;

    /**
     * The additional classpath elements to add to the WLST_EXT_CLASSPATH besides the normal
     * target/classes directory (which is added automatically).  This list supports both
     * file paths or the maven-dependency-plugin's properties goal format:
     *
     * <P><code>&lt;group-id&gt;:&lt;artifact-id&gt;:&lt;type&lt;</code></P>
     *
     * <P>For example, org.antlr:antlr4-runtime:jar</P>
     */
    @Parameter(property = "wlstExtClasspath")
    private List<String> wlstExtClasspath;

    /**
     * In versions of WLST 12.1.3 and older, the wlst.sh/wlst.cmd scripts used the CLASSPATH environment
     * variable to add JAR files to the WLST execution environment. This changed in WLST 12.2.1 where
     * CLASSPATH is now ignored (due to new classloading strategies). To allow adding JARs to the base
     * WLST classpath, Oracle introduced a new WLST_EXT_CLASSPATH environment variable.
     *
     * By default, the plugin assumes it is using WLST 12.2.1 or newer, so it used the WLST_EXT_CLASSPATH
     * environment variable to add the wlstExtClasspath parameter elements to the WLST execution environment.
     * To execute in an older version, set this property to true and the plugin will use the CLASSPATH
     * environment variable instead.
     */
    @Parameter(property = "usingOldWlstVersion", defaultValue = "false")
    private boolean usingOldWlstVersion;

    /**
     * The directory where wlst.sh/wlst.cmd is located.
     */
    @Parameter(property = "wlstScriptDirectory", required = true, defaultValue = WLST_DIR_NOT_SET)
    private File wlstScriptDirectory;

    /**
     * The directory where the plugin looks for WLST source scripts being tested.
     */
    @Parameter(property = "wlstSourcesRootDirectory", defaultValue = "${project.basedir}/src/main/python")
    private File wlstSourcesRootDirectory;

    /**
     * The directory where the plugin looks for WLST unit tests.
     */
    @Parameter(property = "wlstTestsRootDirectory", defaultValue = "${project.basedir}/src/test/python")
    private File wlstTestsRootDirectory;

    /**
     * The verbosity level for messages from the unittest suite.  Default is verbose (2).
     * <ul>
     *   <li>0 (quiet): Outputs the total number of tests executed and the final result</li>
     *   <li>1 (default): Outputs the same as 'quiet' plus a dot for every successful test or F for each failure</li>
     *   <li>2 (verbose): Outputs the help string of every test and the each result</li>
     * </ul>
     */
    @Parameter(property = "verbosity", defaultValue = "2")
    private Integer verbosity;

    private File wlstScript;
    private boolean isDebug = false;

    /**
     * The entry point for the plugin goal.
     *
     * @throws MojoExecutionException if a configuration or execution environment-related error occurs
     * @throws MojoFailureException   if an unexpected error occurs related to a plugin or Maven failure
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            getLog().info(getMessage("WLSTTEST-018"));
            return;
        } else if (getLog().isDebugEnabled()) {
            isDebug = true;
        }

        verifyArguments();
        List<File> testsToRunSourceFiles = gatherTestsToRunSourceFiles();
        if (testsToRunSourceFiles.isEmpty()) {
            getLog().info(getMessage("WLSTTEST-012", wlstTestsRootDirectory.getAbsolutePath()));
            return;
        }

        executeDependencyPluginPropertiesGoal();

        File testScriptsExecutionDirectory =
            getCanonicalFile(new File(mavenProject.getBasedir(), WLST_TEST_TEST_EXEC_DIR));
        copyScriptsToTargetDirectory(testScriptsExecutionDirectory);

        List<File> testsToRunTargetFiles = gatherTestsToRunTargetFiles(testScriptsExecutionDirectory);
        File testsDriverScript = writeTestsDriverScript(testScriptsExecutionDirectory);

        runTests(testScriptsExecutionDirectory, testsDriverScript, testsToRunTargetFiles);
    }

    /**
     * Verifies the arguments.
     *
     * @throws MojoExecutionException if an error related to the plugin parameter verification occurs
     * @throws MojoFailureException   if Maven injection of a Maven-related parameter or component
     *                                failed to properly initialize the plugin variable
     */
    private void verifyArguments() throws MojoExecutionException, MojoFailureException {
        if (mavenProject == null) {
            throw new MojoFailureException(getMessage("WLSTTEST-001"));
        } else if (mavenSession == null) {
            throw new MojoFailureException(getMessage("WLSTTEST-002"));
        } else if (pluginManager == null) {
            throw new MojoFailureException(getMessage("WLSTTEST-003"));
        }

        verifyWlstScriptDirectoryArg();
        verifyWlstTestRootDirectoryArg();
        verifyVerbosityArg();

        if (isEmpty(mavenDependencyPluginVersion)) {
            throw new MojoExecutionException("WLSTTEST-009");
        } else if (isEmpty(mavenResourcesPluginVersion)) {
            throw new MojoExecutionException("WLSTTEST-010");
        } else if (isEmpty(execMavenPluginVersion)) {
            throw new MojoExecutionException("WLSTTEST-011");
        }
    }

    /**
     * Gather the list of test files to run from the wlstTestsRotDirectory.
     *
     * @return the list of test py files matching the naming pattern (file name ends in test.py)
     */
    private List<File> gatherTestsToRunSourceFiles() {
        List<File> testsToRun = new ArrayList<>();
        if (wlstTestsRootDirectory.exists()) {
            testsToRun.addAll(gatherTestsToRun(wlstTestsRootDirectory));
        }
        return testsToRun;
    }

    /**
     * This method builds a set of properties for each dependency where the property names match the
     * format <code>&lt;group-id&gt;:&lt;artifact-id&gt;:&lt;type&lt;</code>; for example,
     * org.antlr:antlr4-runtime:jar.
     *
     * This allows the user to list dependencies in this format in the plugin's wlstExtClasspath
     * parameter and have the plugin pass them to the WLST execution environment.
     *
     * @throws MojoExecutionException if the Maven Properties plugin execution fails.
     */
    private void executeDependencyPluginPropertiesGoal() throws MojoExecutionException {
        Plugin dependencyPlugin = plugin(groupId(DEPENDENCY_PLUGIN_GROUP_ID),
                                         artifactId(DEPENDENCY_PLUGIN_ARTIFACT_ID),
                                         version(mavenDependencyPluginVersion));

        String propertiesGoal = goal(DEPENDENCY_PLUGIN_PROPERTIES_GOAL);
        ExecutionEnvironment executionEnvironment = executionEnvironment(mavenProject, mavenSession, pluginManager);
        Xpp3Dom configuration = configuration();
        executeMojo(dependencyPlugin, propertiesGoal, configuration, executionEnvironment);
    }

    /**
     * This method copies the Python scripts out of the source directory tree to the target directory.
     * This prevents the source directory tree from being polluted Jython generating class files
     * during test execution.
     *
     * @param testScriptsExecutionDirectory the base directory where the tests will execute
     * @throws MojoExecutionException if the Maven Resources plugin execution fails
     */
    private void copyScriptsToTargetDirectory(File testScriptsExecutionDirectory) throws MojoExecutionException {
        File basedir = getCanonicalFile(new File(mavenProject.getBasedir(), WLST_TEST_BASE_DIRECTORY));
        createDirectoryIfNeeded(basedir, "Plugin output");

        Plugin resourcesPlugin = plugin(groupId(RESOURCES_PLUGIN_GROUP_ID),
                                        artifactId(RESOURCES_PLUGIN_ARTIFACT_ID), version(mavenResourcesPluginVersion));
        String copyResourcesGoal = goal(RESOURCES_PLUGIN_COPY_RESOURCES_GOAL);
        ExecutionEnvironment executionEnvironment = executionEnvironment(mavenProject, mavenSession, pluginManager);

        if (wlstSourcesRootDirectory.isDirectory() && wlstSourcesRootDirectory.exists()) {
            File targetSourcesDir = getCanonicalFile(new File(mavenProject.getBasedir(), WLST_TEST_MAIN_EXEC_DIR));
            createDirectoryIfNeeded(targetSourcesDir, "Sources Execution");

            Xpp3Dom configuration = createResourcesPluginConfiguration(wlstSourcesRootDirectory, targetSourcesDir);
            executeMojo(resourcesPlugin, copyResourcesGoal, configuration, executionEnvironment);
        }

        createDirectoryIfNeeded(testScriptsExecutionDirectory, "Test Sources Execution");
        Xpp3Dom configuration =
            createResourcesPluginConfiguration(wlstTestsRootDirectory, testScriptsExecutionDirectory);
        executeMojo(resourcesPlugin, copyResourcesGoal, configuration, executionEnvironment);
    }

    /**
     * Gather the list of test files to run from the target directory's test execution location.
     *
     * @param testScriptsExecutionDirectory the base directory where the tests will execute
     * @return the list of test py files matching the naming pattern (file name ends in test.py)
     *         to run from the test scripts execution directory
     */
    private List<File> gatherTestsToRunTargetFiles(File testScriptsExecutionDirectory) {
        List<File> testsToRun = new ArrayList<>();
        if (testScriptsExecutionDirectory.exists()) {
            testsToRun.addAll(gatherTestsToRun(testScriptsExecutionDirectory));
        }
        return testsToRun;
    }

    /**
     * This method writes a driver script to the test script test execution directory that will be
     * run by WLST via the Exec Maven plugin.
     *
     * @param testScriptsExecutionDirectory the base directory where the tests will execute
     * @return the File object for the driver script
     * @throws MojoExecutionException  if the plugin fails to write the driver script to the target location
     * @throws MojoFailureException    if the plugin is unable to find the driver script in its JAR
     */
    private File writeTestsDriverScript(File testScriptsExecutionDirectory)
        throws MojoExecutionException, MojoFailureException {

        File targetScriptFile = getCanonicalFile(new File(testScriptsExecutionDirectory, RUN_ALL_TESTS_SCRIPT_NAME));

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(RUN_ALL_TESTS_RESOURCE_NAME);
        if (inputStream == null) {
            throw new MojoFailureException(getMessage("WLSTTEST-014"));
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(targetScriptFile)) {
            byte[] buffer = new byte[BUF_SIZE];
            int bytesRead = inputStream.read(buffer);
            while (bytesRead != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }
            fileOutputStream.flush();
        } catch (IOException ioe) {
            throw new MojoExecutionException(getMessage("WLSTTEST-015", targetScriptFile.getAbsolutePath(),
                                                        ioe.getLocalizedMessage()), ioe);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignore) {
                getLog().debug(getMessage("WLSTTEST-016", ignore.getLocalizedMessage()), ignore);
            }
        }
        return targetScriptFile;
    }

    /**
     * This method invoked the Exec Maven plugin to run the driver script with WLST.  The driver script in turns
     * uses the Python unittest module to execute the unit tests.
     *
     * @param testScriptsExecutionDirectory the test scripts execution directory
     * @param testsDriverScript             the driver script
     * @param testsToRun                    the list of test files to run
     * @throws MojoExecutionException       if a configuration or execution environment-related error occurs
     */
    private void runTests(File testScriptsExecutionDirectory, File testsDriverScript, List<File> testsToRun)
        throws MojoExecutionException {
        Plugin execPlugin = plugin(groupId(EXEC_PLUGIN_GROUP_ID), artifactId(EXEC_PLUGIN_ARTIFACT_ID),
                                   version(execMavenPluginVersion));
        String execGoal = goal(EXEC_PLUGIN_EXEC_GOAL);
        ExecutionEnvironment executionEnvironment = executionEnvironment(mavenProject, mavenSession, pluginManager);

        Element envVariables = buildEnvironmentVariablesElement();
        Element arguments = buildExecPluginArgumentsElement(testsDriverScript, testsToRun);
        Element workingDirectory =
            new Element(EXEC_PLUGIN_WORKING_DIRECTORY, testScriptsExecutionDirectory.getAbsolutePath());
        Element executable = new Element(EXEC_PLUGIN_EXECUTABLE, wlstScript.getAbsolutePath());

        Xpp3Dom configuration = configuration(executable, workingDirectory, arguments, envVariables);
        executeMojo(execPlugin, execGoal, configuration, executionEnvironment);
    }

    ///////////////////////////////////////////////////////////////////////////
    //                 Mojo-related Utility Functions                        //
    ///////////////////////////////////////////////////////////////////////////

    private void verifyWlstScriptDirectoryArg() throws MojoExecutionException {
        String argName = "wlstScriptDirectory";
        if (wlstScriptDirectory == null || WLST_DIR_NOT_SET.equals(wlstScriptDirectory.getName())) {
            throw new MojoExecutionException(getMessage("WLSTTEST-004", argName));
        } else if (!wlstScriptDirectory.isDirectory()) {
            throw new MojoExecutionException(getMessage("WLSTTEST-005", argName,
                    wlstScriptDirectory.getAbsolutePath()));
        } else if (!wlstScriptDirectory.exists()) {
            throw new MojoExecutionException(getMessage("WLSTTEST-006", argName,
                    wlstScriptDirectory.getAbsolutePath()));
        } else {
            wlstScript = getCanonicalFile(new File(wlstScriptDirectory, WLST_SCRIPT_NAME));
            if (!wlstScript.exists()) {
                throw new MojoExecutionException(getMessage("WLSTTEST-007", wlstScript.getAbsolutePath()));
            } else if (!wlstScript.canExecute()) {
                throw new MojoExecutionException(getMessage("WLSTTEST-008", wlstScript.getAbsolutePath()));
            }
        }
    }

    private void verifyWlstTestRootDirectoryArg() throws MojoExecutionException {
        String argName = "wlstTestRootDirectory";
        if (wlstTestsRootDirectory == null) {
            throw new MojoExecutionException(getMessage("WLSTTEST-004", argName));
        } else if (!wlstTestsRootDirectory.isDirectory()) {
            throw new MojoExecutionException(getMessage("WLSTTEST-005", argName,
                    wlstTestsRootDirectory.getAbsolutePath()));
        }
    }

    private void verifyVerbosityArg() throws MojoExecutionException {
        String argName = "verbosity";
        // valid values for unittest verbosity are 0, 1, or 2
        if (!(verbosity >= 0 && verbosity <= 2)) {
            throw new MojoExecutionException(getMessage("WLSTTEST-019", argName, verbosity));
        }
    }
    private List<File> gatherTestsToRun(File directory) {
        List<File> result = new ArrayList<>();
        File[] directoryEntries = directory.listFiles();
        if (!isEmpty(directoryEntries)) {
            for (File directoryEntry : directoryEntries) {
                if (directoryEntry.isDirectory()) {
                    result.addAll(gatherTestsToRun(directoryEntry));
                } else if (directoryEntry.getName().toLowerCase().endsWith(TEST_PY_FILE_ENDING)) {
                    result.add(directoryEntry);
                }
            }
        }
        return result;
    }

    private Xpp3Dom createResourcesPluginConfiguration(File sourceDirectory, File targetDirectory) {
        Element[] includesArray = new Element[1];
        includesArray[0] = new Element(RESOURCES_PLUGIN_INCLUDE, RESOURCES_PLUGIN_INCLUDE_VALUE);

        Element[] resourceArray = new Element[2];
        resourceArray[0] = new Element(RESOURCES_PLUGIN_DIRECTORY, sourceDirectory.getAbsolutePath());
        resourceArray[1] = new Element(RESOURCES_PLUGIN_INCLUDES, includesArray);

        Element[] resourcesArray = new Element[1];
        resourcesArray[0] = new Element(RESOURCES_PLUGIN_RESOURCE, resourceArray);
        Element resources = new Element(RESOURCES_PLUGIN_RESOURCES, resourcesArray);
        Element outputDirectory = new Element(RESOURCES_PLUGIN_OUTPUT_DIRECTORY, targetDirectory.getAbsolutePath());

        return configuration(outputDirectory, resources);
    }

    private Element buildEnvironmentVariablesElement() throws MojoExecutionException {
        // Add one for the wlstExtClasspath which must always have the ${project.build.directory}
        // even if the configuration element is empty
        int numEnvVars = environmentVariables.size() + 1;
        // Add one if WLST_PROPERTIES will be added
        if (isDebug || !isEmpty(systemProperties) || !isEmpty(argLine)) {
            numEnvVars++;
        }
        Element[] envVariablesArray = new Element[numEnvVars];

        int index = 0;
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            String errorKey = "WLSTTEST-017";
            if (CLASSPATH_VARIABLE_NAME.equals(name)) {
                throw new MojoExecutionException(getMessage(errorKey,
                                                            CLASSPATH_VARIABLE_NAME, "wlstExtClasspath"));
            } else if (WLST_EXT_CLASSPATH_VARIABLE_NAME.equals(name)) {
                throw new MojoExecutionException(getMessage(errorKey,
                                                            WLST_EXT_CLASSPATH_VARIABLE_NAME, "wlstExtClasspath"));
            } else if (WLST_PROPERTIES_VARIABLE_NAME.equals(name)) {
                throw new MojoExecutionException(getMessage(errorKey,
                                                            WLST_PROPERTIES_VARIABLE_NAME, "systemProperties"));
            }
            envVariablesArray[index++] = new Element(name, value);
        }

        envVariablesArray[index++] = getWlstExtClasspathElement();

        Element wlstPropertiesElement = getWlstPropertiesElement();
        if (wlstPropertiesElement != null) {
            envVariablesArray[index] = wlstPropertiesElement;
        }
        return new Element(EXEC_PLUGIN_ENVIRONMENT_VARIABLES, envVariablesArray);
    }

    private Element getWlstExtClasspathElement() {
        List<String> result = new ArrayList<>(wlstExtClasspath.size() + 1);
        for (String wlstExtClasspathElement : wlstExtClasspath) {
            Matcher matcher = ARTIFACT_PROPERTY_PATTERN.matcher(wlstExtClasspathElement);
            if (matcher.matches()) {
                result.add(String.format(ARTIFACT_PROPERTY_CLASSPATH_FORMAT, wlstExtClasspathElement));
            } else {
                result.add(getCanonicalFile(new File(wlstExtClasspathElement)).getAbsolutePath());
            }
        }
        // Add the target/classes directory so that any Java classes used by the python code are in the WLST classpath
        result.add(getCanonicalFile(new File(mavenProject.getBuild().getOutputDirectory())).getAbsolutePath());

        String environmentVariableName = WLST_EXT_CLASSPATH_VARIABLE_NAME;
        if (usingOldWlstVersion) {
            environmentVariableName = CLASSPATH_VARIABLE_NAME;
        }
        return new Element(environmentVariableName, getDelimitedStringFromList(result, File.pathSeparatorChar));
    }

    private Element getWlstPropertiesElement() {
        List<String> strings = new ArrayList<>();
        if (isDebug) {
            String debugFlag = String.format(SYSTEM_PROPERTY_FORMAT, WLST_TEST_DEBUG_PROPERTY_NAME, "true");
            getLog().debug(WLST_PROPERTIES_VARIABLE_NAME + " (debug): " + debugFlag);
            strings.add(debugFlag);
        }

        if (argLine != null) {
            getLog().debug(WLST_PROPERTIES_VARIABLE_NAME + " (argLine): " + argLine);
            strings.add(argLine);
        }

        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            String propertyName = entry.getKey();
            String propertyValue = entry.getValue();
            String systemProperty = String.format(SYSTEM_PROPERTY_FORMAT, propertyName, propertyValue);
            getLog().debug(WLST_PROPERTIES_VARIABLE_NAME + " (systemProperties): " + systemProperty);
            strings.add(systemProperty);
        }

        if (strings.isEmpty()) {
            return null;
        } else {
            return new Element(WLST_PROPERTIES_VARIABLE_NAME, getDelimitedStringFromList(strings, ' '));
        }
    }

    private Element buildExecPluginArgumentsElement(File testsDriverScript, List<File> testsToRun) {
        int len = EXEC_PLUGIN_ARGS_LENGTH + testsToRun.size();
        if (skipModuleScanning) {
            len++;
        }

        Element[] argsArray = new Element[len];
        int idx = 0;
        if (skipModuleScanning) {
            argsArray[idx++] = new Element(EXEC_PLUGIN_ARGUMENT, SKIP_MODULE_SCANNING_SWITCH);
        }

        argsArray[idx++] = new Element(EXEC_PLUGIN_ARGUMENT, testsDriverScript.getName());

        File mainPythonSourcesExecuteDirectory =
            getCanonicalFile(new File(mavenProject.getBasedir(), WLST_TEST_MAIN_EXEC_DIR));
        argsArray[idx++] = new Element(EXEC_PLUGIN_ARGUMENT, mainPythonSourcesExecuteDirectory.getAbsolutePath());

        File testPythonSourcesExecuteDirectory =
            getCanonicalFile(new File(mavenProject.getBasedir(), WLST_TEST_TEST_EXEC_DIR));
        argsArray[idx++] = new Element(EXEC_PLUGIN_ARGUMENT, testPythonSourcesExecuteDirectory.getAbsolutePath());

        argsArray[idx++] = new Element(EXEC_PLUGIN_ARGUMENT, verbosity.toString());

        for (File testToRun : testsToRun) {
            argsArray[idx++] = new Element(EXEC_PLUGIN_ARGUMENT, testToRun.getAbsolutePath());
        }
        return new Element(EXEC_PLUGIN_ARGUMENTS, argsArray);
    }

    ///////////////////////////////////////////////////////////////////////////
    //               Miscellaneous Utility Functions                         //
    ///////////////////////////////////////////////////////////////////////////

    private File getCanonicalFile(File file) {
        File result;
        try {
            result = file.getCanonicalFile();
        } catch (IOException ignore) {
            result = file.getAbsoluteFile();
        }
        return result;
    }

    private void createDirectoryIfNeeded(File dir, String name) throws MojoExecutionException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new MojoExecutionException(getMessage("WLSTTEST-013", name, dir.getAbsolutePath()));
        }
    }

    private boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    private boolean isEmpty(File[] files) {
        return files == null || files.length == 0;
    }

    private boolean isEmpty(Map<?,?> map) {
        return map == null || map.isEmpty();
    }

    private String getDelimitedStringFromList(List<String> elements, char separatorChar) {
        StringBuilder stringBuilder = new StringBuilder(elements.get(0));
        for (int idx = 1; idx < elements.size(); idx++) {
            stringBuilder.append(separatorChar);
            stringBuilder.append(elements.get(idx));
        }
        return stringBuilder.toString();
    }

    private String getMessage(String key, Object... args) {
        String tokenizedMessage;
        try {
            tokenizedMessage = WLST_TEST_BUNDLE.getString(key);
        } catch (MissingResourceException ignore) {
            tokenizedMessage = key;
        }

        return MessageFormat.format(tokenizedMessage, args);
    }
}
