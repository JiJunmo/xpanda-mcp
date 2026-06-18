package jmp0.abc.mcp;

import com.google.gson.*;
import jmp0.abc.disasm.PandaDisAssemblerFakeCodeHelper;
import jmp0.abc.disasm.block.PandaIRCFG;
import jmp0.abc.file.PandaFile;
import jmp0.abc.file.clazz.PandaClass;
import jmp0.abc.file.desc.IndexHeader;
import jmp0.abc.file.method.PandaMethod;
import jmp0.abc.util.HapUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

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

        JsonObject emptySchema = new JsonObject();
        emptySchema.addProperty("type", "object");
        emptySchema.add("properties", new JsonObject());

        // 2. get_project_tree
        JsonObject getTree = new JsonObject();
        getTree.addProperty("name", "get_project_tree");
        getTree.addProperty("description", "Returns the package structure tree of the loaded project, grouping by packages.");
        getTree.add("inputSchema", emptySchema);
        tools.add(getTree);

        // 3. get_manifest_info
        JsonObject getManifest = new JsonObject();
        getManifest.addProperty("name", "get_manifest_info");
        getManifest.addProperty("description", "Parses and returns formatted module.json5 or app.json5 from the loaded archive.");
        getManifest.add("inputSchema", emptySchema);
        tools.add(getManifest);

        // 4. get_class_source
        JsonObject getClassSource = new JsonObject();
        getClassSource.addProperty("name", "get_class_source");
        getClassSource.addProperty("description", "Returns the full decompiled source code for a specific class.");
        JsonObject classInput = new JsonObject();
        classInput.addProperty("type", "object");
        JsonObject classProps = new JsonObject();
        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "The class name, e.g. Lcom/huawei/wallet/Main;");
        classProps.add("className", classNameProp);
        classInput.add("properties", classProps);
        JsonArray classReq = new JsonArray();
        classReq.add("className");
        classInput.add("required", classReq);
        getClassSource.add("inputSchema", classInput);
        tools.add(getClassSource);

        // 5. get_method_source & 6. get_bytecode
        JsonObject methodInput = new JsonObject();
        methodInput.addProperty("type", "object");
        JsonObject methodProps = new JsonObject();
        methodProps.add("className", classNameProp);
        JsonObject methodNameProp = new JsonObject();
        methodNameProp.addProperty("type", "string");
        methodNameProp.addProperty("description", "The exact method name.");
        methodProps.add("methodName", methodNameProp);
        JsonObject sigProp = new JsonObject();
        sigProp.addProperty("type", "string");
        sigProp.addProperty("description", "Optional signature to resolve overloads");
        methodProps.add("signature", sigProp);
        methodInput.add("properties", methodProps);
        JsonArray methodReq = new JsonArray();
        methodReq.add("className");
        methodReq.add("methodName");
        methodInput.add("required", methodReq);

        JsonObject getMethodSource = new JsonObject();
        getMethodSource.addProperty("name", "get_method_source");
        getMethodSource.addProperty("description", "Returns the decompiled source code for a specific method.");
        getMethodSource.add("inputSchema", methodInput);
        tools.add(getMethodSource);

        JsonObject getBytecode = new JsonObject();
        getBytecode.addProperty("name", "get_bytecode");
        getBytecode.addProperty("description", "Returns raw bytecode instructions (Smali-like) for a specific method.");
        getBytecode.add("inputSchema", methodInput);
        tools.add(getBytecode);

        // 7. search_classes
        JsonObject searchClasses = new JsonObject();
        searchClasses.addProperty("name", "search_classes");
        searchClasses.addProperty("description", "Searches for a class name in the loaded archive using a keyword.");
        JsonObject searchInput = new JsonObject();
        searchInput.addProperty("type", "object");
        JsonObject searchProps = new JsonObject();
        JsonObject keywordProp = new JsonObject();
        keywordProp.addProperty("type", "string");
        keywordProp.addProperty("description", "Keyword to search for (e.g. MainAbility)");
        searchProps.add("keyword", keywordProp);
        searchInput.add("properties", searchProps);
        JsonArray searchReq = new JsonArray();
        searchReq.add("keyword");
        searchInput.add("required", searchReq);
        searchClasses.add("inputSchema", searchInput);
        tools.add(searchClasses);

        // 8. get_class_info
        JsonObject getClassInfo = new JsonObject();
        getClassInfo.addProperty("name", "get_class_info");
        getClassInfo.addProperty("description", "Returns a list of methods and signatures for a specific class without decompiling it fully.");
        getClassInfo.add("inputSchema", classInput);
        tools.add(getClassInfo);

        // 9. get_xrefs_to_class
        JsonObject getXrefsClass = new JsonObject();
        getXrefsClass.addProperty("name", "get_xrefs_to_class");
        getXrefsClass.addProperty("description", "Finds all classes and methods that reference the specified class.");
        getXrefsClass.add("inputSchema", classInput);
        tools.add(getXrefsClass);

        // 10. get_xrefs_to_method
        JsonObject getXrefsMethod = new JsonObject();
        getXrefsMethod.addProperty("name", "get_xrefs_to_method");
        getXrefsMethod.addProperty("description", "Finds all methods that call the specified method.");
        getXrefsMethod.add("inputSchema", methodInput);
        tools.add(getXrefsMethod);

        // 11. get_xrefs_to_field
        JsonObject fieldInput = new JsonObject();
        fieldInput.addProperty("type", "object");
        JsonObject fieldProps = new JsonObject();
        fieldProps.add("className", classNameProp);
        JsonObject fieldNameProp = new JsonObject();
        fieldNameProp.addProperty("type", "string");
        fieldNameProp.addProperty("description", "The exact field name.");
        fieldProps.add("fieldName", fieldNameProp);
        fieldInput.add("properties", fieldProps);
        JsonArray fieldReq = new JsonArray();
        fieldReq.add("className");
        fieldReq.add("fieldName");
        fieldInput.add("required", fieldReq);
        
        JsonObject getXrefsField = new JsonObject();
        getXrefsField.addProperty("name", "get_xrefs_to_field");
        getXrefsField.addProperty("description", "Finds all methods that read or write to the specified field.");
        getXrefsField.add("inputSchema", fieldInput);
        tools.add(getXrefsField);

        // 12. get_inheritance_hierarchy
        JsonObject getInheritance = new JsonObject();
        getInheritance.addProperty("name", "get_inheritance_hierarchy");
        getInheritance.addProperty("description", "Returns the inheritance chain (parent/children) for a class.");
        getInheritance.add("inputSchema", classInput);
        tools.add(getInheritance);

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
                    requireContext();
                    contentText = generateProjectTree();
                    break;
                case "get_manifest_info":
                    requireContext();
                    contentText = getManifestInfo();
                    break;
                case "get_class_source":
                    requireContext();
                    contentText = getClassSource(args.get("className").getAsString());
                    break;
                case "get_method_source":
                    requireContext();
                    contentText = getMethodSource(args.get("className").getAsString(), args.get("methodName").getAsString());
                    break;
                case "get_bytecode":
                    requireContext();
                    contentText = getBytecode(args.get("className").getAsString(), args.get("methodName").getAsString());
                    break;
                case "search_classes":
                    requireContext();
                    contentText = searchClasses(args.get("keyword").getAsString());
                    break;
                case "get_class_info":
                    requireContext();
                    contentText = getClassInfo(args.get("className").getAsString());
                    break;
                case "get_xrefs_to_class":
                    requireContext();
                    contentText = formatXrefs("Class " + args.get("className").getAsString(), WorkspaceContext.getInstance().getXrefManager().getClassXrefs(args.get("className").getAsString()));
                    break;
                case "get_xrefs_to_method":
                    requireContext();
                    contentText = formatXrefs("Method " + args.get("methodName").getAsString(), WorkspaceContext.getInstance().getXrefManager().getMethodXrefs(args.get("className").getAsString(), args.get("methodName").getAsString()));
                    break;
                case "get_xrefs_to_field":
                    requireContext();
                    contentText = formatXrefs("Field " + args.get("fieldName").getAsString(), WorkspaceContext.getInstance().getXrefManager().getFieldXrefs(args.get("className").getAsString(), args.get("fieldName").getAsString()));
                    break;
                case "get_inheritance_hierarchy":
                    requireContext();
                    contentText = WorkspaceContext.getInstance().getXrefManager().getInheritanceHierarchy(args.get("className").getAsString());
                    break;
                default:
                    throw new Exception("Unknown tool: " + name);
            }
            sendToolResponse(id, contentText, false);
        } catch (Exception e) {
            sendToolResponse(id, "Error executing tool: " + e.getMessage(), true);
        }
    }

    private static void requireContext() throws Exception {
        if (WorkspaceContext.getInstance().getCurrentProject() == null) {
            throw new Exception("No archive loaded. Call open_archive first.");
        }
    }

    private static String formatXrefs(String target, List<String> xrefs) {
        if (xrefs == null || xrefs.isEmpty()) {
            return "No cross-references found for " + target;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Cross-references for ").append(target).append(" (").append(xrefs.size()).append(" found):\n");
        // Limit output to prevent token explosion
        int count = 0;
        for (String xref : xrefs) {
            sb.append("- ").append(xref).append("\n");
            count++;
            if (count >= 300) {
                sb.append("... (truncated at 300 results)");
                break;
            }
        }
        return sb.toString();
    }

    private static PandaClass findPandaClass(String className) throws Exception {
        PandaFile pf = WorkspaceContext.getInstance().getCurrentProject().getPandaFile();
        for (IndexHeader ih : pf.getIndexHeaders()) {
            for (PandaClass pc : ih.getPandaClasses()) {
                if (pc.getName().getContent().equals(className)) {
                    return pc;
                }
            }
        }
        throw new Exception("Class not found: " + className);
    }

    private static PandaMethod findPandaMethod(PandaClass pc, String methodName) throws Exception {
        PandaMethod method = pc.getPandaMethods().get(methodName);
        if (method == null) {
            throw new Exception("Method not found: " + methodName + " in class " + pc.getName().getContent());
        }
        return method;
    }

    private static String getClassSource(String className) throws Exception {
        PandaClass pc = findPandaClass(className);
        PandaIRCFG[] cfgs = pc.disAssembleAllMethods();
        return PandaDisAssemblerFakeCodeHelper.genFakeCode(pc, cfgs);
    }

    private static String getMethodSource(String className, String methodName) throws Exception {
        PandaClass pc = findPandaClass(className);
        PandaMethod pm = findPandaMethod(pc, methodName);
        PandaIRCFG cfg = pm.disAssemble();
        if (cfg == null) {
            return "Method " + methodName + " has no code or cannot be disassembled.";
        }
        return PandaDisAssemblerFakeCodeHelper.genFakeCode(pc, new PandaIRCFG[]{cfg});
    }

    private static String getBytecode(String className, String methodName) throws Exception {
        PandaClass pc = findPandaClass(className);
        PandaMethod pm = findPandaMethod(pc, methodName);
        PandaIRCFG cfg = pm.disAssemble();
        if (cfg == null) {
            return "Method " + methodName + " has no bytecode.";
        }
        return "Method: " + methodName + "\n" + cfg.toString();
    }
    
    private static String searchClasses(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        PandaFile pf = WorkspaceContext.getInstance().getCurrentProject().getPandaFile();
        Set<String> matches = new LinkedHashSet<>();
        
        for (IndexHeader ih : pf.getIndexHeaders()) {
            for (PandaClass pc : ih.getPandaClasses()) {
                String cName = pc.getName().getContent();
                if (cName.toLowerCase().contains(lowerKeyword)) {
                    matches.add(cName);
                    if (matches.size() >= 100) {
                        break;
                    }
                }
            }
            if (matches.size() >= 100) break;
        }

        if (matches.size() < 100) {
            GlobalXrefManager xrefs = WorkspaceContext.getInstance().getXrefManager();
            List<String> classesUsingString = xrefs.getClassesUsingString(keyword);
            for(String c : classesUsingString) {
                matches.add(c + " (via string literal match)");
                if (matches.size() >= 100) break;
            }
        }
        
        if (matches.isEmpty()) {
            return "No classes found matching keyword: " + keyword;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Search Results (max 100):\n");
        for (String m : matches) {
            sb.append("- ").append(m).append("\n");
        }
        return sb.toString();
    }

    private static String getClassInfo(String className) throws Exception {
        PandaClass pc = findPandaClass(className);
        StringBuilder sb = new StringBuilder();
        sb.append("Class Info: ").append(pc.getName().getContent()).append("\n");
        sb.append("Methods:\n");
        if (pc.getPandaMethods().isEmpty()) {
            sb.append("(No methods found)");
        } else {
            for (PandaMethod pm : pc.getPandaMethods().values()) {
                sb.append("- ").append(pm.getName().getContent())
                  .append(" (Args: ").append(pm.getMethodCode() != null ? pm.getMethodCode().getNumArgs().intValue() : "unknown").append(")\n");
            }
        }
        return sb.toString();
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
        List<String> sortedClasses = new ArrayList<>(classes);
        sortedClasses.sort(String::compareTo);
        for(String c : sortedClasses) {
            sb.append("- ").append(c).append("\n");
        }
        
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
