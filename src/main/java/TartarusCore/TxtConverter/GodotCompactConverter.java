package TartarusCore.TxtConverter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class GodotCompactConverter {

    // --- Constants & Patterns ---
    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[(.*?)\\]");
    private static final Pattern ATTR_PATTERN = Pattern.compile("(\\w+)=((?:\"[^\"]*\")|(?:[^\\s\\]]+))");
    private static final Pattern TRANSFORM_PATTERN = Pattern.compile("Transform3D\\((.*?)\\)");
    private static final Pattern VECTOR_PATTERN = Pattern.compile("(Vector[234])\\((.*?)\\)");
    private static final Pattern COLOR_PATTERN = Pattern.compile("Color\\((.*?)\\)");

    private static final Map<String, String> TYPE_ABBREVIATIONS = Map.ofEntries(
            Map.entry("MeshInstance3D", "Mesh"),
            Map.entry("CollisionShape3D", "ColShape"),
            Map.entry("NavigationAgent3D", "NavAgent"),
            Map.entry("CharacterBody3D", "CharBody"),
            Map.entry("RigidBody3D", "RigidBody"),
            Map.entry("StaticBody3D", "StaticBody"),
            Map.entry("StandardMaterial3D", "StdMat"),
            Map.entry("BoxShape3D", "Box"),
            Map.entry("SphereShape3D", "Sphere"),
            Map.entry("CapsuleShape3D", "Capsule"),
            Map.entry("CylinderShape3D", "Cylinder"),
            Map.entry("BoxMesh", "BoxMesh"),
            Map.entry("SphereMesh", "SphereMesh"),
            Map.entry("QuadMesh", "Quad"),
            Map.entry("GPUParticles3D", "GPU_Part"),
            Map.entry("CPUParticles3D", "CPU_Part"),
            Map.entry("Script", "Scr"),
            Map.entry("PackedScene", "Scene")
    );

    private static final Set<String> IGNORED_PROPS = Set.of(
            "uid", "load_steps", "format", "q_index", "node_paths", "skeleton"
    );

    private static final DecimalFormat FLOAT_FMT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        FLOAT_FMT = new DecimalFormat("0.##", symbols);
    }

    // --- State ---
    private final Map<String, String> extResourceAliases = new HashMap<>();
    private final Map<String, GdNode> subResourceCache = new HashMap<>();
    private final Map<String, GdNode> nodePathMap = new HashMap<>();
    private final List<GdNode> rootNodes = new ArrayList<>();
    private final StringBuilder output = new StringBuilder();

    // --- Public Entry Point ---
    public static String convert(String content, String fileName) {
        return new GodotCompactConverter().process(content, fileName);
    }

    // --- Processing Logic ---
    private String process(String content, String fileName) {
        parseFile(content, fileName);
        optimizeTree(rootNodes);

        output.append(">>> ").append(fileName).append(" (Godot Compact v3)\n");
        // Print root nodes (usually just one, but resources file can have multiple)
        if (rootNodes.size() == 1) {
            printNode(rootNodes.get(0), "");
        } else {
            for (GdNode node : rootNodes) {
                printNode(node, "");
                output.append("\n");
            }
        }
        return output.toString();
    }

    private void parseFile(String content, String fileName) {
        String[] lines = content.split("\\R");

        // Pass 1: Extract External Resources
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
                    if (path.endsWith(".gd")) alias = "$Scr_" + fName;
                    else if (path.endsWith(".tscn")) alias = "$Scn_" + fName;
                    else alias = "$Res_" + fName;
                } else {
                    alias = "$Ext_" + abbreviateType(type) + "_" + id;
                }
                extResourceAliases.put(id, alias);
            }
        }

        // Pass 2: Build Node Tree
        GdNode currentNode = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;

            if (line.startsWith("[")) {
                String header = extractContent(line);
                Map<String, String> attrs = parseAttributes(header);
                String typeKey = header.split("\\s+")[0];

                if (typeKey.equals("node")) {
                    currentNode = new GdNode();
                    currentNode.name = cleanStr(attrs.get("name"));
                    currentNode.type = cleanStr(attrs.get("type"));

                    String parent = cleanStr(attrs.get("parent"));
                    String fullPath = (parent == null || parent.equals(".")) ? currentNode.name : parent + "/" + currentNode.name;
                    nodePathMap.put(fullPath, currentNode);

                    if (parent == null || parent.equals(".")) {
                        rootNodes.add(currentNode);
                    } else {
                        GdNode parentNode = nodePathMap.get(parent);
                        if (parentNode != null) {
                            parentNode.children.add(currentNode);
                        } else {
                            // Fallback if parent not found (rare in valid tscn)
                            rootNodes.add(currentNode);
                        }
                    }
                } else if (typeKey.equals("sub_resource")) {
                    currentNode = new GdNode();
                    currentNode.type = cleanStr(attrs.get("type"));
                    currentNode.isSubResource = true;
                    subResourceCache.put(cleanStr(attrs.get("id")), currentNode);
                } else if (typeKey.equals("resource")) {
                    currentNode = new GdNode();
                    currentNode.name = "RootRes";
                    currentNode.type = "Resource";
                    rootNodes.add(currentNode);
                } else if (typeKey.equals("connection")) {
                    // Store signals in the source node
                    String from = cleanStr(attrs.get("from"));
                    String signal = cleanStr(attrs.get("signal"));
                    String to = cleanStr(attrs.get("to"));
                    String method = cleanStr(attrs.get("method"));

                    GdNode fromNode = nodePathMap.get(from);
                    if (fromNode == null && from.equals(".")) fromNode = rootNodes.isEmpty() ? null : rootNodes.get(0);

                    if (fromNode != null) {
                        fromNode.signals.add(signal + "->" + to + "." + method);
                    }
                    currentNode = null;
                } else {
                    currentNode = null;
                }
            } else if (currentNode != null) {
                int eqIndex = line.indexOf('=');
                if (eqIndex > 0) {
                    String key = line.substring(0, eqIndex).trim();
                    String val = line.substring(eqIndex + 1).trim();
                    if (!key.startsWith("metadata/") && !IGNORED_PROPS.contains(key)) {
                        currentNode.properties.put(key, val);
                    }
                }
            }
        }
    }

    // --- Optimization & Folding ---

    private void optimizeTree(List<GdNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return;

        // 1. Recursively optimize children first
        for (GdNode node : nodes) {
            optimizeTree(node.children);
        }

        // 2. Collapse identical siblings
        List<GdNode> optimizedList = new ArrayList<>();
        int i = 0;
        while (i < nodes.size()) {
            GdNode current = nodes.get(i);
            int j = i + 1;
            // Count how many subsequent nodes are "similar" to current
            while (j < nodes.size() && areNodesSimilar(current, nodes.get(j))) {
                j++;
            }

            int count = j - i;
            if (count >= 3) { // Threshold to collapse
                GdNode groupNode = new GdNode();
                groupNode.name = "@Repeated(" + count + ") \"" + current.type + "\"";
                groupNode.type = current.type;
                groupNode.isGroupPlaceholder = true;
                groupNode.properties = new LinkedHashMap<>(current.properties);
                groupNode.properties.remove("transform");
                groupNode.properties.remove("position");
                groupNode.properties.remove("rotation");
                // We keep one set of children as representative
                groupNode.children = current.children;
                groupNode.signals = current.signals;
                // Add a hint about variation
                groupNode.properties.put("Layout", "Grid/Procedural");

                optimizedList.add(groupNode);
                i = j;
            } else {
                optimizedList.add(current);
                i++;
            }
        }

        nodes.clear();
        nodes.addAll(optimizedList);
    }

    private boolean areNodesSimilar(GdNode a, GdNode b) {
        if (!Objects.equals(a.type, b.type)) return false;
        if (a.children.size() != b.children.size()) return false;

        // Compare properties (ignoring transform/position/rotation/name)
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(a.properties.keySet());
        allKeys.addAll(b.properties.keySet());

        for (String key : allKeys) {
            if (key.equals("transform") || key.equals("position") || key.equals("rotation") || key.equals("rotation_degrees")) continue;
            String v1 = a.properties.get(key);
            String v2 = b.properties.get(key);
            if (!Objects.equals(v1, v2)) return false;
        }

        // Shallow compare children types
        for (int k = 0; k < a.children.size(); k++) {
            if (!Objects.equals(a.children.get(k).type, b.children.get(k).type)) return false;
        }

        return true;
    }

    // --- Printing ---

    private void printNode(GdNode node, String indent) {
        output.append(indent);

        String typeAbbr = abbreviateType(node.type);

        if (node.isGroupPlaceholder) {
            output.append(node.name);
        } else if (node.isSubResource) {
            // SubResources usually printed inline, but if printed standalone:
            output.append("@Sub ").append(typeAbbr);
        } else {
            // Node name + Type
            if (node.name.equals("RootRes") || node.name.equals("RootResource")) {
                output.append("ROOT");
            } else {
                output.append(node.name);
            }
            if (node.type != null && !node.name.equals(node.type)) {
                output.append(" (").append(typeAbbr).append(")");
            }
        }

        output.append(" {");

        List<String> props = new ArrayList<>();

        // Properties
        for (Map.Entry<String, String> entry : node.properties.entrySet()) {
            String key = shortenKey(entry.getKey());
            String val = formatValue(entry.getValue());

            // If value refers to a SubResource, try to inline it if it's simple
            String subId = extractSubResourceId(val);
            if (subId != null && subResourceCache.containsKey(subId)) {
                val = formatSubResourceInline(subResourceCache.get(subId));
            }

            props.add(key + ":" + val);
        }

        // Signals
        for (String sig : node.signals) {
            props.add("$Sig:" + sig);
        }

        // Script
        // Often script is in properties, but if logic needs specific handling:
        // (Handled by general properties loop usually, but check for ExtResource alias)

        if (!props.isEmpty()) {
            output.append(" ").append(String.join(", ", props));
        }

        // Children
        if (!node.children.isEmpty()) {
            if (!props.isEmpty()) output.append(",");
            output.append("\n").append(indent).append("  children: [\n");
            for (int i = 0; i < node.children.size(); i++) {
                printNode(node.children.get(i), indent + "    ");
                if (i < node.children.size() - 1) output.append(",");
                output.append("\n");
            }
            output.append(indent).append("  ]");
        } else {
            output.append(" ");
        }

        output.append("}");
    }

    private String formatSubResourceInline(GdNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(abbreviateType(node.type)).append("{");
        List<String> p = new ArrayList<>();
        for (var entry : node.properties.entrySet()) {
            p.add(shortenKey(entry.getKey()) + ":" + formatValue(entry.getValue()));
        }
        sb.append(String.join(",", p));
        sb.append("}");
        return sb.toString();
    }

    // --- Helpers ---

    private String abbreviateType(String type) {
        if (type == null) return "Null";
        return TYPE_ABBREVIATIONS.getOrDefault(type, type);
    }

    private String shortenKey(String key) {
        if (key.equals("transform")) return "xt";
        if (key.equals("position")) return "pos";
        if (key.equals("rotation_degrees")) return "rot";
        if (key.equals("material_override")) return "mat";
        if (key.equals("collision_layer")) return "layer";
        if (key.equals("collision_mask")) return "mask";
        return key;
    }

    private String formatValue(String val) {
        if (val == null) return "null";

        // Handle ExtResource Alias
        String extId = extractExtResourceId(val);
        if (extId != null && extResourceAliases.containsKey(extId)) {
            return extResourceAliases.get(extId);
        }

        // Vectors [x, y, z]
        Matcher vecM = VECTOR_PATTERN.matcher(val);
        if (vecM.matches()) {
            String[] parts = vecM.group(2).split(",");
            return "[" + Arrays.stream(parts).map(this::cleanFloat).collect(Collectors.joining(",")) + "]";
        }

        // Colors [r, g, b, a]
        Matcher colM = COLOR_PATTERN.matcher(val);
        if (colM.matches()) {
            String[] parts = colM.group(1).split(",");
            return "[" + Arrays.stream(parts).map(this::cleanFloat).collect(Collectors.joining(",")) + "]";
        }

        // Transform3D - aggressively simplify
        if (val.startsWith("Transform3D")) {
            Matcher transM = TRANSFORM_PATTERN.matcher(val);
            if (transM.find()) {
                String[] p = transM.group(1).split(",");
                // Extract only position if rotation/scale is identity
                if (p.length == 12) {
                    // 9, 10, 11 are pos x, y, z
                    return "[" + cleanFloat(p[9]) + "," + cleanFloat(p[10]) + "," + cleanFloat(p[11]) + "]";
                }
            }
            return "Xt(...)";
        }

        // Strings
        if (val.startsWith("\"")) return val;

        // Numbers
        try {
            float f = Float.parseFloat(val);
            return cleanFloat(val); // It's a number
        } catch (NumberFormatException e) {
            // It's an enum or string constant
            return val;
        }
    }

    private String cleanFloat(String numStr) {
        try {
            double d = Double.parseDouble(numStr.trim());
            if (d == 0) return "0";
            if (d == 1) return "1";
            // Check if integer
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((int)d);
            }
            return FLOAT_FMT.format(d);
        } catch (Exception e) {
            return numStr.trim();
        }
    }

    private String extractSubResourceId(String val) {
        return extractIdGeneric(val, "SubResource");
    }

    private String extractExtResourceId(String val) {
        return extractIdGeneric(val, "ExtResource");
    }

    private String extractIdGeneric(String val, String keyword) {
        int idx = val.indexOf(keyword + "(");
        if (idx == -1) return null;
        int start = idx + keyword.length() + 1;
        int end = val.indexOf(")", start);
        if (end == -1) return null;
        return val.substring(start, end).trim().replace("\"", "");
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
        if (slash >= 0) {
            String name = path.substring(slash + 1);
            int dot = name.lastIndexOf('.');
            return (dot > 0) ? name.substring(0, dot) : name;
        }
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

    // --- Internal Data Structure ---
    private static class GdNode {
        String name;
        String type;
        boolean isSubResource = false;
        boolean isGroupPlaceholder = false;
        Map<String, String> properties = new LinkedHashMap<>();
        List<GdNode> children = new ArrayList<>();
        List<String> signals = new ArrayList<>();
    }
}