/*
 * Copyright (c) 2024-2025 tigeriodev (tigeriodev@tutamail.com)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package fr.tigeriodev.tigersafe.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringUtils {
    
    private static final HexFormat HEX_FORMATTER = HexFormat.ofDelimiter(" ");
    private static final Pattern LINES_PATTERN = Pattern.compile("(\r\n)|(\r)|(\n)");
    
    private StringUtils() {}
    
    public static int getLinesNumber(String str) {
        Matcher m = LINES_PATTERN.matcher(str);
        int lines = 1;
        while (m.find()) {
            lines++;
        }
        return lines;
    }
    
    public static final String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
    
    public static final String quote(String str) {
        return str == null ? "null" : '"' + str + '"';
    }
    
    public static final String clone(String str) {
        // new String(str) uses same internal byte[], and therefore is cleared when str is cleared.
        char[] chars = str.toCharArray();
        String res = new String(chars);
        MemUtils.clearCharArray(chars);
        return res;
    }
    
    public static final String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.close();
        return sw.toString();
    }
    
    public static final byte[] hexStrToBytes(String str) {
        return HEX_FORMATTER.parseHex(str);
    }
    
    public static final String bytesToHexStr(byte[] bytes) {
        return HEX_FORMATTER.formatHex(bytes);
    }
    
    public static final String bytesToStr(byte[] bytes) {
        return "byte[" + bytes.length + "]=[" + new String(bytes, StandardCharsets.UTF_8) + " "
                + bytesToHexStr(bytes) + "]";
    }
    
    public static final String charArrayToObfuscatedStr(char[] src) {
        return src != null ? "char[" + src.length + "]" : "null";
    }
    
    /**
     * Removes all characters from {@code str} listed in {@code separators} if they appear at least twice.
     * @param str the string to process.
     * @param separators the characters that will be removed if they appear at least twice in {@code str}.
     * @return the string {@code str} without the characters in {@code separators} that are present at least twice.
     */
    public static final String removeSeparatorsIn(String str, Set<Character> separators) {
        StringBuilder res = new StringBuilder();
        Map<Character, Integer> separatorsFoundInd = new HashMap<>();
        for (Character separator : separators) {
            separatorsFoundInd.put(separator, 0);
        }
        
        int curResInd = 0;
        Integer curSepFoundInd;
        for (Character c : str.toCharArray()) {
            curSepFoundInd = separatorsFoundInd.get(c);
            if (curSepFoundInd != null) {
                if (curSepFoundInd == 0) {
                    separatorsFoundInd.put(c, -(curResInd + 1));
                } else if (curSepFoundInd < 0) {
                    separatorsFoundInd.put(c, -curSepFoundInd);
                    continue;
                } else {
                    continue;
                }
            }
            res.append(c);
            curResInd++;
        }
        
        int toDelIndsLen = separatorsFoundInd.size();
        int[] toDelInds = new int[toDelIndsLen];
        int toDelIndsCurInd = 0;
        int sepsFoundCurInd;
        for (Integer sepFoundInd : separatorsFoundInd.values()) {
            sepsFoundCurInd = sepFoundInd.intValue();
            if (sepsFoundCurInd > 0) {
                toDelInds[toDelIndsCurInd++] = sepsFoundCurInd - 1;
            }
        }
        
        if (toDelIndsCurInd != 0) {
            if (toDelIndsCurInd != toDelIndsLen) {
                toDelInds = Arrays.copyOf(toDelInds, toDelIndsCurInd);
                toDelIndsLen = toDelIndsCurInd;
            }
            Arrays.sort(toDelInds);
            for (int i = toDelIndsLen - 1; i >= 0; i--) {
                res.deleteCharAt(toDelInds[i]);
            }
        }
        
        return res.toString();
    }
    
    public static final Character getDuplicateCharIn(String str) {
        // nlog(n) time complexity, n extra memory
        char[] chars = str.toCharArray();
        Arrays.sort(chars);
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == chars[i + 1]) {
                return chars[i];
            }
        }
        return null;
    }
    
    public static final String getSafeObjName(final Object obj) {
        return obj.getClass().getCanonicalName() + "@" + (obj.hashCode() & 0xfff);
    }
    
}
