/*
 * Copyright (c) 2011 IETF Trust and the persons identified as
 * authors of the code. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, is permitted pursuant to, and subject to the license
 * terms contained in, the Simplified BSD License set forth in Section
 * 4.c of the IETF Trust's Legal Provisions Relating to IETF Documents
 * (http://trustee.ietf.org/license-info).
 */

/*
 * DISCLAIMER
 * 
 * This software is not affiliated with, endorsed by, or approved by
 * the following entities: Internet Society, IETF, IETF Trust,
 * Johan Rydell, or any other entity.
 * Any references to these entities are for informational purposes only
 * and do not imply any association, sponsorship, or approval.
 */

package fr.tigeriodev.tigersafe.utils;

import java.lang.reflect.UndeclaredThrowableException;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class was taken from the example implementation of the OATH
 * TOTP algorithm of the IETF RFC 6238, and slightly modified by tigeriodev.
 * Visit www.openauthentication.org for more information.
 *
 * @author Johan Rydell, PortWise, Inc.
 * @author tigeriodev (tigeriodev@tutamail.com)
 */
public final class TOTPUtils {
    
    private static final byte[] intervalIndBuf = new byte[8];
    
    private TOTPUtils() {}
    
    /**
     * This code was taken from IETF RFC 6238.
     * Please reproduce this note if possible.
     * 
     * This method uses the JCE to provide the crypto algorithm.
     * HMAC computes a Hashed Message Authentication Code with the
     * crypto hash algorithm as a parameter.
     *
     * @param crypto: the crypto algorithm (HmacSHA1, HmacSHA256,
     *                             HmacSHA512)
     * @param keyBytes: the bytes to use for the HMAC key
     * @param text: the message or text to be authenticated
     */
    private static byte[] hmac_sha(String crypto, byte[] keyBytes, byte[] text) {
        try {
            Mac hmac;
            hmac = Mac.getInstance(crypto);
            SecretKeySpec macKey = new SecretKeySpec(keyBytes, "RAW");
            hmac.init(macKey);
            return hmac.doFinal(text);
        } catch (GeneralSecurityException gse) {
            throw new UndeclaredThrowableException(gse);
        }
    }
    
    private static final int[] DIGITS_POWER = {
            1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000
    };
    
    /**
     * NB: Not thread safe.
     * @param keyBytes
     * @param intervalInd timeSecondsSinceEpoch / periodSeconds
     * @param codeDigits
     * @param crypto
     * @return
     */
    public static String generateTOTP(byte[] keyBytes, long intervalInd, int codeDigits,
            String crypto) {
        intervalIndBuf[0] = (byte) (intervalInd >>> 56);
        intervalIndBuf[1] = (byte) (intervalInd >>> 48);
        intervalIndBuf[2] = (byte) (intervalInd >>> 40);
        intervalIndBuf[3] = (byte) (intervalInd >>> 32);
        intervalIndBuf[4] = (byte) (intervalInd >>> 24);
        intervalIndBuf[5] = (byte) (intervalInd >>> 16);
        intervalIndBuf[6] = (byte) (intervalInd >>> 8);
        intervalIndBuf[7] = (byte) (intervalInd >>> 0);
        
        return generateTOTP(keyBytes, intervalIndBuf, codeDigits, crypto);
    }
    
    /**
     * This code was derived from IETF RFC 6238.
     * Please reproduce this note if possible.
     * 
     * @param keyBytes
     * @param intervalIndBytes
     * @param codeDigits
     * @param crypto
     * @return
     */
    public static String generateTOTP(byte[] keyBytes, byte[] intervalIndBytes, int codeDigits,
            String crypto) {
        String result = null;
        
        byte[] hash = hmac_sha(crypto, keyBytes, intervalIndBytes);
        
        int offset = hash[hash.length - 1] & 0xf;
        
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        
        int otp = binary % DIGITS_POWER[codeDigits];
        
        result = Integer.toString(otp);
        while (result.length() < codeDigits) {
            result = "0" + result;
        }
        return result;
    }
    
}
