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

/**
 * Implementation of Base32 encoding, as defined in IETF RFC 4648.
 */
public final class Base32Encoding {
    
    private static final char[] B32_CHAR_BY_IND = new char[] {
            'A',
            'B',
            'C',
            'D',
            'E',
            'F',
            'G',
            'H',
            'I',
            'J',
            'K',
            'L',
            'M',
            'N',
            'O',
            'P',
            'Q',
            'R',
            'S',
            'T',
            'U',
            'V',
            'W',
            'X',
            'Y',
            'Z',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7'
    };
    private static final int[] B32_IND_BY_CHAR_IND = {
            26,
            27,
            28,
            29,
            30,
            31,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            18,
            19,
            20,
            21,
            22,
            23,
            24,
            25,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            18,
            19,
            20,
            21,
            22,
            23,
            24,
            25
    };
    
    private Base32Encoding() {}
    
    public static char[] encode(byte[] bytes) {
        int resInd = 0;
        int i = 0;
        int startBitInd = 0;
        int b32Ind;
        int curByte;
        int nextByte;
        
        char[] res = new char[((bytes.length + 4) / 5) * 8];
        
        while (i < bytes.length) {
            curByte = bytes[i] & 0xff;
            
            if (startBitInd <= 3) {
                b32Ind = (curByte >>> (3 - startBitInd)) & 0x1f;
                if (startBitInd == 3) {
                    i++;
                }
            } else {
                if (++i < bytes.length) {
                    nextByte = bytes[i] & 0xff;
                } else {
                    nextByte = 0;
                }
                
                b32Ind = ((curByte << (startBitInd - 3)) & 0x1f)
                        | (nextByte >>> (11 - startBitInd));
            }
            startBitInd += 5;
            if (startBitInd >= 8) {
                startBitInd -= 8;
            }
            
            res[resInd++] = B32_CHAR_BY_IND[b32Ind];
        }
        
        while (resInd < res.length) {
            res[resInd++] = '=';
        }
        
        return res;
    }
    
    public static byte[] decode(char[] encoded) {
        int encodedLastValidCharInd = encoded.length - 1;
        while (
            encodedLastValidCharInd >= 0 && getBase32Ind(encoded[encodedLastValidCharInd]) == -1
        ) {
            encodedLastValidCharInd--;
        }
        
        byte[] res = new byte[((encodedLastValidCharInd + 1) * 5) / 8];
        if (res.length == 0) {
            return res;
        }
        
        for (int i = 0, resStartBitInd = 0, resInd = 0, b32Ind; i <= encodedLastValidCharInd; i++) {
            b32Ind = getBase32Ind(encoded[i]);
            if (b32Ind == -1) {
                continue;
            }
            
            if (resStartBitInd <= 3) {
                res[resInd] |= b32Ind << (3 - resStartBitInd);
                if (resStartBitInd == 3 && ++resInd >= res.length) {
                    break;
                }
            } else {
                res[resInd++] |= b32Ind >>> (resStartBitInd - 3);
                if (resInd >= res.length) {
                    break;
                }
                res[resInd] |= b32Ind << (11 - resStartBitInd);
            }
            resStartBitInd += 5;
            if (resStartBitInd >= 8) {
                resStartBitInd -= 8;
            }
        }
        return res;
    }
    
    private static final int getBase32Ind(final char b32Char) {
        int b32CharInd = b32Char - '2';
        if (b32CharInd < 0 || b32CharInd >= B32_IND_BY_CHAR_IND.length) {
            return -1;
        }
        return B32_IND_BY_CHAR_IND[b32CharInd];
    }
    
}
