package net.rubygrapefruit.gradle.profiler;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.build.BuildEnvironment;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean ok;
        try {
            ok = run(args);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            ok = false;
        }
        System.out.println();
        System.out.flush();
        System.exit(ok ? 0 : 1);
    }

    private static boolean run(String[] args) throws Exception {
        InvocationSettings settings = new CommandLineParser().parseSettings(args);
        if (settings == null) {
            return false;
        }

        setupLogging();

        System.out.println();
        System.out.println("Project dir: " + settings.getProjectDir());
        System.out.println("Profile: " + settings.isProfile());
        System.out.println("Benchmark: " + settings.isBenchmark());
        System.out.println("Tasks: " + settings.getTasks());

        PidInstrumentation pidInstrumentation = new PidInstrumentation();
        JFRControl jfrControl = new JFRControl();
        List<String> tasks = settings.getTasks();

        GradleConnector connector = GradleConnector.newConnector();
        ProjectConnection projectConnection = connector.forProjectDirectory(settings.getProjectDir()).connect();
        try {
            BuildEnvironment buildEnvironment = projectConnection.getModel(BuildEnvironment.class);
            System.out.println("Gradle version: " + buildEnvironment.getGradle().getGradleVersion());
            System.out.println("Java home: " + buildEnvironment.getJava().getJavaHome());
            System.out.println("OS name: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            List<String> jvmArgs = new ArrayList<>(buildEnvironment.getJava().getJvmArguments());
            jvmArgs.add("-XX:+UnlockCommercialFeatures");
            jvmArgs.add("-XX:+FlightRecorder");
            jvmArgs.add("-XX:FlightRecorderOptions=stackdepth=1024");
            jvmArgs.add("-XX:+UnlockDiagnosticVMOptions");
            jvmArgs.add("-XX:+DebugNonSafepoints");

            System.out.println("Java args: " + jvmArgs);

            startOperation("Running warm-up build #1 with tasks " + tasks);
            runBuild(projectConnection, tasks, jvmArgs, pidInstrumentation);
            String pid = pidInstrumentation.getPidForLastBuild();

            startOperation("Running warm-up build #2 with tasks " + tasks);
            runBuild(projectConnection, tasks, jvmArgs, pidInstrumentation);
            checkPid(pid, pidInstrumentation.getPidForLastBuild());

            startOperation("Starting recording for daemon with pid " + pid);
            jfrControl.start(pid);

            startOperation("Running profiling build with tasks " + tasks);
            runBuild(projectConnection, tasks, jvmArgs, pidInstrumentation);
            checkPid(pid, pidInstrumentation.getPidForLastBuild());

            startOperation("Stopping recording for daemon with pid " + pid);
            jfrControl.stop(pid, new File("profile.jfr"));
        } finally {
            projectConnection.close();
        }
        return true;
    }

    private static void setupLogging() throws FileNotFoundException {
        File logFile = new File("profile.log");
        OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile));
        System.setOut(new PrintStream(new TeeOutputStream(System.out, log)));
    }

    private static void startOperation(String name) {
        System.out.println();
        System.out.println("* " + name);
    }

    private static void checkPid(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException( "Multiple Gradle daemons were used. Please make sure all Gradle daemons are stopped before running the profiler.");
        }
    }

    private static void runBuild(ProjectConnection projectConnection, List<?> tasks, List<String> jvmArgs, PidInstrumentation pidInstrumentation)
            throws IOException {
        BuildLauncher build = projectConnection.newBuild();
        build.forTasks(tasks.toArray(new String[0]));
        build.withArguments(pidInstrumentation.getArgs());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        build.setStandardOutput(outputStream);
        build.setStandardError(outputStream);
        build.setJvmArguments(jvmArgs);
        try {
            build.run();
        } catch (GradleConnectionException e) {
            System.out.println();
            System.out.println("ERROR: failed to run build.");
            System.out.println();
            System.out.append(new String(outputStream.toByteArray()));
            System.out.println();
            throw e;
        }
        System.out.println("Used daemon with pid " + pidInstrumentation.getPidForLastBuild());
    }
}