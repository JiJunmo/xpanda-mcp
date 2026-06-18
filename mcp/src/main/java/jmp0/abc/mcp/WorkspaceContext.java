package jmp0.abc.mcp;

import jmp0.abc.gui.data.PandaProjectInfo;
import java.io.File;

public class WorkspaceContext {
    private static WorkspaceContext instance;
    private PandaProjectInfo currentProject;
    private File currentArchiveFile;

    private WorkspaceContext() {}

    public static synchronized WorkspaceContext getInstance() {
        if (instance == null) {
            instance = new WorkspaceContext();
        }
        return instance;
    }

    public void openArchive(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            throw new Exception("File not found: " + path);
        }
        PandaProjectInfo projectInfo = PandaProjectInfo.createPandaProject(file);
        if (projectInfo == null || projectInfo.getPandaFile() == null) {
            throw new Exception("Failed to parse PandaFile from " + path);
        }
        this.currentProject = projectInfo;
        this.currentArchiveFile = file;
    }

    public PandaProjectInfo getCurrentProject() {
        return currentProject;
    }

    public File getCurrentArchiveFile() {
        return currentArchiveFile;
    }
}
