package TartarusCore.TxtConverter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles semantic compression for Godot Engine files (.tscn, .tres).
 * Version 2.0: Supports SubResource inlining, Signals, and accurate Property preservation.
 */
public class GodotCompactConverter {

    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[(.*?)\\]");
    private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)=((?:\"[^\"]*\")|(?:[^\\s\\]]+))");
    private static final Pattern TRANSFORM_PATTERN = Pattern.compile("Transform3D\\((.*?)\\)");

    // Entry point
    public static String convert(String content, String fileName) {
        GodotCompactConverter converter = new GodotCompactConverter();
        return converter.process(content, fileName);
    }

    // --- State ---
    // Maps "ExtResource(id)" -> "AliasName"
    private final Map<String, String> extResourceAliases = new HashMap<>();
    // Maps "SubResource(id)" -> GdNode (The actual resource object)
    private final Map<String, GdNode> subResourceCache = new HashMap<>();

    // Maps NodePath -> GdNode (for scene tree building)
    private final Map<String, GdNode> nodePathMap = new HashMap<>();

    // The main roots (Scene roots or Main Resource)
    private final List<GdNode> rootNodes = new ArrayList<>();

    // Output buffer
    private final StringBuilder output = new StringBuilder();

    // Counters for aliases
    private int scriptCounter = 1;
    private int resourceCounter = 1;

    private String process(String content, String fileName) {
        String[] lines = content.split("\\R");

        // --- PASS 1: Pre-process External Resources (Aliases) ---
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[ext_resource")) {
                Map<String, String> attrs = parseAttributes(extractContent(line));
                String id = cleanStr(attrs.get("id"));
                String path = cleanStr(attrs.get("path"));
                String type = cleanStr(attrs.get("type"));

                String alias;
                if (path != null && !path.isEmpty()) {
                    String fName = extractNameFromPath(path);
                    if (path.endsWith(".gd")) alias = "$Script_" + fName;
                    else if (path.endsWith(".tscn")) alias = "$Scene_" + fName;
                    else alias = "$Res_" + fName;
                } else {
                    alias = "$Ext_" + type + "_" + id;
                }
                extResourceAliases.put(id, alias);
            }
        }

        // --- PASS 2: Parse Nodes, SubResources, and Connections ---
        GdNode currentNode = null;
        String currentContext = null; // "node", "sub_resource", "resource"

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Skip comments (but keep them if meaningful? No, max compression implies stripping)
            if (line.startsWith(";") || line.startsWith("#")) continue;

            if (line.startsWith("[")) {
                String header = extractContent(line);
                Map<String, String> attrs = parseAttributes(header);
                String typeKey = header.split("\\s+")[0];

                if (typeKey.equals("gd_scene") || typeKey.equals("gd_resource")) {
                    continue; // File header, ignore
                }
                else if (typeKey.equals("ext_resource")) {
                    currentNode = null; // Skip body
                }
                else if (typeKey.equals("sub_resource")) {
                    String id = cleanStr(attrs.get("id"));
                    String type = cleanStr(attrs.get("type"));
                    currentNode = new GdNode();
                    currentNode.type = type;
                    currentNode.isSubResource = true;
                    subResourceCache.put(id, currentNode);
                }
                else if (typeKey.equals("resource")) {
                    // Main resource definition in .tres
                    currentNode = new GdNode();
                    currentNode.name = "RootResource";
                    currentNode.type = fileName; // Use filename as type for context
                    rootNodes.add(currentNode);
                }
                else if (typeKey.equals("node")) {
                    currentNode = new GdNode();
                    currentNode.name = cleanStr(attrs.get("name"));
                    currentNode.type = cleanStr(attrs.get("type"));
                    String parent = cleanStr(attrs.get("parent"));

                    // Register path
                    String fullPath = (parent == null || parent.equals(".")) ? currentNode.name : parent + "/" + currentNode.name;
                    nodePathMap.put(fullPath, currentNode);

                    if (parent == null || parent.equals(".")) {
                        rootNodes.add(currentNode);
                    } else {
                        GdNode parentNode = nodePathMap.get(parent);
                        if (parentNode != null) {
                            parentNode.children.add(currentNode);
                        } else {
                            // Parent not found yet (ordering issue or root relative), treat as root temporarily
                            // In valid tscn, parent always defined before child usually.
                            // If not, we might lose hierarchy, but list it as root.
                            rootNodes.add(currentNode);
                        }
                    }
                }
                else if (typeKey.equals("connection")) {
                    // Signal connection
                    String signal = cleanStr(attrs.get("signal"));
                    String from = cleanStr(attrs.get("from"));
                    String to = cleanStr(attrs.get("to"));
                    String method = cleanStr(attrs.get("method"));

                    // Try to attach to the source node
                    GdNode fromNode = nodePathMap.get(from);
                    if (fromNode == null && from.equals(".")) {
                        // "." usually refers to the scene root
                        if (!rootNodes.isEmpty()) fromNode = rootNodes.get(0);
                    }

                    String sigStr = "Signal: " + signal + " -> " + to + "::" + method + "()";
                    if (fromNode != null) {
                        fromNode.signals.add(sigStr);
                    } else {
                        // Orphan signal (should not happen often)
                        if (!rootNodes.isEmpty()) rootNodes.get(0).signals.add(sigStr + " (Unknown Source: "+from+")");
                    }
                    currentNode = null;
                }
            }
            else if (currentNode != null) {
                // Property parsing
                int eqIndex = line.indexOf('=');
                if (eqIndex > 0) {
                    String key = line.substring(0, eqIndex).trim();
                    String val = line.substring(eqIndex + 1).trim();

                    if (!isUselessProperty(key)) {
                        currentNode.properties.put(key, val);
                    }
                }
            }
        }

        // --- PASS 3: Generate Output ---
        output.append("# Godot Compact (v2): ").append(fileName).append("\n");

        for (GdNode node : rootNodes) {
            printNode(node, "");
        }

        return output.toString();
    }

    private void printNode(GdNode node, String indent) {
        // Header: Name (Type)
        output.append(indent);
        if (node.isSubResource) {
            output.append("SubResource");
        } else {
            output.append(node.name);
        }

        if (node.type != null) {
            output.append(" (").append(node.type).append(")");
        }
        output.append(":\n");

        // Properties
        for (Map.Entry<String, String> entry : node.properties.entrySet()) {
            String key = entry.getKey();
            String rawVal = entry.getValue();

            // CHECK FOR SUB_RESOURCE INLINING
            String subResId = extractIdIfSubResource(rawVal);
            if (subResId != null && subResourceCache.containsKey(subResId)) {
                // INLINE THE RESOURCE!
                output.append(indent).append("  ").append(key).append(":\n");
                GdNode resNode = subResourceCache.get(subResId);
                // Print the resource recursively with deeper indentation
                printNode(resNode, indent + "    ");
            }
            else {
                // Standard Value
                String simpleVal = simplifyValue(rawVal);
                output.append(indent).append("  ").append(key).append(": ").append(simpleVal).append("\n");
            }
        }

        // Signals
        for (String sig : node.signals) {
            output.append(indent).append("  ").append(">> ").append(sig).append("\n");
        }

        // Children
        for (GdNode child : node.children) {
            output.append("\n"); // spacing between nodes
            printNode(child, indent + "  ");
        }
    }

    // --- Helpers ---

    private String simplifyValue(String val) {
        // 1. ExtResource -> Alias
        String extId = extractIdIfExtResource(val);
        if (extId != null && extResourceAliases.containsKey(extId)) {
            return extResourceAliases.get(extId);
        }

        // 2. Arrays/Dictionaries (Basic cleanup)
        if (val.startsWith("[") && val.endsWith("]")) return val; // Keep arrays mostly as is

        // 3. Simplify Transform3D (Identity check)
        if (val.startsWith("Transform3D")) {
            Matcher m = TRANSFORM_PATTERN.matcher(val);
            if (m.find()) {
                String[] p = m.group(1).split(",");
                if (p.length == 12) {
                    boolean identity = p[0].trim().startsWith("1") && p[5].trim().startsWith("1") && p[10].trim().startsWith("1"); // 0, 5, 10 are diagonal
                    // Note: Godot transforms are Column Major? Or row? usually 1,0,0, 0,1,0, 0,0,1
                    // Indices: 0,1,2 (x basis), 3,4,5 (y basis), 6,7,8 (z basis), 9,10,11 (origin)
                    // Wait, standard split: 1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z
                    // Indices: 0, 4, 8 are scale/rot diagonals usually.

                    try {
                        float x = Float.parseFloat(p[9].trim());
                        float y = Float.parseFloat(p[10].trim());
                        float z = Float.parseFloat(p[11].trim());
                        return String.format(Locale.US, "Pos(%.2f, %.2f, %.2f)", x, y, z);
                    } catch (Exception e) {}
                }
            }
            return "Transform3D(...)";
        }

        // 4. Color cleanup
        if (val.startsWith("Color")) {
            return val.replace("Color", "").replace("(", "").replace(")", "");
        }

        return val;
    }

    private String extractIdIfSubResource(String val) {
        // SubResource("id") or SubResource(id)
        if (val.contains("SubResource")) {
            return extractIdGeneric(val, "SubResource");
        }
        return null;
    }

    private String extractIdIfExtResource(String val) {
        if (val.contains("ExtResource")) {
            return extractIdGeneric(val, "ExtResource");
        }
        return null;
    }

    private String extractIdGeneric(String val, String keyword) {
        int idx = val.indexOf(keyword + "(");
        if (idx == -1) return null;
        int start = idx + keyword.length() + 1;
        int end = val.indexOf(")", start);
        if (end == -1) return null;
        String content = val.substring(start, end).trim();
        return content.replace("\"", "");
    }

    private boolean isUselessProperty(String key) {
        return key.equals("uid") || key.equals("load_steps") || key.equals("format") || key.equals("q_index") || key.startsWith("metadata/");
    }

    private String extractContent(String line) {
        return line.substring(1, line.length() - 1);
    }

    private String cleanStr(String s) {
        if (s == null) return null;
        return s.replace("\"", "");
    }

    private String extractNameFromPath(String path) {
        path = cleanStr(path);
        int slash = path.lastIndexOf('/');
        if (slash >= 0) return path.substring(slash + 1);
        return path;
    }

    private Map<String, String> parseAttributes(String header) {
        Map<String, String> map = new HashMap<>();
        Matcher m = ATTR_PATTERN.matcher(header);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }
        return map;
    }

    // --- Data Structure ---
    private static class GdNode {
        String name;
        String type;
        boolean isSubResource = false;
        Map<String, String> properties = new LinkedHashMap<>();
        List<GdNode> children = new ArrayList<>();
        List<String> signals = new ArrayList<>();
    }
}