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

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

public abstract class MutableString implements Destroyable {
    
    private MutableString() {}
    
    /**
     * @return the real value (not a duplicate)
     * @NotNull
     */
    public abstract char[] getVal();
    
    public void addChars(char[] src) {
        int initLen = getVal().length;
        resize(initLen + src.length);
        System.arraycopy(src, 0, getVal(), initLen, src.length);
    }
    
    public void addChars(String src) {
        int initLen = getVal().length;
        resize(initLen + src.length());
        char[] val = getVal();
        for (int i = 0; i < src.length(); i++) {
            val[initLen + i] = src.charAt(i);
        }
    }
    
    public void addChar(char c) {
        resize(getVal().length + 1);
        char[] val = getVal();
        val[val.length - 1] = c;
    }
    
    public void insertChars(int startInd, char[] src) {
        int initLen = getVal().length;
        if (startInd < 0 || startInd > initLen) {
            throw new IndexOutOfBoundsException(startInd);
        }
        resize(initLen + src.length);
        char[] val = getVal();
        System.arraycopy(val, startInd, val, startInd + src.length, initLen - startInd);
        System.arraycopy(src, 0, val, startInd, src.length);
    }
    
    public void remLastChar() {
        if (isEmpty()) {
            return;
        }
        resize(getVal().length - 1);
    }
    
    /**
     * 
     * @param startInd inclusive
     * @param endInd exclusive
     */
    public void remChars(int startInd, int endInd) {
        int initLen = getVal().length;
        if (startInd < 0 || endInd <= startInd || endInd > initLen) {
            throw new IllegalArgumentException();
        }
        int removedNum = endInd - startInd;
        char[] initVal = getVal();
        System.arraycopy(initVal, endInd, initVal, startInd, initLen - endInd);
        resize(initLen - removedNum);
    }
    
    public void setChars(String src) {
        resize(src.length());
        char[] val = getVal();
        for (int i = 0; i < src.length(); i++) {
            val[i] = src.charAt(i);
        }
    }
    
    public void setChars(char[] src) {
        resize(src.length);
        char[] val = getVal();
        for (int i = 0; i < src.length; i++) {
            val[i] = src[i];
        }
    }
    
    protected abstract void newValHolder(int size);
    
    public void resize(int newSize) {
        CheckUtils.positive(newSize);
        char[] initVal = getVal();
        if (newSize == initVal.length) {
            return;
        }
        
        newValHolder(newSize);
        char[] newVal = getVal();
        int keptLen = Math.min(newSize, initVal.length);
        if (keptLen > 0) {
            System.arraycopy(initVal, 0, newVal, 0, keptLen);
        }
        MemUtils.clearCharArray(initVal);
    }
    
    public boolean isEmpty() {
        return getVal().length == 0;
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        clear();
    }
    
    public void clear() {
        resize(0);
    }
    
    @Override
    public boolean isDestroyed() {
        return isEmpty();
    }
    
    @Override
    public String toString() {
        return "MutableDestroyableString[hidden value]"; // Use getVal() to read the value
    }
    
    public static final class Simple extends MutableString {
        
        private char[] val;
        
        public Simple(String initValSrc) {
            this();
            setChars(initValSrc);
        }
        
        public Simple(char[] initValSrc) {
            this();
            setChars(initValSrc);
        }
        
        public Simple() {
            val = new char[0];
        }
        
        @Override
        public char[] getVal() {
            return val;
        }
        
        @Override
        protected void newValHolder(int size) {
            val = new char[size];
        }
        
    }
    
    public static final class Advanced extends MutableString {
        
        private final int expectedMinLen;
        private final char[][] valHolders;
        /**
         * -1 = null/empty/use anyLenValHolder
         */
        private int valHolderInd = -1;
        private char[] anyLenValHolder = null;
        
        public Advanced(int expectedMinLen, int expectedMaxLen) {
            this.expectedMinLen = CheckUtils.positive(expectedMinLen);
            valHolders = new char[expectedMaxLen - expectedMinLen + 1][];
            for (int size = expectedMinLen; size <= expectedMaxLen; size++) {
                int ind = getValHolderInd(size);
                valHolders[ind] = new char[size];
                MemUtils.clearCharArray(valHolders[ind]);
            }
        }
        
        @Override
        public char[] getVal() {
            if (valHolderInd >= 0) {
                return valHolders[valHolderInd];
            } else if (anyLenValHolder != null) {
                return anyLenValHolder;
            } else {
                return new char[0];
            }
        }
        
        /**
         * @param size
         * @return the index of the value holder of the specified size, or -1 if unexpected size.
         */
        private int getValHolderInd(int size) {
            if (size < expectedMinLen) {
                return -1;
            }
            int res = size - expectedMinLen;
            return res < valHolders.length ? res : -1;
        }
        
        @Override
        protected void newValHolder(int size) {
            int newInd = getValHolderInd(size);
            if (newInd >= 0) {
                valHolderInd = newInd;
            } else {
                anyLenValHolder = new char[size];
                valHolderInd = -1;
            }
        }
        
        @Override
        public void resize(int newSize) {
            super.resize(newSize);
            if (valHolderInd >= 0 && anyLenValHolder != null) {
                anyLenValHolder = null;
            }
        }
        
    }
    
}
