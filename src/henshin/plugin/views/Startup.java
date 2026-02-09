package henshin.plugin.views;

import org.eclipse.ui.IStartup;

public class Startup implements IStartup {
    @Override
    public void earlyStartup() {
        System.out.println("[henshin.plugin] earlyStartup reached (plugin loaded).");
    }
}
