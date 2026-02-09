package henshin.plugin.views;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);

        // Ensure EMF can load *.ecore files (important for imports in henshin)
        Resource.Factory.Registry.INSTANCE
                .getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());

        System.out.println("[henshin.plugin] Activator started.");
    }
}


