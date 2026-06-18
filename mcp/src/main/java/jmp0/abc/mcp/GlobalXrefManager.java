package jmp0.abc.mcp;

import jmp0.abc.disasm.block.PandaIRBasicBlock;
import jmp0.abc.disasm.block.PandaIRCFG;
import jmp0.abc.disasm.ins.PandaInstruction;
import jmp0.abc.disasm.ins.IPandaInstruction;
import jmp0.abc.disasm.param.IPandaInstructionParam;
import jmp0.abc.disasm.param.PandaInstructionID;
import jmp0.abc.file.PandaFile;
import jmp0.abc.file.clazz.PandaClass;
import jmp0.abc.file.desc.IndexHeader;
import jmp0.abc.file.desc.Offset;
import jmp0.abc.file.desc.PandaString;
import jmp0.abc.file.field.PandaField;
import jmp0.abc.file.method.PandaMethod;

import java.util.*;

public class GlobalXrefManager {
    private final Map<String, List<String>> classXrefs = new HashMap<>();
    private final Map<String, List<String>> methodXrefs = new HashMap<>();
    private final Map<String, List<String>> fieldXrefs = new HashMap<>();
    private final Map<String, List<String>> stringXrefs = new HashMap<>();

    private final Map<String, String> inheritanceParent = new HashMap<>();
    private final Map<String, List<String>> inheritanceChildren = new HashMap<>();

    private boolean isBuilt = false;

    public synchronized void build(PandaFile pf) {
        if (isBuilt) return;
        System.out.println("[MCP] Building global xref cache... This may take a moment.");
        long startTime = System.currentTimeMillis();

        for (IndexHeader ih : pf.getIndexHeaders()) {
            for (PandaClass pc : ih.getPandaClasses()) {
                String className = pc.getName().getContent();

                // Build Inheritance
                try {
                    Offset superOff = pc.getSuperClassOff();
                    if (superOff != null && superOff.getOffset().intValue() != 0) {
                        Object resolved = pf.resolveOffset(superOff.getOffset());
                        if (resolved instanceof PandaClass) {
                            String parentName = ((PandaClass) resolved).getName().getContent();
                            inheritanceParent.put(className, parentName);
                            inheritanceChildren.computeIfAbsent(parentName, k -> new ArrayList<>()).add(className);
                            addClassXref(parentName, className + " (extends)");
                        }
                    }
                } catch (Exception e) {
                    // Ignore inheritance resolve errors
                }

                // Disassemble and extract xrefs
                if (pc.getPandaMethods() != null) {
                    for (PandaMethod pm : pc.getPandaMethods().values()) {
                        String sourceMethod = className + "->" + pm.getName().getContent();
                        try {
                            PandaIRCFG cfg = pm.disAssemble();
                            if (cfg == null || cfg.getPandaIRBasicBlocks() == null) continue;
                            for (PandaIRBasicBlock block : cfg.getPandaIRBasicBlocks()) {
                                if (block.getPandaInstructions() == null) continue;
                                for (PandaInstruction inst : block.getPandaInstructions()) {
                                    if (inst instanceof IPandaInstruction) {
                                        IPandaInstruction instruction = (IPandaInstruction) inst;
                                        for (IPandaInstructionParam param : instruction.getParams()) {
                                            if (param instanceof PandaInstructionID) {
                                                PandaInstructionID idParam = (PandaInstructionID) param;
                                                extractXrefFromParam(idParam, sourceMethod);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip methods that fail to disassemble
                        }
                    }
                }
            }
        }

        // Second pass: heuristic inheritance based on strings
        // If a class uses a string matching another short class name in its <init> or #~@0=#... methods
        // we can guess it might be a parent. For simplicity we'll just expose strings.

        isBuilt = true;
        System.out.println("[MCP] Xref cache built in " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    private void extractXrefFromParam(PandaInstructionID idParam, String sourceMethod) {
        try {
            if (idParam.getType() == PandaInstructionID.TYPE.METHOD) {
                if (idParam.getObj() instanceof PandaMethod) {
                    PandaMethod targetMethod = (PandaMethod) idParam.getObj();
                    if (targetMethod.getParent() != null) {
                        String targetClass = targetMethod.getParent().getName().getContent();
                        String target = targetClass + "->" + targetMethod.getName().getContent();
                        addMethodXref(target, sourceMethod);
                        addClassXref(targetClass, sourceMethod);
                    }
                }
            } else if (idParam.getType() == PandaInstructionID.TYPE.FIELD) {
                if (idParam.getObj() instanceof PandaField) {
                    PandaField targetField = (PandaField) idParam.getObj();
                    if (targetField.getParent() != null) {
                        String targetClass = targetField.getParent().getName().getContent();
                        String target = targetClass + "->" + targetField.getName().getContent();
                        addFieldXref(target, sourceMethod);
                        addClassXref(targetClass, sourceMethod);
                    }
                }
            } else if (idParam.getType() == PandaInstructionID.TYPE.TYPE) {
                if (idParam.getObj() instanceof PandaClass) {
                    addClassXref(((PandaClass) idParam.getObj()).getName().getContent(), sourceMethod);
                }
            } else if (idParam.getType() == PandaInstructionID.TYPE.STRING) {
                if (idParam.getObj() instanceof PandaString) {
                    String strVal = ((PandaString) idParam.getObj()).getContent();
                    addStringXref(strVal, sourceMethod);
                }
            }
        } catch (Exception e) {
            // ignore extraction errors
        }
    }

    private void addClassXref(String target, String source) {
        classXrefs.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
    }

    private void addMethodXref(String target, String source) {
        methodXrefs.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
    }

    private void addFieldXref(String target, String source) {
        fieldXrefs.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
    }

    private void addStringXref(String target, String source) {
        stringXrefs.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
    }

    private String extractShortName(String className) {
        if (className == null) return null;
        String name = className;
        if (name.startsWith("L")) name = name.substring(1);
        if (name.endsWith(";")) name = name.substring(0, name.length() - 1);
        if (name.endsWith("&")) name = name.substring(0, name.length() - 1);
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash != -1) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }

    public List<String> getClassXrefs(String className) {
        Set<String> result = new LinkedHashSet<>(classXrefs.getOrDefault(className, Collections.emptyList()));
        String shortName = extractShortName(className);
        if (shortName != null && !shortName.isEmpty()) {
            for (String usage : stringXrefs.getOrDefault(shortName, Collections.emptyList())) {
                result.add(usage + " (heuristic string match)");
            }
        }
        return new ArrayList<>(result);
    }

    public List<String> getMethodXrefs(String className, String methodName) {
        Set<String> result = new LinkedHashSet<>(methodXrefs.getOrDefault(className + "->" + methodName, Collections.emptyList()));
        if (methodName != null && !methodName.isEmpty()) {
            List<String> strUsages = stringXrefs.get(methodName);
            if (strUsages != null) {
                String shortClass = extractShortName(className);
                for (String usage : strUsages) {
                    if (shortClass != null && stringXrefs.getOrDefault(shortClass, Collections.emptyList()).contains(usage)) {
                        result.add(usage + " (heuristic string match)");
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    public List<String> getFieldXrefs(String className, String fieldName) {
        return fieldXrefs.getOrDefault(className + "->" + fieldName, Collections.emptyList());
    }

    public List<String> getClassesUsingString(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : stringXrefs.entrySet()) {
            if (entry.getKey().toLowerCase().contains(lowerKeyword)) {
                for (String usage : entry.getValue()) {
                    int arrowIdx = usage.indexOf("->");
                    if (arrowIdx != -1) {
                        result.add(usage.substring(0, arrowIdx));
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    public String getInheritanceHierarchy(String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hierarchy for ").append(className).append(":\n");
        String parent = inheritanceParent.get(className);
        
        if (parent == null) {
            // Heuristic fallback for ArkTS
            String shortName = extractShortName(className);
            if (shortName != null) {
                for (String usage : stringXrefs.getOrDefault(shortName, Collections.emptyList())) {
                    if (usage.contains("#~@0=#") || usage.contains("<init>")) {
                        // Found class init doing string ref, the class it belongs to might be child?
                        // Or if this class references something else
                        // Let's check what strings this class uses
                        break;
                    }
                }
            }
        }

        if (parent != null) {
            sb.append("Parent: ").append(parent).append("\n");
        } else {
            sb.append("Parent: (none or unresolved)\n");
            // Find any strings used in the class that look like a base class name
            List<String> candidateParents = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : stringXrefs.entrySet()) {
                String str = entry.getKey();
                if (str.endsWith("Ability") || str.endsWith("Model") || str.startsWith("Base")) {
                    for (String usage : entry.getValue()) {
                        if (usage.startsWith(className + "->")) {
                            candidateParents.add(str);
                            break;
                        }
                    }
                }
            }
            if (!candidateParents.isEmpty()) {
                sb.append("Heuristic Parent Candidates (from string literals): ").append(candidateParents).append("\n");
            }
        }
        
        List<String> children = inheritanceChildren.get(className);
        if (children != null && !children.isEmpty()) {
            sb.append("Children:\n");
            for (String child : children) {
                sb.append("  - ").append(child).append("\n");
            }
        } else {
            sb.append("Children: (none)\n");
        }
        return sb.toString();
    }
}
