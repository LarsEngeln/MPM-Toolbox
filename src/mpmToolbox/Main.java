package mpmToolbox;

import com.alee.laf.WebLookAndFeel;
import meico.Meico;
import mpmToolbox.gui.MpmToolbox;
import mpmToolbox.gui.Settings;
import mpmToolbox.supplementary.Tools;

import java.io.IOException;

/**
 * This class provides the current version number of MPM Toolbox.
 * @author Axel Berndt
 */
public class Main {
    public static final String version = "0.1.43 (alpha) - snap";

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            System.err.println("Uncaught exception in thread: " + thread.getName());
            error.printStackTrace();
        });

        System.out.println("java.version = " + System.getProperty("java.version"));
        System.out.println("java.home = " + System.getProperty("java.home"));
        System.out.println("user.dir = " + System.getProperty("user.dir"));
        System.out.println("classpath = " + System.getProperty("java.class.path"));

        System.out.println("maxHeap = " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MiB");

        // read the application settings from file
        try {
            Settings.readSettings();
        } catch (IOException e) {
            System.err.println("File " + Settings.settingsFile + " not found.");
        }

        // redirect commandline output to a logfile
        if (Settings.makeLogfile)
            Tools.commandlineToLogfile(Settings.logfile);

        System.out.println("MPM Toolkit version " + Main.version + "\nmeico version " + Meico.version + "\nrunning on " + System.getProperty("os.name") + " version " + System.getProperty("os.version") + ", " + System.getProperty("os.arch") + "\nJava version " + System.getProperty("java.version"));

        WebLookAndFeel.setForceSingleEventsThread(true);    // make sure that GUI defining operations are performed in an Event Dispatch Thread (EDT) or throw an exception, TODO: set false for deployment

        new MpmToolbox();
    }
}
