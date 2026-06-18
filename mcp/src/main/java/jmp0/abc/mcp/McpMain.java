package jmp0.abc.mcp;

import com.google.gson.*;
import jmp0.abc.file.PandaFile;
import jmp0.abc.file.clazz.PandaClass;
import jmp0.abc.file.desc.IndexHeader;
import jmp0.abc.util.HapUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class McpMain {
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    JsonObject request = JsonParser.parseString(line).getAsJsonObject();
                    handleRequest(request);
                } catch (Exception e) {
                    sendError(null, -32700, "Parse error", e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(JsonObject request) {
        if (!request.has("method")) return; // Might be a response to something we sent, ignore
        String method = request.get("method").getAsString();
        JsonElement id = request.get("id");

        try {
            switch (method) {
                case "initialize":
                    handleInitialize(id);
                    break;
                case "notifications/initialized":
                    // Do nothing
                    break;
                case "tools/list":
                    handleToolsList(id);
                    break;
                case "tools/call":
                    handleToolsCall(id, request.getAsJsonObject("params"));
                    break;
                default:
                    if (id != null) {
                        sendError(id, -32601, "Method not found", method);
                    }
                    break;
            }
        } catch (Exception e) {
            if (id != null) {
                sendError(id, -32000, "Internal error", e.getMessage());
            }
        }
    }

    private static void handleInitialize(JsonElement id) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");
        
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);
        
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "xpanda-mcp");
        serverInfo.addProperty("version", "1.0.0");
        result.add("serverInfo", serverInfo);
        
        sendResponse(id, result);
    }

    private static void handleToolsList(JsonElement id) {
        JsonArray tools = new JsonArray();

        // 1. open_archive
        JsonObject openArchive = new JsonObject();
        openArchive.addProperty("name", "open_archive");
        openArchive.addProperty("description", "Load HAP/HSP/App or ABC files. Scans all ABC files and builds a global index cache.");
        JsonObject openArchiveInput = new JsonObject();
        openArchiveInput.addProperty("type", "object");
        JsonObject openArchiveProps = new JsonObject();
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute path to the archive file");
        openArchiveProps.add("path", pathProp);
        openArchiveInput.add("properties", openArchiveProps);
        JsonArray openArchiveReq = new JsonArray();
        openArchiveReq.add("path");
        openArchiveInput.add("required", openArchiveReq);
        openArchive.add("inputSchema", openArchiveInput);
        tools.add(openArchive);

        // 2. get_project_tree
        JsonObject getTree = new JsonObject();
        getTree.addProperty("name", "get_project_tree");
        getTree.addProperty("description", "Returns the package structure tree of the loaded project, grouping by packages.");
        JsonObject emptySchema = new JsonObject();
        emptySchema.addProperty("type", "object");
        emptySchema.add("properties", new JsonObject());
        getTree.add("inputSchema", emptySchema);
        tools.add(getTree);

        // 3. get_manifest_info
        JsonObject getManifest = new JsonObject();
        getManifest.addProperty("name", "get_manifest_info");
        getManifest.addProperty("description", "Parses and returns formatted module.json5 or app.json5 from the loaded archive.");
        getManifest.add("inputSchema", emptySchema);
        tools.add(getManifest);

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        sendResponse(id, result);
    }

    private static void handleToolsCall(JsonElement id, JsonObject params) {
        String name = params.get("name").getAsString();
        JsonObject args = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        String contentText;
        try {
            switch (name) {
                case "open_archive":
                    String path = args.get("path").getAsString();
                    WorkspaceContext.getInstance().openArchive(path);
                    PandaFile pf = WorkspaceContext.getInstance().getCurrentProject().getPandaFile();
                    int classCount = 0;
                    for (IndexHeader ih : pf.getIndexHeaders()) {
                        classCount += ih.getPandaClasses().length;
                    }
                    contentText = "Successfully loaded archive: " + path + "\nTotal classes found: " + classCount;
                    break;
                case "get_project_tree":
                    if (WorkspaceContext.getInstance().getCurrentProject() == null) {
                        throw new Exception("No archive loaded. Call open_archive first.");
                    }
                    contentText = generateProjectTree();
                    break;
                case "get_manifest_info":
                    if (WorkspaceContext.getInstance().getCurrentArchiveFile() == null) {
                        throw new Exception("No archive loaded. Call open_archive first.");
                    }
                    contentText = getManifestInfo();
                    break;
                default:
                    throw new Exception("Unknown tool: " + name);
            }
            sendToolResponse(id, contentText, false);
        } catch (Exception e) {
            sendToolResponse(id, "Error executing tool: " + e.getMessage(), true);
        }
    }

    private static String generateProjectTree() {
        PandaFile pf = WorkspaceContext.getInstance().getCurrentProject().getPandaFile();
        List<String> classes = new ArrayList<>();
        for (IndexHeader ih : pf.getIndexHeaders()) {
            for (PandaClass pc : ih.getPandaClasses()) {
                classes.add(pc.getName().getContent());
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Project Tree:\n");
        // Simplified tree generation (listing packages or classes)
        // For token efficiency and readability, we group by the first few segments of the package
        List<String> sortedClasses = new ArrayList<>(classes);
        sortedClasses.sort(String::compareTo);
        for(String c : sortedClasses) {
            sb.append("- ").append(c).append("\n");
        }
        
        // Truncate if too large to avoid blowing up the token context
        if (sb.length() > 50000) {
            return sb.substring(0, 50000) + "\n... (truncated due to size)";
        }
        return sb.toString();
    }

    private static String getManifestInfo() throws Exception {
        File file = WorkspaceContext.getInstance().getCurrentArchiveFile();
        if (file.getName().endsWith(".abc")) {
            return "Loaded file is a raw .abc file. No manifest available.";
        }
        
        // Attempt to read module.json
        String moduleJson = HapUtils.getManifestFromHap(file, "module.json");
        if (moduleJson == null) {
            moduleJson = HapUtils.getManifestFromHap(file, "ets/module.json");
        }
        
        if (moduleJson != null) {
            return "Manifest Info (module.json):\n" + moduleJson;
        }
        return "No module.json found in the archive.";
    }

    private static void sendToolResponse(JsonElement id, String text, boolean isError) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject contentItem = new JsonObject();
        contentItem.addProperty("type", "text");
        contentItem.addProperty("text", text);
        content.add(contentItem);
        result.add("content", content);
        if (isError) {
            result.addProperty("isError", true);
        }
        sendResponse(id, result);
    }

    private static void sendResponse(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        System.out.println(gson.toJson(response));
    }

    private static void sendError(JsonElement id, int code, String message, String data) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        if (data != null) {
            error.addProperty("data", data);
        }
        response.add("error", error);
        System.out.println(gson.toJson(response));
    }
}
