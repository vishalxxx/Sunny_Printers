package sunnyprinters;

/**
 * Entry point for the application. Registers the global uncaught exception
 * handler before JavaFX starts so that all threads — including the FX thread —
 * have their exceptions written to the log file.
 */
public class Launcher {
    public static void main(String[] args) throws Throwable {
        // Register global exception handler as the very first thing
        service.LoggerService.registerUncaughtExceptionHandler();
        Main.main(args);
    }
}
