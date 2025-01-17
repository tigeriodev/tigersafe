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

import javax.security.auth.DestroyFailedException;

import fr.tigeriodev.tigersafe.utils.CheckUtils;
import fr.tigeriodev.tigersafe.utils.DatetimeUtils;
import fr.tigeriodev.tigersafe.utils.MemUtils;
import fr.tigeriodev.tigersafe.utils.StringUtils;

public final class NewPasswordEntry extends PasswordEntry {
    
    private String name;
    private char[] password;
    private Instant lastPasswordChangeTime;
    private String site;
    private String info;
    private TOTP totp;
    
    NewPasswordEntry(String name) {
        this.name = CheckUtils.notNull(name);
        this.password = new char[0];
        this.lastPasswordChangeTime = DatetimeUtils.nowWithoutNanos();
        this.site = "";
        this.info = "";
    }
    
    @Override
    public String getCurrentName() {
        return name;
    }
    
    @Override
    protected void setNewName(String newValSrc, SafeDataManager dm) {
        String newVal = StringUtils.clone(newValSrc);
        try {
            dm.changePwEntryName(this, newVal);
        } catch (IllegalArgumentException ex) {
            MemUtils.tryClearString(newVal);
            throw ex;
        }
        MemUtils.tryClearString(name);
        name = newVal;
    }
    
    @Override
    public char[] getCurrentPassword() {
        return password;
    }
    
    @Override
    protected void setNewPassword(char[] newValSrc) {
        if (password != null) {
            MemUtils.clearCharArray(password);
        }
        password = newValSrc.clone();
        lastPasswordChangeTime = DatetimeUtils.nowWithoutNanos();
    }
    
    @Override
    public Instant getCurrentLastPasswordChangeTime() {
        return lastPasswordChangeTime;
    }
    
    @Override
    public String getCurrentSite() {
        return site;
    }
    
    @Override
    protected void setNewSite(String newValSrc) {
        MemUtils.tryClearString(site);
        site = StringUtils.clone(newValSrc);
    }
    
    @Override
    public String getCurrentInfo() {
        return info;
    }
    
    @Override
    protected void setNewInfo(String newValSrc) {
        MemUtils.tryClearString(info);
        info = StringUtils.clone(newValSrc);
    }
    
    @Override
    public TOTP getCurrentTOTP() {
        return totp;
    }
    
    @Override
    protected void setNewTOTP(TOTP newValSrc) {
        if (totp != null) {
            MemUtils.tryDestroy(totp);
        }
        totp = newValSrc != null ? newValSrc.duplicate() : null;
    }
    
    @Override
    public Data getData() {
        try {
            return new Data(
                    getCurrentName(),
                    getCurrentPassword(),
                    getCurrentLastPasswordChangeTime(),
                    getCurrentSite(),
                    getCurrentInfo(),
                    getCurrentTOTP()
            );
        } catch (NullPointerException | IllegalArgumentException ex) {
            return null;
        }
    }
    
    @Override
    public void destroy() throws DestroyFailedException {
        boolean success = true;
        
        if (password != null) {
            MemUtils.clearCharArray(password);
            password = null;
        }
        
        if (totp != null) {
            success = MemUtils.tryDestroy(totp) && success;
            totp = null;
        }
        
        success = MemUtils.tryClearString(name) && success;
        name = null;
        
        success = MemUtils.tryClearString(site) && success;
        site = null;
        
        success = MemUtils.tryClearString(info) && success;
        info = null;
        
        lastPasswordChangeTime = null;
        
        if (!success) {
            throw new DestroyFailedException();
        }
    }
    
    @Override
    public boolean isDestroyed() {
        return name == null && password == null && site == null && info == null && totp == null;
    }
    
}
