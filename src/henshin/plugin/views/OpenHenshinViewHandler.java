package henshin.plugin.views;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class OpenHenshinViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return null;
            }

            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return null;
            }

            // Open your view by ID
            page.showView(HenshinPluginView.ID);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
