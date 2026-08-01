package billiards.viewer;

import javafx.application.Application;
import javafx.stage.Stage;

public final class Main extends Application {

    // Order is constructor, init, start, stop
    // It would be a lot simpler if these methods didn't exist, and I just did
    // stuff myself. A lot less magic that way.

    @Override
    public void init() {}

    @Override
    public void start(final Stage mainWindow) {

        final Viewer viewer = new Viewer(mainWindow);
        viewer.start();
    }

    @Override
    public void stop() {}
}
