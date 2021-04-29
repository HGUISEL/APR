package com.github.thwak.confix.patch.utils;

import com.github.thwak.confix.diff.Node;
import com.github.thwak.confix.patch.models.PatchInfo;
import com.github.thwak.confix.patch.models.RepairAction;
import com.github.thwak.confix.pool.changes.ChangePool;
import com.github.thwak.confix.util.IOUtils;
import org.eclipse.jdt.core.dom.ASTNode;

import java.io.File;
import java.util.*;

public class PatchUtils {
    public static <T> int rouletteWheelSelection(List<T> list, Map<T, Integer> freqMap, int fMax, Random r) {
        if (list.size() == 0) {
            return -1;
        }
        int index = -1;
        while (index < 0) {
            int idx = r.nextInt(list.size());
            double freq = freqMap.get(list.get(idx));
            if (freq / fMax >= r.nextDouble()) {
                index = idx;
                break;
            }
        }
        return index;
    }

    public static int[] resolveBlockIndex(Node method, Node node) {
        List<String> index = new ArrayList<>();
        if (computeIndex(method, node, index)) {
            if (index.size() > 0) {
                index.remove(0);
            }
            int[] blockIndex = new int[index.size()];
            for (int i = 0; i < index.size(); i++) {
                blockIndex[i] = Integer.parseInt(index.get(i));
            }
            return blockIndex;
        } else {
            return new int[0];
        }
    }

    public static boolean computeIndex(Node node, Node query, List<String> index) {
        if (node.equals(query)) {
            return true;
        }
        int count = 0;
        for (Node child : node.children) {
            if (child.type == ASTNode.BLOCK
                    || child.type == ASTNode.FOR_STATEMENT
                    || child.type == ASTNode.ENHANCED_FOR_STATEMENT) {
                index.add(String.valueOf(count++));
            }
            if (computeIndex(child, query, index)) {
                return true;
            }
            if (child.type == ASTNode.BLOCK
                    || child.type == ASTNode.FOR_STATEMENT
                    || child.type == ASTNode.ENHANCED_FOR_STATEMENT) {
                index.remove(index.size() - 1);
            }
        }
        return false;
    }

    public static Node findMethod(Node node) {
        if (node.type == ASTNode.METHOD_DECLARATION) {
            return node;
        }
        Node parent = node.parent;
        while (parent != null) {
            if (parent.type == ASTNode.METHOD_DECLARATION) {
                return parent;
            }
            parent = parent.parent;
        }
        return node;
    }

    public static String getEditText(PatchInfo info, ChangePool pool) {
        StringBuffer sb = new StringBuffer("Class:");
        sb.append(info.className);
        for (RepairAction ra : info.repairs) {
            sb.append("\n");
            sb.append(ra.toString());
            sb.append("\n");
            sb.append("from\n");
            sb.append(ra.change.id);
            sb.append("\n");
            sb.append(pool.getFrequency(ra.change));
            sb.append("\n");
            sb.append(ra.loc.context);
        }
        return sb.toString();
    }

    public static String getElapsedTime(long time) {
        long sec = time / 1000;
        StringBuffer sb = new StringBuffer();
        sb.append(sec % 60);
        sb.append(" sec. ");
        long min = sec / 60;
        if (min > 0) {
            sb.insert(0, " min. ");
            sb.insert(0, min % 60);
        }
        int hour = (int) min / 60;
        if (hour > 0) {
            sb.insert(0, " hrs. ");
            sb.insert(0, hour);
        }
        return sb.toString();
    }

    public static List<String> getListProperty(Properties props, String key, String delim) {
        List<String> list = new ArrayList<>();
        String value = props.getProperty(key);
        if (value != null) {
            String[] entries = value.split(delim);
            list.addAll(Arrays.asList(entries));
        }
        return list;
    }

    public static String getStringProperty(Properties props, String key, String defaultValue) {
        return props.getProperty(key) == null ? defaultValue : props.getProperty(key);
    }

    public static String loadSource(String sourceDir, String className) {
        String sourceFilePath = sourceDir + File.separator + className.replaceAll("\\.", File.separator + File.separator) + ".java";
        String source = IOUtils.readFile(sourceFilePath);
        return source;
    }
}
