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

package fr.tigeriodev.tigersafe.data;

import java.time.Instant;
import java.util.Arrays;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import fr.tigeriodev.tigersafe.utils.Base32Encoding;
import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.NumberRange;
import fr.tigeriodev.tigersafe.utils.StringUtils;
import fr.tigeriodev.tigersafe.utils.TOTPUtils;

public final class TOTP implements Destroyable {
    
    private static final String URI_PREFIX = "otpauth://";
    private static final String URI_TYPE = "totp";
    
    public static final NumberRange ALGO_ORD_RANGE = new NumberRange(0, 2); // [0; 3]
    public static final NumberRange DIGITS_NUM_RANGE = new NumberRange(1, 4); // [1; 16]
    public static final NumberRange PERIOD_SECONDS_RANGE = new NumberRange(1, 8); // [1; 256]
    
    static {
        if (Algorithm.values().length - 1 > ALGO_ORD_RANGE.getMax()) {
            throw new RuntimeException("ALGO_ORD_RANGE does not match Algorithm values.");
        }
    }
    
    public enum Algorithm {
        
        SHA1("HmacSHA1"),
        SHA256("HmacSHA256"),
        SHA512("HmacSHA512");
        
        private static final Algorithm[] vals = values();
        
        public static Algorithm getByOrdinal(int ord) {
            return vals[ord];
        }
        
        final String macAlgoName;
        
        private Algorithm(String macAlgorithm) {
            this.macAlgoName = macAlgorithm;
        }
        
    }
    
    /**
     * 
     * @param uri using the format specified at https://github.com/google/google-authenticator/wiki/Key-Uri-Format
     * @return
     */
    public static TOTP fromURI(char[] uri) throws IllegalArgumentException {
        if (uri == null || uri.length == 0) {
            throw new IllegalArgumentException();
        }
        
        byte[] keyBytes = null;
        String label = "";
        String issuer = "";
        Algorithm algo = Algorithm.SHA1;
        int digitsNum = 6;
        int period = 30;
        
        int uriPrefixLen = URI_PREFIX.length();
        boolean hasPrefix = false;
        int i = 0;
        if (uri.length >= uriPrefixLen) {
            for (; i < uriPrefixLen; i++) {
                if (uri[i] != URI_PREFIX.charAt(i)) {
                    break;
                }
            }
            if (i == uriPrefixLen) {
                hasPrefix = true;
            }
        }
        
        if (!hasPrefix) {
            int spacesNum = 0;
            for (i = 0; i < uri.length; i++) {
                if (uri[i] == ' ') {
                    spacesNum++;
                }
            }
            
            if (spacesNum > 0) {
                char[] unspacedKeyB32 = new char[uri.length - spacesNum];
                int j = 0;
                for (i = 0; i < uri.length; i++) {
                    if (uri[i] != ' ') {
                        unspacedKeyB32[j++] = uri[i];
                    }
                }
                keyBytes = Base32Encoding.decode(unspacedKeyB32);
                MemUtils.clearCharArray(unspacedKeyB32);
            } else {
                keyBytes = Base32Encoding.decode(uri);
            }
        } else {
            StringBuilder typeSB = new StringBuilder(4);
            while (i < uri.length && uri[i] != '/') {
                typeSB.append(uri[i++]);
            }
            i++;
            if (i >= uri.length) {
                throw new IllegalArgumentException();
            }
            String type = typeSB.toString();
            
            if (!URI_TYPE.equalsIgnoreCase(type)) {
                throw new IllegalArgumentException("Unsupported type: " + type + ".");
            }
            
            StringBuilder labelSB = new StringBuilder();
            while (i < uri.length && uri[i] != '?') {
                labelSB.append(uri[i++]);
            }
            i++;
            if (i >= uri.length) {
                throw new IllegalArgumentException();
            }
            label = labelSB.toString();
            
            StringBuilder paramNameSB = new StringBuilder();
            String paramName = null;
            int paramValStartInd = 0;
            while (i < uri.length) {
                if (paramName == null) {
                    if (uri[i] == '=') {
                        paramName = paramNameSB.toString();
                        paramValStartInd = i + 1;
                    } else {
                        paramNameSB.append(uri[i]);
                    }
                } else {
                    if (i == uri.length - 1 || uri[i + 1] == '&') {
                        char[] paramValChars = Arrays.copyOfRange(uri, paramValStartInd, i + 1);
                        switch (paramName.toLowerCase()) {
                            case "secret":
                                keyBytes = Base32Encoding.decode(paramValChars);
                                MemUtils.clearCharArray(paramValChars);
                                break;
                            case "issuer":
                                issuer = new String(paramValChars);
                                break;
                            case "algorithm":
                                algo = Algorithm.valueOf(new String(paramValChars).toUpperCase());
                                break;
                            case "digits":
                                try {
                                    digitsNum =
                                            Integer.decode(new String(paramValChars)).intValue();
                                } catch (NumberFormatException ex) {
                                    throw new IllegalArgumentException(ex);
                                }
                                break;
                            case "period":
                                try {
                                    period = Integer.decode(new String(paramValChars)).intValue();
                                } catch (NumberFormatException ex) {
                                    throw new IllegalArgumentException(ex);
                                }
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "Unsupported parameter: " + paramName + "."
                                );
                        }
                        
                        paramNameSB = new StringBuilder();
                        paramName = null;
                        paramValStartInd = 0;
                        
                        if (i != uri.length - 1) { // next is '&', ignore it
                            i++;
                        }
                    }
                }
                i++;
            }
        }
        
        try {
            return new TOTP(keyBytes, label, issuer, algo, digitsNum, period);
        } catch (NullPointerException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
    private byte[] keyBytes;
    public final String label;
    public final String issuer;
    public final Algorithm algo;
    public final int digitsNum;
    public final int periodSeconds;
    private long lastCurIntervalInd = -1L;
    private String lastCurCode;
    private String lastNextCode;
    private char[] uri;
    
    public TOTP(byte[] keyBytes, String label, String issuer, Algorithm algo, int digitsNum,
            int periodSeconds) {
        this.keyBytes = CheckUtils.notNull(keyBytes);
        if (keyBytes.length == 0) {
            throw new IllegalArgumentException("keyBytes is empty.");
        }
        this.label = CheckUtils.notNull(label);
        this.issuer = CheckUtils.notNull(issuer);
        this.algo = CheckUtils.notNull(algo);
        this.digitsNum = CheckUtils.inRange(digitsNum, DIGITS_NUM_RANGE);
        this.periodSeconds = CheckUtils.inRange(periodSeconds, PERIOD_SECONDS_RANGE);
    }
    
    /**
     * Should only be used for reading.
     * @return the real value (not a duplicate)
     * @NotNull
     */
    public byte[] getKeyBytes() {
        return keyBytes;
    }
    
    public boolean updateCurTime(Instant curTime) {
        long curIntervalInd = getIntervalInd(curTime);
        if (lastCurIntervalInd == curIntervalInd) {
            return false;
        }
        
        lastCurCode = curIntervalInd == lastCurIntervalInd + 1 ? lastNextCode : null;
        if (lastCurCode == null) {
            lastCurCode = newCode(curIntervalInd);
        }
        lastNextCode = newCode(curIntervalInd + 1);
        lastCurIntervalInd = curIntervalInd;
        return true;
    }
    
    public long getIntervalInd(Instant time) {
        return time.getEpochSecond() / periodSeconds;
    }
    
    public Instant getIntervalStartTime(long intervalInd) {
        return Instant.ofEpochSecond(intervalInd * periodSeconds);
    }
    
    public Instant getNextIntervalStartTime() {
        if (lastCurIntervalInd < 0) {
            throw new IllegalStateException("Should updateCurTime() first.");
        }
        return getIntervalStartTime(lastCurIntervalInd + 1);
    }
    
    public String newCode(long intervalInd) {
        return TOTPUtils.generateTOTP(keyBytes, intervalInd, digitsNum, algo.macAlgoName);
    }
    
    public String getCurCode() {
        if (lastCurCode == null) {
            throw new IllegalStateException("Should updateCurTime() first.");
        }
        return lastCurCode;
    }
    
    public String getNextCode() {
        if (lastNextCode == null) {
            throw new IllegalStateException("Should updateCurTime() first.");
        }
        return lastNextCode;
    }
    
    /**
     * Should only be used for reading.
     * @return the lazy single value (no duplicate)
     * @NotNull
     */
    public char[] getURI() {
        // Single instance for easy and safe clearing.
        if (uri == null) {
            String startPart = URI_PREFIX + URI_TYPE + "/" + label + "?secret=";
            String issuerParam = !issuer.isEmpty() ? "&issuer=" + issuer : "";
            String endPart = "&algorithm=" + algo.name() + "&digits=" + digitsNum + "&period="
                    + periodSeconds;
            
            char[] secretB32Chars = Base32Encoding.encode(keyBytes);
            
            int startPartLen = startPart.length();
            uri = new char[startPartLen
                    + secretB32Chars.length
                    + issuerParam.length()
                    + endPart.length()];
            startPart.getChars(0, startPartLen, uri, 0);
            
            for (int i = 0; i < secretB32Chars.length; i++) {
                uri[startPartLen + i] = secretB32Chars[i];
            }
            MemUtils.clearCharArray(secretB32Chars);
            
            issuerParam
                    .getChars(0, issuerParam.length(), uri, startPartLen + secretB32Chars.length);
            endPart.getChars(
                    0,
                    endPart.length(),
                    uri,
                    startPartLen + secretB32Chars.length + issuerParam.length()
            );
        }
        return uri;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(keyBytes);
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((issuer == null) ? 0 : issuer.hashCode());
        result = prime * result + ((algo == null) ? 0 : algo.hashCode());
        result = prime * result + digitsNum;
        result = prime * result + periodSeconds;
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TOTP other = (TOTP) obj;
        if (!Arrays.equals(keyBytes, other.keyBytes))
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (issuer == null) {
            if (other.issuer != null)
                return false;
        } else if (!issuer.equals(other.issuer))
            return false;
        if (algo != other.algo)
            return false;
        if (digitsNum != other.digitsNum)
            return false;
        if (periodSeconds != other.periodSeconds)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "TOTP [uri=" + StringUtils.charArrayToObfuscatedStr(getURI()) + "]";
    }
    
    public TOTP duplicate() {
        return new TOTP(keyBytes.clone(), label, issuer, algo, digitsNum, periodSeconds);
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        if (keyBytes != null) {
            MemUtils.clearByteArray(keyBytes);
            keyBytes = null;
        }
        if (uri != null) {
            MemUtils.clearCharArray(uri);
            uri = null;
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return keyBytes == null;
    }
    
}
