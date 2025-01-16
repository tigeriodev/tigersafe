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
import java.util.Objects;

import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.DatetimeUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public final class ExistingPasswordEntry extends PasswordEntry {
    
    public final Data originalData;
    private String newName = null;
    private char[] newPassword = null;
    private Instant newLastPasswordChangeTime = null;
    private String newSite = null;
    private String newInfo = null;
    private boolean hasNewTOTP = false;
    private TOTP newTOTP = null;
    
    ExistingPasswordEntry(Data originalData) {
        this.originalData = CheckUtils.notNull(originalData);
    }
    
    @Override
    public String getCurrentName() {
        return newName != null ? newName : originalData.name;
    }
    
    @Override
    protected void setNewName(String newValSrc, SafeDataManager dm) {
        if (!originalData.name.equals(newValSrc)) {
            String newVal = StringUtils.clone(newValSrc);
            try {
                dm.changePwEntryName(this, newVal);
            } catch (IllegalArgumentException ex) {
                MemUtils.tryClearString(newVal);
                throw ex;
            }
            if (newName != null) {
                MemUtils.tryClearString(newName);
            }
            newName = newVal;
        } else {
            dm.changePwEntryName(this, originalData.name);
            if (newName != null) {
                MemUtils.tryClearString(newName);
            }
            newName = null;
        }
    }
    
    @Override
    public char[] getCurrentPassword() {
        return newPassword != null ? newPassword : originalData.getPassword();
    }
    
    @Override
    protected void setNewPassword(char[] newValSrc) {
        if (newPassword != null) {
            MemUtils.clearCharArray(newPassword);
        }
        boolean isDiffPw = !Arrays.equals(originalData.getPassword(), newValSrc);
        newPassword = isDiffPw ? newValSrc.clone() : null;
        newLastPasswordChangeTime = isDiffPw ? DatetimeUtils.nowWithoutNanos() : null;
    }
    
    @Override
    public Instant getCurrentLastPasswordChangeTime() {
        return newLastPasswordChangeTime != null
                ? newLastPasswordChangeTime
                : originalData.lastPasswordChangeTime;
    }
    
    @Override
    public String getCurrentSite() {
        return newSite != null ? newSite : originalData.site;
    }
    
    @Override
    protected void setNewSite(String newValSrc) {
        if (newSite != null) {
            MemUtils.tryClearString(newSite);
        }
        newSite = !originalData.site.equals(newValSrc) ? StringUtils.clone(newValSrc) : null;
    }
    
    @Override
    public String getCurrentInfo() {
        return newInfo != null ? newInfo : originalData.info;
    }
    
    @Override
    protected void setNewInfo(String newValSrc) {
        if (newInfo != null) {
            MemUtils.tryClearString(newInfo);
        }
        newInfo = !originalData.info.equals(newValSrc) ? StringUtils.clone(newValSrc) : null;
    }
    
    @Override
    public TOTP getCurrentTOTP() {
        return hasNewTOTP ? newTOTP : originalData.totp;
    }
    
    @Override
    protected void setNewTOTP(TOTP newValSrc) {
        if (newTOTP != null) {
            MemUtils.tryDestroy(newTOTP);
        }
        if (!Objects.equals(originalData.totp, newValSrc)) {
            hasNewTOTP = true;
            newTOTP = newValSrc != null ? newValSrc.duplicate() : null;
        } else {
            hasNewTOTP = false;
            newTOTP = null;
        }
    }
    
    public boolean isModified() {
        return newName != null
                || newPassword != null
                || newLastPasswordChangeTime != null
                || newSite != null
                || newInfo != null
                || hasNewTOTP;
    }
    
    @Override
    public Data getData() {
        return isModified()
                ? new Data(
                        getCurrentName(),
                        getCurrentPassword(),
                        getCurrentLastPasswordChangeTime(),
                        getCurrentSite(),
                        getCurrentInfo(),
                        getCurrentTOTP()
                )
                : originalData;
    }
    
    @Override
    public String toString() {
        return "ExistingPasswordEntry [originalData=" + originalData + ", newName=" + newName
                + ", newPassword=" + StringUtils.charArrayToObfuscatedStr(newPassword)
                + ", newLastPasswordChangeTime=" + newLastPasswordChangeTime + ", newSite="
                + newSite + ", newInfo=" + newInfo + ", hasNewTOTP=" + hasNewTOTP + ", newTOTP="
                + newTOTP + "]";
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = true;
        success = MemUtils.tryDestroy(originalData) && success;
        
        if (newPassword != null) {
            MemUtils.clearCharArray(newPassword);
            newPassword = null;
        }
        
        if (newTOTP != null) {
            success = MemUtils.tryDestroy(newTOTP) && success;
            newTOTP = null;
        }
        hasNewTOTP = false;
        
        success = MemUtils.tryClearString(newName) && success;
        newName = null;
        
        success = MemUtils.tryClearString(newSite) && success;
        newSite = null;
        
        success = MemUtils.tryClearString(newInfo) && success;
        newInfo = null;
        
        newLastPasswordChangeTime = null;
        
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return originalData.isDestroyed();
    }
    
}
